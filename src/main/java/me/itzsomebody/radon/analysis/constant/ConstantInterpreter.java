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

package me.itzsomebody.radon.analysis.constant;

import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;

import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.analysis.constant.values.ConstantValue;
import me.itzsomebody.radon.analysis.constant.values.NullValue;
import me.itzsomebody.radon.analysis.constant.values.UnknownValue;

public class ConstantInterpreter extends Interpreter<AbstractValue> implements Opcodes
{
	ConstantInterpreter(final int api)
	{
		super(api);
	}

	private static UnknownValue intSymbolicValue(final AbstractInsnNode insnNode)
	{
		return new UnknownValue(insnNode, Type.INT_TYPE);
	}

	private static UnknownValue floatSymbolicValue(final AbstractInsnNode insnNode)
	{
		return new UnknownValue(insnNode, Type.FLOAT_TYPE);
	}

	private static UnknownValue longSymbolicValue(final AbstractInsnNode insnNode)
	{
		return new UnknownValue(insnNode, Type.LONG_TYPE);
	}

	private static UnknownValue doubleSymbolicValue(final AbstractInsnNode insnNode)
	{
		return new UnknownValue(insnNode, Type.DOUBLE_TYPE);
	}

	private static AbstractValue valueFromArrayInsn(final IntInsnNode intInsn)
	{
		switch (intInsn.operand)
		{
			case T_BOOLEAN:
				return new UnknownValue(intInsn, Type.getType("[Z"));
			case T_CHAR:
				return new UnknownValue(intInsn, Type.getType("[C"));
			case T_BYTE:
				return new UnknownValue(intInsn, Type.getType("[B"));
			case T_SHORT:
				return new UnknownValue(intInsn, Type.getType("[S"));
			case T_INT:
				return new UnknownValue(intInsn, Type.getType("[I"));
			case T_FLOAT:
				return new UnknownValue(intInsn, Type.getType("[F"));
			case T_DOUBLE:
				return new UnknownValue(intInsn, Type.getType("[D"));
			case T_LONG:
				return new UnknownValue(intInsn, Type.getType("[J"));
			default:
				throw new IllegalArgumentException("Unexpected array type: " + intInsn.operand);
		}
	}

	@Override
	public AbstractValue newValue(final Type type)
	{
		if (type == null)
			return UnknownValue.UNINITIALIZED_VALUE;
		if (type.getSort() == Type.VOID)
			return null;
		return new UnknownValue(type);
	}

	@Override
	public AbstractValue newOperation(final AbstractInsnNode insnNode)
	{
		switch (insnNode.getOpcode())
		{
			case ACONST_NULL:
				return new NullValue(insnNode);
			case ICONST_M1:
				return ConstantValue.fromInteger(insnNode, -1);
			case ICONST_0:
				return ConstantValue.fromInteger(insnNode, 0);
			case ICONST_1:
				return ConstantValue.fromInteger(insnNode, 1);
			case ICONST_2:
				return ConstantValue.fromInteger(insnNode, 2);
			case ICONST_3:
				return ConstantValue.fromInteger(insnNode, 3);
			case ICONST_4:
				return ConstantValue.fromInteger(insnNode, 4);
			case ICONST_5:
				return ConstantValue.fromInteger(insnNode, 5);
			case LCONST_0:
				return ConstantValue.fromLong(insnNode, 0);
			case LCONST_1:
				return ConstantValue.fromLong(insnNode, 1);
			case FCONST_0:
				return ConstantValue.fromFloat(insnNode, 0);
			case FCONST_1:
				return ConstantValue.fromFloat(insnNode, 1);
			case FCONST_2:
				return ConstantValue.fromFloat(insnNode, 2);
			case DCONST_0:
				return ConstantValue.fromDouble(insnNode, 0);
			case DCONST_1:
				return ConstantValue.fromDouble(insnNode, 1);
			case BIPUSH:
			case SIPUSH:
				return ConstantValue.fromInteger(insnNode, ((IntInsnNode) insnNode).operand);
			case LDC:
			{
				final Object cst = ((LdcInsnNode) insnNode).cst;
				if (cst instanceof Integer)
					return ConstantValue.fromInteger(insnNode, (Integer) cst);
				if (cst instanceof Float)
					return ConstantValue.fromFloat(insnNode, (Float) cst);
				if (cst instanceof Long)
					return ConstantValue.fromLong(insnNode, (Long) cst);
				if (cst instanceof Double)
					return ConstantValue.fromDouble(insnNode, (Double) cst);
				if (cst instanceof String)
					return ConstantValue.fromString(insnNode, (String) cst);
				if (cst instanceof Type)
				{
					final int sort = ((Type) cst).getSort();
					if (sort == Type.OBJECT || sort == Type.ARRAY || sort == Type.METHOD)
						return new UnknownValue(insnNode, (Type) cst);
					throw new IllegalArgumentException("Unexpected/Unsuppored Type LDC constant: " + cst);
				}
				throw new IllegalArgumentException("Unexpected/Unsupported LDC constant: " + cst);
			}
			case JSR:
				throw new UnsupportedOperationException("Do not support instruction types JSR - Deprecated in Java 6");
			case GETSTATIC:
				return new UnknownValue(insnNode, Type.getType(((FieldInsnNode) insnNode).desc));
			case NEW:
				return new UnknownValue(insnNode, Type.getObjectType(((TypeInsnNode) insnNode).desc));
			default:
				throw new IllegalArgumentException("Unexpected new operation instruction opcode: " + insnNode.getOpcode());
		}
	}

	@Override
	public AbstractValue copyOperation(final AbstractInsnNode insnNode, final AbstractValue symbolicValue)
	{
		symbolicValue.addUsage(insnNode);
		return new UnknownValue(symbolicValue.getInsnNode(), symbolicValue.getType());
	}

	@Override
	public AbstractValue unaryOperation(final AbstractInsnNode insnNode, final AbstractValue symbolicValue)
	{
		symbolicValue.addUsage(insnNode);
		switch (insnNode.getOpcode())
		{
			case INEG:
			case INSTANCEOF:
			case ARRAYLENGTH:
			case D2I:
			case F2I:
			case L2I:
			case IINC:
				return intSymbolicValue(insnNode);
			case LNEG:
			case D2L:
			case F2L:
			case I2L:
				return longSymbolicValue(insnNode);
			case FNEG:
			case D2F:
			case L2F:
			case I2F:
				return floatSymbolicValue(insnNode);
			case DNEG:
			case F2D:
			case L2D:
			case I2D:
				return doubleSymbolicValue(insnNode);
			case I2B:
				return new UnknownValue(insnNode, Type.BYTE_TYPE);
			case I2C:
				return new UnknownValue(insnNode, Type.CHAR_TYPE);
			case I2S:
				return new UnknownValue(insnNode, Type.SHORT_TYPE);
			case IFEQ:
			case IFNE:
			case IFLT:
			case IFGE:
			case IFGT:
			case IFLE:
			case TABLESWITCH:
			case LOOKUPSWITCH:
			case IRETURN:
			case LRETURN:
			case FRETURN:
			case DRETURN:
			case ARETURN:
			case PUTSTATIC:
			case MONITORENTER:
			case MONITOREXIT:
			case IFNULL:
			case IFNONNULL:
			case ATHROW:
				return null;
			case GETFIELD:
				return new UnknownValue(insnNode, Type.getType(((FieldInsnNode) insnNode).desc));
			case NEWARRAY:
				return valueFromArrayInsn((IntInsnNode) insnNode);
			case ANEWARRAY:
				return new UnknownValue(insnNode, Type.getType("[" + Type.getObjectType(((TypeInsnNode) insnNode).desc)));
			case CHECKCAST:
				return new UnknownValue(insnNode, Type.getObjectType(((TypeInsnNode) insnNode).desc));
			default:
				throw new IllegalArgumentException("Unexpected unary operation instruction opcode: " + insnNode.getOpcode());
		}
	}

	@Override
	public AbstractValue binaryOperation(final AbstractInsnNode insnNode, final AbstractValue symbolicValue1, final AbstractValue symbolicValue2) throws AnalyzerException
	{
		symbolicValue1.addUsage(insnNode);
		symbolicValue2.addUsage(insnNode);
		switch (insnNode.getOpcode())
		{
			case IALOAD:
			case IADD:
			case ISUB:
			case IMUL:
			case IDIV:
			case IREM:
			case ISHL:
			case ISHR:
			case IUSHR:
			case IAND:
			case IOR:
			case IXOR:
			case LCMP:
			case FCMPL:
			case FCMPG:
			case DCMPL:
			case DCMPG:
				return intSymbolicValue(insnNode);
			case LALOAD:
			case LADD:
			case LSUB:
			case LMUL:
			case LDIV:
			case LREM:
			case LSHL:
			case LSHR:
			case LUSHR:
			case LAND:
			case LOR:
			case LXOR:
				return longSymbolicValue(insnNode);
			case FALOAD:
			case FADD:
			case FSUB:
			case FMUL:
			case FDIV:
			case FREM:
				return floatSymbolicValue(insnNode);
			case DALOAD:
			case DADD:
			case DSUB:
			case DMUL:
			case DDIV:
			case DREM:
				return doubleSymbolicValue(insnNode);
			case AALOAD:
			{
				final Type arrayType = symbolicValue1.getType();
				if (arrayType == null)
					return new UnknownValue(insnNode, null);
				if (arrayType.getSort() != Type.ARRAY)
					throw new AnalyzerException(insnNode, symbolicValue1 + " is not array");
				return new UnknownValue(insnNode, arrayType.getElementType());
			}
			case BALOAD:
				return new UnknownValue(insnNode, Type.BYTE_TYPE);
			case CALOAD:
				return new UnknownValue(insnNode, Type.CHAR_TYPE);
			case SALOAD:
				return new UnknownValue(insnNode, Type.SHORT_TYPE);
			case IF_ICMPEQ:
			case IF_ICMPNE:
			case IF_ICMPLT:
			case IF_ICMPGE:
			case IF_ICMPGT:
			case IF_ICMPLE:
			case IF_ACMPEQ:
			case IF_ACMPNE:
			case PUTFIELD:
				return null;
			default:
				throw new IllegalArgumentException("Unexpected binary operation instruction opcode: " + insnNode.getOpcode());
		}
	}

	@Override
	public AbstractValue ternaryOperation(final AbstractInsnNode abstractInsnNode, final AbstractValue symbolicValue1, final AbstractValue symbolicValue2, final AbstractValue symbolicValue3)
	{
		symbolicValue1.addUsage(abstractInsnNode);
		symbolicValue2.addUsage(abstractInsnNode);
		symbolicValue3.addUsage(abstractInsnNode);
		return null;
	}

	@Override
	public AbstractValue naryOperation(final AbstractInsnNode insnNode, final List<? extends AbstractValue> list)
	{
		for (final AbstractValue abstractValue : list)
			abstractValue.addUsage(insnNode);

		switch (insnNode.getOpcode())
		{
			case INVOKEVIRTUAL:
			case INVOKESPECIAL:
			case INVOKESTATIC:
			case INVOKEINTERFACE:
				return new UnknownValue(insnNode, Type.getReturnType(((MethodInsnNode) insnNode).desc));
			case INVOKEDYNAMIC:
				return new UnknownValue(insnNode, Type.getReturnType(((InvokeDynamicInsnNode) insnNode).desc));
			case MULTIANEWARRAY:
				return new UnknownValue(insnNode, Type.getType(((MultiANewArrayInsnNode) insnNode).desc));
			default:
				throw new IllegalArgumentException("Unexpected nary operation instruction opcode: " + insnNode.getOpcode());
		}
	}

	@Override
	public void returnOperation(final AbstractInsnNode abstractInsnNode, final AbstractValue symbolicValue, final AbstractValue expectedSymbolicValue)
	{
		symbolicValue.addUsage(abstractInsnNode);
	}

	@Override
	public AbstractValue merge(final AbstractValue symbolicValue1, final AbstractValue symbolicValue2)
	{
		if (!symbolicValue1.equals(symbolicValue2))
			return UnknownValue.UNINITIALIZED_VALUE;
		return symbolicValue1;
	}
}
