/*
 * Radon - An open-source Java obfuscator
 * Copyright (C) 2019 ItzSomebody
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package me.itzsomebody.radon.transformers.obfuscators.flow;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.BogusJumps;
import me.itzsomebody.radon.utils.CodeGenerator;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts opaque predicates which always evaluate to false but are meant to insert significantly more edges to a control flow graph.
 * To determine where we should insert the conditions, we use an analyzer to determine where the stack is empty.
 * This leads to less complication when applying obfuscation.
 *
 * <p>
 * TODO: Bogus jumps based on INSTANCEOF
 * </p>
 * 
 * <p>
 * TODO: Insert bogus conditions between original conditions
 * 
 * <pre>
 *     FROM:
 *     if (a == 0 && b == 1)
 *     {
 *         - ORIGINAL CODE 1
 *     }
 *     else
 *     {
 *         - ORIGINAL CODE 2
 *     }
 *
 *     TO:
 *     if (FAKECONDITION ALWAYS RETURN TRUE && a == 0 && b == 1 || FAKECONDITION ALWAYS RETURN FALSE)
 *     {
 *         - ORIGINAL CODE 1
 *     }
 *     else if (FAKECONDITION ALWAYS RETURN TRUE)
 *     {
 *         - ORIGINAL CODE 2
 *     }
 *     else
 *     {
 *         - TRASH CODE
 *     }
 * </pre>
 * </p>
 *
 * <p>
 * TODO: Jump To Random Label
 * 
 * <pre>
 *     IF (FAKECONDITION ALWAYS RETURN FALSE) GOTO [Random Label]
 * </pre>
 * </p>
 *
 * @author ItzSomebody
 */
public class BogusJumpInserter extends FlowObfuscation
{
	private static final int CLASS_PRED_ACCESS = ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
	private static final int INTERFACE_PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger bogusPredicates = new AtomicInteger();
		final AtomicInteger bogusLoops = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();

			final Type predicateType = ASMUtils.getRandomType();
			final String predicateDescriptor = predicateType.getDescriptor();
			final Object predicateInitialValue = RandomUtils.getRandomFloat() > 0.2F ? RandomUtils.getRandomValue(predicateType) : null;

			// TODO: 여러 개의 필드를 만든 후, 메서드에서 필드 값을 가져와 로컬변수에 저장할 때, (미리 만들어 놓은 여러 개의 필드들 중에서) 손에 잡히는 대로 골라잡아(랜덤하게) 쓰면 조금 더 헷갈리게 할 수 있지 않을까?
			final FieldNode predicate = new FieldNode(cw.access.isInterface() ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, getFieldDictionary(cw.originalName).nextUniqueString(), predicateDescriptor, null, predicateInitialValue);

			for (final MethodWrapper mw : cw.methods)
				if (included(mw) && mw.hasInstructions())
				{
					final InsnList insns = mw.getInstructions();

					int leeway = mw.getLeewaySize();
					final int varIndex = mw.getMaxLocals();
					mw.methodNode.maxLocals += predicateType.getSize(); // Prevents breaking of other transformers which rely on this field.

					final AbstractInsnNode[] untouchedList = insns.toArray();

					// TODO: Trap Label을 여러 개를 만든 후, 랜덤하게 골라잡아 사용하면 더 헷갈리게 할 수 있지 않을까?
					// TODO: 단순히 미리 만들어둔 trap label로 점프시키는 것이 아니라, if문의 'else' branch 내의 코드의 내용을 교묘하게 변조한 후 그 코드로 점프하게 함으로써 더 헷갈리게 할 수 있지 않을까?
					final LabelNode trapLabel = createTrap(mw.methodNode);

					final StackHeightZeroFinder shzf = new StackHeightZeroFinder(mw.methodNode, insns.getLast());
					try
					{
						shzf.execute();
					}
					catch (final StackEmulationException e)
					{
						e.printStackTrace();
						throw new RadonException(String.format("Error happened while trying to emulate the stack of %s.%s%s", cw.getName(), mw.getName(), mw.getDescription()));
					}
					final Set<AbstractInsnNode> emptyAt = shzf.getEmptyAt();

					final boolean isCtor = "<init>".equals(mw.getName());
					boolean calledSuper = false;
					for (final AbstractInsnNode insn : untouchedList)
					{
						if (leeway < 15000)
							break;

						// Bad way of detecting if this class was instantiated
						if (isCtor && !calledSuper)
							calledSuper = ASMUtils.isSuperInitializerCall(insn);

						if (insn != insns.getFirst() && !(insn instanceof LineNumberNode))
						{
							if (isCtor && !calledSuper)
								continue;

							// We need to make sure stack is empty before making jumps
							if (emptyAt.contains(insn))
							{
								// Insert bogus opaque conditional expressions
								if (/* RandomUtils.getRandomBoolean() */false)
								{
									// if (false) throw null;
									// original code...

									final InsnList bogusJump = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, trapLabel, false);
									insns.insertBefore(insn, bogusJump);
									leeway -= ASMUtils.evaluateMaxSize(bogusJump);
								}
								else
								{
									// if (true) goto labelSkip
									// - throw null;
									// labelSkip:
									// - original code...
									final InsnList insnList = new InsnList();
									final LabelNode rescueLabel = new LabelNode();
									final InsnList bogusJump = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, rescueLabel, true);
									insnList.add(bogusJump);
									insnList.add(new JumpInsnNode(GOTO, trapLabel));
									insnList.add(rescueLabel);

									insns.insertBefore(insn, insnList);
									leeway -= ASMUtils.evaluateMaxSize(insnList);
								}
								bogusPredicates.incrementAndGet();

								shouldAdd.set(true);
							}
						}
					}

					if (shouldAdd.get())
					{
						final InsnList initializer = new InsnList();
						initializer.add(new FieldInsnNode(GETSTATIC, cw.getName(), predicate.name, predicateDescriptor));
						switch (predicateType.getSort())
						{
							case Type.FLOAT:
								initializer.add(new VarInsnNode(FSTORE, varIndex));
								break;
							case Type.LONG:
								initializer.add(new VarInsnNode(LSTORE, varIndex));
								break;
							case Type.DOUBLE:
								initializer.add(new VarInsnNode(DSTORE, varIndex));
								break;
							default:
								initializer.add(new VarInsnNode(ISTORE, varIndex));
								break;
						}

						ASMUtils.insertAfterConstructorCall(mw.methodNode, initializer);
					}
				}

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		info("+ Inserted " + bogusPredicates.get() + " bogus predicates.");
		info("+ Inserted " + bogusLoops.get() + " bogus loops.");
	}

	@Override
	public String getName()
	{
		return "Bogus Jump Inserter";
	}

	/**
	 * Generates a generic "escape" pattern to avoid inserting multiple copies of the same bytecode instructions.
	 *
	 * @param  mn
	 *            the {@link MethodNode} we are inserting into.
	 * @return    a {@link LabelNode} which "escapes" all other flow.
	 */
	private static LabelNode createTrap(final MethodNode mn)
	{
		final LabelNode label = new LabelNode();
		final LabelNode escapeNode = new LabelNode();

		final InsnList pattern = new InsnList();
		pattern.add(new JumpInsnNode(GOTO, escapeNode));
		pattern.add(label);
		pattern.add(CodeGenerator.generateTrapInstructions(mn));
		pattern.add(escapeNode);
		ASMUtils.insertAfterConstructorCall(mn, pattern);

		return label;
	}
}
