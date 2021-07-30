package me.itzsomebody.radon.utils;

import java.util.Optional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class BogusJumps
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

		// Push the local variable to the stack
		insnList.add(new VarInsnNode(ASMUtils.getVarOpcode(predicateType, false), predicateLocalVarIndex));

		if (predicateType.getSort() == Type.BOOLEAN)
			insnList.add(new JumpInsnNode((predicateValue == null || (int) predicateValue == 0) == invertCondition ? Opcodes.IFEQ : Opcodes.IFNE, jumpTo));
		else if (RandomUtils.getRandomBoolean())
			switch (predicateType.getSort())
			{
				case Type.LONG:
					createBogusComparisonLong(insnList, predicateValue, jumpTo, invertCondition);
					break;
				case Type.FLOAT:
					createBogusComparisonFloat(predicateValue, jumpTo, invertCondition, insnList);
					break;
				case Type.DOUBLE:
					createBogusComparisonDouble(predicateValue, jumpTo, invertCondition, insnList);
					break;
				default: // int, byte, char, etc.
					createBogusComparisonInt(predicateValue, jumpTo, invertCondition, insnList);
			}
		else
			switch (predicateType.getSort())
			{
				case Type.LONG:
					createFakeEqualityCheckLong(predicateValue, jumpTo, invertCondition, insnList);
					break;
				case Type.FLOAT:
					createFakeEqualityCheckFloat(predicateValue, jumpTo, invertCondition, insnList);
					break;
				case Type.DOUBLE:
					createFakeEqualityCheckDouble(predicateValue, jumpTo, invertCondition, insnList);
					break;
				default: // int, byte, char, etc.
					createFakeEqualityCheckInt(predicateValue, jumpTo, invertCondition, insnList);
			}

		return insnList;
	}

	private static void createFakeEqualityCheckInt(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final int value = Optional.ofNullable(predicateValue).map(o -> (int) o).orElse(0);

		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
					break;
				case 1:
					jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
					break;
				default:
					jumpOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
			jumpOpcode = value < 0 ? invertCondition ? Opcodes.IFLT : Opcodes.IFGE : invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
		else
			jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;

		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createFakeEqualityCheckLong(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final long value = Optional.ofNullable(predicateValue).map(o -> (long) o).orElse(0L);

		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
					break;
				case 1:
					jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
					break;
				default:
					jumpOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
			jumpOpcode = value < 0L ? invertCondition ? Opcodes.IFLT : Opcodes.IFGE : invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
		else
			jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;

		insnList.add(new InsnNode(Opcodes.LCONST_0));
		insnList.add(new InsnNode(Opcodes.LCMP));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createFakeEqualityCheckFloat(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final float value = Optional.ofNullable(predicateValue).map(o -> (float) o).orElse(0.0F);
		insnList.add(new InsnNode(Opcodes.FCONST_0));
		final int compareOpcode;
		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					compareOpcode = Opcodes.FCMPG;
					jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
					break;
				case 1:
					compareOpcode = Opcodes.FCMPL;
					jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
					break;
				default:
					compareOpcode = Opcodes.FCMPL;
					jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
		{
			compareOpcode = value < 0 ? Opcodes.FCMPL : Opcodes.FCMPG;
			jumpOpcode = value < 0 ? invertCondition ? Opcodes.IFLT : Opcodes.IFGE : invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
		}
		else
		{
			compareOpcode = Opcodes.FCMPL;
			jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;
		}

		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createFakeEqualityCheckDouble(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
	{
		final double value = Optional.ofNullable(predicateValue).map(o -> (double) o).orElse(0.0);
		insnList.add(new InsnNode(Opcodes.DCONST_0));
		final int compareOpcode;
		final int jumpOpcode;

		if (value == 0) // The predicate is zero
			switch (RandomUtils.getRandomInt(3))
			{
				case 0:
					compareOpcode = Opcodes.DCMPG;
					jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
					break;
				case 1:
					compareOpcode = Opcodes.DCMPL;
					jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
					break;
				default:
					compareOpcode = Opcodes.DCMPL;
					jumpOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
					break;
			}
		else if (RandomUtils.getRandomBoolean()) // The predicate is not zero
		{
			compareOpcode = value < 0 ? Opcodes.DCMPL : Opcodes.DCMPG;
			jumpOpcode = value < 0 ? invertCondition ? Opcodes.IFLT : Opcodes.IFGE : invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
		}
		else
		{
			compareOpcode = Opcodes.DCMPL;
			jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;
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
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPGE : Opcodes.IF_ICMPLT; // less
				break;
			case 1:
				operand = RandomUtils.getRandomInt(value, Integer.MAX_VALUE);
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPLE : Opcodes.IF_ICMPGT;
				break;
			case 2:
				operand = RandomUtils.getRandomInt(Integer.MIN_VALUE, value - 1);
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPGT : Opcodes.IF_ICMPLE;
				break;
			case 3:
				operand = RandomUtils.getRandomInt(value + 1, Integer.MAX_VALUE);
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPLT : Opcodes.IF_ICMPGE;
				break;
			case 4:
				int findOperand;
				do
					findOperand = RandomUtils.getRandomInt();
				while (findOperand == value);

				operand = findOperand;
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPNE : Opcodes.IF_ICMPEQ;
				break;
			default: // 5
				operand = value;
				jumpOpcode = invertCondition ? Opcodes.IF_ICMPEQ : Opcodes.IF_ICMPNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new JumpInsnNode(jumpOpcode, jumpTo));
	}

	private static void createBogusComparisonLong(final InsnList insnList, final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition)
	{
		final long value = Optional.ofNullable(predicateValue).map(o -> (long) o).orElse(0L);
		final long operand;
		final int jumpOpcode;

		switch (RandomUtils.getRandomInt(6))
		{
			case 0:
				operand = RandomUtils.getRandomLong(Long.MIN_VALUE, value); // long.minvalue ~ value
				jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomLong(Long.MIN_VALUE, value - 1L); // long.minvalue ~ (value + 1)
				jumpOpcode = invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
				break;
			case 2:
				operand = RandomUtils.getRandomLong(value, Long.MAX_VALUE); // value ~ long.maxvalue
				jumpOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
				break;
			case 3:
				operand = RandomUtils.getRandomLong(value + 1L, Long.MAX_VALUE); // (value + 1) ~ long.maxvalue
				jumpOpcode = invertCondition ? Opcodes.IFLT : Opcodes.IFGE;
				break;
			case 4:
				long findOperand;
				do
					findOperand = RandomUtils.getRandomLong();
				while (findOperand == value);

				operand = findOperand;
				jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;
				break;
			default:
				operand = value;
				jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new InsnNode(Opcodes.LCMP));
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
				compareOpcode = Opcodes.FCMPG;
				jumpOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomFloat(value, max);
				compareOpcode = Opcodes.FCMPL;
				jumpOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
				break;
			case 2:
				operand = RandomUtils.getRandomFloat(min, value - 1.0F);
				compareOpcode = Opcodes.FCMPG;
				jumpOpcode = invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
				break;
			case 3:
				operand = RandomUtils.getRandomFloat(value + 1.0F, max);
				compareOpcode = Opcodes.FCMPL;
				jumpOpcode = invertCondition ? Opcodes.IFLT : Opcodes.IFGE;
				break;
			case 4:
				float findOperand;
				do
					findOperand = RandomUtils.getRandomFloat();
				while (findOperand == value);

				operand = findOperand;
				compareOpcode = Opcodes.FCMPL;
				jumpOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;
				break;
			default:
				operand = value;
				compareOpcode = Opcodes.FCMPL;
				jumpOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
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
				compareOpcode = Opcodes.DCMPG;
				jumpConditionOpcode = invertCondition ? Opcodes.IFGE : Opcodes.IFLT;
				break;
			case 1:
				operand = RandomUtils.getRandomDouble(value, max);
				compareOpcode = Opcodes.DCMPL;
				jumpConditionOpcode = invertCondition ? Opcodes.IFLE : Opcodes.IFGT;
				break;
			case 2:
				operand = RandomUtils.getRandomDouble(min, value - 1.0);
				compareOpcode = Opcodes.DCMPG;
				jumpConditionOpcode = invertCondition ? Opcodes.IFGT : Opcodes.IFLE;
				break;
			case 3:
				operand = RandomUtils.getRandomDouble(value + 1.0, max);
				compareOpcode = Opcodes.DCMPL;
				jumpConditionOpcode = invertCondition ? Opcodes.IFLT : Opcodes.IFGE;
				break;
			case 4:
				float findOperand;
				do
					findOperand = RandomUtils.getRandomFloat();
				while (findOperand == value);

				operand = findOperand;
				compareOpcode = Opcodes.DCMPL;
				jumpConditionOpcode = invertCondition ? Opcodes.IFNE : Opcodes.IFEQ;
				break;
			default:
				operand = value;
				compareOpcode = Opcodes.DCMPL;
				jumpConditionOpcode = invertCondition ? Opcodes.IFEQ : Opcodes.IFNE;
				break;
		}

		insnList.add(ASMUtils.getNumberInsn(operand));
		insnList.add(new InsnNode(compareOpcode));
		insnList.add(new JumpInsnNode(jumpConditionOpcode, jumpTo));
	}

	/**
	 * Create the exit instruction such as RETURN, IRETURN, ARETURN. Usuful for creating fake-exit label.
	 * 
	 * @param  type
	 *              Type of the method which this exit label inserted in.
	 * @return      The generated exit label.
	 */
	public static InsnList createBogusExit(final Type type)
	{
		final InsnList insnList = new InsnList();

		AbstractInsnNode pushNode = null;
		final int popNodeOp;

		if (RandomUtils.getRandomBoolean())
			switch (type.getSort()) // Create fake 'return' statement
			{
				case Type.VOID:
					popNodeOp = Opcodes.RETURN;
					break;
				case Type.BOOLEAN:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(2));
					popNodeOp = Opcodes.IRETURN;
					break;
				case Type.CHAR:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Character.MAX_VALUE + 1));
					popNodeOp = Opcodes.IRETURN;
					break;
				case Type.BYTE:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Byte.MAX_VALUE + 1));
					popNodeOp = Opcodes.IRETURN;
					break;
				case Type.SHORT:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Short.MAX_VALUE + 1));
					popNodeOp = Opcodes.IRETURN;
					break;
				case Type.INT:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt());
					popNodeOp = Opcodes.IRETURN;
					break;
				case Type.LONG:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomLong());
					popNodeOp = Opcodes.LRETURN;
					break;
				case Type.FLOAT:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomFloat());
					popNodeOp = Opcodes.FRETURN;
					break;
				case Type.DOUBLE:
					pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomDouble());
					popNodeOp = Opcodes.DRETURN;
					break;
				default:
					pushNode = new InsnNode(Opcodes.ACONST_NULL);
					popNodeOp = Opcodes.ARETURN;
			}
		else
		{
			// Create unused 'throw null' statement
			// TODO: Diversify exception types - if you want to use it, you should expand stack size before, or get a VerifyError.
			pushNode = new InsnNode(Opcodes.ACONST_NULL);
			popNodeOp = Opcodes.ATHROW;
		}

		if (pushNode != null)
			insnList.add(pushNode);
		insnList.add(new InsnNode(popNodeOp));

		return insnList;
	}

	private BogusJumps()
	{

	}
}
