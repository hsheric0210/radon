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
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Throwables;

/**
 * Replaces IFNONNULL and IFNULL with a semantically equivalent try-catch block. This relies on the fact that {@link NullPointerException} is thrown when a method is invoked upon null. Very similar to https://www.sable.mcgill.ca/JBCO/examples.html#RIITCB
 *
 * @author ItzSomebody
 */
public class InstanceOfCheckMutilator extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final MethodNode methodNode = mw.getMethodNode();

			final StackHeightZeroFinder shzf = new StackHeightZeroFinder(methodNode, mw.getInstructions().getLast());
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
			int leeway = mw.getLeewaySize();

			for (final AbstractInsnNode insn : methodNode.instructions.toArray())
			{
				if (leeway < 10000)
					break;

				if (insn.getOpcode() == INSTANCEOF && insn.getNext() != null)
				{
					final TypeInsnNode instCheck = (TypeInsnNode) insn;

					final int opcode = insn.getNext().getOpcode();
					if (opcode == IFEQ || opcode == IFNE)
					{
						final JumpInsnNode jump = (JumpInsnNode) insn.getNext();
						final LabelNode jumpTarget = jump.label;

						if (!emptyAt.contains(jumpTarget))
							continue;

						final LabelNode trapStart = new LabelNode();
						final LabelNode trapEnd = new LabelNode();
						final LabelNode catchStart = new LabelNode();
						final LabelNode catchEnd = new LabelNode();

						final InsnList insns = new InsnList();
						insns.add(trapStart);
						insns.add(new TypeInsnNode(CHECKCAST, instCheck.desc));
						insns.add(new InsnNode(POP)); // Ignore the return value of method
						insns.add(trapEnd);

						insns.add(new JumpInsnNode(GOTO, opcode == IFEQ ? catchEnd : jumpTarget));
						insns.add(catchStart);
						insns.add(new InsnNode(POP)); // Ignore the catch block parameter
						if (opcode == IFEQ)
							insns.add(new JumpInsnNode(GOTO, jumpTarget));
						insns.add(catchEnd);

						methodNode.instructions.insert(insn, insns);
						methodNode.instructions.remove(insn);
						methodNode.instructions.remove(jump);
						methodNode.tryCatchBlocks.add(0, new TryCatchBlockNode(trapStart, trapEnd, catchStart, Throwables.ClassCastException));

						counter.incrementAndGet();
						leeway -= ASMUtils.evaluateMaxSize(insns);
					}
				}
			}
		}));

		info("+ Mutilated " + counter.get() + " instanceof checks");
	}
}
