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
import me.itzsomebody.radon.utils.RandomUtils;
import me.itzsomebody.radon.utils.Throwables;

/**
 * Replaces IFNONNULL and IFNULL with a semantically equivalent try-catch block.
 * This relies on the fact that {@link NullPointerException} is thrown when a method is invoked upon null.
 * Very similar to https://www.sable.mcgill.ca/JBCO/examples.html#RIITCB
 *
 * @author ItzSomebody
 */
public class NullCheckMutilator extends FlowObfuscation
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
				shzf.execute(false);
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

				if (insn.getOpcode() == IFNULL || insn.getOpcode() == IFNONNULL)
				{
					final JumpInsnNode jump = (JumpInsnNode) insn;
					final LabelNode jumpTarget = jump.label;

					if (!emptyAt.contains(jumpTarget))
						// When an exception is caught, the stack is first cleared before pushing the exception instance
						// when the instruction pointer is moved into the catch block. So we have to make sure the stack
						// is empty at the jump site if we're gonna swap out the ifnull/ifnonnull with a try-catch
						continue;

					final LabelNode trapStart = new LabelNode();
					final LabelNode trapEnd = new LabelNode();
					final LabelNode catchStart = new LabelNode();
					final LabelNode catchEnd = new LabelNode();

					final InsnList tcInsns = new InsnList();
					tcInsns.add(trapStart);
					tcInsns.add(createNPERaiser()); // Inject NPE Raiser
					tcInsns.add(new InsnNode(POP)); // Ignore the return value of method
					tcInsns.add(trapEnd);

					tcInsns.add(new JumpInsnNode(GOTO, insn.getOpcode() == IFNULL ? catchEnd : jumpTarget));
					tcInsns.add(catchStart);
					tcInsns.add(new InsnNode(POP)); // Ignore the catch block parameter
					if (insn.getOpcode() == IFNULL)
						tcInsns.add(new JumpInsnNode(GOTO, jumpTarget));
					tcInsns.add(catchEnd);

					insns.insert(insn, tcInsns);
					insns.remove(insn);
					methodNode.tryCatchBlocks.add(0, new TryCatchBlockNode(trapStart, trapEnd, catchStart, Throwables.NullPointerException));

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(tcInsns);
				}
			}
		}));

		info("+ Mutilated " + counter.get() + " null checks");
	}

	@Override
	public String getName()
	{
		return "Null Check Mutilator";
	}

	private static InsnList createNPERaiser()
	{
		final int methodOpcode;
		final String methodOwner;
		final String methodName;
		final String methodDescriptor;

		final InsnList insnList = new InsnList();

		switch (RandomUtils.getRandomInt(6))
		{
			// Methods in java.lang.Object
			case 0:
				methodOpcode = INVOKEVIRTUAL;
				methodOwner = "java/lang/Object";
				methodName = "getClass";
				methodDescriptor = "()Ljava/lang/Class;";
				break;
			case 1:
				methodOpcode = INVOKEVIRTUAL;
				methodOwner = "java/lang/Object";
				methodName = "hashCode";
				methodDescriptor = "()I";
				break;
			case 2:
				insnList.add(new InsnNode(ACONST_NULL));
				methodOpcode = INVOKEVIRTUAL;
				methodOwner = "java/lang/Object";
				methodName = "equals";
				methodDescriptor = "(Ljava/lang/Object;)Z";
				break;
			case 3:
				methodOpcode = INVOKEVIRTUAL;
				methodOwner = "java/lang/Object";
				methodName = "toString";
				methodDescriptor = "()Ljava/lang/String;";
				break;

			// java.util.Objects.requireNonNull
			case 4:
				methodOpcode = INVOKESTATIC;
				methodOwner = "java/util/Objects";
				methodName = "requireNonNull";
				methodDescriptor = "(Ljava/lang/Object;)Ljava/lang/Object;";
				break;
			default:
				insnList.add(new InsnNode(ACONST_NULL));
				methodOpcode = INVOKESTATIC;
				methodOwner = "java/util/Objects";
				methodName = "requireNonNull";
				methodDescriptor = "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
				break;
		}

		insnList.add(new MethodInsnNode(methodOpcode, methodOwner, methodName, methodDescriptor, false));

		return insnList;
	}
}
