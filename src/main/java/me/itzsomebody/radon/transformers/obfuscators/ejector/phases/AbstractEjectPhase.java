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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.transformers.obfuscators.ejector.EjectorContext;
import me.itzsomebody.radon.utils.RandomUtils;

public abstract class AbstractEjectPhase implements Opcodes
{
	protected final EjectorContext ejectorContext;

	public AbstractEjectPhase(final EjectorContext ejectorContext)
	{
		this.ejectorContext = ejectorContext;
	}

	// TODO: Improve name generation logic
	static String getProxyMethodName(final MethodWrapper mw)
	{
//		final String fixedMethodName = mw.getName().replace('<', '_').replace('>', '_');
//		return fixedMethodName + "$" + Math.abs(RandomUtils.getRandomInt());

		final StringBuilder nameBuilder = new StringBuilder();

		// 사람들은 보통 앞 숫자들을 보고 서로 다른 문자열들을 구분한다. 이 심리를 역이용하여 앞 글자들을 Class name를 XOR 암호화한 문자열로 바꾸면, 같은 클래스 안에서는 앞 숫자들이 모두 같게 나오게 되기에, 헷갈리게 될 것이다. //
		nameBuilder.append(mw.getOwner().getName().chars().mapToObj(i -> String.valueOf(i ^ mw.getOwner().getName().length())).collect(Collectors.joining()));
		nameBuilder.append(mw.getName().chars().mapToObj(i -> String.valueOf(i ^ mw.getName().length())).collect(Collectors.joining()));
		nameBuilder.append(mw.getDescription().chars().mapToObj(i -> String.valueOf(i ^ mw.getDescription().length())).collect(Collectors.joining()));
		nameBuilder.append("$");
		IntStream.range(0, RandomUtils.getRandomInt(3, 8)).forEach(i -> nameBuilder.append(Math.abs(RandomUtils.getRandomLong())));

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

	protected int getJunkArgumentCount()
	{
		return ejectorContext.getJunkArgumentStrength() == 0 ? 0 : RandomUtils.getRandomInt(ejectorContext.getJunkArgumentStrength() / 2, ejectorContext.getJunkArgumentStrength());
	}
}
