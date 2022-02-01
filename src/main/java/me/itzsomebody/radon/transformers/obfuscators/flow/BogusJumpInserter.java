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
import me.itzsomebody.radon.config.Configuration;
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
 * <ul>
 * <li>TODO: Bogus jumps based on {@code instanceof} operator ({@code INSTANCEOF + IFEQ/IFNE})</li>
 * <li>TODO: 여러 개의 필드를 만든 후, 메서드에서 필드 값을 가져와 로컬변수에 저장할 때, (미리 만들어 놓은 여러 개의 필드들 중에서) 손에 잡히는 대로 골라잡아(랜덤하게) 쓰면 조금 더 헷갈리게 할 수 있지 않을까?</li>
 * <li>TODO: Trap Label을 여러 개를 만든 후, 랜덤하게 골라잡아 사용하면 더 헷갈리게 할 수 있지 않을까?</li>
 * <li>
 * TODO: 단순히 미리 만들어둔 trap label로 점프시키는 것이 아니라, if문의 'else' branch 내의 코드의 내용을 교묘하게 변조한 후 그 코드로 점프하게 함으로써 더 헷갈리게 할 수 있지 않을까?
 * 이때, BogusSwitchJumpInserter를 참고하면 편하게 코딩할 수 있을 것 같다.
 * </li>
 * <li>
 * TODO: 현재의 구현은, 미리 만들어 둔 필드에서 값을 읽어와 이를 위해 새로 만든 로컬 변수에 저장한 후, 그 로컬 변수를 읽어들인 후 조건문을 작성하는 식으로 구현이 되어있다.
 * 이를 asm에서 제공하는(또는 직접 만들어서) Analyzer을 새로 만든 것이 아닌 이미 이전부터 존재했던 로컬 변수들의 현재 값을 추적하고, 이를 기반으로 bogus jump를 작성하는 식으로 하면 효율과 코드 길이, 난독성 모든 면에서 이득을 볼 수 있을 것으로 보인다.
 * 보아하니, 이전부터 존재했던 로컬 변수들의 값을 추적하는 것에 사용할 Analyzer는 그냥 내가 새로 만드는 것이 제일 빠르고 편할 것 같다.
 * </li>
 * <li>
 * <p>
 * TODO: Insert opaque bogus conditions between original conditions
 * 
 * <pre>
 * FROM:
 * {@code
 * if (a == 0 && b == 1)
 * {
 *     *** Original code in 'if' branch
 * }
 * else
 * {
 *     *** Original code in 'else' branch
 * }
 * }
 * 
 * TO:
 * {@code
 * if (opaqueConditionAlwaysReturnTrue && a == 0 && b == 1 || opaqueConditionAlwaysReturnFalse)
 * {
 *     *** Original code in 'if' branch
 * }
 * else if (FAKECONDITION ALWAYS RETURN TRUE)
 * {
 *     *** Original code in 'else' branch
 * }
 * else
 *     *** Dummy code
 * }
 * 
 * </pre>
 * 
 * 
 * </p>
 * </li>
 * </ul>
 * </p>
 *
 * @author ItzSomebody
 */
public class BogusJumpInserter extends FlowObfuscation
{
	private static final int CLASS_PRED_ACCESS = ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
	private static final int INTERFACE_PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	private boolean insertAlwaysSucceedingPredicate;
	private boolean insertAlwaysFailingPredicate;
	private boolean insertAlwaysSucceedingLoop;
	private boolean insertAlwaysFailingLoop;

	@Override
	public void transform()
	{
		final AtomicInteger bogusSucceedingPredicates = new AtomicInteger();
		final AtomicInteger bogusFailingPredicates = new AtomicInteger();
		final AtomicInteger bogusSucceedingLoops = new AtomicInteger();
		final AtomicInteger bogusFailingLoops = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();

			final Type predicateType = ASMUtils.getRandomType();
			final String predicateDescriptor = predicateType.getDescriptor();
			final Object predicateInitialValue = RandomUtils.getRandomFloat() > 0.2F ? RandomUtils.getRandomValue(predicateType) : null;

			final FieldNode predicate = new FieldNode(cw.access.isInterface() ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, getFieldDictionary(cw.originalName).nextUniqueString(), predicateDescriptor, null, predicateInitialValue);

			for (final MethodWrapper mw : cw.methods)
				if (included(mw) && mw.hasInstructions())
				{
					final MethodNode methodNode = mw.methodNode;

					int leeway = mw.getLeewaySize();
					final int varIndex = mw.getMaxLocals();
					methodNode.maxLocals += predicateType.getSize(); // Prevents breaking of other transformers which rely on this field.

					final InsnList insns = mw.getInstructions();
					final AbstractInsnNode[] untouchedList = insns.toArray();
					final LabelNode predefinedTrapLabel = createTrap(methodNode);

					final StackHeightZeroFinder shzf = new StackHeightZeroFinder(methodNode, insns.getLast());
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
							// We need to make sure stack is empty before making jumps
							if (isCtor && !calledSuper || !emptyAt.contains(insn))
								continue;

							// Insert bogus opaque conditional expressions
							if (RandomUtils.getRandomBoolean())
							{
								final InsnList insertBefore = new InsnList();
								final InsnList insert = new InsnList();

								final LabelNode loopStartLabel = new LabelNode();
								final LabelNode originalCodeStartLabel = new LabelNode();

								insertBefore.add(loopStartLabel); // Label that indicates 'start of loop'

								if (RandomUtils.getRandomBoolean())
								{
									// do-while with false-predicate trap and continue
									final LabelNode trapLabel = new LabelNode();

									insert.add(new LabelNode());
									insert.add(BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, trapLabel, false));
									insert.add(new LabelNode());
									insert.add(new JumpInsnNode(GOTO, originalCodeStartLabel));
									insert.add(trapLabel);
									insert.add(CodeGenerator.generateTrapInstructions(methodNode));
									insert.add(new JumpInsnNode(GOTO, loopStartLabel)); // Loop
									insert.add(originalCodeStartLabel);

									bogusFailingLoops.incrementAndGet();
								}
								else
								{
									// while with true-condition trap and continue
									final LabelNode firstAfterLoopEndLabel = new LabelNode();

									insertBefore.add(BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, originalCodeStartLabel, true));
									insertBefore.add(new LabelNode());
									insertBefore.add(CodeGenerator.generateTrapInstructions(methodNode));
									insertBefore.add(new LabelNode());
									insertBefore.add(new JumpInsnNode(GOTO, loopStartLabel));
									insertBefore.add(originalCodeStartLabel);

									insert.add(new LabelNode());
									insert.add(new JumpInsnNode(GOTO, firstAfterLoopEndLabel));
									insert.add(firstAfterLoopEndLabel);

									bogusSucceedingLoops.incrementAndGet();
								}

								insns.insertBefore(insn, insertBefore);
								insns.insert(insn, insert);
								leeway -= ASMUtils.evaluateMaxSize(insertBefore) + ASMUtils.evaluateMaxSize(insert);
							}
							else if (RandomUtils.getRandomBoolean())
							{
								// if (false) throw null;
								// original code...

								final InsnList bogusJump = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, predefinedTrapLabel, false);
								insns.insertBefore(insn, bogusJump);
								leeway -= ASMUtils.evaluateMaxSize(bogusJump);

								bogusFailingPredicates.incrementAndGet();
							}
							else
							{
								// if (true) goto labelSkip
								// - throw null;
								// labelSkip:
								// - original code...
								final InsnList insnList = new InsnList();
								final LabelNode originalCodeStartLabel = new LabelNode();
								final InsnList bogusJump = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, originalCodeStartLabel, true);
								insnList.add(bogusJump);
								insnList.add(new JumpInsnNode(GOTO, predefinedTrapLabel));
								insnList.add(originalCodeStartLabel);

								insns.insertBefore(insn, insnList);
								leeway -= ASMUtils.evaluateMaxSize(insnList);

								bogusSucceedingPredicates.incrementAndGet();
							}

							shouldAdd.set(true);
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

						ASMUtils.insertAfterConstructorCall(methodNode, initializer);
					}
				}

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		info("+ Inserted " + bogusSucceedingPredicates.get() + " always-succeeding bogus predicates." + (insertAlwaysSucceedingPredicate ? "" : " (Disabled in config)"));
		info("+ Inserted " + bogusFailingPredicates.get() + " always-failing bogus predicates." + (insertAlwaysFailingPredicate ? "" : " (Disabled in config)"));
		info("+ Inserted " + bogusSucceedingLoops.get() + " always-succeeding bogus loops." + (insertAlwaysSucceedingLoop ? "" : " (Disabled in config)"));
		info("+ Inserted " + bogusFailingLoops.get() + " always-failing bogus loops." + (insertAlwaysFailingLoop ? "" : " (Disabled in config)"));
	}

	@Override
	public String getName()
	{
		return "Bogus Jump Inserter";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
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
