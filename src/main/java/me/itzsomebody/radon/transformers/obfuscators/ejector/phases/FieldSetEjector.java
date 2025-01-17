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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.transformers.obfuscators.ejector.EjectorContext;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;

public final class FieldSetEjector extends AbstractEjectPhase
{
	public FieldSetEjector(final EjectorContext ejectorContext)
	{
		super(ejectorContext);
	}

	private static Map<FieldSetInfo, List<FieldInsnNode>> analyzeFieldSets(final MethodNode methodNode, final Frame<AbstractValue>... frames)
	{
		final Map<FieldSetInfo, List<FieldInsnNode>> result = new HashMap<>();
		final InsnList insnList = methodNode.instructions;
		final ListIterator<AbstractInsnNode> iterator = insnList.iterator();
		while (iterator.hasNext())
		{
			final AbstractInsnNode abstractInsnNode = iterator.next();

			if (!(abstractInsnNode instanceof FieldInsnNode))
				continue;
			final FieldInsnNode fieldInsnNode = (FieldInsnNode) abstractInsnNode;
			if (fieldInsnNode.getOpcode() != PUTFIELD && fieldInsnNode.getOpcode() != PUTSTATIC)
				continue;

			final int frameIndex = insnList.indexOf(fieldInsnNode);
			if (frameIndex >= frames.length)
				continue;

			final Frame<AbstractValue> frame = frames[frameIndex];
			if (frame == null)
				continue;

			final AbstractValue value = frame.getStack(frame.getStackSize() - 1);
			if (!value.isConstant())
				continue;

			final FieldSetInfo fieldSetInfo = new FieldSetInfo(fieldInsnNode.getOpcode(), fieldInsnNode.desc);

			if (result.containsKey(fieldSetInfo))
				result.get(fieldSetInfo).add(fieldInsnNode);
			else
			{
				final List<FieldInsnNode> list = new ArrayList<>();
				list.add(fieldInsnNode);
				result.put(fieldSetInfo, list);
			}
		}
		return result;
	}

	private static MethodNode createProxyMethod(final String name, final FieldSetInfo fieldSetInfo)
	{
		final List<Type> arguments = new ArrayList<>();
		if (fieldSetInfo.opcode != PUTSTATIC)
			arguments.add(Type.getType(Object.class));
		arguments.add(Type.getType(fieldSetInfo.desc));
		arguments.add(Type.INT_TYPE);

		final MethodNode methodNode = new MethodNode(getRandomAccess(), name, Type.getMethodDescriptor(Type.VOID_TYPE, arguments.toArray(Constants.EMPTY_TYPE_ARRAY)), null, null);
		methodNode.instructions = ASMUtils.singletonList(new InsnNode(Opcodes.RETURN));
		return methodNode;
	}

	private Map<Integer, InsnList> createJunkArguments(final List<? extends FieldInsnNode> fieldInsnNodes, final boolean isStatic)
	{
		final Map<Integer, InsnList> junkArguments = new HashMap<>();

		for (int k = 0, l = getJunkArgumentCount(); k < l; k++)
		{
			final FieldInsnNode fieldInsnNode = RandomUtils.getRandomElement(fieldInsnNodes);
			final Type type = Type.getType(fieldInsnNode.desc);

			final InsnList junkProxyArgumentFix = new InsnList();
			if (!isStatic)
			{
				junkProxyArgumentFix.add(new VarInsnNode(ALOAD, 0));
				junkProxyArgumentFix.add(new TypeInsnNode(CHECKCAST, fieldInsnNode.owner));
			}
			junkProxyArgumentFix.add(ASMUtils.getRandomInsn(type));
			junkProxyArgumentFix.add(fieldInsnNode.clone(null));

			junkArguments.put(ejectorContext.getNextId(), junkProxyArgumentFix);
		}
		return junkArguments;
	}

	private InsnList processFieldSet(final MethodNode methodNode, final Frame<AbstractValue>[] frames, final Map<? super AbstractInsnNode, ? super InsnList> patches, final FieldInsnNode fieldInsnNode)
	{
		final InsnList proxyArgumentFix = new InsnList();
		final Frame<AbstractValue> frame = frames[methodNode.instructions.indexOf(fieldInsnNode)];

		final Type type = Type.getType(fieldInsnNode.desc);
		final AbstractValue argumentValue = frame.getStack(frame.getStackSize() - 1);
		if (argumentValue.isConstant() && argumentValue.getUsages().size() == 1)
		{
			final AbstractInsnNode valueInsn = ejectorContext.isJunkArguments() ? ASMUtils.getRandomInsn(type) : ASMUtils.getDefaultValue(type);
			patches.put(argumentValue.getInsnNode(), ASMUtils.singletonList(valueInsn));
			if (fieldInsnNode.getOpcode() != PUTSTATIC)
			{
				proxyArgumentFix.add(new VarInsnNode(ALOAD, 0));
				proxyArgumentFix.add(new TypeInsnNode(CHECKCAST, fieldInsnNode.owner));
			}
			proxyArgumentFix.add(argumentValue.getInsnNode().clone(null));
			proxyArgumentFix.add(fieldInsnNode.clone(null));
		}

		return proxyArgumentFix;
	}

	@Override
	public void process(final MethodWrapper methodWrapper, final Frame<AbstractValue>[] frames)
	{
		final ClassWrapper classWrapper = ejectorContext.getClassWrapper();
		final MethodNode methodNode = methodWrapper.methodNode;

		final Map<FieldSetInfo, List<FieldInsnNode>> fieldSets = analyzeFieldSets(methodNode, frames);
		if (fieldSets.isEmpty())
			return;
		methodWrapper.methodNode.maxStack++;

		final Map<AbstractInsnNode, InsnList> patches = new HashMap<>();
		fieldSets.forEach((setInfo, setInsns) ->
		{
			final MethodNode proxyMethod = createProxyMethod(getProxyMethodName(methodWrapper), setInfo);
			final boolean isStatic = setInfo.opcode == PUTSTATIC;

			final Map<Integer, InsnList> proxyFixes = new HashMap<>();

			classWrapper.addMethod(proxyMethod);

			for (final FieldInsnNode insn : setInsns)
			{
				final int id = ejectorContext.getNextId();

				patches.put(insn, ASMUtils.asList(new LdcInsnNode(id), new MethodInsnNode(Opcodes.INVOKESTATIC, classWrapper.getName(), proxyMethod.name, proxyMethod.desc, false)));

				final InsnList proxyArgumentFix = processFieldSet(methodNode, frames, patches, insn);

				proxyFixes.put(id, proxyArgumentFix);
				ejectorContext.getCounter().incrementAndGet();

				if (ejectorContext.isJunkArguments())
					proxyFixes.putAll(createJunkArguments(setInsns, isStatic));
			}

			insertFixes(proxyMethod, proxyFixes, getLastArgumentVar(Type.getArgumentTypes(proxyMethod.desc)));
		});

		patches.forEach((patchTarget, patch) ->
		{
			methodNode.instructions.insertBefore(patchTarget, patch);
			methodNode.instructions.remove(patchTarget);
		});
	}

	private static class FieldSetInfo
	{
		final int opcode;
		final String desc;

		FieldSetInfo(final int opcode, final String desc)
		{
			this.opcode = opcode;
			this.desc = desc;
		}

		@Override
		public boolean equals(final Object o)
		{
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			final FieldSetInfo that = (FieldSetInfo) o;
			return opcode == that.opcode && Objects.equals(desc, that.desc);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(opcode, desc);
		}
	}
}
