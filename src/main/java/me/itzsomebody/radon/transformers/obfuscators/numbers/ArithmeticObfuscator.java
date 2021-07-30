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

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Splits number constants into arithmetic operations.
 *
 * @author ItzSomebody
 */
public class ArithmeticObfuscator extends NumberObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.getMethods().stream().filter(this::included).forEach(methodWrapper ->
		{
			int leeway = methodWrapper.getLeewaySize();
			final InsnList methodInstructions = methodWrapper.getInstructions();

			for (final AbstractInsnNode insn : methodInstructions.toArray())
			{
				if (leeway < 10000)
					break;

				if (ASMUtils.isIntInsn(insn) && master.isIntegerTamperingEnabled())
				{
					final InsnList insns = obfuscateNumber(ASMUtils.getIntegerFromInsn(insn));

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= insns.size() * 2;
				}
				else if (ASMUtils.isLongInsn(insn) && master.isLongTamperingEnabled())
				{
					final InsnList insns = obfuscateNumber(ASMUtils.getLongFromInsn(insn));

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= insns.size() * 2;
				}
				else if (ASMUtils.isFloatInsn(insn) && master.isFloatTamperingEnabled())
				{
					final InsnList insns = obfuscateNumber(ASMUtils.getFloatFromInsn(insn));

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= insns.size() * 2;
				}
				else if (ASMUtils.isDoubleInsn(insn) && master.isDoubleTamperingEnabled())
				{
					final InsnList insns = obfuscateNumber(ASMUtils.getDoubleFromInsn(insn));

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= insns.size() * 2;
				}
			}
		}));

		Main.info("Split " + counter.get() + " number constants into arithmetic instructions");
	}

	private InsnList obfuscateNumber(final int originalNum)
	{
		int current = randomInt(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		for (int i = 0, j = RandomUtils.getRandomInt(2, 6); i < j; i++)
		{
			final int operand;

			switch (RandomUtils.getRandomInt(6))
			{
				case 0:
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IADD));

					current += operand;
					break;
				case 1:
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(ISUB));

					current -= operand;
					break;
				case 2:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IMUL));

					current *= operand;
					break;
				case 3:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IDIV));

					current /= operand;
					break;
				case 4:
				default:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IREM));

					current %= operand;
					break;
			}
		}

		final int correctionOperand = originalNum - current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(IADD));

		return insns;
	}

	private InsnList obfuscateNumber(final long originalNum)
	{
		long current = randomLong(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		for (int i = 0, j = RandomUtils.getRandomInt(2, 6); i < j; i++)
		{
			final long operand;

			switch (RandomUtils.getRandomInt(6))
			{
				case 0:
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LADD));

					current += operand;
					break;
				case 1:
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LSUB));

					current -= operand;
					break;
				case 2:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LMUL));

					current *= operand;
					break;
				case 3:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LDIV));

					current /= operand;
					break;
				case 4:
				default:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LREM));

					current %= operand;
					break;
			}
		}

		final long correctionOperand = originalNum - current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(LADD));

		return insns;
	}

	private InsnList obfuscateNumber(final float originalNum)
	{
		float current = randomFloat(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		for (int i = 0, j = RandomUtils.getRandomInt(2, 6); i < j; i++)
		{
			final float operand;

			switch (RandomUtils.getRandomInt(6))
			{
				case 0:
					operand = randomFloat(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(FADD));

					current += operand;
					break;
				case 1:
					operand = randomFloat(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(FSUB));

					current -= operand;
					break;
				case 2:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(FMUL));

					current *= operand;
					break;
				case 3:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(FDIV));

					current /= operand;
					break;
				case 4:
				default:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(FREM));

					current %= operand;
					break;
			}
		}

		final float correctionOperand = originalNum - current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(FADD));

		return insns;
	}

	private InsnList obfuscateNumber(final double originalNum)
	{
		double current = randomDouble(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		for (int i = 0, j = RandomUtils.getRandomInt(2, 6); i < j; i++)
		{
			final double operand;

			switch (RandomUtils.getRandomInt(6))
			{
				case 0:
					operand = randomDouble(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(DADD));

					current += operand;
					break;
				case 1:
					operand = randomDouble(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(DSUB));

					current -= operand;
					break;
				case 2:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(DMUL));

					current *= operand;
					break;
				case 3:
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(DDIV));

					current /= operand;
					break;
				case 4:
				default:
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(DREM));

					current %= operand;
					break;
			}
		}

		final double correctionOperand = originalNum - current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(DADD));

		return insns;
	}
}
