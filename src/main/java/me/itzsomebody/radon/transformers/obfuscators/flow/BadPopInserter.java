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

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts redundant POP instructions which does nothing to confuse decompilers and skidders.
 * Original source code of Javari BadPop Obfuscator: https://github.com/NeroReal/Javari/blob/master/roman/finn/javari/obfmethods/BadPop.java
 * Original source code of JObf BadPop Obfuscator: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/processors/flowObfuscation/FlowObfuscator.java
 *
 * @author superblaubeere27, Roman
 */
public class BadPopInserter extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			int leeway = mw.getLeewaySize();

			final InsnList insns = mw.getInstructions();
			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (leeway < 10000)
					break;

				final int opcode = insn.getOpcode();
				if (opcode == GOTO)
				{
					final InsnList badPop = createBadPOP(POP);
					insns.insertBefore(insn, badPop);
					leeway -= ASMUtils.evaluateMaxSize(badPop);

					counter.incrementAndGet();
				}

				if (opcode == POP)
				{
					final InsnList badPop = createBadPOP(POP2);
					insns.insert(insn, badPop);
					insns.remove(insn);
					leeway -= ASMUtils.evaluateMaxSize(badPop);

					counter.incrementAndGet();
				}
			}
		}));

		info("+ Inserted " + counter.get() + " bad POP instructions");
	}

	private InsnList createBadPOP(final int popOpcode)
	{
		final InsnList insns = new InsnList();

		if (RandomUtils.getRandomBoolean())
		{
			// Javari BadPop obfuscsation
			insns.add(createRandomInsn());
			insns.add(createRandomInsn());
			insns.add(createRandomInsn());
			insns.add(new InsnNode(POP));
			insns.add(new InsnNode(SWAP));
			insns.add(new InsnNode(POP));
			insns.add(createRandomInsn());
			insns.add(new InsnNode(POP2));
			if (popOpcode == POP2)
				insns.add(new InsnNode(POP));
			return insns;
		}

		insns.add(createRandomStringInsn());
		insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
		insns.add(new InsnNode(popOpcode));
		return insns;
	}

	private AbstractInsnNode createRandomInsn()
	{
		final Type type = ASMUtils.getRandomType();
		if (type.getSize() > 1)
			return createRandomStringInsn();
		return ASMUtils.getRandomInsn(type);
	}

	private AbstractInsnNode createRandomStringInsn()
	{
		if (RandomUtils.getRandomInt(50) == 0)
			return new LdcInsnNode(Main.ATTRIBUTION);
		return new LdcInsnNode(getGenericDictionary().randomString());
	}

	@Override
	public String getName()
	{
		return "Bad POP";
	}
}
