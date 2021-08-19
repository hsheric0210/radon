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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.LocalVariableProvider;
import me.itzsomebody.radon.utils.LocalVariableProvider.Local;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * https://www.sable.mcgill.ca/JBCO/examples.html#PLVB
 *
 * Original source code: https://github.com/soot-oss/soot/blob/develop/src/main/java/soot/jbco/bafTransformations/LocalsToBitField.java
 *
 * FIXME: HANG
 */
public class LocalVariablePacker extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger totalFound = new AtomicInteger();
		final AtomicInteger newLongsCounter = new AtomicInteger();
		final AtomicInteger affectedCounter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final InsnList insns = mw.getInstructions();

			final LocalVariableProvider varProvider = new LocalVariableProvider(mw);

			final List<Local> locals = varProvider.localVariables;

			final List<Local> booleans = new ArrayList<>();
			final List<Local> bytes = new ArrayList<>();
			final List<Local> chars = new ArrayList<>();
			final List<Local> ints = new ArrayList<>();
			for (final Local local : locals)
			{
				totalFound.incrementAndGet();

				if (local != null)
				{
					final int sort = local.typeSort;
					if (sort >= Type.BOOLEAN && sort <= Type.INT)
						switch (sort - Type.BOOLEAN)
						{
							case 0:
								booleans.add(local);
								break;
							case Type.BYTE - Type.BOOLEAN:
								bytes.add(local);
								break;
							case Type.CHAR - Type.BOOLEAN:
								chars.add(local);
								break;
							case Type.INT - Type.BOOLEAN:
								ints.add(local);
						}
				}
			}

			InsnList initializer = null;

			final Map<Local, Local> intToNewLocals = new HashMap<>();
			int total = booleans.size() + (bytes.size() << 3) + (chars.size() << 4) + (ints.size() << 5);
			final Map<Local, Map<Local, Integer>> newLocals = new HashMap<>();
			while (total >= 32 && booleans.size() + bytes.size() + chars.size() + ints.size() > 2)
			{
				final Local nloc = new Local(varProvider.allocateVar(2), Type.LONG_TYPE, null, null, null);
				final Map<Local, Integer> nlocMap = new HashMap<>();

				boolean done = false;
				int index = 0;
				while (index < 64 && !done)
				{
					int max = 64 - index;
					max = max > 31 ? 4 : max > 15 ? 3 : max > 7 ? 2 : 1;
					final int rand = RandomUtils.getRandomInt(max);
					max = index;
					switch (rand)
					{
						case 3:
							if (ints.size() > 0)
							{
								final Local l = ints.remove(RandomUtils.getRandomInt(ints.size()));
								nlocMap.put(l, index);
								index += 32;
								intToNewLocals.put(l, nloc);
								index = getNewIndex(index, ints, chars, bytes, booleans);
								break;
							}
						case 2:
							if (chars.size() > 0)
							{
								final Local l = chars.remove(RandomUtils.getRandomInt(chars.size()));
								nlocMap.put(l, index);
								index += 16;
								intToNewLocals.put(l, nloc);
								index = getNewIndex(index, ints, chars, bytes, booleans);
								break;
							}
						case 1:
							if (bytes.size() > 0)
							{
								final Local l = bytes.remove(RandomUtils.getRandomInt(bytes.size()));
								nlocMap.put(l, index);
								index += 8;
								intToNewLocals.put(l, nloc);
								index = getNewIndex(index, ints, chars, bytes, booleans);
								break;
							}
						case 0:
							if (booleans.size() > 0)
							{
								final Local l = booleans.remove(RandomUtils.getRandomInt(booleans.size()));
								nlocMap.put(l, index++);
								intToNewLocals.put(l, nloc);
								index = getNewIndex(index, ints, chars, bytes, booleans);
								break;
							}
					}

					if (max == index)
						done = true;
				}
				newLocals.put(nloc, nlocMap);

				if (initializer == null)
					initializer = new InsnList();

				initializer.add(ASMUtils.getNumberInsn(0L));
				initializer.add(new VarInsnNode(LSTORE, nloc.varIndex));

				total = booleans.size() + (bytes.size() << 3) + (chars.size() << 4) + (ints.size() << 5);
			}

			if (intToNewLocals.isEmpty())
				return;

			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (insn instanceof VarInsnNode)
				{

					final VarInsnNode varInsn = (VarInsnNode) insn;
					final int varIndex = varInsn.var;

					if (insn.getOpcode() == ISTORE)
					{
						final Optional<Local> optLocal = locals.stream().filter(entry -> entry.varIndex == varIndex && entry.availableOn.contains(insn)).findAny();
						if (optLocal.isPresent())
						{
							final Local intLocal = optLocal.get();
							final Local longLocal = intToNewLocals.get(intLocal);
							if (longLocal != null)
							{
								final int index = newLocals.get(longLocal).get(intLocal);
								final int size = intLocal.size;
								final long longmask = (size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index;

								final InsnList replace = new InsnList();

								replace.add(new InsnNode(I2L));
								if (index > 0)
								{
									replace.add(ASMUtils.getNumberInsn(index));
									replace.add(new InsnNode(LSHL));
								}
								replace.add(ASMUtils.getNumberInsn(~longmask));
								replace.add(new InsnNode(LAND));
								replace.add(new VarInsnNode(LLOAD, longLocal.varIndex));
								replace.add(ASMUtils.getNumberInsn(longmask));
								replace.add(new InsnNode(LAND));
								replace.add(new InsnNode(LXOR));
								replace.add(new VarInsnNode(LSTORE, longLocal.varIndex));

								insns.insertBefore(insn, replace);
								insns.remove(insn);
							}
						}
					}

					if (insn.getOpcode() == ILOAD)
					{
						final Optional<Local> optLocal = locals.stream().filter(entry -> entry.varIndex == varIndex && entry.availableOn.contains(insn)).findAny();
						if (optLocal.isPresent())
						{
							final Local intLocal = optLocal.get();
							final Local longLocal = intToNewLocals.get(intLocal);
							if (longLocal != null)
							{
								final int index = newLocals.get(longLocal).get(intLocal);
								final int size = intLocal.size;
								final long longmask = (size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index;

								final InsnList replace = new InsnList();

								replace.add(new VarInsnNode(LLOAD, longLocal.varIndex));
								replace.add(ASMUtils.getNumberInsn(longmask));
								replace.add(new InsnNode(LAND));
								if (index > 0)
								{
									replace.add(ASMUtils.getNumberInsn(index));
									replace.add(new InsnNode(LSHR));
								}

								final Type originalType = intLocal.type;
								final Type actualType = getType(originalType);
								replace.add(ASMUtils.getPrimitiveCastInsn(Type.LONG_TYPE, actualType)); // long -> int
								if (originalType.getSort() != Type.INT && originalType.getSort() != Type.BOOLEAN)
									replace.add(ASMUtils.getPrimitiveCastInsn(actualType, originalType)); // int -> byte, short, char, etc.

								insns.insertBefore(insn, replace);
								insns.remove(insn);
							}
						}
					}
				}

				if (insn instanceof IincInsnNode)
				{
					final IincInsnNode ii = (IincInsnNode) insn;
					final int varIndex = ii.var;
					final Optional<Local> optLocal = locals.stream().filter(entry -> entry.varIndex == varIndex && entry.availableOn.contains(insn)).findAny();
					if (optLocal.isPresent())
					{
						final Local intLocal = optLocal.get();
						final Local nloc = intToNewLocals.get(intLocal);
						if (nloc != null)
						{
							final int index = newLocals.get(nloc).get(intLocal);
							final int size = intLocal.size;
							final long longmask = (size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index;

							final InsnList replace = new InsnList();

							replace.add(ASMUtils.getNumberInsn(ii.incr));
							replace.add(new VarInsnNode(LLOAD, nloc.varIndex));
							replace.add(ASMUtils.getNumberInsn(longmask));
							replace.add(new InsnNode(LAND));
							if (index > 0)
							{
								replace.add(ASMUtils.getNumberInsn(index));
								replace.add(new InsnNode(LSHR));
							}
							replace.add(new InsnNode(L2I));
							replace.add(new InsnNode(IADD));
							replace.add(new InsnNode(I2L));
							if (index > 0)
							{
								replace.add(ASMUtils.getNumberInsn(index));
								replace.add(new InsnNode(LSHL));
							}

							replace.add(new VarInsnNode(LLOAD, nloc.varIndex));
							replace.add(ASMUtils.getNumberInsn(~longmask));
							replace.add(new InsnNode(LAND));
							replace.add(new InsnNode(LXOR));
							replace.add(new VarInsnNode(LSTORE, nloc.varIndex));

							insns.insertBefore(insn, replace);
							insns.remove(insn);
						}
					}
				}
			}

			final Iterator<Local> iterator = locals.iterator();
			while (iterator.hasNext())
			{
				final Local local = iterator.next();
				if (intToNewLocals.containsKey(local))
				{
					iterator.remove();
					newLongsCounter.incrementAndGet();
				}
			}

			if (initializer != null)
				insns.insertBefore(insns.getFirst(), initializer);
		}));

		info("+ Packed " + newLongsCounter.get() + " local variables (" + totalFound.get() + " locals found)");
	}

	@Override
	public String getName()
	{
		return "Local Variable Packer";
	}

	private static Type getType(final Type t)
	{
		if (t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.SHORT)
			return Type.INT_TYPE;
		return t;
	}

	private int getNewIndex(int index, final Collection<Local> ints, final Collection<Local> chars, final Collection<Local> bytes, final Collection<Local> booleans)
	{
		int max = 0;
		if (!booleans.isEmpty() && index < 63)
			max = 64;
		else if (!bytes.isEmpty() && index < 56)
			max = 57;
		else if (!chars.isEmpty() && index < 48)
			max = 49;
		else if (!ints.isEmpty() && index < 32)
			max = 33;

		if (max != 0)
		{
			final int rand = RandomUtils.getRandomInt(4);
			max -= index;
			if (max > rand)
				max = rand;
			else if (max != 1)
				max = RandomUtils.getRandomInt(max);
			index += max;
		}
		return index;
	}
}