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

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Splits number constants into random arithmetic operations.
 *
 * @author ItzSomebody
 */
public class ArithmeticObfuscator extends NumberObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(this::included).forEach(mw ->
		{
			int leeway = mw.getLeewaySize();
			final InsnList methodInstructions = mw.getInstructions();

			for (final AbstractInsnNode insn : methodInstructions.toArray())
			{
				if (leeway < 10000)
				{
					verboseWarn(() -> "Number obfuscation in method '" + cw.getName() + "." + mw.getName() + mw.getDescription() + "' might not be finished due leeway limit (Try to reduce number-obfuscation iteration count in configuration)");
					break;
				}

				if (ASMUtils.isIntInsn(insn) && master.integerTamperingEnabled)
				{
					final int originalNum = ASMUtils.getIntegerFromInsn(insn);
					final InsnList insns = obfuscateNumber(originalNum, leeway);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isLongInsn(insn) && master.longTamperingEnabled)
				{
					final long originalNum = ASMUtils.getLongFromInsn(insn);
					final InsnList insns = obfuscateNumber(originalNum, leeway);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isFloatInsn(insn) && master.floatTamperingEnabled)
				{
					final float originalNum = ASMUtils.getFloatFromInsn(insn);
					if (originalNum == Float.MIN_VALUE || Float.isNaN(originalNum) || Float.isInfinite(originalNum)) // Cannot support these cases
						continue;
					final InsnList insns = obfuscateNumber(originalNum, leeway);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isDoubleInsn(insn) && master.doubleTamperingEnabled)
				{
					final double originalNum = ASMUtils.getDoubleFromInsn(insn);
					if (originalNum == Double.MAX_VALUE || Double.isNaN(originalNum) || Double.isInfinite(originalNum)) // Cannot support these cases
						continue;
					final InsnList insns = obfuscateNumber(originalNum, leeway);

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (insn.getOpcode() == IINC && master.integerTamperingEnabled)
				{
					final IincInsnNode iincInsn = (IincInsnNode) insn;
					final int var = iincInsn.var;
					final int originalNum = iincInsn.incr;

					final InsnList insns = new InsnList();
					insns.add(new VarInsnNode(ILOAD, var));
					insns.add(obfuscateNumber(originalNum, leeway));
					insns.add(new InsnNode(IADD));
					insns.add(new VarInsnNode(ISTORE, var));
					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
			}
		}));

		info("+ Split " + counter.get() + " number constants into arithmetic instructions (minIteration: " + master.minIteration + ", maxIteration: " + master.maxIteration + ")");
	}

	private InsnList obfuscateNumber(final int originalNum, final int _leeway)
	{
		final StringBuilder builder = new StringBuilder("! [ArithmeticObfuscator] Verify failed: Obfuscate int '" + originalNum + "' to '");

		int current = randomInt(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final int operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(ISUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final int correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insns.add(ASMUtils.getNumberInsn(correctionOperand));
			insns.add(new InsnNode(IADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insns.add(ASMUtils.getNumberInsn(-correctionOperand)); // Negate manually
			else
			{
				insns.add(ASMUtils.getNumberInsn(correctionOperand));
				insns.add(new InsnNode(INEG)); // Negate by the opcode
			}

			insns.add(new InsnNode(ISUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (originalNum != current)
		{
			verboseWarn(builder::toString);
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}

	private InsnList obfuscateNumber(final long originalNum, final int _leeway)
	{
		final StringBuilder builder = new StringBuilder("! [ArithmeticObfuscator] Verify failed: Obfuscate long '" + originalNum + "' to '");

		long current = randomLong(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final long operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LSUB));
					current -= operand;
					builder.append(" - ").append(operand);
					break;
				}
				case 2:
				{
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(1, 65535);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final long correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insns.add(ASMUtils.getNumberInsn(correctionOperand));
			insns.add(new InsnNode(LADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insns.add(ASMUtils.getNumberInsn(-correctionOperand));
			else
			{
				insns.add(ASMUtils.getNumberInsn(correctionOperand));
				insns.add(new InsnNode(LNEG));
			}
			insns.add(new InsnNode(LSUB));

			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (originalNum != current)
		{
			verboseWarn(builder::toString);
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}

	private InsnList obfuscateNumber(final float originalNum, final int _leeway)
	{
		final StringBuilder builder = new StringBuilder("! [ArithmeticObfuscator] Verify failed: Obfuscate float '" + originalNum + "' to '");

		float current = originalNum == 0 ? randomFloat(Integer.MAX_VALUE) : randomFloat(originalNum);

		final InsnList insnList = new InsnList();
		insnList.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final float operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = Float.isInfinite(current * 2) || current + (current - 1.0F) >= Float.MAX_VALUE ? randomFloat(Float.MAX_VALUE - current - 1.0F) : randomFloat(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(FADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = Float.isInfinite(current - (current - 1.0F)) || current + (current - 1.0F) <= Float.MIN_VALUE ? randomFloat(Float.MIN_VALUE + current + 1.0F) : randomFloat(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(FSUB));
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
					insnList.add(new InsnNode(FMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(2, 65535);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(FDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(FREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final float correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insnList.add(ASMUtils.getNumberInsn(correctionOperand));
			insnList.add(new InsnNode(FADD));
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
				insnList.add(new InsnNode(FNEG));
			}

			insnList.add(new InsnNode(FSUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (!(Float.isNaN(originalNum) && Float.isNaN(current)) && originalNum - current > 0.000001F)
		{
			verboseWarn(builder::toString);
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insnList;
	}

	private InsnList obfuscateNumber(final double originalNum, final int _leeway)
	{
		final StringBuilder builder = new StringBuilder("! [ArithmeticObfuscator] Verify failed: Obfuscate double '" + originalNum + "' to '");

		double current = originalNum == 0 ? randomDouble(Integer.MAX_VALUE) : randomDouble(originalNum);

		final InsnList insnList = new InsnList();
		insnList.add(ASMUtils.getNumberInsn(current));
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final double operand;

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
				{
					operand = Double.isInfinite(current + (current - 1.0)) || current + (current - 1.0) >= Double.MAX_VALUE ? randomDouble(Double.MAX_VALUE - current - 1.0) : randomDouble(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(DADD));
					current += operand;
					builder.append(" + ").append(operand);
					break;
				}
				case 1:
				{
					operand = Double.isInfinite(current - (current - 1.0)) || current + (current - 1) <= Double.MIN_VALUE ? randomDouble(Double.MIN_VALUE + current + 1.0) : randomDouble(current);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(DSUB));
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
					insnList.add(new InsnNode(DMUL));
					current *= operand;
					builder.append(" * ").append(operand);
					break;
				}
				case 3:
				{
					operand = RandomUtils.getRandomInt(2, 65535);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(DDIV));
					current /= operand;
					builder.append(" / ").append(operand);
					break;
				}
				case 4:
				{
					operand = RandomUtils.getRandomInt(1, 255);

					insnList.add(ASMUtils.getNumberInsn(operand));
					insnList.add(new InsnNode(DREM));
					current %= operand;
					builder.append(" % ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final double correctionOperand = originalNum - current;
		if (RandomUtils.getRandomBoolean())
		{
			insnList.add(ASMUtils.getNumberInsn(correctionOperand));
			insnList.add(new InsnNode(DADD));
			builder.append(" + ").append(correctionOperand).append('\'');
		}
		else
		{
			if (RandomUtils.getRandomBoolean())
				insnList.add(ASMUtils.getNumberInsn(-correctionOperand));
			else
			{
				insnList.add(ASMUtils.getNumberInsn(correctionOperand));
				insnList.add(new InsnNode(DNEG));
			}

			insnList.add(new InsnNode(DSUB));
			builder.append(" - ").append(-correctionOperand).append('\'');
		}
		current += correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');

		if (!(Double.isNaN(originalNum) && Double.isNaN(current)) && originalNum - current > 0.00000001)
		{
			verboseWarn(builder::toString);
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insnList;
	}
}
