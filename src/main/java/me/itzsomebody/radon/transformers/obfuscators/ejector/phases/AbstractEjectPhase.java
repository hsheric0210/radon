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

package me.itzsomebody.radon.transformers.obfuscators.ejector.phases;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.transformers.obfuscators.ejector.EjectorContext;
import me.itzsomebody.radon.utils.RandomUtils;

public abstract class AbstractEjectPhase implements Opcodes
{
	protected final EjectorContext ejectorContext;
	private static Map<String, Collection<String>> proxyMethodNameExclusionMap = new HashMap<>();

	public AbstractEjectPhase(final EjectorContext ejectorContext)
	{
		this.ejectorContext = ejectorContext;
	}

	static String getProxyMethodName(final MethodWrapper mw)
	{
		final String ownerName = mw.owner.getName();
		final int ownerNameLength = ownerName.length();

		final String name = mw.getName();
		final int nameLength = name.length();

		final String desc = mw.getDescription();
		final int descLength = desc.length();

		final StringBuilder nameBuilder = new StringBuilder();
		nameBuilder.append(ownerName.chars().mapToObj(i -> Integer.toString(Math.abs(i ^ ownerNameLength), 16).toUpperCase(Locale.ENGLISH)).collect(Collectors.joining()));
		IntStream.range(0, RandomUtils.getRandomInt(3, 5)).forEach(i -> nameBuilder.append(Long.toString(Math.abs(RandomUtils.getRandomLong()), 16).toUpperCase(Locale.ENGLISH)));
		nameBuilder.append(name.chars().mapToObj(i -> Integer.toString(Math.abs(i ^ nameLength), 16).toUpperCase(Locale.ENGLISH)).collect(Collectors.joining()));
		IntStream.range(0, RandomUtils.getRandomInt(3, 5)).forEach(i -> nameBuilder.append(Long.toString(Math.abs(RandomUtils.getRandomLong()), 16).toUpperCase(Locale.ENGLISH)));
		nameBuilder.append(desc.chars().mapToObj(i -> Integer.toString(Math.abs(i ^ descLength), 16).toUpperCase(Locale.ENGLISH)).collect(Collectors.joining()));
		return nameBuilder.toString();
	}

	protected static int getRandomAccess()
	{
		int access = ACC_STATIC;
		if (RandomUtils.getRandomBoolean())
			access += RandomUtils.getRandomBoolean() ? ACC_PRIVATE : ACC_PROTECTED;
		if (RandomUtils.getRandomBoolean())
			access += ACC_FINAL;
		access += ACC_SYNTHETIC;
		access += ACC_BRIDGE;
		return access;
	}

	// TODO: Improve key generation & verification algorithm
	protected static void insertFixes(final MethodNode methodNode, final Map<Integer, ? extends InsnList> fixes, final int idVariable)
	{
		final InsnList proxyFix = new InsnList();
		final LabelNode end = new LabelNode();

		final ArrayList<Integer> keys = new ArrayList<>(fixes.keySet());
		Collections.shuffle(keys);

		keys.forEach(id ->
		{
			final int xorKey = RandomUtils.getRandomInt();

			final InsnList insnList = fixes.get(id);
			proxyFix.add(new VarInsnNode(Opcodes.ILOAD, idVariable));
			proxyFix.add(new LdcInsnNode(xorKey));
			proxyFix.add(new InsnNode(IXOR));
			proxyFix.add(new LdcInsnNode(id ^ xorKey));
			final LabelNode labelNode = new LabelNode();
			proxyFix.add(new JumpInsnNode(Opcodes.IF_ICMPNE, labelNode));

			proxyFix.add(insnList);
			proxyFix.add(new JumpInsnNode(Opcodes.GOTO, end));
			proxyFix.add(labelNode);
		});

		proxyFix.add(end);
		methodNode.instructions.insert(proxyFix);
	}

	public abstract void process(MethodWrapper methodWrapper, Frame<AbstractValue>... frames);

	protected static int getLastArgumentVar(final Type... argumentTypes)
	{
		return IntStream.range(0, argumentTypes.length - 1).map(i -> argumentTypes[i].getSize()).sum();
	}

	protected int getJunkArgumentCount()
	{
		return ejectorContext.getJunkArgumentStrength() == 0 ? 0 : RandomUtils.getRandomInt(ejectorContext.getJunkArgumentStrength() / 2, ejectorContext.getJunkArgumentStrength());
	}
}
