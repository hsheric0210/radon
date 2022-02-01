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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.LocalVariableProvider;
import me.itzsomebody.radon.asm.LocalVariableProvider.Local;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.dictionaries.Dictionary;
import me.itzsomebody.radon.dictionaries.DictionaryFactory;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.CodeGenerator;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts redundant instructions + {@code POP} instruction which literally does nothing to confuse decompilers and skidders.
 *
 * <ul>
 * <li>Original source code of Javari BadPop Obfuscator: https://github.com/NeroReal/Javari/blob/master/roman/finn/javari/obfmethods/BadPop.java</li>
 * <li>Original source code of JObf BadPop Obfuscator: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/processors/flowObfuscation/FlowObfuscator.java</li>
 * <li>Original source code of LdcSwapInvokeSwapPopRemover: https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/transformers/general/peephole/LdcSwapInvokeSwapPopRemover.java</li>
 * <li>Original source code of SkidSuite BadPop: https://github.com/GenericException/SkidSuite/blob/master/archive/skidsuite-2/obfu/src/main/java/me/lpk/obfuscation/MiscAnti.java</li>
 * <li>Original source code of Allatori JunkRemover: https://github.com/GraxCode/threadtear/blob/master/core/src/main/java/me/nov/threadtear/execution/allatori/JunkRemoverAllatori.java</li>
 * </ul>
 *
 * @author superblaubeere27, Roman, samczsun, GenericException
 */
public class BadPopInserter extends FlowObfuscation
{
	private WrappedDictionary ldcStringDictionary;

	@Override
	public void transform()
	{
		final AtomicInteger insertedBeforeGOTOs = new AtomicInteger();
		final AtomicInteger mutilatedPOPs = new AtomicInteger();
		final AtomicInteger insertedBeforeLOADs = new AtomicInteger();

		final WrappedDictionary ldcStringDictionary = Optional.ofNullable(this.ldcStringDictionary).orElseGet(this::getGenericDictionary);

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			int leeway = mw.getLeewaySize();

			final LocalVariableProvider varProvider = mw.getVarProvider();
			final List<? extends Local> validLocals = Optional.ofNullable(varProvider.localVariables).map(locals -> locals.stream().filter(local -> local.typeSort != Type.LONG && local.typeSort != Type.DOUBLE).collect(Collectors.toList())).orElseGet(ArrayList::new);

			final InsnList insns = mw.getInstructions();
			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (leeway < 10000)
					break;

				final Supplier<List<? extends Local>> availableLocals = () -> validLocals.stream().filter(local -> local.isAvailableOn(insn)).collect(Collectors.toList());

				if (ASMUtils.isIntInsn(insn) || insn.getOpcode() == NEWARRAY)
				{
					// Allatori BadPop
					final InsnList badPop = new InsnList();
					badPop.add(new InsnNode(ICONST_1));
					badPop.add(new InsnNode(DUP));
					badPop.add(new InsnNode(POP2));
					insns.insert(insn, badPop);
					leeway -= ASMUtils.evaluateMaxSize(badPop);
				}
				else if (ASMUtils.isFloatInsn(insn))
				{
					// Allatori BadPop
					final InsnList badPop = new InsnList();
					badPop.add(new InsnNode(FCONST_1));
					badPop.add(new InsnNode(DUP));
					badPop.add(new InsnNode(POP2));
					insns.insert(insn, badPop);
					leeway -= ASMUtils.evaluateMaxSize(badPop);
				}
				else
				{
					final int opcode = insn.getOpcode();
					switch (opcode)
					{
						case GOTO:
						{
							final InsnList badPop = createBadPOP(POP, availableLocals, ldcStringDictionary, varProvider);
							insns.insertBefore(insn, badPop);
							leeway -= ASMUtils.evaluateMaxSize(badPop);

							insertedBeforeGOTOs.incrementAndGet();
							break;
						}

						case POP:
						{
							final InsnList badPop = createBadPOP(POP2, availableLocals, ldcStringDictionary, varProvider);
							insns.insert(insn, badPop);
							insns.remove(insn);
							leeway -= ASMUtils.evaluateMaxSize(badPop);

							mutilatedPOPs.incrementAndGet();
							break;
						}

						case ILOAD:
						case FLOAD:
						case ALOAD:
						{
							// SkidSuite BadPop
							final VarInsnNode varInsn = (VarInsnNode) insn;

							final InsnList before = new InsnList();
							before.add(new VarInsnNode(opcode, varInsn.var));
							before.add(new VarInsnNode(opcode, varInsn.var));
							insns.insertBefore(varInsn, before);

							final InsnNode after = new InsnNode(POP2);
							insns.insert(varInsn, after);

							before.add(after);
							leeway -= ASMUtils.evaluateMaxSize(before);
							insertedBeforeLOADs.incrementAndGet();
							break;
						}
					}
				}
			}
		}));

		info("+ Inserted " + insertedBeforeGOTOs.get() + " bad POP instructions before GOTOs.");
		info("+ Mutilated POP instructions with " + mutilatedPOPs.get() + " bad POP instructions.");
		info("+ Inserted " + insertedBeforeLOADs.get() + " bad POP instructions before local variable load instructions.");
	}

	private static InsnList createBadPOP(final int popOpcode, final Supplier<? extends List<? extends Local>> availableLocals, final WrappedDictionary ldcStringDictionary, final LocalVariableProvider varProvider /* TEMPORARY */)
	{
		final InsnList insns = new InsnList();

		if (RandomUtils.getRandomBoolean())
		{
			// Javari BadPop / Skidsuite-2 massiveLdc
			insns.add(generateRandomPushInsn(ldcStringDictionary));
			insns.add(generateRandomPushInsn(ldcStringDictionary));
			insns.add(generateRandomPushInsn(ldcStringDictionary));
			insns.add(new InsnNode(POP));
			insns.add(new InsnNode(SWAP));
			insns.add(new InsnNode(POP));
			insns.add(generateRandomPushInsn(ldcStringDictionary));
			insns.add(new InsnNode(POP2));
			if (popOpcode == POP2)
				insns.add(new InsnNode(POP));
			return insns;
		}

		switch (RandomUtils.getRandomInt(2))
		{
			case 1:
				// Local variable load
				final List<? extends Local> available = availableLocals.get();
				if (!available.isEmpty())
				{
					final Local choice = available.get(RandomUtils.getRandomInt(available.size()));
					varProvider.allocateVar(choice.size);
					insns.add(new VarInsnNode(ASMUtils.getVarOpcode(choice.type, false), choice.varIndex));

					final InsnList dummyInsns = new InsnList();
					CodeGenerator.generateDummyCallRecursive(dummyInsns, choice.type);
					insns.add(dummyInsns);
					break;
				}

			case 0:
			{
				// String push
				final String stringInStack = generateRandomString(ldcStringDictionary);
				insns.add(new LdcInsnNode(stringInStack));

				final InsnList dummyInsns = new InsnList();
				CodeGenerator.generateStringCallRecursive(dummyInsns, stringInStack, ldcStringDictionary, true);
				insns.add(dummyInsns);
				break;
			}

			case 2:
			{
				// TODO: Field load
				break;
			}
		}

		insns.add(new InsnNode(popOpcode));
		return insns;
	}

	private static AbstractInsnNode generateRandomPushInsn(final WrappedDictionary ldcStringDictionary)
	{
		final Type type = ASMUtils.getRandomType();
		if (type.getSize() > 1) // wide types(long, double) are not supported
			return new LdcInsnNode(generateRandomString(ldcStringDictionary));
		return ASMUtils.getRandomInsn(type);
	}

	private static String generateRandomString(final WrappedDictionary dictionary)
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
