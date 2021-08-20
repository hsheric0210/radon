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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.ArrayUtils;
import me.itzsomebody.radon.utils.BogusJumps;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts opaque predicates which always evaluate to false but are meant to insert significantly more edges to a control flow graph.
 * To determine where we should insert the conditions, we use an analyzer to determine where the stack is empty.
 * This leads to less complication when applying obfuscation.
 *
 * TODO: Improve strength as ZKM :)
 * TODO: Bogus jumps based on INSTANCEOF
 *
 * @author ItzSomebody
 */
public class BogusJumpInserter extends FlowObfuscation
{
	private static final int CLASS_PRED_ACCESS = ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC;
	private static final int INTERFACE_PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger fakePredicates = new AtomicInteger();
		final AtomicInteger fakeLoops = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();

			final Type predicateType = ASMUtils.getRandomType();
			final String predicateDescriptor = predicateType.getDescriptor();
			final Object predicateInitialValue = RandomUtils.getRandomFloat() > 0.2F ? RandomUtils.getRandomValue(predicateType) : null;

			final FieldNode predicate = new FieldNode(cw.access.isInterface() ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, getFieldDictionary(cw.originalName).nextUniqueString(), predicateDescriptor, null, predicateInitialValue);

			for (MethodWrapper mw : cw.methods)
			{
				if (included(mw) && mw.hasInstructions())
				{
					final MethodNode mn = mw.methodNode;

					final int maxStack = mn.maxStack;
					final int maxLocals = mn.maxLocals;
					mn.maxStack = mn.maxLocals = 1000;

					final Frame<BasicValue>[] frames;
					try
					{
						frames = new Analyzer<>(new BasicInterpreter()).analyze(mn.name, mn);
					}
					catch (final AnalyzerException e)
					{
						warn("Failed to analyze method " + mn.name, e);
						continue;
					}
					finally
					{
						mn.maxStack = maxStack;
						mn.maxLocals = maxLocals;
					}

					final InsnList insns = mw.getInstructions();

					int leeway = mw.getLeewaySize();
					final int varIndex = mw.getMaxLocals();
					mw.methodNode.maxLocals += predicateType.getSize(); // Prevents breaking of other transformers which rely on this field.

					final AbstractInsnNode[] untouchedList = insns.toArray();
					final LabelNode jumpTo = createBogusJumpTarget(mw.methodNode);

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
					final List<LabelNode> labels = emptyAt.stream().filter(insn -> insn instanceof LabelNode).map(insn -> (LabelNode) insn).collect(Collectors.toList());

					final boolean isCtor = "<init>".equals(mw.getName());
					boolean calledSuper = false;
					AbstractInsnNode currentLabel = null;
					for (final AbstractInsnNode insn : untouchedList)
					{
						if (insn instanceof LabelNode)
							currentLabel = insn;

						if (insn instanceof FrameNode)

							if (leeway < 10000)
								break;

						// Bad way of detecting if this class was instantiated
						if (isCtor && !calledSuper)
							calledSuper = ASMUtils.isSuperInitializerCall(mw.methodNode, insn);

						if (insn != insns.getFirst() && !(insn instanceof LineNumberNode))
						{
							if (isCtor && !calledSuper)
								continue;

							if (emptyAt.contains(insn))
							{
								// We need to make sure stack is empty before making jumps

								// Type A:
								// L0
								// - ORIGINAL CODE...
								// L1
								// - IF (FAKEPREDICATE ALWAYS RETURN TRUE) GOTO L3
								//
								// - TRASH CODE...
								// L2
								// - GOTO L0
								// L3
								// - Original Codes...

								// Type B:
								// L0
								// - ORIGINAL CODE...
								// L1
								// - IF (FAKEPREDICATE ALWAYS RETURN FALSE) GOTO L0
								//
								// - TRASH CODE...
								// L2
								// - GOTO L0
								// L3
								// - Original Codes...

								if (labels.size() > 3 && RandomUtils.getRandomBoolean()) // Jump to the random label
								{
									final Frame<BasicValue> currentFrame = frames[ArrayUtils.indexOf(untouchedList, insn)];
									if (currentFrame != null)
									{
										final InsnList insertedBefore = new InsnList();
										final InsnList insertAfter = new InsnList();

										final LabelNode startLabel = new LabelNode();
										final LabelNode endLabel = new LabelNode();

										insertedBefore.add(startLabel);
										insertedBefore.add(ASMUtils.createStackMapFrame(F_NEW, currentFrame));

										final int currentLabelIndex = labels.indexOf(currentLabel);

										insertAfter.add(new LabelNode());

										if (RandomUtils.getRandomBoolean())
										{
											// Exclude the current label and adjacent two labels
											insertAfter.add(BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, endLabel, true));

											insertAfter.add(new LabelNode());
											insertAfter.add(new JumpInsnNode(GOTO, labels.get(RandomUtils.getRandomIntWithExclusion(0, labels.size(), Arrays.asList(currentLabelIndex - 1, currentLabelIndex, currentLabelIndex + 1)))));
										}
										else
										{
											// Exclude the current label and adjacent two labels
											insertAfter.add(BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, startLabel, false));
										}

										insertAfter.add(endLabel);
										fakeLoops.incrementAndGet();
									}
								}
								else
								{
									// Insert fake IF's
									final InsnList inserted = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, jumpTo, false);
									insns.insertBefore(insn, inserted);
									leeway -= ASMUtils.evaluateMaxSize(inserted);
									fakePredicates.incrementAndGet();
								}

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
			}

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		info(String.format("+ Inserted %d bogus predicates, %d bogus loops (jumps)", fakePredicates.get(), fakeLoops.get()));
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
	private static LabelNode createBogusJumpTarget(final MethodNode mn)
	{
		final LabelNode label = new LabelNode();
		final LabelNode escapeNode = new LabelNode();

		final InsnList pattern = new InsnList();
		pattern.add(new JumpInsnNode(GOTO, escapeNode));
		pattern.add(label);
		pattern.add(BogusJumps.createBogusExit(mn));
		pattern.add(escapeNode);
		ASMUtils.insertAfterConstructorCall(mn, pattern);

		return label;
	}
}
