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

import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Separate return from computation(s) of return value
 * Original source code: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/processors/flowObfuscation/ReturnMangler.java
 *
 * @author superblaubeere27
 */
public class ReturnMutilator extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final MethodNode mn = mw.methodNode;

				final LabelNode returnLabel = new LabelNode();
				final Type returnType = Type.getReturnType(mn.desc);
				final boolean isVoidType = returnType.getSort() == Type.VOID;
				int returnSlot = -1;

				if (!isVoidType)
					returnSlot = mn.maxLocals++;

				final InsnList insns = mn.instructions;
				for (final AbstractInsnNode insn : insns.toArray())
					if (insn.getOpcode() >= IRETURN && insn.getOpcode() <= RETURN)
					{
						final InsnList insnList = new InsnList();

						if (!isVoidType)
							insnList.add(new VarInsnNode(returnType.getOpcode(ISTORE), returnSlot));

						insnList.add(new JumpInsnNode(GOTO, returnLabel));

						insns.insert(insn, insnList);
						insns.remove(insn);

						counter.incrementAndGet();
					}

				insns.add(returnLabel);
				if (isVoidType)
					insns.add(new InsnNode(RETURN));
				else
				{
					insns.add(new VarInsnNode(returnType.getOpcode(ILOAD), returnSlot));
					insns.add(new InsnNode(returnType.getOpcode(IRETURN)));
				}
			});
		});

		info("+ Mutilated " + counter.get() + " returns");
	}

	@Override
	public String getName()
	{
		return "Return Mutilator";
	}
}
