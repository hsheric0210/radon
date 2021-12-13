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

package me.itzsomebody.radon.transformers.obfuscators.flow;

import static me.itzsomebody.radon.config.ConfigurationSetting.FLOW_OBFUSCATION;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.dictionaries.Dictionary;
import me.itzsomebody.radon.dictionaries.DictionaryFactory;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts redundant instructions + POP instruction which literally does nothing to confuse decompilers and skidders.
 * Original source code of Javari BadPop Obfuscator: https://github.com/NeroReal/Javari/blob/master/roman/finn/javari/obfmethods/BadPop.java
 * Original source code of JObf BadPop Obfuscator: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/processors/flowObfuscation/FlowObfuscator.java
 * Original source code of LdcSwapInvokeSwapPopRemover: https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/transformers/general/peephole/LdcSwapInvokeSwapPopRemover.java
 * Original source code of SkidSuite BadPop: https://github.com/GenericException/SkidSuite/blob/master/archive/skidsuite-2/obfu/src/main/java/me/lpk/obfuscation/MiscAnti.java
 *
 * @author superblaubeere27, Roman, samczsun, GenericException
 */
public class BadPopInserter extends FlowObfuscation
{
	private WrappedDictionary ldcStringDictionary;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		final WrappedDictionary ldcStringDictionary = Optional.ofNullable(this.ldcStringDictionary).orElseGet(this::getGenericDictionary);

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			int leeway = mw.getLeewaySize();

			final InsnList insns = mw.getInstructions();
			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (leeway < 10000)
					break;

				final int opcode = insn.getOpcode();
				switch (opcode)
				{
					case GOTO:
					{
						final InsnList badPop = createBadPOP(POP, mw.originalLocals, ldcStringDictionary);
						insns.insertBefore(insn, badPop);
						leeway -= ASMUtils.evaluateMaxSize(badPop);

						counter.incrementAndGet();
						break;
					}

					case POP:
					{
						final InsnList badPop = createBadPOP(POP2, mw.originalLocals, ldcStringDictionary);
						insns.insert(insn, badPop);
						insns.remove(insn);
						leeway -= ASMUtils.evaluateMaxSize(badPop);

						counter.incrementAndGet();
						break;
					}

					case ILOAD:
					case FLOAD:
					case ALOAD:
					{
						// SkidSuite BadPop
						// https://github.com/GenericException/SkidSuite/blob/master/archive/skidsuite-2/obfu/src/main/java/me/lpk/obfuscation/MiscAnti.java
						final VarInsnNode varInsn = (VarInsnNode) insn;

						final InsnList before = new InsnList();
						before.add(new VarInsnNode(opcode, varInsn.var));
						before.add(new VarInsnNode(opcode, varInsn.var));
						insns.insertBefore(varInsn, before);

						final InsnNode after = new InsnNode(POP2);
						insns.insert(varInsn, after);

						before.add(after);
						leeway -= ASMUtils.evaluateMaxSize(before);
						counter.incrementAndGet();
						break;
					}
				}
			}
		}));

		info("+ Inserted " + counter.get() + " bad POP instructions");
	}

	private InsnList createBadPOP(final int popOpcode, final List<? extends LocalVariableNode> availableLocals, final WrappedDictionary ldcStringDictionary)
	{
		final InsnList insns = new InsnList();

		if (RandomUtils.getRandomBoolean())
		{
			// Javari BadPop / Skidsuite-2 massiveLdc
			insns.add(createRandomPushInsn(ldcStringDictionary));
			insns.add(createRandomPushInsn(ldcStringDictionary));
			insns.add(createRandomPushInsn(ldcStringDictionary));
			insns.add(new InsnNode(POP));
			insns.add(new InsnNode(SWAP));
			insns.add(new InsnNode(POP));
			insns.add(createRandomPushInsn(ldcStringDictionary));
			insns.add(new InsnNode(POP2));
			if (popOpcode == POP2)
				insns.add(new InsnNode(POP));
			return insns;
		}

		// JObf BadPop
		final String stringInStack = getRandomString(ldcStringDictionary);
		insns.add(new LdcInsnNode(ldcStringDictionary.randomString()));
		insns.add(generateStringInvokeVirtualRecursive(new InsnList(), stringInStack, ldcStringDictionary));

		// TODO: String 말고도 LocalVar을 불러온다던지, Field를 불러온다던지 하는 등의 Trash code의 종류를 다양화해야만이 deobfuscation을 방지할 수 있다.
		// switch (RandomUtils.getRandomInt(3))
		// {
		// 	case 0:
		// 	{
		// 		// String push
		// 		final String stringInStack = getRandomString(ldcStringDictionary);
		// 		insns.add(new LdcInsnNode(ldcStringDictionary));
		// 		insns.add(generateStringInvokeVirtualRecursive(new InsnList(), stringInStack, ldcStringDictionary));
		// 		break;
		// 	}
		//
		// 	case 1:
		// 	{
		// 		// Local variable load
		// 		final LocalVariableNode choice = availableLocals.get(RandomUtils.getRandomInt(availableLocals.size()));
		// 		insns.add(new VarInsnNode(ASMUtils.getVarOpcode(Type.getType(choice.desc), false), choice.index));
		// 		break;
		// 	}
		//
		// 	case 2:
		// 	{
		// 		// Field load
		//
		// 		break;
		// 	}
		// }

		insns.add(new InsnNode(popOpcode));
		return insns;
	}

	public static InsnList generateStringInvokeVirtual(final InsnList insns, final CharSequence stringInStack, final WrappedDictionary ldcStringDictionary)
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
		return insns;
	}

	public static InsnList generateStringInvokeVirtualRecursive(final InsnList insns, String stringInStack, final WrappedDictionary ldcStringDictionary)
	{
		final float random = RandomUtils.getRandomFloat();
		if (random > 0.8f)
			return insns;

		if (random < 0.2f)
			return generateStringInvokeVirtual(insns, stringInStack, ldcStringDictionary);

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

		return generateStringInvokeVirtualRecursive(insns, stringInStack, ldcStringDictionary);
	}

	private AbstractInsnNode createRandomPushInsn(final WrappedDictionary ldcStringDictionary)
	{
		final Type type = ASMUtils.getRandomType();
		if (type.getSize() > 1) // wide types(long, double) are not supported
			return new LdcInsnNode(getRandomString(ldcStringDictionary));
		return ASMUtils.getRandomInsn(type);
	}

	private String getRandomString(final WrappedDictionary dictionary)
	{
		if (RandomUtils.getRandomInt(50) == 0)
			return Main.ATTRIBUTION;

		return dictionary.randomString();
	}

	@Override
	public String getName()
	{
		return "Bad POP";
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setConfiguration(final Configuration config)
	{
		final String ldcStringDictionaryPrefix = FLOW_OBFUSCATION + "." + FlowObfuscationSetting.INSERT_BAD_POPS + ".ldcStringDictionary.";
		if (Stream.of("dictionary", "minLength", "maxLength").allMatch(s -> config.contains(ldcStringDictionaryPrefix + s)))
		{
			Dictionary dictionary = null;
			final Object obj = config.get(ldcStringDictionaryPrefix + "dictionary");
			if (obj instanceof List)
				dictionary = DictionaryFactory.getCustom((List<String>) obj);
			else if (obj instanceof String)
				dictionary = DictionaryFactory.get((String) obj);

			if (dictionary != null)
				ldcStringDictionary = new WrappedDictionary(dictionary, config.get(ldcStringDictionaryPrefix + "minLength"), config.get(ldcStringDictionaryPrefix + "maxLength"));
		}
	}
}
