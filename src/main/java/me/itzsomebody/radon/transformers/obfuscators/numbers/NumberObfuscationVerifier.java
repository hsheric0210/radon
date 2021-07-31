package me.itzsomebody.radon.transformers.obfuscators.numbers;

import java.util.Stack;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import me.itzsomebody.radon.utils.ASMUtils;

final class NumberObfuscationVerifier
{
	private final InsnList insnList;
	private int expected_int = Integer.MIN_VALUE;
	private long expected_long = Long.MIN_VALUE;
	private float expected_float = Float.MIN_VALUE;
	private double expected_double = Double.MIN_VALUE;

	NumberObfuscationVerifier(final InsnList obfuscatedNumberInstructions, final int originalNumber)
	{
		insnList = obfuscatedNumberInstructions;
		expected_int = originalNumber;
	}

	NumberObfuscationVerifier(final InsnList obfuscatedNumberInstructions, final long originalNumber)
	{
		insnList = obfuscatedNumberInstructions;
		expected_long = originalNumber;
	}

	NumberObfuscationVerifier(final InsnList obfuscatedNumberInstructions, final float originalNumber)
	{
		insnList = obfuscatedNumberInstructions;
		expected_float = originalNumber;
	}

	NumberObfuscationVerifier(final InsnList obfuscatedNumberInstructions, final double originalNumber)
	{
		insnList = obfuscatedNumberInstructions;
		expected_double = originalNumber;
	}

	public int checkInt()
	{
		// Emulate the stack
		final Stack<Integer> stack = new Stack<>();

		for (final AbstractInsnNode insnNode : insnList.toArray())
			if (ASMUtils.isIntInsn(insnNode))
				stack.push(ASMUtils.getIntegerFromInsn(insnNode));
			else
				switch (insnNode.getOpcode())
				{
					case Opcodes.INEG:
					{
						final int first = stack.pop();
						stack.push(-first);
						break;
					}
					case Opcodes.IMUL:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second * first);
						break;
					}
					case Opcodes.IDIV:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second / first);
						break;
					}
					case Opcodes.IREM:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second % first);
						break;
					}
					case Opcodes.IADD:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second + first);
						break;
					}
					case Opcodes.ISUB:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second - first);
						break;
					}

					case Opcodes.ISHR:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second >> first);
						break;
					}
					case Opcodes.ISHL:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second << first);
						break;
					}
					case Opcodes.IUSHR:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(second >>> first);
						break;
					}

					case Opcodes.IAND:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(first & second);
						break;
					}
					case Opcodes.IOR:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(first | second);
						break;
					}
					case Opcodes.IXOR:
					{
						final int first = stack.pop();
						final int second = stack.pop();
						stack.push(first ^ second);
						break;
					}
				}

		return stack.pop();
	}

	public long checkLong()
	{
		// Emulate the stack
		final Stack<Number> stack = new Stack<>();

		for (final AbstractInsnNode insnNode : insnList.toArray())
			if (ASMUtils.isLongInsn(insnNode))
				stack.push(ASMUtils.getLongFromInsn(insnNode));
			else if (ASMUtils.isIntInsn(insnNode))
				stack.push(ASMUtils.getIntegerFromInsn(insnNode));
			else
				switch (insnNode.getOpcode())
				{
					case Opcodes.LNEG:
					{
						final long first = stack.pop().longValue();
						stack.push(-first);
						break;
					}
					case Opcodes.LMUL:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(second * first);
						break;
					}
					case Opcodes.LDIV:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(second / first);
						break;
					}
					case Opcodes.LREM:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(second % first);
						break;
					}
					case Opcodes.LADD:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(second + first);
						break;
					}
					case Opcodes.LSUB:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(second - first);
						break;
					}

					case Opcodes.LSHR:
					{
						final int first = stack.pop().intValue();
						final long second = stack.pop().longValue();
						stack.push(second >> first);
						break;
					}
					case Opcodes.LSHL:
					{
						final int first = stack.pop().intValue();
						final long second = stack.pop().longValue();
						stack.push(second << first);
						break;
					}
					case Opcodes.LUSHR:
					{
						final int first = stack.pop().intValue();
						final long second = stack.pop().longValue();
						stack.push(second >>> first);
						break;
					}

					case Opcodes.LAND:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(first & second);
						break;
					}
					case Opcodes.LOR:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(first | second);
						break;
					}
					case Opcodes.LXOR:
					{
						final long first = stack.pop().longValue();
						final long second = stack.pop().longValue();
						stack.push(first ^ second);
						break;
					}
				}

		return stack.pop().longValue();
	}

	public float checkFloat()
	{
		// Emulate the stack
		final Stack<Float> stack = new Stack<>();

		for (final AbstractInsnNode insnNode : insnList.toArray())
			if (ASMUtils.isFloatInsn(insnNode))
				stack.push(ASMUtils.getFloatFromInsn(insnNode)); // 11(FCONST_0), 12(FCONST_1), 13(FCONST_2), 18(LDC)
			else
				switch (insnNode.getOpcode())
				{
					case Opcodes.FNEG:
					{
						final float first = stack.pop();
						stack.push(-first);
						break;
					}
					case Opcodes.FMUL: // 106
					{
						final float first = stack.pop();
						final float second = stack.pop();
						stack.push(second * first);
						break;
					}
					case Opcodes.FDIV: // 110
					{
						final float first = stack.pop();
						final float second = stack.pop();
						stack.push(second / first);
						break;
					}
					case Opcodes.FREM: // 114
					{
						final float first = stack.pop();
						final float second = stack.pop();
						stack.push(second % first);
						break;
					}
					case Opcodes.FADD: // 98
					{
						final float first = stack.pop();
						final float second = stack.pop();
						stack.push(second + first);
						break;
					}
					case Opcodes.FSUB: // 102
					{
						final float first = stack.pop();
						final float second = stack.pop();
						stack.push(second - first);
						break;
					}
				}

		return stack.pop();
	}

	public double checkDouble()
	{
		// Emulate the stack
		final Stack<Double> stack = new Stack<>();

		for (final AbstractInsnNode insnNode : insnList.toArray())
			if (ASMUtils.isDoubleInsn(insnNode))
				stack.push(ASMUtils.getDoubleFromInsn(insnNode)); // 14(DCONST_0), 15(DCONST_1), 18(LDC)
			else
				switch (insnNode.getOpcode())
				{
					case Opcodes.DNEG:
					{
						final double first = stack.pop();
						stack.push(-first);
						break;
					}
					case Opcodes.DMUL:// 107
					{
						final double first = stack.pop();
						final double second = stack.pop();
						stack.push(second * first);
						break;
					}
					case Opcodes.DDIV:// 111
					{
						final double first = stack.pop();
						final double second = stack.pop();
						stack.push(second / first);
						break;
					}
					case Opcodes.DREM:// 115
					{
						final double first = stack.pop();
						final double second = stack.pop();
						stack.push(second % first);
						break;
					}
					case Opcodes.DADD: // 99
					{
						final double first = stack.pop();
						final double second = stack.pop();
						stack.push(second + first);
						break;
					}
					case Opcodes.DSUB:// 103
					{
						final double first = stack.pop();
						final double second = stack.pop();
						stack.push(second - first);
						break;
					}
				}

		return stack.pop();
	}
}
