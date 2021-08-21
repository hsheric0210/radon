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
 * Replaces INSTANCEOF + IFEQ/IFNE with a semantically equivalent try-catch block.
 *
 * @author ItzSomebody, hsheric0210
 */
public class InstanceofCheckMutilator extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final MethodNode methodNode = mw.methodNode;

			final StackHeightZeroFinder shzf = new StackHeightZeroFinder(methodNode, mw.getInstructions().getLast());
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
			int leeway = mw.getLeewaySize();

			final InsnList insns = methodNode.instructions;
			for (final AbstractInsnNode insn : insns.toArray())
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

						final InsnList tcInsns = new InsnList();
						tcInsns.add(trapStart);
						tcInsns.add(new TypeInsnNode(CHECKCAST, instCheck.desc));
						tcInsns.add(new InsnNode(POP)); // Ignore the return value of method
						tcInsns.add(trapEnd);

						// TODO: Insert Trash Codes
						tcInsns.add(new JumpInsnNode(GOTO, opcode == IFEQ ? catchEnd : jumpTarget));
						tcInsns.add(catchStart);
						tcInsns.add(new InsnNode(POP)); // Ignore the catch block parameter
						if (opcode == IFEQ)
							tcInsns.add(new JumpInsnNode(GOTO, jumpTarget));
						tcInsns.add(catchEnd);

						insns.insert(insn, tcInsns);
						insns.remove(insn);
						insns.remove(jump);
						methodNode.tryCatchBlocks.add(0, new TryCatchBlockNode(trapStart, trapEnd, catchStart, Throwables.ClassCastException));

						counter.incrementAndGet();
						leeway -= ASMUtils.evaluateMaxSize(tcInsns);
					}
				}
			}
		}));

		info("+ Mutilated " + counter.get() + " instanceof checks");
	}

	@Override
	public String getName()
	{
		return "Instanceof Check Mutilator";
	}
}
