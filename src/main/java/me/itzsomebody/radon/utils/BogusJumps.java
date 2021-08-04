package me.itzsomebody.radon.utils;

import java.util.Optional;

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

		// Push the local variable to the stack
		insnList.add(new VarInsnNode(ASMUtils.getVarOpcode(predicateType, false), predicateLocalVarIndex));

		if (predicateType.getSort() == Type.BOOLEAN)
			insnList.add(new JumpInsnNode((predicateValue == null || (int) predicateValue == 0) == invertCondition ? IFEQ : IFNE, jumpTo));
		else if (RandomUtils.getRandomBoolean())
			switch (predicateType.getSort())
			{
				case Type.LONG:
					createBogusComparisonLong(insnList, predicateValue, jumpTo, invertCondition);
					return insnList;
				case Type.FLOAT:
					createBogusComparisonFloat(predicateValue, jumpTo, invertCondition, insnList);
					return insnList;
				case Type.DOUBLE:
					createBogusComparisonDouble(predicateValue, jumpTo, invertCondition, insnList);
					return insnList;
				default: // int, byte, char, etc.
					createBogusComparisonInt(predicateValue, jumpTo, invertCondition, insnList);
			}
		else
			switch (predicateType.getSort())
			{
				case Type.LONG:
					createFakeEqualityCheckLong(predicateValue, jumpTo, invertCondition, insnList);
					return insnList;
				case Type.FLOAT:
					createFakeEqualityCheckFloat(predicateValue, jumpTo, invertCondition, insnList);
					return insnList;
				case Type.DOUBLE:
					createFakeEqualityCheckDouble(predicateValue, jumpTo, invertCondition, insnList);
					return insnList;
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

	private static void createFakeEqualityCheckLong(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
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

	private static void createFakeEqualityCheckFloat(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
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
					jumpOpcode = invertCondition ? IFNE : IFGT;
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

	private static void createFakeEqualityCheckDouble(final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition, final InsnList insnList)
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

	private static void createBogusComparisonLong(final InsnList insnList, final Object predicateValue, final LabelNode jumpTo, final boolean invertCondition)
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

	public static InsnList createBogusExit(final MethodNode mn)
	{
		final InsnList insnList = new InsnList();

		AbstractInsnNode pushNode = null;
		int popNodeOpcode;

		switch (RandomUtils.getRandomInt(3))
		{
			case 0:
			{
				switch (RandomUtils.getRandomInt(2))
				{
					case 0:
					{
						// System.exit(n)
						insnList.add(new LdcInsnNode(RandomUtils.getRandomInt()));
						insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
						break;
					}

					case 1:
					{
						insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;", false));
						insnList.add(new LdcInsnNode(RandomUtils.getRandomInt()));
						insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Runtime", RandomUtils.getRandomBoolean() ? "halt" : "exit", "(I)V", false));
						break;
					}
				}

				popNodeOpcode = IRETURN;
				switch (Type.getReturnType(mn.desc).getSort())
				{
					case Type.VOID:
						pushNode = null;
						popNodeOpcode = RETURN;
						break;
					case Type.BOOLEAN:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(2));
						break;
					case Type.CHAR:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Character.MAX_VALUE + 1));
						break;
					case Type.BYTE:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Byte.MAX_VALUE + 1));
						break;
					case Type.SHORT:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Short.MAX_VALUE + 1));
						break;
					case Type.INT:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomInt());
						break;
					case Type.LONG:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomLong());
						popNodeOpcode = LRETURN;
						break;
					case Type.FLOAT:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomFloat());
						popNodeOpcode = FRETURN;
						break;
					case Type.DOUBLE:
						pushNode = ASMUtils.getNumberInsn(RandomUtils.getRandomDouble());
						popNodeOpcode = DRETURN;
						break;
					default:
						pushNode = new InsnNode(ACONST_NULL);
						popNodeOpcode = ARETURN;
						break;
				}
				break;
			}

			case 1:
			{
				mn.maxStack++;
				final String exceptionClass = RandomUtils.getRandomElement(Throwables.getRandomThrowable());
				insnList.add(new TypeInsnNode(NEW, exceptionClass));
				insnList.add(new InsnNode(DUP));
				insnList.add(new MethodInsnNode(INVOKESPECIAL, exceptionClass, "<init>", "()V", false));
				popNodeOpcode = ATHROW;
				break;
			}

			default:
			{
				pushNode = new InsnNode(ACONST_NULL);
				popNodeOpcode = ATHROW;
				break;
			}
		}

		if (pushNode != null)
			insnList.add(pushNode);
		insnList.add(new InsnNode(popNodeOpcode));

		return insnList;
	}

	private BogusJumps()
	{

	}
}
