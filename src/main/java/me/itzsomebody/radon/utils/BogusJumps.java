package me.itzsomebody.radon.utils;

import java.util.Optional;
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class BogusJumps implements Opcodes
{

	/**
	 * Create the new bogus jump
	 *
	 * @param  predicateLocalVarIndex
	 *                                Local variable index which the bogus predicate field value loaded on.
	 * @param  predicateType
	 *                                Type of the bogus predicate field.
	 * @param  predicateValue
	 *                                Value of the bogus predicate field.
	 * @param  jumpTo
	 *                                Jump target of the jump.
	 * @param  invertCondition
	 *                                If true, this method will return the always-true condition instead of always-false condition.
	 * @return                        The generated bogus jump instructions.
	 */
	public static InsnList createBogusJump(final int predicateLocalVarIndex, final Type predicateType, final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition)
	{
		final InsnList insnList = new InsnList();

		final int inversionFieldSize = RandomUtils.getRandomInt(1, 8);
		int inversionField = IntStream.range(0, inversionFieldSize).map(i -> (invertCondition && RandomUtils.getRandomBoolean() ? 1 : 0) << i).reduce(0, (a, b) -> a | b);

		// Fail-safe
		if (invertCondition && inversionField == 0)
			inversionField |= 1 << RandomUtils.getRandomInt(0, inversionFieldSize);

		for (int i = 0; i < inversionFieldSize; i++)
		{
			// Push the local variable to the stack
			insnList.add(new VarInsnNode(ASMUtils.getVarOpcode(predicateType, false), predicateLocalVarIndex));

			final int predicateTypeSort = predicateType.getSort();
			final boolean invert = (inversionField >> i & 1) == 1;
			if (predicateTypeSort == Type.BOOLEAN)
				insnList.add(new JumpInsnNode((predicateValue == null || (int) predicateValue == 0) == invert ? IFEQ : IFNE, jumpTo));
			else if (RandomUtils.getRandomBoolean())
				switch (predicateTypeSort)
				{
					case Type.LONG:
						createBogusComparisonLong(predicateValue, jumpTo, invert, insnList);
						break;
					case Type.FLOAT:
						createBogusComparisonFloat(predicateValue, jumpTo, invert, insnList);
						break;
					case Type.DOUBLE:
						createBogusComparisonDouble(predicateValue, jumpTo, invert, insnList);
						break;
					default: // int, byte, char, etc.
						createBogusComparisonInt(predicateValue, jumpTo, invert, insnList);
				}
			else
				switch (predicateTypeSort)
				{
					case Type.LONG:
						createBogusZeroComparisonLong(predicateValue, jumpTo, invert, insnList);
						break;
					case Type.FLOAT:
						createBogusZeroComparisonFloat(predicateValue, jumpTo, invert, insnList);
						break;
					case Type.DOUBLE:
						createBogusZeroComparisonDouble(predicateValue, jumpTo, invert, insnList);
						break;
					default: // int, byte, char, etc.
						createBogusZeroComparisonInt(predicateValue, jumpTo, invert, insnList);
				}
		}

		return insnList;
	}

	private static void createBogusZeroComparisonInt(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final int value = Optional.ofNullable(predicateValue).map(o -> (int) o).orElse(0);

		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					jumpOpcode = invertCondition ? IFGE : IFLT;
					break;
				case 1:
					jumpOpcode = invertCondition ? IFEQ : IFNE;
					break;
				default:
					jumpOpcode = invertCondition ? IFLE : IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
			jumpOpcode = value < 0 ? invertCondition ? IFLT : IFGE : invertCondition ? IFGT : IFLE;
		else
			jumpOpcode = invertCondition ? IFNE : IFEQ;

		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusZeroComparisonLong(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final long value = Optional.ofNullable(predicateValue).map(o -> (long) o).orElse(0L);

		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					jumpOpcode = invertCondition ? IFGE : IFLT;
					break;
				case 1:
					jumpOpcode = invertCondition ? IFEQ : IFNE;
					break;
				default:
					jumpOpcode = invertCondition ? IFLE : IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
			jumpOpcode = value < 0L ? invertCondition ? IFLT : IFGE : invertCondition ? IFGT : IFLE;
		else
			jumpOpcode = invertCondition ? IFNE : IFEQ;

		insnList.add(new InsnNode(LCONST_0));
		insnList.add(new InsnNode(LCMP));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusZeroComparisonFloat(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final float value = Optional.ofNullable(predicateValue).map(o -> (float) o).orElse(0.0F);
		insnList.add(new InsnNode(FCONST_0));
		final int compareOpcode;
		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					compareOpcode = FCMPG;
					jumpOpcode = invertCondition ? IFGE : IFLT;
					break;
				case 1:
					compareOpcode = FCMPL;
					jumpOpcode = invertCondition ? IFEQ : IFNE;
					break;
				default:
					compareOpcode = FCMPL;
					jumpOpcode = invertCondition ? IFLE : IFGT; // 오타 하나 때문에 금같은 4시간이 날라감...
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
		{
			compareOpcode = value < 0 ? FCMPL : FCMPG;
			jumpOpcode = value < 0 ? invertCondition ? IFLT : IFGE : invertCondition ? IFGT : IFLE;
		}
		else
		{
			compareOpcode = FCMPL;
			jumpOpcode = invertCondition ? IFNE : IFEQ;
		}

		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusZeroComparisonDouble(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final double value = Optional.ofNullable(predicateValue).map(o -> (double) o).orElse(0.0);
		insnList.add(new InsnNode(DCONST_0));
		final int compareOpcode;
		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					compareOpcode = DCMPG;
					jumpOpcode = invertCondition ? IFGE : IFLT;
					break;
				case 1:
					compareOpcode = DCMPL;
					jumpOpcode = invertCondition ? IFEQ : IFNE;
					break;
				default:
					compareOpcode = DCMPL;
					jumpOpcode = invertCondition ? IFLE : IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
		{
			compareOpcode = value < 0 ? DCMPL : DCMPG;
			jumpOpcode = value < 0 ? invertCondition ? IFLT : IFGE : invertCondition ? IFGT : IFLE;
		}
		else
		{
			compareOpcode = DCMPL;
			jumpOpcode = invertCondition ? IFNE : IFEQ;
		}

		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusComparisonInt(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final int value = Optional.ofNullable(predicateValue).map(o -> (int) o).orElse(0);
		final int operand;
		final int jumpOpcode;

		switch (RandomUtils.getRandomInt(6))
		{
			case 0:
				operand = RandomUtils.getRandomInt(Integer.MIN_VALUE, value);
				jumpOpcode = invertCondition ? IF_ICMPGE : IF_ICMPLT; // less
				break;
			case 1:
				operand = RandomUtils.getRandomInt(value, Integer.MAX_VALUE);
				jumpOpcode = invertCondition ? IF_ICMPLE : IF_ICMPGT;
				break;
			case 2:
				operand = RandomUtils.getRandomInt(Integer.MIN_VALUE, value - 1);
				jumpOpcode = invertCondition ? IF_ICMPGT : IF_ICMPLE;
				break;
			case 3:
				operand = RandomUtils.getRandomInt(value + 1, Integer.MAX_VALUE);
				jumpOpcode = invertCondition ? IF_ICMPLT : IF_ICMPGE;
				break;
			case 4:
				int findOperand;
				do
					findOperand = RandomUtils.getRandomInt();
				while (findOperand == value);

				operand = findOperand;
				jumpOpcode = invertCondition ? IF_ICMPNE : IF_ICMPEQ;
				break;
			default: // 5
				operand = value;
				jumpOpcode = invertCondition ? IF_ICMPEQ : IF_ICMPNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusComparisonLong(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final long value = Optional.ofNullable(predicateValue).map(o -> (long) o).orElse(0L);
		final long operand;
		final int jumpOpcode;

		switch (RandomUtils.getRandomInt(6))
		{
			case 0:
				operand = RandomUtils.getRandomLong(Long.MIN_VALUE, value); // long.minvalue ~ value
				jumpOpcode = invertCondition ? IFGE : IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomLong(Long.MIN_VALUE, value - 1L); // long.minvalue ~ (value + 1)
				jumpOpcode = invertCondition ? IFGT : IFLE;
				break;
			case 2:
				operand = RandomUtils.getRandomLong(value, Long.MAX_VALUE); // value ~ long.maxvalue
				jumpOpcode = invertCondition ? IFLE : IFGT;
				break;
			case 3:
				operand = RandomUtils.getRandomLong(value + 1L, Long.MAX_VALUE); // (value + 1) ~ long.maxvalue
				jumpOpcode = invertCondition ? IFLT : IFGE;
				break;
			case 4:
				long findOperand;
				do
					findOperand = RandomUtils.getRandomLong();
				while (findOperand == value);

				operand = findOperand;
				jumpOpcode = invertCondition ? IFNE : IFEQ;
				break;
			default:
				operand = value;
				jumpOpcode = invertCondition ? IFEQ : IFNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new InsnNode(LCMP));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusComparisonFloat(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final float value = Optional.ofNullable(predicateValue).map(o -> (float) o).orElse(0.0F);
		final float operand;
		final int compareOpcode;
		final int jumpOpcode;

		final float max = Float.MAX_VALUE;
		final float min = -Float.MAX_VALUE;

		switch (RandomUtils.getRandomInt(6))
		{
			case 0:
				operand = RandomUtils.getRandomFloat(min, value);
				compareOpcode = FCMPG;
				jumpOpcode = invertCondition ? IFGE : IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomFloat(value, max);
				compareOpcode = FCMPL;
				jumpOpcode = invertCondition ? IFLE : IFGT;
				break;
			case 2:
				operand = RandomUtils.getRandomFloat(min, value - 1.0F);
				compareOpcode = FCMPG;
				jumpOpcode = invertCondition ? IFGT : IFLE;
				break;
			case 3:
				operand = RandomUtils.getRandomFloat(value + 1.0F, max);
				compareOpcode = FCMPL;
				jumpOpcode = invertCondition ? IFLT : IFGE;
				break;
			case 4:
				float findOperand;
				do
					findOperand = RandomUtils.getRandomFloat();
				while (findOperand == value);

				operand = findOperand;
				compareOpcode = FCMPL;
				jumpOpcode = invertCondition ? IFNE : IFEQ;
				break;
			default:
				operand = value;
				compareOpcode = FCMPL;
				jumpOpcode = invertCondition ? IFEQ : IFNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusComparisonDouble(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final double value = Optional.ofNullable(predicateValue).map(o -> (double) o).orElse(0.0);
		final double operand;
		final int compareOpcode;
		final int jumpConditionOpcode;

		final double max = Double.MAX_VALUE;
		final double min = -Double.MAX_VALUE;

		switch (RandomUtils.getRandomInt(6))
		{
			case 0:
				operand = RandomUtils.getRandomDouble(min, value);
				compareOpcode = DCMPG;
				jumpConditionOpcode = invertCondition ? IFGE : IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomDouble(value, max);
				compareOpcode = DCMPL;
				jumpConditionOpcode = invertCondition ? IFLE : IFGT;
				break;
			case 2:
				operand = RandomUtils.getRandomDouble(min, value - 1.0);
				compareOpcode = DCMPG;
				jumpConditionOpcode = invertCondition ? IFGT : IFLE;
				break;
			case 3:
				operand = RandomUtils.getRandomDouble(value + 1.0, max);
				compareOpcode = DCMPL;
				jumpConditionOpcode = invertCondition ? IFLT : IFGE;
				break;
			case 4:
				float findOperand;
				do
					findOperand = RandomUtils.getRandomFloat();
				while (findOperand == value);

				operand = findOperand;
				compareOpcode = DCMPL;
				jumpConditionOpcode = invertCondition ? IFNE : IFEQ;
				break;
			default:
				operand = value;
				compareOpcode = DCMPL;
				jumpConditionOpcode = invertCondition ? IFEQ : IFNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpConditionOpcode, jumpTo));
	}

	private BogusJumps()
	{

	}
}
