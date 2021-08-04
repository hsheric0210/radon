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

package me.itzsomebody.radon.transformers.obfuscators.strings;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;

// TODO: Make more customizable(configurable)
public class StringPooler extends StringEncryption
{
	private final StringEncryption master;

	public StringPooler(final StringEncryption master)
	{
		this.master = master;
	}

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();
		final boolean randomOrder = master.isStringPoolerRandomOrder();

		if (master.isStringPoolerGlobal())
		{
			final List<String> totalStrings = getClassWrappers().parallelStream().filter(this::included).flatMap(classWrapper -> classWrapper.getMethods().parallelStream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).map(MethodWrapper::getMethodNode).flatMap(methodNode -> Arrays.stream(methodNode.instructions.toArray()).filter(insn -> insn instanceof LdcInsnNode).map(insn -> ((LdcInsnNode) insn).cst).filter(cst -> cst instanceof String).map(cst -> (String) cst).filter(str -> !master.excludedString(str)))).distinct().collect(Collectors.toList());
			final int totalStringsCount = totalStrings.size();

			final Map<String, Integer> mappings = new HashMap<>(totalStringsCount);
			final List<String> reverseMappings;

			if (randomOrder)
			{
				Collections.shuffle(totalStrings);

				reverseMappings = new ArrayList<>(totalStringsCount);
				for (int i = 0; i < totalStringsCount; i++)
				{
					final String string = totalStrings.get(i);
					mappings.put(string, i);
					reverseMappings.add(string);
				}
			}
			else
			{
				reverseMappings = totalStrings;
				for (int i = 0; i < totalStringsCount; i++)
					mappings.put(totalStrings.get(i), i);
			}

			final boolean inject = master.isStringPoolerInjectGlobalPool();
			final ClassWrapper classWrapper;
			final String classPath;
			if (inject)
			{
				classWrapper = RandomUtils.getRandomElement(getClassWrappers().stream().filter(this::included).collect(Collectors.toList())); // TODO: Constant-pool leeway safeguard
				classPath = classWrapper.getName();
			}
			else
			{
				final ClassNode fakeNode = new ClassNode();
				classPath = randomClassName();
				fakeNode.name = classPath;
				classWrapper = new ClassWrapper(fakeNode, false);
			}

			// Update usages
			final String fieldName = fieldDictionary.uniqueRandomString();
			getClassWrappers().stream().filter(this::included).forEach(cw -> cw.getMethods().stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).map(MethodWrapper::getMethodNode).forEach(methodNode -> Stream.of(methodNode.instructions.toArray()).filter(insn -> insn instanceof LdcInsnNode).map(insn -> (LdcInsnNode) insn).filter(ldc -> ldc.cst instanceof String && !master.excludedString((String) ldc.cst)).forEach(ldc ->
			{
				if (mappings.containsKey((String) ldc.cst))
				{
					methodNode.instructions.insertBefore(ldc, new FieldInsnNode(GETSTATIC, classPath, fieldName, "[Ljava/lang/String;"));
					methodNode.instructions.insertBefore(ldc, ASMUtils.getNumberInsn(mappings.get((String) ldc.cst)));
					methodNode.instructions.set(ldc, new InsnNode(AALOAD));
					counter.incrementAndGet();
				}
				else
					verboseWarn(String.format("! String %s not registered in mappings! This can't be happened!!!", ldc.cst));
			})));

			if (!reverseMappings.isEmpty())
			{
				if (!inject)
					classWrapper.getClassNode().visit(V1_5, ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC, classPath, null, "java/lang/Object", null);
				createInitializer(reverseMappings, classWrapper, methodDictionary, fieldName);
				if (!inject)
					getClasses().put(classWrapper.getName(), classWrapper);

				verboseInfo(() -> String.format("Global string pool injected into class '%s'", classPath));
			}
		}
		else
			getClassWrappers().stream().filter(classWrapper -> included(classWrapper) && (classWrapper.getAccessFlags() & ACC_INTERFACE) == 0
			// *** Interfaces are excluded from pooling for following problem:
			// Exception in thread "main" java.lang.IncompatibleClassChangeError: Method 'void me.itzsomebody.radon.utils.Constants.vUa0ibitmil4UMsz3Tf2pqwav7CrzmHx()' must be InterfaceMethodref constant
			// - at me.itzsomebody.radon.utils.Constants.<clinit>(Unknown Source)
			// - at me.itzsomebody.radon.Main.<clinit>(Unknown Source)
			).forEach(cw ->
			{

				final List<String> totalStrings = cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).flatMap(insns -> Stream.of(insns.toArray()).filter(insn -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String).map(insn -> (String) ((LdcInsnNode) insn).cst).filter(string -> !master.excludedString(string)).distinct()).collect(Collectors.toList());
				final int totalStringsCount = totalStrings.size();

				final Map<String, Integer> mappings = new HashMap<>(totalStringsCount);
				final List<String> reverseMappings;

				if (randomOrder)
				{
					Collections.shuffle(totalStrings);

					reverseMappings = new ArrayList<>(totalStringsCount);
					for (int i = 0; i < totalStringsCount; i++)
					{
						final String string = totalStrings.get(i);
						mappings.put(string, i);
						reverseMappings.add(string);
					}
				}
				else
				{
					reverseMappings = totalStrings;
					for (int i = 0; i < totalStringsCount; i++)
						mappings.put(totalStrings.get(i), i);
				}

				final String fieldName = fieldDictionary.uniqueRandomString();
				cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList -> Stream.of(insnList.toArray()).filter(insn -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String).forEach(insn ->
				{
					final String stringToPool = (String) ((LdcInsnNode) insn).cst;

					if (mappings.containsKey(stringToPool))
					{
						insnList.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, cw.getName(), fieldName, "[Ljava/lang/String;"));
						insnList.insertBefore(insn, ASMUtils.getNumberInsn(mappings.get(stringToPool)));
						insnList.set(insn, new InsnNode(Opcodes.AALOAD));
						counter.incrementAndGet();
					}
				}));

				if (!totalStrings.isEmpty())
					createInitializer(reverseMappings, cw, methodDictionary, fieldName);
			});

		info(String.format("+ Pooled %d strings.", counter.get()));
	}

	private void createInitializer(final List<String> mappings, final ClassWrapper classWrapper, final WrappedDictionary methodDictionary, final String fieldName)
	{
		final List<MethodNode> poolInits = createStringPoolMethod(classWrapper.getName(), methodDictionary, fieldName, mappings);
		for (int i = 0, poolInitsSize = poolInits.size(); i < poolInitsSize; i++)
		{
			final MethodNode mn = poolInits.get(i);
			classWrapper.addMethod(mn);
			final int finalI = i;
			verboseInfo(() -> String.format("String pool initializer method #%d name: %s", finalI, mn.name));
		}

		final Optional<MethodNode> staticBlock = ASMUtils.findMethod(classWrapper.getClassNode(), "<clinit>", "()V");
		if (staticBlock.isPresent())
		{
			final InsnList insns = staticBlock.get().instructions;
			final InsnList init = new InsnList();
			for (final MethodNode mn : poolInits)
				init.add(new MethodInsnNode(Opcodes.INVOKESTATIC, classWrapper.getName(), mn.name, "()V", false));
			insns.insertBefore(insns.getFirst(), init);
		}
		else
		{
			final MethodNode newStaticBlock = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "<clinit>", "()V", null, null);
			final InsnList insnList = new InsnList();
			for (final MethodNode mn : poolInits)
				insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, classWrapper.getName(), mn.name, "()V", false));
			insnList.add(new InsnNode(Opcodes.RETURN));
			newStaticBlock.instructions = insnList;
			classWrapper.addMethod(newStaticBlock);
		}

		final FieldNode stringPoolField = new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, fieldName, "[Ljava/lang/String;", null, null);
		classWrapper.addField(stringPoolField);
	}

	private static List<MethodNode> createStringPoolMethod(final String className, final WrappedDictionary methodDictionary, final String fieldName, final List<String> mappings)
	{
		final List<MethodNode> pools = new ArrayList<>();
		int flags = 0;
		Set<Integer> rngExclusions = null;

		while (true)
		{
			final MethodNode mv = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE, methodDictionary.uniqueRandomString(), "()V", null, null);

			mv.visitCode();

			long leeway = Constants.MAX_CODE_SIZE;
			final int numberOfStrings = mappings.size();

			if ((flags & INITIALIZED) == 0)
			{
				rngExclusions = new HashSet<>(numberOfStrings);

				ASMUtils.getNumberInsn(numberOfStrings).accept(mv);
				mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");

				flags |= INITIALIZED;
			}
			else
				mv.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, "[Ljava/lang/String;");

			while (rngExclusions.size() < numberOfStrings)
			{
				final int i = RandomUtils.getRandomIntWithExclusion(0, numberOfStrings, rngExclusions);

				mv.visitInsn(Opcodes.DUP);
				ASMUtils.getNumberInsn(i).accept(mv);
				mv.visitLdcInsn(mappings.get(i));
				mv.visitInsn(Opcodes.AASTORE);

				rngExclusions.add(i);
				leeway -= ASMUtils.evaluateMaxSize(mv);
				if (leeway < 30000)
				{
					flags |= LOOP_REQUIRED;
					break;
				}
			}
			mv.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, "[Ljava/lang/String;");
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(3, 0);
			mv.visitEnd();

			pools.add(mv);

			if ((flags & LOOP_REQUIRED) == 0)
				return pools;
			flags &= ~LOOP_REQUIRED;
		}
	}

	public static final int INITIALIZED = 0b0000000000001;
	public static final int LOOP_REQUIRED = 0b0000000000010;
}
