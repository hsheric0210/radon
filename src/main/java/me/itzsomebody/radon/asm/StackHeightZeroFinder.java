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

package me.itzsomebody.radon.asm;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.exceptions.StackEmulationException;

/**
 * Attempts to find all points where the stack height is zero (or in simple terms: empty) in a method.
 *
 * @author ItzSomebody
 */
public class StackHeightZeroFinder implements Opcodes
{
	/**
	 * {@link MethodNode} we are checking.
	 */
	private final MethodNode methodNode;

	/**
	 * {@link AbstractInsnNode} opcode which is the breakpoint.
	 */
	private final AbstractInsnNode breakPoint;

	/**
	 * {@link HashSet} of {@link AbstractInsnNode}s where the stack is empty
	 */
	private final Set<AbstractInsnNode> emptyAt;

	/**
	 * Constructor to create a {@link StackHeightZeroFinder} object.
	 *
	 * @param methodNode
	 *                   the method node we want to check.
	 * @param breakPoint
	 *                   the opcode we want to break on.
	 */
	public StackHeightZeroFinder(final MethodNode methodNode, final AbstractInsnNode breakPoint)
	{
		this.methodNode = methodNode;
		this.breakPoint = breakPoint;
		emptyAt = new HashSet<>();
	}

	/**
	 * Returns {@link HashSet} of {@link AbstractInsnNode}s where the stack is empty.
	 *
	 * @return {@link HashSet} of {@link AbstractInsnNode}s where the stack is empty.
	 */
	public Set<AbstractInsnNode> getEmptyAt()
	{
		return emptyAt;
	}

	/**
	 * Weakly emulates stack execution until no more instructions are left or the breakpoint is reached.
	 */
	public void execute() throws StackEmulationException
	{
		int stackSize = 0; // Emulated stack
		final Set<LabelNode> handlers = methodNode.tryCatchBlocks.stream().map(tryCatchBlockNode -> tryCatchBlockNode.handler).collect(Collectors.toSet());
		for (int i = 0, j = methodNode.instructions.size(); i < j; i++)
		{
			final AbstractInsnNode insn = methodNode.instructions.get(i);

			if (insn instanceof LabelNode && handlers.contains(insn))
				stackSize = 1; // Stack gets cleared and exception is pushed.

			if (stackSize < 0) // Should never happen
				throw new StackEmulationException("Stack underrun (stackSize < 0) BCI: " + i + ", Opcode: " + insn.getOpcode());

			if (stackSize == 0)
				emptyAt.add(insn);

			if (breakPoint == insn)
				break;

			switch (insn.getOpcode())
			{
				case ACONST_NULL:
				case ICONST_M1:
				case ICONST_0:
				case ICONST_1:
				case ICONST_2:
				case ICONST_3:
				case ICONST_4:
				case ICONST_5:
				case FCONST_0:
				case FCONST_1:
				case FCONST_2:
				case BIPUSH:
				case SIPUSH:
				case ILOAD:
				case FLOAD:
				case ALOAD:
				case DUP:
				case DUP_X1:
				case DUP_X2:
				case I2L:
				case I2D:
				case F2L:
				case F2D:
				case NEW:
					// Pushes one-word constant to stack
					stackSize++;
					break;
				case LDC:
					final LdcInsnNode ldc = (LdcInsnNode) insn;
					if (ldc.cst instanceof Long || ldc.cst instanceof Double)
						stackSize++;

					stackSize++;
					break;
				case LCONST_0:
				case LCONST_1:
				case DCONST_0:
				case DCONST_1:
				case LLOAD:
				case DLOAD:
				case DUP2:
				case DUP2_X1:
				case DUP2_X2:
					// Pushes two-word constant or two one-word constants to stack
					stackSize += 2;
					break;
				case IALOAD: // (index, arrayref) -> (int)
				case FALOAD: // (index, arrayref) -> (float)
				case AALOAD: // (index, arrayref) -> (Object)
				case BALOAD: // (index, arrayref) -> (byte)
				case CALOAD: // (index, arrayref) -> (char)
				case SALOAD: // (index, arrayref) -> (short)
				case ISTORE:
				case FSTORE:
				case ASTORE:
				case POP:
				case IADD:
				case FADD:
				case ISUB:
				case FSUB:
				case IMUL:
				case FMUL:
				case IDIV:
				case FDIV:
				case IREM:
				case FREM:
				case ISHL:
				case ISHR:
				case IUSHR:
				case LSHL:
				case LSHR:
				case LUSHR:
				case IAND:
				case IOR:
				case IXOR:
				case L2I:
				case L2F:
				case D2I:
				case D2F:
				case FCMPL:
				case FCMPG:
				case IFEQ:
				case IFNE:
				case IFLT:
				case IFGE:
				case IFGT:
				case IFLE:
				case TABLESWITCH:
				case LOOKUPSWITCH:
				case IRETURN:
				case FRETURN:
				case ATHROW:
				case MONITORENTER:
				case MONITOREXIT:
				case IFNULL:
				case IFNONNULL:
				case ARETURN:
					// Pops one-word constant off stack
					stackSize--;
					break;
				case LSTORE:
				case DSTORE:
				case POP2:
				case LADD:
				case DADD:
				case LSUB:
				case DSUB:
				case LMUL:
				case DMUL:
				case LDIV:
				case DDIV:
				case LREM:
				case DREM:
				case LAND:
				case LOR:
				case LXOR:
				case IF_ICMPEQ:
				case IF_ICMPNE:
				case IF_ICMPLT:
				case IF_ICMPGE:
				case IF_ICMPGT:
				case IF_ICMPLE:
				case IF_ACMPEQ:
				case IF_ACMPNE:
				case LRETURN:
				case DRETURN:
					// Pops two-word or two one-word constant(s) off stack
					stackSize -= 2;
					break;
				case IASTORE:
				case FASTORE:
				case AASTORE:
				case BASTORE:
				case CASTORE:
				case SASTORE:
				case LCMP:
				case DCMPL:
				case DCMPG:
					// Pops three one-word constants off stack
					stackSize -= 3;
					break;
				case LASTORE:
				case DASTORE:
					// Pops two one-word constants and one two-word constant off stack
					stackSize -= 4;
					break;
				case GETSTATIC:
					stackSize += doFieldEmulation(((FieldInsnNode) insn).desc, true);
					break;
				case PUTSTATIC:
					stackSize += doFieldEmulation(((FieldInsnNode) insn).desc, false);
					break;
				case GETFIELD:
					stackSize--; // Objectref
					stackSize += doFieldEmulation(((FieldInsnNode) insn).desc, true);
					break;
				case PUTFIELD:
					stackSize--; // Objectref
					stackSize += doFieldEmulation(((FieldInsnNode) insn).desc, false);
					break;
				case INVOKEVIRTUAL:
				case INVOKESPECIAL:
				case INVOKEINTERFACE:
					stackSize--; // Objectref
					stackSize += doMethodEmulation(((MethodInsnNode) insn).desc);
					break;
				case INVOKESTATIC:
					stackSize += doMethodEmulation(((MethodInsnNode) insn).desc);
					break;
				case INVOKEDYNAMIC:
					stackSize += doMethodEmulation(((InvokeDynamicInsnNode) insn).desc);
					break;
				case MULTIANEWARRAY:
					stackSize -= ((MultiANewArrayInsnNode) insn).dims;
					stackSize++; // arrayref
					break;
				case JSR:
				case RET:
					throw new StackEmulationException("Did not expect JSR/RET instructions");
				default:
					break;
			}
		}
	}

	private static int doFieldEmulation(final String desc, final boolean isGet)
	{
		final Type type = Type.getType(desc);
		final int result = type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE ? 2 : 1;

		return isGet ? result : -result;
	}

	private static int doMethodEmulation(final String desc)
	{
		int result = 0;

		final Type[] args = Type.getArgumentTypes(desc);
		for (final Type type : args)
		{
			if (type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE)
				result--;

			result--;
		}

		final Type returnType = Type.getReturnType(desc);
		if (returnType.getSort() != Type.VOID)
		{
			if (returnType.getSort() == Type.LONG || returnType.getSort() == Type.DOUBLE)
				result++;

			result++;
		}

		return result;
	}
}
