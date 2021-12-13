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

package me.itzsomebody.radon.utils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import me.itzsomebody.radon.exceptions.RadonException;

/**
 * Bytecode utilities for bytecode instructions.
 *
 * @author ItzSomebody.
 */
public final class ASMUtils implements Opcodes
{
	public static boolean isInstruction(final AbstractInsnNode insn)
	{
		return !(insn instanceof FrameNode) && !(insn instanceof LineNumberNode) && !(insn instanceof LabelNode);
	}

	public static boolean isReturn(final int opcode)
	{
		return opcode >= IRETURN && opcode <= RETURN;
	}

	public static boolean hasAnnotations(final ClassNode classNode)
	{
		return classNode.visibleAnnotations != null && !classNode.visibleAnnotations.isEmpty() || classNode.invisibleAnnotations != null && !classNode.invisibleAnnotations.isEmpty();
	}

	public static boolean hasAnnotations(final MethodNode methodNode)
	{
		return methodNode.visibleAnnotations != null && !methodNode.visibleAnnotations.isEmpty() || methodNode.invisibleAnnotations != null && !methodNode.invisibleAnnotations.isEmpty();
	}

	public static boolean hasAnnotations(final FieldNode fieldNode)
	{
		return fieldNode.visibleAnnotations != null && !fieldNode.visibleAnnotations.isEmpty() || fieldNode.invisibleAnnotations != null && !fieldNode.invisibleAnnotations.isEmpty();
	}

	public static boolean isIntInsn(final AbstractInsnNode insn)
	{
		if (insn == null)
			return false;
		final int opcode = insn.getOpcode();
		return opcode >= ICONST_M1 && opcode <= ICONST_5 || opcode == BIPUSH || opcode == SIPUSH || insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer;
	}

	public static boolean isLongInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();
		return opcode == LCONST_0 || opcode == LCONST_1 || insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Long;
	}

	public static boolean isFloatInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();
		return opcode >= FCONST_0 && opcode <= FCONST_2 || insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float;
	}

	public static boolean isDoubleInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();
		return opcode >= DCONST_0 && opcode <= DCONST_1 || insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double;
	}

	public static AbstractInsnNode getNumberInsn(final int number)
	{
		if (number >= -1 && number <= 5)
			return new InsnNode(number + 3);
		if (number >= -128 && number <= 127)
			return new IntInsnNode(BIPUSH, number);
		if (number >= -32768 && number <= 32767)
			return new IntInsnNode(SIPUSH, number);
		return new LdcInsnNode(number);
	}

	public static AbstractInsnNode getNumberInsn(final long number)
	{
		if (number >= 0 && number <= 1)
			return new InsnNode((int) (number + 9));
		return new LdcInsnNode(number);
	}

	public static AbstractInsnNode getNumberInsn(final float number)
	{
		if (number == 0 || number == 1 || number == 2)
			return new InsnNode((int) (number + 11));
		return new LdcInsnNode(number);
	}

	public static AbstractInsnNode getNumberInsn(final double number)
	{
		if (number == 0 || number == 1)
			return new InsnNode((int) (number + 14));
		return new LdcInsnNode(number);
	}

	public static int getIntegerFromInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();

		if (opcode >= ICONST_M1 && opcode <= ICONST_5)
			return opcode - 3;
		if (insn instanceof IntInsnNode && insn.getOpcode() != NEWARRAY)
			return ((IntInsnNode) insn).operand;
		if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer)
			return (Integer) ((LdcInsnNode) insn).cst;

		throw new RadonException("Unexpected instruction");
	}

	public static long getLongFromInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();

		if (opcode >= LCONST_0 && opcode <= LCONST_1)
			return opcode - 9;
		if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Long)
			return (Long) ((LdcInsnNode) insn).cst;

		throw new RadonException("Unexpected instruction");
	}

	public static float getFloatFromInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();

		if (opcode >= FCONST_0 && opcode <= FCONST_2)
			return opcode - 11;
		if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float)
			return (Float) ((LdcInsnNode) insn).cst;

		throw new RadonException("Unexpected instruction");
	}

	public static double getDoubleFromInsn(final AbstractInsnNode insn)
	{
		final int opcode = insn.getOpcode();

		if (opcode >= DCONST_0 && opcode <= DCONST_1)
			return opcode - 14;
		if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double)
			return (Double) ((LdcInsnNode) insn).cst;

		throw new RadonException("Unexpected instruction");
	}

	public static AbstractInsnNode getPrimitiveCastInsn(final Type from, final Type to)
	{
		int srcSort = from.getSort();
		if (srcSort >= Type.BOOLEAN && srcSort <= Type.SHORT)
			srcSort = Type.INT;

		int destSort = to.getSort();
		if (destSort >= Type.BOOLEAN && destSort <= Type.SHORT)
			destSort = Type.INT;

		switch (srcSort - Type.INT)
		{
			case 0: // INT
				switch (to.getSort() - Type.CHAR)
				{
					case 0: // CHAR
						return new InsnNode(I2C);
					case Type.SHORT - Type.CHAR: // SHORT
						return new InsnNode(I2S);
					case Type.BYTE - Type.CHAR: // BYTE
						return new InsnNode(I2B);
					case Type.FLOAT - Type.CHAR: // FLOAT
						return new InsnNode(I2F);
					case Type.LONG - Type.CHAR: // LONG
						return new InsnNode(I2L);
					case Type.DOUBLE - Type.CHAR: // DOUBLE
						return new InsnNode(I2D);
				}
				break;
			case 1: // FLOAT
				switch (destSort - Type.INT)
				{
					case 0: // INT
						return new InsnNode(F2I);
					case 2: // LONG
						return new InsnNode(F2L);
					case 3: // DOUBLE
						return new InsnNode(F2D);
				}
				break;
			case 2: // LONG
				switch (destSort - Type.INT)
				{
					case 0: // INT
						return new InsnNode(L2I);
					case 1: // FLOAT
						return new InsnNode(L2F);
					case 3: // DOUBLE
						return new InsnNode(L2D);
				}
				break;
			case 3: // DOUBLE
				switch (destSort - Type.INT)
				{
					case 0: // INT
						return new InsnNode(D2I);
					case 1: // FLOAT
						return new InsnNode(D2F);
					case 2: // LONG
						return new InsnNode(D2L);
				}
		}

		throw new AssertionError("From=" + from + ", To=" + to);
	}

	public static String getGenericMethodDesc(final String desc)
	{
		final Type[] args = Type.getArgumentTypes(desc);
		for (int i = 0, j = args.length; i < j; i++)
			if (args[i].getSort() == Type.OBJECT)
				args[i] = Type.getType("Ljava/lang/Object;");

		return Type.getMethodDescriptor(Type.getReturnType(desc), args);
	}

	public static int getReturnOpcode(final Type type)
	{
		switch (type.getSort())
		{
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				return IRETURN;
			case Type.FLOAT:
				return FRETURN;
			case Type.LONG:
				return LRETURN;
			case Type.DOUBLE:
				return DRETURN;
			case Type.ARRAY:
			case Type.OBJECT:
				return ARETURN;
			case Type.VOID:
				return RETURN;
			default:
				throw new IllegalArgumentException("getReturnOpcode(): Unexpected type: " + type.getClassName());
		}
	}

	public static int getVarOpcode(final Type type, final boolean store)
	{
		switch (type.getSort())
		{
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				return store ? ISTORE : ILOAD;
			case Type.FLOAT:
				return store ? FSTORE : FLOAD;
			case Type.LONG:
				return store ? LSTORE : LLOAD;
			case Type.DOUBLE:
				return store ? DSTORE : DLOAD;
			case Type.ARRAY:
			case Type.OBJECT:
				return store ? ASTORE : ALOAD;
			default:
				throw new IllegalArgumentException("getVarOpcode(): Unexpected type " + type.getClassName());
		}
	}

	public static InsnList asList(final AbstractInsnNode abstractInsnNode, final AbstractInsnNode... abstractInsnNodes)
	{
		final InsnList insnList = new InsnList();
		insnList.add(abstractInsnNode);
		if (abstractInsnNodes != null)
			Arrays.stream(abstractInsnNodes).forEach(insnList::add);

		return insnList;
	}

	public static InsnList singletonList(final AbstractInsnNode abstractInsnNode)
	{
		final InsnList insnList = new InsnList();
		insnList.add(abstractInsnNode);
		return insnList;
	}

	public static AbstractInsnNode getDefaultValue(final Type type)
	{
		switch (type.getSort())
		{
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				return getNumberInsn(0);
			case Type.FLOAT:
				return getNumberInsn(0.00f);
			case Type.LONG:
				return getNumberInsn(0L);
			case Type.DOUBLE:
				return getNumberInsn(0.00d);
			case Type.OBJECT:
			case Type.ARRAY:
				return new InsnNode(ACONST_NULL);
			default:
				throw new AssertionError();
		}
	}

	public static Type getRandomType()
	{
		switch (RandomUtils.getRandomInt(7))
		{
			case 0:
				return Type.BOOLEAN_TYPE;
			case 1:
				return Type.CHAR_TYPE;
			case 2:
				return Type.BYTE_TYPE;
			case 3:
				return Type.SHORT_TYPE;
			case 4:
				return Type.INT_TYPE;
			case 5:
				return Type.LONG_TYPE;
			case 6:
				return Type.FLOAT_TYPE;
			default:
				return Type.DOUBLE_TYPE;
		}
	}

	public static AbstractInsnNode getRandomInsn(final Type type)
	{
		switch (type.getSort())
		{
			case Type.BOOLEAN:
				return getNumberInsn(RandomUtils.getRandomInt(0, 2));
			case Type.CHAR:
				return getNumberInsn(RandomUtils.getRandomInt(Character.MIN_VALUE, Character.MAX_VALUE));
			case Type.BYTE:
				return getNumberInsn(RandomUtils.getRandomInt(Byte.MIN_VALUE, Byte.MAX_VALUE));
			case Type.SHORT:
				return getNumberInsn(RandomUtils.getRandomInt(Short.MIN_VALUE, Short.MAX_VALUE));
			case Type.INT:
				return getNumberInsn(RandomUtils.getRandomInt());
			case Type.FLOAT:
				return getNumberInsn(RandomUtils.getRandomFloat());
			case Type.LONG:
				return getNumberInsn(RandomUtils.getRandomLong());
			case Type.DOUBLE:
				return getNumberInsn(RandomUtils.getRandomDouble());
			case Type.ARRAY:
			case Type.OBJECT:
				return new InsnNode(ACONST_NULL);
			default:
				throw new AssertionError();
		}
	}

	public static int evaluateMaxSize(final InsnList insns)
	{
		final CodeSizeEvaluator cse = new CodeSizeEvaluator(null);
		insns.accept(cse);
		return cse.getMaxSize();
	}

	public static int evaluateMaxSize(final MethodNode methodNode)
	{
		final CodeSizeEvaluator cse = new CodeSizeEvaluator(null);
		methodNode.accept(cse);
		return cse.getMaxSize();
	}

	public static Optional<MethodNode> findMethod(final ClassNode classNode, final String methodName, final String methodDescriptor)
	{
		Objects.requireNonNull(methodName, "methodName");
		return Objects.requireNonNull(classNode, "classNode").methods.stream().filter(methodNode -> methodName.equals(methodNode.name) && methodDescriptor.equals(methodNode.desc)).findFirst();
	}

	public static Type getType(final VarInsnNode abstractInsnNode)
	{
		final int offset;

		final int opcode = abstractInsnNode.getOpcode();
		if (opcode >= ISTORE && opcode <= ASTORE)
			offset = opcode - ISTORE;
		else if (opcode >= ILOAD && opcode <= ALOAD)
			offset = opcode - ILOAD;
		else if (opcode == RET)
			throw new UnsupportedOperationException("RET is not supported");
		else
			throw new IllegalArgumentException("VarInsnNode has unexpected opcode: " + opcode);

		switch (offset)
		{
			case 0:
				return Type.INT_TYPE;
			case 1:
				return Type.LONG_TYPE;
			case 2:
				return Type.FLOAT_TYPE;
			case 3:
				return Type.DOUBLE_TYPE;
			case 4:
				return Type.getType("Ljava/lang/Object;");
		}

		throw new IllegalArgumentException("Unexpected offset: " + offset);
	}

	public static boolean isSuperInitializerCall(final MethodNode mn, final AbstractInsnNode insn)
	{
		return insn != null && insn.getOpcode() == INVOKESPECIAL && "<init>".equals(((MethodInsnNode) insn).name) // Check if the current instruction is INVOKESPECIAL which calling <init>
				&& insn.getPrevious() != null && insn.getPrevious().getOpcode() == ALOAD && ((VarInsnNode) insn.getPrevious()).var == 0;
	}

	public static boolean isSuperCall(final AbstractInsnNode insn)
	{
		return insn != null && insn.getOpcode() == INVOKESPECIAL && // Check if the current instruction is INVOKESPECIAL
				insn.getPrevious() != null && insn.getPrevious().getOpcode() == ALOAD && ((VarInsnNode) insn.getPrevious()).var == 0; // Check if the previous instruction is ALOAD_0 (Reference to self)
	}

	public static void insertAfterConstructorCall(final MethodNode mn, final InsnList inserted)
	{
		final InsnList insns = mn.instructions;
		final Optional<AbstractInsnNode> optSuperCall = "<init>".equals(mn.name) ? Arrays.stream(insns.toArray()).filter(insn -> isSuperInitializerCall(mn, insn)).findFirst() : Optional.empty();
		if (optSuperCall.isPresent())
		{
			insns.insert(optSuperCall.get(), inserted);
			return;
		}

		insns.insert(inserted);
	}

	public static FrameNode createStackMapFrame(final int frameType, final Frame<? extends BasicValue> currentFrame)
	{
		final int numStack = currentFrame.getStackSize();
		final int numLocal = currentFrame.getLocals();

		final Object[] stack;
		if (numStack > 0)
		{
			stack = new Object[numStack];
			for (int i = 0; i < numStack; i++)
			{
				final Type type = currentFrame.getStack(i).getType();
				if (type == null)
					stack[i] = NULL;
				else
					stack[i] = type.getInternalName();
			}
		}
		else
			stack = null;

		final Object[] local;
		if (numLocal > 0)
		{
			local = new Object[numLocal];
			for (int i = 0; i < numLocal; i++)
			{
				final Type type = currentFrame.getLocal(i).getType();
				if (type == null)
					local[i] = NULL;
				else
					local[i] = type.getInternalName();
			}
		}
		else
			local = null;

		return new FrameNode(frameType, numLocal, local, numStack, stack);
	}

	/**
	 * @see <a href="https://github.com/openjdk/jdk/blob/master/src/hotspot/share/classfile/classFileParser.cpp">classFileParser.cpp</a>, line 4741
	 */
	public static boolean isIllegalMethodName(final char character)
	{
		return character == '.' || character == ';' || character == '[' || character == '/' || character == '<' || character == '>';
	}

	public static <T extends Value> Frame<T>[] runAnalyzer(final Analyzer<T> analyzer, final MethodNode mn) throws AnalyzerException
	{
		final int maxStack = mn.maxStack;
		final int maxLocals = mn.maxLocals;

		mn.maxStack = mn.maxLocals=1000;

		try
		{
			return analyzer.analyze(mn.name, mn);
		}
		finally
		{
			mn.maxStack = maxStack;
			mn.maxLocals = maxLocals;
		}
	}

	private ASMUtils()
	{
	}
}
