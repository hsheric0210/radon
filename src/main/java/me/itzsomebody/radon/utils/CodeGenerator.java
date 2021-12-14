package me.itzsomebody.radon.utils;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.dictionaries.WrappedDictionary;

public final class CodeGenerator implements Opcodes
{
	private CodeGenerator()
	{

	}

	public static void generateDummyCallRecursive(final InsnList insns, final Type typeOfValueInStack)
	{
		final float random = RandomUtils.getRandomFloat();
		if (random > 0.8f)
			return;

		final MethodInsnNode methodNode;
		final Type typeOfNewValueInStack;

		switch (typeOfValueInStack.getSort())
		{
			case Type.BOOLEAN:
				switch (RandomUtils.getRandomInt(6))
				{
					case 0:
						// new Boolean(Z)
						insns.add(new TypeInsnNode(NEW, "java/lang/Boolean"));
						insns.add(new InsnNode(DUP));
						methodNode = new MethodInsnNode(INVOKESPECIAL, "java/lang/Boolean", "<init>", "(Z)V", false);
						typeOfNewValueInStack = Type.getType(Boolean.class);
						break;
					case 1:
						// Boolean.valueOf(Z)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
						typeOfNewValueInStack = Type.getType(Boolean.class);
						break;
					case 2:
						// Boolean.toString(Z)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "toString", "(Z)Ljava/lang/String;", false);
						typeOfNewValueInStack = Type.getType(String.class);
						break;
					case 3:
						// Boolean.hashCode(Z)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "hashCode", "(Z)I", false);
						typeOfNewValueInStack = Type.INT_TYPE;
						break;
					case 4:
						// Boolean.compare(ZZ)
						insns.add(ASMUtils.getRandomInsn(Type.BOOLEAN_TYPE));
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "compare", "(ZZ)I", false);
						typeOfNewValueInStack = Type.INT_TYPE;
						break;
					case 5:
						final String methodName;
						switch (RandomUtils.getRandomInt(3))
						{
							case 0:
								methodName = "logicalAnd";
								break;
							case 1:
								methodName = "logicalOr";
								break;
							case 2:
								methodName = "logicalXor";
								break;
							default:
								throw new AssertionError();
						}

						insns.add(ASMUtils.getRandomInsn(Type.BOOLEAN_TYPE));
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", methodName, "(ZZ)Z", false);
						typeOfNewValueInStack = Type.BOOLEAN_TYPE;
						break;
					default:
						throw new AssertionError();
				}
				insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "hashCode", "(Z)I", false));
				break;
			case Type.CHAR:
				switch (RandomUtils.getRandomInt(7))
				{
					case 0:
						// new Character(C)
						insns.add(new TypeInsnNode(NEW, "java/lang/Character"));
						insns.add(new InsnNode(DUP));
						methodNode = new MethodInsnNode(INVOKESPECIAL, "java/lang/Character", "<init>", "(C)V", false);
						typeOfNewValueInStack = Type.getType(Character.class);
						break;
					case 1:
						// Character.valueOf(C)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
						typeOfNewValueInStack = Type.getType(Character.class);
						break;
					case 2:
						// Character.toString(Z)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "toString", "(C)Ljava/lang/String;", false);
						typeOfNewValueInStack = Type.getType(String.class);
						break;
					case 3:
						// Character.hashCode(Z)
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "hashCode", "(C)I", false);
						typeOfNewValueInStack = Type.INT_TYPE;
						break;
					case 4:
						// Character.toCodePoint(CC)
						insns.add(ASMUtils.getRandomInsn(Type.CHAR_TYPE));
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "toCodePoint", "(CC)I");
						typeOfNewValueInStack = Type.BOOLEAN_TYPE;
						break;
					case 5:
					{
						final String methodName;
						switch (RandomUtils.getRandomInt(19))
						{
							case 0:
								methodName = "isHighSurrogate";
								break;
							case 1:
								methodName = "isLowSurrogate";
								break;
							case 2:
								methodName = "isSurrogate";
								break;
							case 3:
								methodName = "isLowerCase";
								break;
							case 4:
								methodName = "isUpperCase";
								break;
							case 5:
								methodName = "isTitleCase";
								break;
							case 6:
								methodName = "isDigit";
								break;
							case 7:
								methodName = "isDefined";
								break;
							case 8:
								methodName = "isLetter";
								break;
							case 9:
								methodName = "isLetterOrDigit";
								break;
							case 10:
								methodName = "isJavaIdentifierStart";
								break;
							case 11:
								methodName = "isJavaIdentifierPart";
								break;
							case 12:
								methodName = "isUnicodeIdentifierStart";
								break;
							case 13:
								methodName = "isUnicodeIdentifierPart";
								break;
							case 14:
								methodName = "isIdentifierIgnorable";
								break;
							case 15:
								methodName = "isSpaceChar";
								break;
							case 16:
								methodName = "isWhiteSpace";
								break;
							case 17:
								methodName = "isISOControl";
								break;
							case 18:
								methodName = "isMirrored";
								break;
							default:
								throw new AssertionError();
						}
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", methodName, "(C)Z");
						typeOfNewValueInStack = Type.BOOLEAN_TYPE;
						break;
					}
					case 6:
					{
						final String methodName;
						switch (RandomUtils.getRandomInt(3))
						{
							case 0:
								methodName = "toLowerCase";
								break;
							case 1:
								methodName = "toUpperCase";
								break;
							case 2:
								methodName = "toTitleCase";
								break;
							case 3:
								methodName = "reverseBytes";
								break;
							default:
								throw new AssertionError();
						}
						methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Character", methodName, "(C)C");
						typeOfNewValueInStack = Type.CHAR_TYPE;
						break;
					}
					default:
						throw new AssertionError();
				}
				break;
			case Type.SHORT:
				methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "toUnsignedInt", "(S)I", false);
				typeOfNewValueInStack = Type.INT_TYPE;
				break;
			case Type.BYTE:
				insns.add(ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Byte.MAX_VALUE)));
				methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "compare", "(BB)I", false);
				typeOfNewValueInStack = Type.INT_TYPE;
				break;
			case Type.INT:
				methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "toBinaryString", "(I)Ljava/lang/String;", false);
				typeOfNewValueInStack = Type.getType(String.class);
				break;
			case Type.FLOAT:
				methodNode = new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "floatToIntBits", "(F)I", false);
				typeOfNewValueInStack = Type.INT_TYPE;
				break;
			default:
				methodNode = new MethodInsnNode(INVOKESTATIC, "java/util/Objects", "hashCode", "(Ljava/lang/Object;)I", false);
				typeOfNewValueInStack = Type.INT_TYPE;
				break;
		}

		insns.add(methodNode);
		generateDummyCallRecursive(insns, typeOfNewValueInStack);
	}

	public static void generateStringCallTerminal(final InsnList insns, final CharSequence stringInStack, final WrappedDictionary ldcStringDictionary)
	{
		final String methodName;
		final String methodDesc;

		final int length = stringInStack.length();
		if (length <= 0 || RandomUtils.getRandomFloat() > 0.7f)
		{
			final Supplier<AbstractInsnNode> randomStringNode = () -> new LdcInsnNode(ldcStringDictionary.randomString());
			final Supplier<AbstractInsnNode> randomCharacterNode = () -> ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Character.MIN_SUPPLEMENTARY_CODE_POINT));

			switch (RandomUtils.getRandomInt(19))
			{
				case 0:
					methodName = "length";
					methodDesc = "()I";
					break;

				case 1:
					methodName = "isEmpty";
					methodDesc = "()Z";
					break;

				case 2:
					insns.add(new LdcInsnNode("UTF-8"));
					methodName = "getBytes";
					methodDesc = "(Ljava/lang/String;)[B";
					break;

				case 3:
					methodName = "getBytes";
					methodDesc = "()[B";
					break;

				case 4:
					insns.add(randomStringNode.get());
					methodName = "contentEquals";
					methodDesc = "(Ljava/lang/CharSequence;)Z";
					break;

				case 5:
					insns.add(randomStringNode.get());
					methodName = "equalsIgnoreCase";
					methodDesc = "(Ljava/lang/String;)Z";
					break;

				case 6:
					insns.add(randomStringNode.get());
					methodName = "compareTo";
					methodDesc = "(Ljava/lang/String;)I";
					break;

				case 7:
					insns.add(randomStringNode.get());
					methodName = "compareToIgnoreCase";
					methodDesc = "(Ljava/lang/String;)I";
					break;

				case 8:
					insns.add(randomStringNode.get());
					methodName = "startsWith";
					methodDesc = "(Ljava/lang/String;)Z";
					break;

				case 9:
					insns.add(randomStringNode.get());
					methodName = "endsWith";
					methodDesc = "(Ljava/lang/String;)Z";
					break;

				case 10:
					methodName = "hashCode";
					methodDesc = "()I";
					break;

				case 11:
					insns.add(randomCharacterNode.get());
					methodName = "indexOf";
					methodDesc = "(I)I";
					break;

				case 12:
					insns.add(randomCharacterNode.get());
					methodName = "lastIndexOf";
					methodDesc = "(I)I";
					break;

				case 13:
					insns.add(randomStringNode.get());
					methodName = "indexOf";
					methodDesc = "(Ljava/lang/String;)I";
					break;

				case 14:
					insns.add(randomStringNode.get());
					methodName = "lastIndexOf";
					methodDesc = "(Ljava/lang/String;)I";
					break;

				case 15:
					insns.add(randomStringNode.get());
					methodName = "contains";
					methodDesc = "(Ljava/lang/CharSequence;)Z";
					break;

				case 16:
					insns.add(randomStringNode.get());
					insns.add(ASMUtils.getNumberInsn(RandomUtils.getRandomInt(32)));
					methodName = "split";
					methodDesc = "(Ljava/lang/String;I)[Ljava/lang/String;";
					break;

				case 17:
					insns.add(randomStringNode.get());
					methodName = "split";
					methodDesc = "(Ljava/lang/String;)[Ljava/lang/String;";
					break;

				case 18:
					methodName = "toCharArray";
					methodDesc = "()[C";
					break;

				default:
					throw new AssertionError();
			}
		}
		else
		{
			final int startIndex = RandomUtils.getRandomInt(length);
			final int endIndex = RandomUtils.getRandomInt(startIndex, length);

			insns.add(ASMUtils.getNumberInsn(startIndex));
			final Supplier<AbstractInsnNode> endIndexNode = () -> ASMUtils.getNumberInsn(endIndex);

			switch (RandomUtils.getRandomInt(5))
			{
				case 0:
					methodName = "charAt";
					methodDesc = "(I)C";
					break;

				case 1:
					methodName = "codePointAt";
					methodDesc = "(I)I";
					break;

				case 2:

					methodName = "codePointBefore";
					methodDesc = "(I)I";
					break;

				case 3:
					insns.add(endIndexNode.get());

					methodName = "codePointCount";
					methodDesc = "(II)I";
					break;

				case 4:
					insns.add(endIndexNode.get());

					methodName = "offsetByCodePoints";
					methodDesc = "(II)I";
					break;

				default:
					throw new AssertionError();
			}
		}

		insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", methodName, methodDesc, false));
	}

	public static void generateStringCallRecursive(final InsnList insns, String stringInStack, final WrappedDictionary ldcStringDictionary, final boolean allowTerminal)
	{
		final float random = RandomUtils.getRandomFloat();
		if (random > 0.8f)
			return;

		if (allowTerminal && random < 0.2f)
		{
			generateStringCallTerminal(insns, stringInStack, ldcStringDictionary);
			return;
		}

		final String methodName;
		final String methodDesc;

		final int length = stringInStack.length();

		if (length <= 0 || RandomUtils.getRandomFloat() > 0.7f)
			switch (RandomUtils.getRandomInt(9))
			{
				case 0:
					final String other = ldcStringDictionary.randomString();

					insns.add(new LdcInsnNode(other));
					methodName = "concat";
					methodDesc = "(Ljava/lang/String;)Ljava/lang/String;";

					stringInStack += other;
					break;

				case 1:
				{
					final int from = RandomUtils.getRandomInt(Character.MAX_VALUE);
					final int to = RandomUtils.getRandomInt(Character.MAX_VALUE);

					insns.add(ASMUtils.getNumberInsn(from));
					insns.add(ASMUtils.getNumberInsn(to));

					methodName = "replace";
					methodDesc = "(CC)Ljava/lang/String;";

					stringInStack = stringInStack.replace((char) from, (char) to);
					break;
				}

				case 2:
				{
					final String from = ldcStringDictionary.randomString();
					final String to = ldcStringDictionary.randomString();

					insns.add(new LdcInsnNode(from));
					insns.add(new LdcInsnNode(to));

					methodName = "replaceFirst";
					methodDesc = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";

					stringInStack = stringInStack.replaceFirst(from, to);
					break;
				}

				case 3:
				{
					final String from = ldcStringDictionary.randomString();
					final String to = ldcStringDictionary.randomString();

					insns.add(new LdcInsnNode(from));
					insns.add(new LdcInsnNode(to));

					methodName = "replaceAll";
					methodDesc = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";

					stringInStack = stringInStack.replaceAll(from, to);
					break;
				}

				case 4:
				{
					final String from = ldcStringDictionary.randomString();
					final String to = ldcStringDictionary.randomString();

					insns.add(new LdcInsnNode(from));
					insns.add(new LdcInsnNode(to));

					methodName = "replace";
					methodDesc = "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;";

					stringInStack = stringInStack.replace(from, to);
					break;
				}

				case 5:
					methodName = "toLowerCase";
					methodDesc = "()Ljava/lang/String;";
					break;

				case 6:
					methodName = "toUpperCase";
					methodDesc = "()Ljava/lang/String;";
					break;

				case 7:
					methodName = "trim";
					methodDesc = "()Ljava/lang/String;";
					break;

				case 8:
					// xd
					methodName = "toString";
					methodDesc = "()Ljava/lang/String;";
					break;

				default:
					throw new AssertionError();
			}
		else
		{
			final int startIndex = RandomUtils.getRandomInt(length);
			insns.add(ASMUtils.getNumberInsn(startIndex));

			switch (RandomUtils.getRandomInt(2))
			{
				case 0:
					methodName = "substring";
					methodDesc = "(I)Ljava/lang/String;";

					stringInStack = stringInStack.substring(startIndex);
					break;

				case 1:
					final int endIndex = RandomUtils.getRandomInt(startIndex, length);

					insns.add(ASMUtils.getNumberInsn(endIndex));
					methodName = "substring";
					methodDesc = "(II)Ljava/lang/String;";

					stringInStack = stringInStack.substring(startIndex, endIndex);
					break;

				default:
					throw new AssertionError();
			}
		}

		insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", methodName, methodDesc, false));

		generateStringCallRecursive(insns, stringInStack, ldcStringDictionary, allowTerminal);
	}

	public static InsnList generateTrapInstructions(final MethodNode mn)
	{
		final InsnList insnList = new InsnList();

		AbstractInsnNode pushNode = null;
		int popNodeOpcode;

		switch (/*RandomUtils.getRandomInt(3)*/1)
		{
			case 0:
			{
				switch (RandomUtils.getRandomInt(2))
				{
					case 0:
					{
						// System.exit(n);
						insnList.add(ASMUtils.getNumberInsn(RandomUtils.getRandomInt()));
						insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
						break;
					}

					case 1:
					{
						// Runtime.getRuntime().halt(n);
						insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;", false));
						insnList.add(ASMUtils.getNumberInsn(RandomUtils.getRandomInt()));
						insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Runtime", RandomUtils.getRandomBoolean() ? "halt" : "exit", "(I)V", false));
						break;
					}

					case 2:
					{
						// while(true) {} - Infinite loop
						final LabelNode loopStart = new LabelNode();
						insnList.add(loopStart);
						insnList.add(new FrameNode(F_NEW, 0, new Object[0], 0, new Object[0]));
						insnList.add(new LabelNode());
						insnList.add(new JumpInsnNode(GOTO, loopStart));
						break;
					}
				}

				popNodeOpcode = IRETURN;
				switch (Type.getReturnType(mn.desc).getSort())
				{
					case Type.VOID:
						// return;
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
						// return null;
						pushNode = new InsnNode(ACONST_NULL);
						popNodeOpcode = ARETURN;
						break;
				}
				break;
			}

			case 1:
			{
				// throw new <randomThrowable>();
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
				// throw null;
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
}
