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

package me.itzsomebody.radon.transformers.obfuscators.numbers;

import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Splits integer and long constants into random bitwise operations.
 *
 * @author ItzSomebody
 */
public class BitwiseObfuscator extends NumberObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.getMethods().stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).forEach(methodWrapper ->
		{
			int leeway = methodWrapper.getLeewaySize();
			final InsnList methodInstructions = methodWrapper.getInstructions();

			for (final AbstractInsnNode insn : methodInstructions.toArray())
			{
				if (leeway < 10000)
					break;

				if (ASMUtils.isIntInsn(insn) && master.isIntegerTamperingEnabled())
				{
					final int originalNum = ASMUtils.getIntegerFromInsn(insn);
					final InsnList insns = obfuscateNumber(originalNum);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isLongInsn(insn) && master.isLongTamperingEnabled())
				{
					final long originalNum = ASMUtils.getLongFromInsn(insn);
					final InsnList insns = obfuscateNumber(originalNum);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
			}
		}));

		Main.info("+ Split " + counter.get() + " number constants into bitwise instructions (minIteration: " + master.getMinIteration() + ", maxIteration: " + master.getMaxIteration() + ")");
	}

	private InsnList obfuscateNumber(final int originalNum)
	{

		int current = randomInt(originalNum);
		int verifySum = current;

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		final StringBuilder builder = new StringBuilder("*** [BitwiseObfuscator] Tampering int " + originalNum + " => ");
		builder.append(current);

		for (int i = 0, iterations = getIterationCount(); i < iterations; i++)
		{
			final int operand;

			switch (RandomUtils.getRandomInt(Math.abs(current) <= 1L ? 4 : 7))
			{
				case 0:
				{
					// And
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IAND));

					current &= operand;
					builder.append(" & ").append(operand);
					verifySum &= operand;
					break;
				}
				case 1:
				{
					// Or
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IOR));

					current |= operand;
					builder.append(" | ").append(operand);
					verifySum |= operand;
					break;
				}
				case 2:
				{
					// XOR
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IXOR));

					current ^= operand;
					builder.append(" ^ ").append(operand);
					verifySum ^= operand;
					break;
				}
				case 3:
				{
					// Bitwise NOT 'y = ~(x)'

					//
					// long x = 1234L;
					// long y = ~x;
					//
					// LDC 1234
					// LSTORE 1
					// LLOAD 1: x
					// LDC -1
					// LXOR
					// LSTORE 3
					//

					insns.add(new InsnNode(Opcodes.ICONST_M1));
					insns.add(new InsnNode(Opcodes.IXOR));

					current = ~current;
					builder.append(" ^ ").append(-1);
					verifySum = ~verifySum; // Exceptionally
					break;
				}
				case 4:
				{
					// Shift-left
					int j = 0x1F;
					while (j > 0 && (current << j == 0 || current << j <= Integer.MIN_VALUE || current << j >= Integer.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.ISHL));

					current <<= operand;
					builder.append(" << ").append(operand);
					verifySum <<= operand;
					break;
				}
				case 5:
				{
					// Shift-right
					int j = 0x1E;
					while (j > 0 && (current >> j == 0 || current >> j <= Integer.MIN_VALUE || current >> j >= Integer.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.ISHR));

					current >>= operand;
					builder.append(" >> ").append(operand);
					verifySum >>= operand;
					break;
				}
				case 6:
				{
					// Logical shift-right
					int j = 0x1E;
					while (j > 0 && (current >>> j == 0 || current >>> j <= Integer.MIN_VALUE || current >>> j >= Integer.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IUSHR));

					current >>>= operand;
					builder.append(" >>> ").append(operand);
					verifySum >>>= operand;
					break;
				}
			}
		}

		final int correctionOperand = originalNum ^ current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(Opcodes.IXOR));

		builder.append(" ^ ").append(correctionOperand);

		verifySum ^= correctionOperand;

		final int verifyOpcode = new NumberObfuscationVerifier(insns, originalNum).checkInt();
		builder.append(" [verifySum: ").append(verifySum).append(", verifyOpcode: ").append(verifyOpcode).append("]");
		if (originalNum != verifySum || originalNum != verifyOpcode)
		{
			builder.append(" => !!~~ FAILED TO VERIFY ~~!! Skipping...");
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}

	private InsnList obfuscateNumber(final long originalNum)
	{

		long current = randomLong(originalNum);
		long verifySum = current;

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		final StringBuilder builder = new StringBuilder("*** [BitwiseObfuscator] Tampering long " + originalNum + " => ");
		builder.append(current);

		for (int i = 0, iterations = getIterationCount(); i < iterations; i++)
		{
			final long operand;

			switch (RandomUtils.getRandomInt(Math.abs(current) <= 1L ? 4 : 7))
			{
				case 0:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LAND));

					current &= operand;
					builder.append(" & ").append(operand);
					verifySum &= operand;
					break;
				}
				case 1:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LOR));

					current |= operand;
					builder.append(" | ").append(operand);
					verifySum |= operand;
					break;
				}
				case 2:
				{
					operand = randomLong(current);
					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LXOR));

					current ^= operand;
					builder.append(" ^ ").append(operand);
					verifySum ^= operand;
					break;
				}
				case 3:
				{
					// Bitwise NOT 'y = ~(x)'

					//
					// long x = 1234L;
					// long y = ~x;
					//
					// LDC 1234
					// LSTORE 1
					// LLOAD 1: x
					// LDC -1
					// LXOR
					// LSTORE 3

					insns.add(new LdcInsnNode(-1L));
					insns.add(new InsnNode(Opcodes.LXOR));

					current = ~current;
					builder.append(" ^ ").append(-1);
					verifySum = ~verifySum; // Exceptionally
					break;
				}
				case 4:
				{
					int j = 0x3F;
					while (j > 0 && (current << j == 0 || current << j <= Long.MIN_VALUE || current << j >= Long.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(Opcodes.LSHL));

					current <<= operand;
					builder.append(" << ").append(operand);
					verifySum <<= operand;
					break;
				}
				case 5:
				{
					int j = 0x3F;
					while (j > 0 && (current >> j == 0 || current >> j <= Long.MIN_VALUE || current >> j >= Long.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(Opcodes.LSHR));

					current >>= operand;
					builder.append(" >> ").append(operand);
					verifySum >>= operand;
					break;
				}
				case 6:
				{
					int j = 0x3F;
					while (j > 0 && (current >>> j == 0 || current >>> j <= Long.MIN_VALUE || current >>> j >= Long.MAX_VALUE))
						j--;

					operand = RandomUtils.getRandomInt(1, j > 0 ? j : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(Opcodes.LUSHR));

					current >>>= operand;
					builder.append(" >>> ").append(operand);
					verifySum >>>= operand;
					break;
				}
			}
		}

		final long correctionOperand = originalNum ^ current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(Opcodes.LXOR));

		builder.append(" ^ ").append(correctionOperand);

		verifySum ^= correctionOperand;

		final long verifyOpcode = new NumberObfuscationVerifier(insns, originalNum).checkLong();
		builder.append(" [verifySum: ").append(verifySum).append(", verifyOpcode: ").append(verifyOpcode).append("]");
		if (originalNum != verifySum || originalNum != verifyOpcode)
		{
			builder.append(" => !!~~ FAILED TO VERIFY ~~!! Skipping...");
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}
}
