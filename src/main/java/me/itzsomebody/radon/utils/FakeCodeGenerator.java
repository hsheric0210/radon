package me.itzsomebody.radon.utils;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

public final class FakeCodeGenerator implements Opcodes
{
	public InsnList generateTrashInstructions(final ClassNode cn, final MethodNode mn, final Frame<BasicValue> frame)
	{
		final InsnList insns = new InsnList();

		final Type returnType = Type.getReturnType(mn.desc);
		final int stack = frame.getStackSize();
		final int maxMoreStacks = RandomUtils.getRandomInt(2, 10);
		final BasicValue[] stacks = IntStream.range(0, stack).mapToObj(frame::getStack).toArray(BasicValue[]::new);
		final int local = frame.getLocals();
		final BasicValue[] locals = IntStream.range(0, local).mapToObj(frame::getLocal).toArray(BasicValue[]::new);

		final Type[] objectsInLocal = Arrays.stream(locals).map(BasicValue::getType).filter(type -> type.getSort() == Type.OBJECT).toArray(Type[]::new);

		final Collection<MethodNode> methods = cn.methods.stream().filter(m -> (m.access & ACC_PUBLIC) != 0 && (m.access & ACC_STATIC) != 0).collect(Collectors.toSet());
		final Collection<FieldNode> fields = cn.fields.stream().filter(f -> (f.access & ACC_PUBLIC) != 0 && (f.access & ACC_STATIC) != 0).collect(Collectors.toSet());

		final Deque<Type> typeStack = new ArrayDeque<>(maxMoreStacks);

		for (int i = 0, j = RandomUtils.getRandomInt(20); i < j; i++)
		{
			switch (RandomUtils.getRandomInt(4))
			{
				case 0:
					if (!typeStack.isEmpty())
					{
						// UNARY OPERATOR
						final Entry<Type, AbstractInsnNode> entry = createUnaryOperator(cn.name, methods, typeStack.pop());
						insns.add(entry.getValue());
						typeStack.push(entry.getKey());
						break;
					}
				case 1:
					if (typeStack.size() > 1)
					{
						// BINARY OPERATOR
						final Entry<Type, AbstractInsnNode> entry = createBinaryOperator(cn.name, methods, typeStack.pop(), typeStack.pop());
						insns.add(entry.getValue());
						typeStack.push(entry.getKey());
						break;
					}
				case 2:
					if (typeStack.size() > 2)
					{
						// TENARY OPERATOR
						final Entry<Type, AbstractInsnNode> entry = createTenaryOperator(cn.name, methods, typeStack.pop(), typeStack.pop(), typeStack.pop());
						insns.add(entry.getValue());
						typeStack.push(entry.getKey());
						break;
					}
				case 3:
					if (!typeStack.isEmpty())
					{
						// SINGLE STACK CONSUMER
						insns.add(createStackConsumer(cn.name, methods, typeStack.pop()));
						break;
					}
					break;
				default:
					// STACK SUPPLIER
					final Type newType = RandomUtils.getRandomBoolean() ? RandomUtils.getRandomElement(objectsInLocal) : ASMUtils.getRandomType();
					insns.add(createStackSupplier(cn.name, methods, fields, newType, locals));
					typeStack.push(newType);
			}
		}

		while (!typeStack.isEmpty())
		{
//			REMAINING STACK CONSUMER HERE
		}
		return insns;
	}

	public AbstractInsnNode createStackSupplier(final String owner, final Collection<? extends MethodNode> methods, final Collection<? extends FieldNode> fields, final Type type, final BasicValue[] locals)
	{
		final String typeDescriptor = type.getDescriptor();
		final int typeSort = type.getSort();

		switch (RandomUtils.getRandomInt(4))
		{
			case 0:
				// INVOKESTATIC
				final MethodNode[] _methods = methods.stream().filter(f -> Type.getReturnType(f.desc).getSort() == typeSort).toArray(MethodNode[]::new);
				if (_methods.length > 0)
					return new MethodInsnNode(INVOKESTATIC, owner, RandomUtils.getRandomElement(_methods).name, typeDescriptor);
			case 1:
				// GETSTATIC
				final FieldNode[] _fields = fields.stream().filter(f -> typeDescriptor.equals(f.desc)).toArray(FieldNode[]::new);
				if (_fields.length > 0)
					return new FieldInsnNode(GETSTATIC, owner, RandomUtils.getRandomElement(_fields).name, typeDescriptor);
			case 2:
				// PUTSTATIC
				final List<Integer> availableLocals = new ArrayList<>();
				for (int i = 0, j = locals.length; i < j; i++)
					if (locals[i] != null && locals[i].getType().getSort() == typeSort)
						availableLocals.add(i);
				if (availableLocals.size() > 0)
					return new VarInsnNode(ASMUtils.getVarOpcode(type, false), RandomUtils.getRandomElement(availableLocals));
			default:
				return ASMUtils.getRandomInsn(type);
		}
	}

	private AbstractInsnNode createStackConsumer(final String name, final Collection<MethodNode> methods, final Type type)
	{
		throw new UnsupportedOperationException();
	}

	private Entry<Type, AbstractInsnNode> createUnaryOperator(final String name, final Collection<MethodNode> methods, final Type pop)
	{
		throw new UnsupportedOperationException();
	}

	private Entry<Type, AbstractInsnNode> createBinaryOperator(final String name, final Collection<MethodNode> methods, final Type pop, final Type pop1)
	{
		throw new UnsupportedOperationException();
	}

	private Entry<Type, AbstractInsnNode> createTenaryOperator(final String name, final Collection<MethodNode> methods, final Type pop, final Type pop1, final Type pop2)
	{
		throw new UnsupportedOperationException();
	}

	public static InsnList generateCodes(final MethodNode mn)
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

	private FakeCodeGenerator()
	{

	}
}
