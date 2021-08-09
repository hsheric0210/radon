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

package me.itzsomebody.radon.transformers.obfuscators.numbers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;

public class NumberPooler extends NumberObfuscation
{
	@Override
	public final void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		final boolean poolIntegers = master.canPoolInteger();
		final boolean poolLongs = master.canPoolLong();
		final boolean poolFloats = master.canPoolFloat();
		final boolean poolDoubles = master.canPoolDouble();

		final boolean randomOrder = master.isNumberPoolerRandomOrder();

		if (master.canNumberPoolerGlobal())
		{
			final Set<Integer> integersToPoolSet = new HashSet<>();
			final Set<Long> longsToPoolSet = new HashSet<>();
			final Set<Float> floatsToPoolSet = new HashSet<>();
			final Set<Double> doublesToPoolSet = new HashSet<>();

			getClassWrappers().stream().filter(this::included).forEach(cw -> cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList ->
			{
				if (poolIntegers)
					Stream.of(insnList.toArray()).filter(ASMUtils::isIntInsn).mapToInt(ASMUtils::getIntegerFromInsn).filter(integer -> !integersToPoolSet.contains(integer)).forEach(integersToPoolSet::add);

				if (poolLongs)
					Stream.of(insnList.toArray()).filter(ASMUtils::isLongInsn).mapToLong(ASMUtils::getLongFromInsn).filter(_long -> !longsToPoolSet.contains(_long)).forEach(longsToPoolSet::add);

				if (poolFloats)
					Stream.of(insnList.toArray()).filter(ASMUtils::isFloatInsn).map(ASMUtils::getFloatFromInsn).filter(_float -> !floatsToPoolSet.contains(_float)).forEach(floatsToPoolSet::add);

				if (poolDoubles)
					Stream.of(insnList.toArray()).filter(ASMUtils::isDoubleInsn).mapToDouble(ASMUtils::getDoubleFromInsn).filter(_double -> !doublesToPoolSet.contains(_double)).forEach(doublesToPoolSet::add);
			}));

			final List<Integer> integersToPool = new ArrayList<>(integersToPoolSet);
			final List<Long> longsToPool = new ArrayList<>(longsToPoolSet);
			final List<Float> floatsToPool = new ArrayList<>(floatsToPoolSet);
			final List<Double> doublesToPool = new ArrayList<>(doublesToPoolSet);

			final int integerCountToPool = integersToPool.size();
			final Map<Integer, Integer> integerMappings = new HashMap<>(integerCountToPool);
			List<Integer> integerReverseMappings = null;
			if (integerCountToPool > 0)
				if (randomOrder)
				{
					Collections.shuffle(integersToPool);

					integerReverseMappings = new ArrayList<>(integerCountToPool);
					for (int i = 0; i < integerCountToPool; i++)
					{
						final int value = integersToPool.get(i);
						integerMappings.put(value, i);
						integerReverseMappings.add(value);
					}
				}
				else
				{
					integerReverseMappings = integersToPool;
					for (int i = 0; i < integerCountToPool; i++)
						integerMappings.put(integersToPool.get(i), i);
				}

			final int longCountToPool = longsToPool.size();
			final Map<Long, Integer> longMappings = new HashMap<>(longCountToPool);
			List<Long> longReverseMappings = null;
			if (longCountToPool > 0)
				if (randomOrder)
				{
					Collections.shuffle(longsToPool);

					longReverseMappings = new ArrayList<>(longCountToPool);
					for (int i = 0; i < longCountToPool; i++)
					{
						final long value = longsToPool.get(i);
						longMappings.put(value, i);
						longReverseMappings.add(value);
					}
				}
				else
				{
					longReverseMappings = longsToPool;
					for (int i = 0; i < longCountToPool; i++)
						longMappings.put(longsToPool.get(i), i);
				}

			final int floatCountToPool = floatsToPool.size();
			final Map<Float, Integer> floatMappings = new HashMap<>(floatCountToPool);
			List<Float> floatReverseMappings = null;
			if (floatCountToPool > 0)
				if (randomOrder)
				{
					Collections.shuffle(floatsToPool);

					floatReverseMappings = new ArrayList<>(floatCountToPool);
					for (int i = 0; i < floatCountToPool; i++)
					{
						final float value = floatsToPool.get(i);
						floatMappings.put(value, i);
						floatReverseMappings.add(value);
					}
				}
				else
				{
					floatReverseMappings = floatsToPool;
					for (int i = 0; i < floatCountToPool; i++)
						floatMappings.put(floatsToPool.get(i), i);
				}

			final int doubleCountToPool = doublesToPool.size();
			final Map<Double, Integer> doubleMappings = new HashMap<>(doubleCountToPool);
			List<Double> doubleReverseMappings = null;
			if (doubleCountToPool > 0)
				if (randomOrder)
				{
					Collections.shuffle(doublesToPool);

					doubleReverseMappings = new ArrayList<>(doubleCountToPool);
					for (int i = 0; i < doubleCountToPool; i++)
					{
						final double value = doublesToPool.get(i);
						doubleMappings.put(value, i);
						doubleReverseMappings.add(value);
					}
				}
				else
				{
					doubleReverseMappings = doublesToPool;
					for (int i = 0; i < doubleCountToPool; i++)
						doubleMappings.put(doublesToPool.get(i), i);
				}

			final boolean inject = master.canNumberPoolerInjectGlobalPool();
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

			final WrappedDictionary fieldDictionary = getFieldDictionary(classPath);
			final String integerPoolFieldName = fieldDictionary.nextUniqueString();
			final String longPoolFieldName = fieldDictionary.nextUniqueString();
			final String floatPoolFieldName = fieldDictionary.nextUniqueString();
			final String doublePoolFieldName = fieldDictionary.nextUniqueString();

			getClassWrappers().stream().filter(this::included).forEach(cw ->
			{
				cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList ->
				{
					if (poolIntegers)
						Stream.of(insnList.toArray()).filter(ASMUtils::isIntInsn).forEach(insn ->
						{
							final int value = ASMUtils.getIntegerFromInsn(insn);

							if (integerMappings.containsKey(value))
							{
								final int index = integerMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, classWrapper.getName(), integerPoolFieldName, "[I"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(IALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Integer %d not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolLongs)
						Stream.of(insnList.toArray()).filter(ASMUtils::isLongInsn).forEach(insn ->
						{
							final long value = ASMUtils.getLongFromInsn(insn);

							if (longMappings.containsKey(value))
							{
								final int index = longMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, classWrapper.getName(), longPoolFieldName, "[J"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(LALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Long %d not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolFloats)
						Stream.of(insnList.toArray()).filter(ASMUtils::isFloatInsn).forEach(insn ->
						{
							final float value = ASMUtils.getFloatFromInsn(insn);

							if (floatMappings.containsKey(value))
							{
								final int index = floatMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, classWrapper.getName(), floatPoolFieldName, "[F"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(FALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Float %.6f not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolDoubles)
						Stream.of(insnList.toArray()).filter(ASMUtils::isDoubleInsn).forEach(insn ->
						{
							final double value = ASMUtils.getDoubleFromInsn(insn);

							if (doubleMappings.containsKey(value))
							{
								final int index = doubleMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, classWrapper.getName(), doublePoolFieldName, "[D"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(DALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Float %.16f not registered in integerMappings! This can't be happened!!!", value));
						});
				});
			});

			if (!integersToPool.isEmpty() || !longsToPool.isEmpty() || !floatsToPool.isEmpty() || !doublesToPool.isEmpty())
			{
				if (!inject)
					classWrapper.getClassNode().visit(V1_5, ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC, classPath, null, "java/lang/Object", null);
				createInitializer(integerReverseMappings, longReverseMappings, floatReverseMappings, doubleReverseMappings, classWrapper, getMethodDictionary(classPath), integerPoolFieldName, longPoolFieldName, floatPoolFieldName, doublePoolFieldName);
				if (!inject)
					getClasses().put(classWrapper.getName(), classWrapper);

				verboseInfo(() -> String.format("Global number pool injected into class '%s'", classPath));
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
				// CHECK: Should use Collections.synchronizedList()?
				final List<Integer> integersToPool = new ArrayList<>();
				final List<Long> longsToPool = new ArrayList<>();
				final List<Float> floatsToPool = new ArrayList<>();
				final List<Double> doublesToPool = new ArrayList<>();

				cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList ->
				{
					if (poolIntegers)
						Stream.of(insnList.toArray()).filter(ASMUtils::isIntInsn).mapToInt(ASMUtils::getIntegerFromInsn).filter(integer -> !integersToPool.contains(integer)).forEach(integersToPool::add);

					if (poolLongs)
						Stream.of(insnList.toArray()).filter(ASMUtils::isLongInsn).mapToLong(ASMUtils::getLongFromInsn).filter(_long -> !longsToPool.contains(_long)).forEach(longsToPool::add);

					if (poolFloats)
						Stream.of(insnList.toArray()).filter(ASMUtils::isFloatInsn).map(ASMUtils::getFloatFromInsn).filter(_float -> !floatsToPool.contains(_float)).forEach(floatsToPool::add);

					if (poolDoubles)
						Stream.of(insnList.toArray()).filter(ASMUtils::isDoubleInsn).mapToDouble(ASMUtils::getDoubleFromInsn).filter(_double -> !doublesToPool.contains(_double)).forEach(doublesToPool::add);
				});

				final int integerCountToPool = integersToPool.size();
				final Map<Integer, Integer> integerMappings = new HashMap<>(integerCountToPool);
				List<Integer> integerReverseMappings = null;
				if (integerCountToPool > 0)
					if (randomOrder)
					{
						Collections.shuffle(integersToPool);

						integerReverseMappings = new ArrayList<>(integerCountToPool);
						for (int i = 0; i < integerCountToPool; i++)
						{
							final int value = integersToPool.get(i);
							integerMappings.put(value, i);
							integerReverseMappings.add(value);
						}
					}
					else
					{
						integerReverseMappings = integersToPool;
						for (int i = 0; i < integerCountToPool; i++)
							integerMappings.put(integersToPool.get(i), i);
					}

				final int longCountToPool = longsToPool.size();
				final Map<Long, Integer> longMappings = new HashMap<>(longCountToPool);
				List<Long> longReverseMappings = null;
				if (longCountToPool > 0)
					if (randomOrder)
					{
						Collections.shuffle(longsToPool);

						longReverseMappings = new ArrayList<>(longCountToPool);
						for (int i = 0; i < longCountToPool; i++)
						{
							final long value = longsToPool.get(i);
							longMappings.put(value, i);
							longReverseMappings.add(value);
						}
					}
					else
					{
						longReverseMappings = longsToPool;
						for (int i = 0; i < longCountToPool; i++)
							longMappings.put(longsToPool.get(i), i);
					}

				final int floatCountToPool = floatsToPool.size();
				final Map<Float, Integer> floatMappings = new HashMap<>(floatCountToPool);
				List<Float> floatReverseMappings = null;
				if (floatCountToPool > 0)
					if (randomOrder)
					{
						Collections.shuffle(floatsToPool);

						floatReverseMappings = new ArrayList<>(floatCountToPool);
						for (int i = 0; i < floatCountToPool; i++)
						{
							final float value = floatsToPool.get(i);
							floatMappings.put(value, i);
							floatReverseMappings.add(value);
						}
					}
					else
					{
						floatReverseMappings = floatsToPool;
						for (int i = 0; i < floatCountToPool; i++)
							floatMappings.put(floatsToPool.get(i), i);
					}

				final int doubleCountToPool = doublesToPool.size();
				final Map<Double, Integer> doubleMappings = new HashMap<>(doubleCountToPool);
				List<Double> doubleReverseMappings = null;
				if (doubleCountToPool > 0)
					if (randomOrder)
					{
						Collections.shuffle(doublesToPool);

						doubleReverseMappings = new ArrayList<>(doubleCountToPool);
						for (int i = 0; i < doubleCountToPool; i++)
						{
							final double value = doublesToPool.get(i);
							doubleMappings.put(value, i);
							doubleReverseMappings.add(value);
						}
					}
					else
					{
						doubleReverseMappings = doublesToPool;
						for (int i = 0; i < doubleCountToPool; i++)
							doubleMappings.put(doublesToPool.get(i), i);
					}

				final WrappedDictionary fieldDictionary = getFieldDictionary(cw.getOriginalName());
				final String integerPoolFieldName = fieldDictionary.nextUniqueString();
				final String longPoolFieldName = fieldDictionary.nextUniqueString();
				final String floatPoolFieldName = fieldDictionary.nextUniqueString();
				final String doublePoolFieldName = fieldDictionary.nextUniqueString();

				cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList ->
				{
					if (poolIntegers)
						Stream.of(insnList.toArray()).filter(ASMUtils::isIntInsn).forEach(insn ->
						{
							final int value = ASMUtils.getIntegerFromInsn(insn);

							if (integerMappings.containsKey(value))
							{
								final int index = integerMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, cw.getName(), integerPoolFieldName, "[I"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(IALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Integer %d not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolLongs)
						Stream.of(insnList.toArray()).filter(ASMUtils::isLongInsn).forEach(insn ->
						{
							final long value = ASMUtils.getLongFromInsn(insn);

							if (longMappings.containsKey(value))
							{
								final int index = longMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, cw.getName(), longPoolFieldName, "[J"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(LALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Long %d not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolFloats)
						Stream.of(insnList.toArray()).filter(ASMUtils::isFloatInsn).forEach(insn ->
						{
							final float value = ASMUtils.getFloatFromInsn(insn);

							if (floatMappings.containsKey(value))
							{
								final int index = floatMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, cw.getName(), floatPoolFieldName, "[F"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(FALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Integer %.6f not registered in integerMappings! This can't be happened!!!", value));
						});

					if (poolDoubles)
						Stream.of(insnList.toArray()).filter(ASMUtils::isDoubleInsn).forEach(insn ->
						{
							final double value = ASMUtils.getDoubleFromInsn(insn);

							if (doubleMappings.containsKey(value))
							{
								final int index = doubleMappings.get(value);

								insnList.insertBefore(insn, new FieldInsnNode(GETSTATIC, cw.getName(), doublePoolFieldName, "[D"));
								insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
								insnList.set(insn, new InsnNode(DALOAD));
								counter.incrementAndGet();
							}
							else
								verboseWarn(() -> String.format("! Integer %.16f not registered in integerMappings! This can't be happened!!!", value));
						});
				});

				if (!integersToPool.isEmpty() || !longsToPool.isEmpty() || !floatsToPool.isEmpty() || !doublesToPool.isEmpty())
					createInitializer(integerReverseMappings, longReverseMappings, floatReverseMappings, doubleReverseMappings, cw, getMethodDictionary(cw.getOriginalName()), integerPoolFieldName, longPoolFieldName, floatPoolFieldName, doublePoolFieldName);
			});

		info(String.format("+ Pooled %d numbers.", counter.get()));
	}

	private void createInitializer(final List<Integer> integerMappings, final List<Long> longMappings, final List<Float> floatMappings, final List<Double> doubleMappings, final ClassWrapper classWrapper, final WrappedDictionary methodDictionary, final String integerPoolFieldName, final String longPoolFieldName, final String floatPoolFieldName, final String doublePoolFieldName)
	{
		final List<MethodNode> poolInits = createNumberPoolMethod(integerMappings, longMappings, floatMappings, doubleMappings, classWrapper, methodDictionary, integerPoolFieldName, longPoolFieldName, floatPoolFieldName, doublePoolFieldName);

		for (final MethodNode mn : poolInits)
			classWrapper.addMethod(mn);

		final Optional<MethodNode> staticBlock = ASMUtils.findMethod(classWrapper.getClassNode(), "<clinit>", "()V");
		if (staticBlock.isPresent())
		{
			final InsnList insns = staticBlock.get().instructions;
			final InsnList init = new InsnList();
			for (final MethodNode mn : poolInits)
				init.add(new MethodInsnNode(INVOKESTATIC, classWrapper.getName(), mn.name, "()V", false));
			insns.insertBefore(insns.getFirst(), init);
		}
		else
		{
			final MethodNode newStaticBlock = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "<clinit>", "()V", null, null);
			final InsnList insnList = new InsnList();
			for (final MethodNode mn : poolInits)
				insnList.add(new MethodInsnNode(INVOKESTATIC, classWrapper.getName(), mn.name, "()V", false));
			insnList.add(new InsnNode(RETURN));
			newStaticBlock.instructions = insnList;
			classWrapper.getClassNode().methods.add(newStaticBlock);
		}
	}

	private static List<MethodNode> createNumberPoolMethod(final List<Integer> integerMappings, final List<Long> longMappings, final List<Float> floatMappings, final List<Double> doubleMappings, final ClassWrapper classWrapper, final WrappedDictionary methodDictionary, final String integerPoolFieldName, final String longPoolFieldName, final String floatPoolFieldName, final String doublePoolFieldName)
	{
		final List<MethodNode> pools = new ArrayList<>();
		int flags = 0;
		Collection<Integer> intIndexRNGExclusions = null;
		Collection<Integer> longIndexRNGExclusions = null;
		Collection<Integer> floatIndexRNGExclusions = null;
		Collection<Integer> doubleIndexRNGExclusions = null;

		while (true)
		{
			final MethodNode mv = new MethodNode(ACC_PRIVATE | ACC_STATIC /* | ACC_SYNTHETIC | ACC_BRIDGE */, methodDictionary.nextUniqueString(), "()V", null, null);
			mv.visitCode();

			long leeway = Constants.MAX_CODE_SIZE;
			if (integerMappings != null && !integerMappings.isEmpty())
			{
				final int numberOfIntegers = integerMappings.size();

				if ((flags & INPOOL_INITIALIZED) == 0)
				{
					intIndexRNGExclusions = new HashSet<>(numberOfIntegers);

					ASMUtils.getNumberInsn(numberOfIntegers).accept(mv);
					mv.visitIntInsn(NEWARRAY, T_INT);

					final FieldNode fieldNode = new FieldNode(POOL_FIELD_ACCESS, integerPoolFieldName, "[I", null, null);
					classWrapper.addField(fieldNode);
					flags |= INPOOL_INITIALIZED;
				}
				else
					mv.visitFieldInsn(GETSTATIC, classWrapper.getName(), integerPoolFieldName, "[I");

				while (intIndexRNGExclusions.size() < numberOfIntegers)
				{
					final int index = RandomUtils.getRandomIntWithExclusion(0, numberOfIntegers, intIndexRNGExclusions);

					mv.visitInsn(DUP);
					ASMUtils.getNumberInsn(index).accept(mv);
					ASMUtils.getNumberInsn(integerMappings.get(index)).accept(mv);
					mv.visitInsn(IASTORE);

					intIndexRNGExclusions.add(index);
					leeway -= ASMUtils.evaluateMaxSize(mv);
					if (leeway < 30000)
					{
						flags |= LOOP_REQUIERD;
						break;
					}
				}
				mv.visitFieldInsn(PUTSTATIC, classWrapper.getName(), integerPoolFieldName, "[I");
			}

			if (longMappings != null && !longMappings.isEmpty() && leeway >= 30000)
			{
				final int numberOfLongs = longMappings.size();

				if ((flags & LONGPOOL_INITIALIZED) == 0)
				{
					longIndexRNGExclusions = new HashSet<>(numberOfLongs);

					ASMUtils.getNumberInsn(numberOfLongs).accept(mv);
					mv.visitIntInsn(NEWARRAY, T_LONG);

					final FieldNode fieldNode = new FieldNode(POOL_FIELD_ACCESS, longPoolFieldName, "[J", null, null);
					classWrapper.addField(fieldNode);
					flags |= LONGPOOL_INITIALIZED;
				}
				else
					mv.visitFieldInsn(GETSTATIC, classWrapper.getName(), longPoolFieldName, "[J");

				while (longIndexRNGExclusions.size() < numberOfLongs)
				{
					final int index = RandomUtils.getRandomIntWithExclusion(0, numberOfLongs, longIndexRNGExclusions);

					mv.visitInsn(DUP);
					ASMUtils.getNumberInsn(index).accept(mv);
					ASMUtils.getNumberInsn(longMappings.get(index)).accept(mv);
					mv.visitInsn(LASTORE);

					longIndexRNGExclusions.add(index);
					leeway -= ASMUtils.evaluateMaxSize(mv);
					if (leeway < 30000)
					{
						flags |= LOOP_REQUIERD;
						break;
					}
				}
				mv.visitFieldInsn(PUTSTATIC, classWrapper.getName(), longPoolFieldName, "[J");
			}

			if (floatMappings != null && !floatMappings.isEmpty() && leeway >= 30000)
			{
				final int numberOfFloats = floatMappings.size();

				if ((flags & FLOATPOOL_INITIALIZED) == 0)
				{
					floatIndexRNGExclusions = new HashSet<>(numberOfFloats);

					ASMUtils.getNumberInsn(numberOfFloats).accept(mv);
					mv.visitIntInsn(NEWARRAY, T_FLOAT);

					final FieldNode fieldNode = new FieldNode(POOL_FIELD_ACCESS, floatPoolFieldName, "[F", null, null);
					classWrapper.addField(fieldNode);
					flags |= FLOATPOOL_INITIALIZED;
				}
				else
					mv.visitFieldInsn(GETSTATIC, classWrapper.getName(), floatPoolFieldName, "[F");

				while (floatIndexRNGExclusions.size() < numberOfFloats)
				{
					final int index = RandomUtils.getRandomIntWithExclusion(0, numberOfFloats, floatIndexRNGExclusions);

					mv.visitInsn(DUP);
					ASMUtils.getNumberInsn(index).accept(mv);
					ASMUtils.getNumberInsn(floatMappings.get(index)).accept(mv);
					mv.visitInsn(FASTORE);

					floatIndexRNGExclusions.add(index);
					leeway -= ASMUtils.evaluateMaxSize(mv);
					if (leeway < 30000)
					{
						flags |= LOOP_REQUIERD;
						break;
					}
				}
				mv.visitFieldInsn(PUTSTATIC, classWrapper.getName(), floatPoolFieldName, "[F");
			}

			if (doubleMappings != null && !doubleMappings.isEmpty() && leeway >= 30000)
			{
				final int numberOfDoubles = doubleMappings.size();

				if ((flags & DOUBLEPOOL_INITIALIZED) == 0)
				{
					doubleIndexRNGExclusions = new HashSet<>(numberOfDoubles);

					ASMUtils.getNumberInsn(numberOfDoubles).accept(mv);
					mv.visitIntInsn(NEWARRAY, T_DOUBLE);

					final FieldNode fieldNode = new FieldNode(POOL_FIELD_ACCESS, doublePoolFieldName, "[D", null, null);
					classWrapper.addField(fieldNode);
					flags |= DOUBLEPOOL_INITIALIZED;
				}
				else
					mv.visitFieldInsn(GETSTATIC, classWrapper.getName(), floatPoolFieldName, "[D");

				while (doubleIndexRNGExclusions.size() < numberOfDoubles)
				{
					final int index = RandomUtils.getRandomIntWithExclusion(0, numberOfDoubles, doubleIndexRNGExclusions);

					mv.visitInsn(DUP);
					ASMUtils.getNumberInsn(index).accept(mv);
					ASMUtils.getNumberInsn(doubleMappings.get(index)).accept(mv);
					mv.visitInsn(DASTORE);

					doubleIndexRNGExclusions.add(index);
					leeway -= ASMUtils.evaluateMaxSize(mv);
					if (leeway < 30000)
					{
						flags |= LOOP_REQUIERD;
						break;
					}
				}
				mv.visitFieldInsn(PUTSTATIC, classWrapper.getName(), doublePoolFieldName, "[D");
			}

			mv.visitInsn(RETURN);
			mv.visitMaxs(3, 0);
			mv.visitEnd();

			pools.add(mv);

			if ((flags & LOOP_REQUIERD) == 0)
				return pools;
			flags &= ~LOOP_REQUIERD;
		}
	}

	public static final int INPOOL_INITIALIZED = 0b0000000000001;
	public static final int LONGPOOL_INITIALIZED = 0b0000000000010;
	public static final int FLOATPOOL_INITIALIZED = 0b0000000000100;
	public static final int DOUBLEPOOL_INITIALIZED = 0b0000000001000;
	public static final int LOOP_REQUIERD = 0b0000000010000;
	public static final int POOL_FIELD_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC;
}
