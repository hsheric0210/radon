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

import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.BogusJumps;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts opaque predicates which always evaluate to false but are meant to insert significantly more edges to a control flow graph.
 * To determine where we should insert the conditions, we use an analyzer to determine where the stack is empty.
 * This leads to less complication when applying obfuscation.
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
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();

			final Type predicateType = ASMUtils.getRandomType();
			final String predicateDescriptor = predicateType.getDescriptor();
			final Object predicateInitialValue = RandomUtils.getRandomFloat() > 0.2F ? RandomUtils.getRandomValue(predicateType) : null;

			final FieldNode predicate = new FieldNode((cw.getAccessFlags() & ACC_INTERFACE) != 0 ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, getFieldDictionary(cw.getOriginalName()).nextUniqueString(), predicateDescriptor, null, predicateInitialValue);

			cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				int leeway = mw.getLeewaySize();
				final int varIndex = mw.getMaxLocals();
				mw.getMethodNode().maxLocals += predicateType.getSize(); // Prevents breaking of other transformers which rely on this field.

				final AbstractInsnNode[] untouchedList = insns.toArray();
				final LabelNode jumpTo = createBogusJumpTarget(mw.getMethodNode());

				final StackHeightZeroFinder shzf = new StackHeightZeroFinder(mw.getMethodNode(), insns.getLast());
				try
				{
					shzf.execute(false);
				}
				catch (final StackEmulationException e)
				{
					e.printStackTrace();
					throw new RadonException(String.format("Error happened while trying to emulate the stack of %s.%s%s", cw.getName(), mw.getName(), mw.getDescription()));
				}
				final Set<AbstractInsnNode> emptyAt = shzf.getEmptyAt();

				final boolean isCtor = "<init>".equals(mw.getName());
				boolean calledSuper = false;
				AbstractInsnNode superCall = null;
				for (final AbstractInsnNode insn : untouchedList)
				{
					if (leeway < 10000)
						break;

					// Bad way of detecting if this class was instantiated
					if (isCtor && !calledSuper)
					{
						calledSuper = ASMUtils.isSuperCall(mw.getMethodNode(), insn);
						superCall = insn;
					}

					if (insn != insns.getFirst() && !(insn instanceof LineNumberNode))
					{
						if (isCtor && !calledSuper)
							continue;

						if (emptyAt.contains(insn))
						{
							// We need to make sure stack is empty before making jumps

							// <TODO>
							// while(true) [or for (;;)]
							// {
							// ... (original code)
							//
							// if (inverted fakepredicate)
							// break;
							// }
							// which same as
							// do {
							// i++;
							// } while (fakepredicate);
							//
							// and
							//
							// while(inverted fakepredicate) [or for(;inverted fakepredicate;)]
							// {
							// ... (original code)
							// }
							//
							//
							// Impl:
							// L0
							// - Original Codes...
							// L1
							// - IF (FAKEPREDICATE) GOTO L3
							// L2
							// - GOTO L0
							// L3
							// - Original Codes...
							//
							// </TODO>

							final InsnList fakeJump = BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, jumpTo, false);
							insns.insertBefore(insn, fakeJump);

							leeway -= ASMUtils.evaluateMaxSize(insns);
							counter.incrementAndGet();
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

					if (superCall == null)
						insns.insert(initializer);
					else
						insns.insert(superCall, initializer);
				}
			});

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		info(String.format("+ Inserted %d bogus jumps", counter.get()));
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
		final InsnList insnList = mn.instructions;
		final AbstractInsnNode first = insnList.getFirst();

		final InsnList pattern = new InsnList();
		pattern.add(new JumpInsnNode(GOTO, escapeNode));
		pattern.add(label);
		pattern.add(BogusJumps.createBogusExit(mn));
		pattern.add(escapeNode);
		insnList.insertBefore(first, pattern);

		return label;
	}
}
