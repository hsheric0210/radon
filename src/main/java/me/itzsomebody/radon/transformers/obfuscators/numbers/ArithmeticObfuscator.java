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

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.concurrent.atomic.AtomicInteger;

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
				else if (ASMUtils.isFloatInsn(insn) && master.isFloatTamperingEnabled())
				{
					final float originalNum = ASMUtils.getFloatFromInsn(insn);
					if (originalNum == Float.MIN_VALUE || Float.isNaN(originalNum) || Float.isInfinite(originalNum)) // Cannot support these cases
						continue;
					final InsnList insns = obfuscateNumber(originalNum);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isDoubleInsn(insn) && master.isDoubleTamperingEnabled())
				{
					final double originalNum = ASMUtils.getDoubleFromInsn(insn);
					if (originalNum == Double.MAX_VALUE || Double.isNaN(originalNum) || Double.isInfinite(originalNum)) // Cannot support these cases
						continue;
					final InsnList insns = obfuscateNumber(originalNum);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
			}
		}));

		Main.info("+ Split " + counter.get() + " number constants into arithmetic instructions (minIteration: " + master.getMinIteration() + ", maxIteration: " + master.getMaxIteration() + ")");
	}

	private InsnList obfuscateNumber(final int originalNum)
	{
		final StringBuilder builder = new StringBuilder("*** [ArithmeticObfuscator] Verify failed: Obfuscate int '" + originalNum + "' to '");

		int current = randomInt(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		for (int i = 0, j = getIterationCount(); i < j; i++)
		{
			final int operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.ISUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.IREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}
		}

		final int correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insns.add(ASMUtils.getNumberInsn(correctionOperand));
			insns.add(new InsnNode(Opcodes.IADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insns.add(ASMUtils.getNumberInsn(-correctionOperand)); // Negate manually
			else
			{
				insns.add(ASMUtils.getNumberInsn(correctionOperand));
				insns.add(new InsnNode(Opcodes.INEG)); // Negate by the opcode
			}

			insns.add(new InsnNode(Opcodes.ISUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (originalNum != current)
		{
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}

	private InsnList obfuscateNumber(final long originalNum)
	{
		final StringBuilder builder = new StringBuilder("*** [ArithmeticObfuscator] Verify failed: Obfuscate long '" + originalNum + "' to '");

		long current = randomLong(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		for (int i = 0, j = getIterationCount(); i < j; i++)
		{
			final long operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LSUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(Opcodes.LREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}
		}

		final long correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insns.add(ASMUtils.getNumberInsn(correctionOperand));
			insns.add(new InsnNode(Opcodes.LADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insns.add(ASMUtils.getNumberInsn(-correctionOperand));
			else
			{
				insns.add(ASMUtils.getNumberInsn(correctionOperand));
				insns.add(new InsnNode(Opcodes.LNEG));
			}
			insns.add(new InsnNode(Opcodes.LSUB));

			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (originalNum != current)
		{
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}

	private InsnList obfuscateNumber(final float originalNum) // TODO: Overflow protection
	{
		final StringBuilder builder = new StringBuilder("*** [ArithmeticObfuscator] Verify failed: Obfuscate float '" + originalNum + "' to '");

		float current = originalNum == 0 ? randomFloat(Integer.MAX_VALUE) : randomFloat(originalNum);

		final InsnList insnList = new InsnList();
		insnList.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		for (int i = 0, iterations = getIterationCount(); i < iterations; i++)
		{
			final float operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = Float.isInfinite(current * 2) || current + (current - 1.0F) >= Float.MAX_VALUE ? randomFloat(Float.MAX_VALUE - current - 1.0F) : randomFloat(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.FADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = Float.isInfinite(current - (current - 1.0F)) || current + (current - 1.0F) <= Float.MIN_VALUE ? randomFloat(Float.MIN_VALUE + current + 1.0F) : randomFloat(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.FSUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					int operandLimit = 65535;

					while (operandLimit > 0 && (Float.isInfinite(current * operandLimit) || current * operandLimit >= Integer.MAX_VALUE))
						operandLimit--;

					if (current == 0)
						operand = RandomUtils.getRandomInt(); // 0 * <n> = always 0
					else if (operandLimit == 0)
						operand = 0.0F; // it will make the current zero
					else if (operandLimit <= 1)
						break; // as you know multiplying 1 is absolutely redundant
					else
						operand = RandomUtils.getRandomInt(2, operandLimit);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.FMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(2, 65535);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.FDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.FREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}
		}

		final float correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insnList.add(ASMUtils.getNumberInsn(correctionOperand));
			insnList.add(new InsnNode(Opcodes.FADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insnList.add(ASMUtils.getNumberInsn(-correctionOperand));
			else
			{
				// Negate by opcode FNEG
				insnList.add(ASMUtils.getNumberInsn(correctionOperand));
				insnList.add(new InsnNode(Opcodes.FNEG));
			}

			insnList.add(new InsnNode(Opcodes.FSUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (!(Float.isNaN(originalNum) && Float.isNaN(current)) && originalNum - current > 0.000001F)
		{
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insnList;
	}

	private InsnList obfuscateNumber(final double originalNum) // TODO: Overflow protection
	{
		final StringBuilder builder = new StringBuilder("*** [ArithmeticObfuscator] Verify failed: Obfuscate double '" + originalNum + "' to '");

		double current = originalNum == 0 ? randomDouble(Integer.MAX_VALUE) : randomDouble(originalNum);

		final InsnList insnList = new InsnList();
		insnList.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		for (int i = 0, iterations = getIterationCount(); i < iterations; i++)
		{
			final double operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = Double.isInfinite(current + (current - 1.0)) || current + (current - 1.0) >= Double.MAX_VALUE ? randomDouble(Double.MAX_VALUE - current - 1.0) : randomDouble(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.DADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = Double.isInfinite(current - (current - 1.0)) || current + (current - 1) <= Double.MIN_VALUE ? randomDouble(Double.MIN_VALUE + current + 1.0) : randomDouble(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.DSUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					int operandLimit = 65535;

					while (operandLimit > 0 && (Double.isInfinite(current * operandLimit) || current * operandLimit >= Integer.MAX_VALUE))
						operandLimit--;

					if (current == 0)
						operand = RandomUtils.getRandomInt(); // 0 * <n> = always 0
					else if (operandLimit == 0)
						operand = 0.0F; // it will make the current zero
					else if (operandLimit <= 1)
						break; // as you know multiplying 1 is absolutely redundant
					else
						operand = RandomUtils.getRandomInt(2, operandLimit);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.DMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(2, 65535);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.DDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(Opcodes.DREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}
		}

		final double correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insnList.add(ASMUtils.getNumberInsn(correctionOperand));
			insnList.add(new InsnNode(Opcodes.DADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insnList.add(ASMUtils.getNumberInsn(-correctionOperand));
			else
			{
				insnList.add(ASMUtils.getNumberInsn(correctionOperand));
				insnList.add(new InsnNode(Opcodes.DNEG));
			}

			insnList.add(new InsnNode(Opcodes.DSUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (!(Double.isNaN(originalNum) && Double.isNaN(current)) && originalNum - current > 0.00000001)
		{
			Main.info(builder.toString());
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insnList;
	}
}
