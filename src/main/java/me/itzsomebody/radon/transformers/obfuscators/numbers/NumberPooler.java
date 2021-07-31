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

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final String initPoolMethodName = methodDictionary.uniqueRandomString();

			final String integerPoolFieldName = fieldDictionary.uniqueRandomString();
			final String longPoolFieldName = fieldDictionary.uniqueRandomString();
			final String floatPoolFieldName = fieldDictionary.uniqueRandomString();
			final String doublePoolFieldName = fieldDictionary.uniqueRandomString();

			final Queue<Integer> integersToPool = new ConcurrentLinkedQueue<>();
			final Queue<Long> longsToPool = new ConcurrentLinkedQueue<>();
			final Queue<Float> floatsToPool = new ConcurrentLinkedQueue<>();
			final Queue<Double> doublesToPool = new ConcurrentLinkedQueue<>();

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
			final Map<Integer, Integer> integerIndexMappings = new HashMap<>(integerCountToPool);
			if (integerCountToPool > 0)
			{
				final Collection<Integer> rngExclusions = new ArrayList<>(integerCountToPool);
				final AtomicInteger sequentialIndex = new AtomicInteger();
				integersToPool.forEach(value ->
				{
					final int index;
					if (randomOrder)
					{
						index = RandomUtils.getRandomIntWithExclusion(0, integerCountToPool, rngExclusions);
						rngExclusions.add(index);
					}
					else
						index = sequentialIndex.getAndIncrement();
					integerIndexMappings.put(value, index);
				});
			}

			final int longCountToPool = longsToPool.size();
			final Map<Long, Integer> longIndexMappings = new HashMap<>(longCountToPool);
			if (longCountToPool > 0)
			{
				final Collection<Integer> rngExclusions = new ArrayList<>(longCountToPool);
				final AtomicInteger sequentialIndex = new AtomicInteger();
				longsToPool.forEach(value ->
				{
					final int index;
					if (randomOrder)
					{
						index = RandomUtils.getRandomIntWithExclusion(0, longCountToPool, rngExclusions);
						rngExclusions.add(index);
					}
					else
						index = sequentialIndex.getAndIncrement();
					longIndexMappings.put(value, index);
				});
			}

			final int floatCountToPool = floatsToPool.size();
			final Map<Float, Integer> floatIndexMappings = new HashMap<>(floatCountToPool);
			if (floatCountToPool > 0)
			{
				final Collection<Integer> rngExclusions = new ArrayList<>(floatCountToPool);
				final AtomicInteger sequentialIndex = new AtomicInteger();
				floatsToPool.forEach(value ->
				{
					final int index;
					if (randomOrder)
					{
						index = RandomUtils.getRandomIntWithExclusion(0, floatCountToPool, rngExclusions);
						rngExclusions.add(index);
					}
					else
						index = sequentialIndex.getAndIncrement();
					floatIndexMappings.put(value, index);
				});
			}

			final int doubleCountToPool = doublesToPool.size();
			final Map<Double, Integer> doubleIndexMappings = new HashMap<>(doubleCountToPool);
			if (doubleCountToPool > 0)
			{
				final Collection<Integer> rngExclusions = new ArrayList<>(doubleCountToPool);
				final AtomicInteger sequentialIndex = new AtomicInteger();
				doublesToPool.forEach(value ->
				{
					final int index;
					if (randomOrder)
					{
						index = RandomUtils.getRandomIntWithExclusion(0, doubleCountToPool, rngExclusions);
						rngExclusions.add(index);
					}
					else
						index = sequentialIndex.getAndIncrement();
					doubleIndexMappings.put(value, index);
				});
			}

			cw.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).map(MethodWrapper::getInstructions).forEach(insnList ->
			{
				if (poolIntegers)
					Stream.of(insnList.toArray()).filter(ASMUtils::isIntInsn).forEach(insn ->
					{
						final int value = ASMUtils.getIntegerFromInsn(insn);

						if (integerIndexMappings.containsKey(value))
						{
							final int index = integerIndexMappings.get(value);

							insnList.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, cw.getName(), integerPoolFieldName, "[I"));
							insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
							insnList.set(insn, new InsnNode(Opcodes.IALOAD));
							counter.incrementAndGet();
						}
					});

				if (poolLongs)
					Stream.of(insnList.toArray()).filter(ASMUtils::isLongInsn).forEach(insn ->
					{
						final long value = ASMUtils.getLongFromInsn(insn);

						if (longIndexMappings.containsKey(value))
						{
							final int index = longIndexMappings.get(value);

							insnList.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, cw.getName(), longPoolFieldName, "[J"));
							insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
							insnList.set(insn, new InsnNode(Opcodes.LALOAD));
							counter.incrementAndGet();
						}
					});

				if (poolFloats)
					Stream.of(insnList.toArray()).filter(ASMUtils::isFloatInsn).forEach(insn ->
					{
						final float value = ASMUtils.getFloatFromInsn(insn);

						if (floatIndexMappings.containsKey(value))
						{
							final int index = floatIndexMappings.get(value);

							insnList.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, cw.getName(), floatPoolFieldName, "[F"));
							insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
							insnList.set(insn, new InsnNode(Opcodes.FALOAD));
							counter.incrementAndGet();
						}
					});

				if (poolDoubles)
					Stream.of(insnList.toArray()).filter(ASMUtils::isDoubleInsn).forEach(insn ->
					{
						final double value = ASMUtils.getDoubleFromInsn(insn);

						if (doubleIndexMappings.containsKey(value))
						{
							final int index = doubleIndexMappings.get(value);

							insnList.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, cw.getName(), doublePoolFieldName, "[D"));
							insnList.insertBefore(insn, ASMUtils.getNumberInsn(index));
							insnList.set(insn, new InsnNode(Opcodes.DALOAD));
							counter.incrementAndGet();
						}
					});
			});

			if (!integersToPool.isEmpty() || !longsToPool.isEmpty() || !floatsToPool.isEmpty() || !doublesToPool.isEmpty())
			{
				final Map<Integer, Integer> fixedIntegerIndexMappings = new HashMap<>(integerIndexMappings.size());
				if (!integerIndexMappings.isEmpty())
					integerIndexMappings.forEach((key, value) -> fixedIntegerIndexMappings.put(value, key));

				final Map<Integer, Long> fixedLongIndexMappings = new HashMap<>(longIndexMappings.size());
				if (!longIndexMappings.isEmpty())
					longIndexMappings.forEach((key, value) -> fixedLongIndexMappings.put(value, key));

				final Map<Integer, Float> fixedFloatIndexMappings = new HashMap<>(floatIndexMappings.size());
				if (!floatIndexMappings.isEmpty())
					floatIndexMappings.forEach((key, value) -> fixedFloatIndexMappings.put(value, key));

				final Map<Integer, Double> fixedDoubleIndexMappings = new HashMap<>(doubleIndexMappings.size());
				if (!doubleIndexMappings.isEmpty())
					doubleIndexMappings.forEach((key, value) -> fixedDoubleIndexMappings.put(value, key));

				final MethodNode mv = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE, initPoolMethodName, "()V", null, null);

				mv.visitCode();

				// Integers
				if (!integerIndexMappings.isEmpty())
				{
					final int numberOfIntegers = fixedIntegerIndexMappings.size();
					ASMUtils.getNumberInsn(numberOfIntegers).accept(mv);
					mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
					IntStream.range(0, fixedIntegerIndexMappings.size()).forEach(i ->
					{
						mv.visitInsn(Opcodes.DUP);
						ASMUtils.getNumberInsn(i).accept(mv);
						ASMUtils.getNumberInsn(fixedIntegerIndexMappings.get(i)).accept(mv);
						mv.visitInsn(Opcodes.IASTORE);
					});
					mv.visitFieldInsn(Opcodes.PUTSTATIC, cw.getName(), integerPoolFieldName, "[I");

					final FieldNode fieldNode = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, integerPoolFieldName, "[I", null, null);
					cw.addField(fieldNode);
				}

				// Longs
				if (!longIndexMappings.isEmpty())
				{
					final int numberOfLongs = fixedLongIndexMappings.size();
					ASMUtils.getNumberInsn(numberOfLongs).accept(mv);
					mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG);
					IntStream.range(0, fixedLongIndexMappings.size()).forEach(i ->
					{
						mv.visitInsn(Opcodes.DUP);
						ASMUtils.getNumberInsn(i).accept(mv);
						ASMUtils.getNumberInsn(fixedLongIndexMappings.get(i)).accept(mv);
						mv.visitInsn(Opcodes.LASTORE);
					});
					mv.visitFieldInsn(Opcodes.PUTSTATIC, cw.getName(), longPoolFieldName, "[J");

					final FieldNode fieldNode = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, longPoolFieldName, "[J", null, null);
					cw.addField(fieldNode);
				}

				// Floats
				if (!floatIndexMappings.isEmpty())
				{
					final int numberOfFloats = fixedFloatIndexMappings.size();
					ASMUtils.getNumberInsn(numberOfFloats).accept(mv);
					mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
					IntStream.range(0, fixedFloatIndexMappings.size()).forEach(i ->
					{
						mv.visitInsn(Opcodes.DUP);
						ASMUtils.getNumberInsn(i).accept(mv);
						ASMUtils.getNumberInsn(fixedFloatIndexMappings.get(i)).accept(mv);
						mv.visitInsn(Opcodes.FASTORE);
					});
					mv.visitFieldInsn(Opcodes.PUTSTATIC, cw.getName(), floatPoolFieldName, "[F");

					final FieldNode fieldNode = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, floatPoolFieldName, "[F", null, null);
					cw.addField(fieldNode);
				}

				// Doubles
				if (!doubleIndexMappings.isEmpty())
				{
					final int numberOfDoubles = fixedDoubleIndexMappings.size();
					ASMUtils.getNumberInsn(numberOfDoubles).accept(mv);
					mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
					IntStream.range(0, fixedDoubleIndexMappings.size()).forEach(i ->
					{
						mv.visitInsn(Opcodes.DUP);
						ASMUtils.getNumberInsn(i).accept(mv);
						ASMUtils.getNumberInsn(fixedDoubleIndexMappings.get(i)).accept(mv);
						mv.visitInsn(Opcodes.DASTORE);
					});
					mv.visitFieldInsn(Opcodes.PUTSTATIC, cw.getName(), doublePoolFieldName, "[D");

					final FieldNode fieldNode = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, doublePoolFieldName, "[D", null, null);
					cw.addField(fieldNode);
				}

				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(3, 0);
				mv.visitEnd();

				cw.addMethod(mv);

				MethodNode staticBlock = cw.getClassNode().methods.stream().filter(methodNode -> "<clinit>".equals(methodNode.name)).findFirst().orElse(null);
				if (staticBlock == null)
				{
					staticBlock = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "<clinit>", "()V", null, null);
					final InsnList insnList = new InsnList();
					insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, cw.getName(), initPoolMethodName, "()V", false));
					insnList.add(new InsnNode(Opcodes.RETURN));
					staticBlock.instructions = insnList;
					cw.getClassNode().methods.add(staticBlock);
				}
				else
					staticBlock.instructions.insertBefore(staticBlock.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, cw.getName(), initPoolMethodName, "()V", false));
			}
		});

		Main.info(String.format("+ Pooled %d numbers.", counter.get()));
	}
}
