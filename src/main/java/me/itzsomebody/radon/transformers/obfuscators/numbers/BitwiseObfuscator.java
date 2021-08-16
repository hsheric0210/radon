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

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).forEach(mw ->
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
					if (Float.isNaN(originalNum))
						continue;

					final InsnList insns = obfuscateNumber(Float.floatToIntBits(originalNum), leeway);
					insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false));

					methodInstructions.insert(insn, insns);
					methodInstructions.remove(insn);

					counter.incrementAndGet();
					leeway -= ASMUtils.evaluateMaxSize(insns);
				}
				else if (ASMUtils.isDoubleInsn(insn) && master.doubleTamperingEnabled)
				{
					final double originalNum = ASMUtils.getDoubleFromInsn(insn);
					if (Double.isNaN(originalNum))
						continue;

					final InsnList insns = obfuscateNumber(Double.doubleToLongBits(originalNum), leeway);
					insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false));

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

		info("+ Split " + counter.get() + " number constants into bitwise instructions (minIteration: " + master.minIteration + ", maxIteration: " + master.maxIteration + ")");
	}

	private InsnList obfuscateNumber(final int originalNum, final int _leeway)
	{
		int current = randomInt(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		final StringBuilder builder = new StringBuilder("! [BitwiseObfuscator] Verify failed: Obfuscate int '" + originalNum + "' to '");
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final int operand;

			switch (RandomUtils.getRandomInt(Math.abs(current) <= 1L ? 4 : 7))
			{
				case 0:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IAND));
					current &= operand;
					builder.append(" & ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IOR));
					current |= operand;
					builder.append(" | ").append(operand);
					break;
				}
				case 2:
				{
					operand = randomInt(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IXOR));
					current ^= operand;
					builder.append(" ^ ").append(operand);
					break;
				}
				case 3:
				{
					insns.add(new InsnNode(ICONST_M1));
					insns.add(new InsnNode(IXOR));

					current = ~current;
					builder.append(" ^ ").append(-1);
					break;
				}
				case 4:
				{
					int k = 0x1F;
					while (k > 0 && (current << k == 0 || current << k <= Integer.MIN_VALUE || current << k >= Integer.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(ISHL));
					current <<= operand;
					builder.append(" << ").append(operand);
					break;
				}
				case 5:
				{
					int k = 0x1E;
					while (k > 0 && (current >> k == 0 || current >> k <= Integer.MIN_VALUE || current >> k >= Integer.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(ISHR));
					current >>= operand;
					builder.append(" >> ").append(operand);
					break;
				}
				case 6:
				{
					int k = 0x1E;
					while (k > 0 && (current >>> k == 0 || current >>> k <= Integer.MIN_VALUE || current >>> k >= Integer.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x1E);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(IUSHR));
					current >>>= operand;
					builder.append(" >>> ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final int correctionOperand = originalNum ^ current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(IXOR));

		builder.append(" ^ ").append(correctionOperand).append('\'');

		current ^= correctionOperand;

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
		long current = randomLong(originalNum);

		final InsnList insns = new InsnList();
		insns.add(ASMUtils.getNumberInsn(current));

		final StringBuilder builder = new StringBuilder("! [BitwiseObfuscator] Verify failed: Obfuscate long '" + originalNum + "' to '");
		builder.append(current);

		int leeway = _leeway - 3;

		for (int i = 0, j = RandomUtils.getRandomInt(master.minIteration, master.maxIteration); i < j && leeway >= 10000; i++)
		{
			final long operand;

			switch (RandomUtils.getRandomInt(Math.abs(current) <= 1L ? 4 : 7))
			{
				case 0:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LAND));
					current &= operand;
					builder.append(" & ").append(operand);
					break;
				}
				case 1:
				{
					operand = randomLong(current);

					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LOR));
					current |= operand;
					builder.append(" | ").append(operand);
					break;
				}
				case 2:
				{
					operand = randomLong(current);
					insns.add(ASMUtils.getNumberInsn(operand));
					insns.add(new InsnNode(LXOR));
					current ^= operand;
					builder.append(" ^ ").append(operand);
					break;
				}
				case 3:
				{
					insns.add(new LdcInsnNode(-1L));
					insns.add(new InsnNode(LXOR));
					current = ~current;
					builder.append(" ^ ").append(-1);
					break;
				}
				case 4:
				{
					int k = 0x3F;
					while (k > 0 && (current << k == 0 || current << k <= Long.MIN_VALUE || current << k >= Long.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(LSHL));
					current <<= operand;
					builder.append(" << ").append(operand);
					break;
				}
				case 5:
				{
					int k = 0x3F;
					while (k > 0 && (current >> k == 0 || current >> k <= Long.MIN_VALUE || current >> k >= Long.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(LSHR));
					current >>= operand;
					builder.append(" >> ").append(operand);
					break;
				}
				case 6:
				{
					int k = 0x3F;
					while (k > 0 && (current >>> k == 0 || current >>> k <= Long.MIN_VALUE || current >>> k >= Long.MAX_VALUE))
						k--;

					operand = RandomUtils.getRandomInt(1, k > 0 ? k : 0x3F);

					insns.add(ASMUtils.getNumberInsn((int) operand));
					insns.add(new InsnNode(LUSHR));
					current >>>= operand;
					builder.append(" >>> ").append(operand);
					break;
				}
			}

			leeway -= 4;
		}

		final long correctionOperand = originalNum ^ current;
		insns.add(ASMUtils.getNumberInsn(correctionOperand));
		insns.add(new InsnNode(LXOR));

		builder.append(" ^ ").append(correctionOperand).append('\'');

		current ^= correctionOperand;

		builder.append(" [current: ").append(current).append(", delta: ").append(current - originalNum).append(']');
		if (originalNum != current)
		{
			verboseWarn(builder::toString);
			return ASMUtils.singletonList(ASMUtils.getNumberInsn(originalNum));
		}

		return insns;
	}
}
