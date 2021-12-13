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

	private InsnList createBadPOP(final int popOpcode, final List<LocalVariableNode> originalLocals, final WrappedDictionary ldcStringDictionary)
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
		// TODO: String 말고도 LocalVar을 불러온다던지, Field를 불러온다던지 하는 등의 Trash code의 종류를 다양화해야만이 deobfuscation을 방지할 수 있다.
		insns.add(createRandomStringPushInsn(ldcStringDictionary));
		// TODO: String.length() 말고도 다른 것들도 좀 호출해봐 좀...
		insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
		insns.add(new InsnNode(popOpcode));
		return insns;
	}

	private AbstractInsnNode createRandomPushInsn(final WrappedDictionary ldcStringDictionary)
	{
		final Type type = ASMUtils.getRandomType();
		if (type.getSize() > 1) // wide types(long, double) are not supported
			return createRandomStringPushInsn(ldcStringDictionary);
		return ASMUtils.getRandomInsn(type);
	}

	private AbstractInsnNode createRandomStringPushInsn(final WrappedDictionary dictionary)
	{
		if (RandomUtils.getRandomInt(50) == 0)
			return new LdcInsnNode(Main.ATTRIBUTION);

		return new LdcInsnNode(dictionary.randomString());
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
