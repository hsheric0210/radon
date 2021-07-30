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
import java.util.stream.IntStream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.transformers.obfuscators.ejector.EjectorContext;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

public final class MethodCallEjector extends AbstractEjectPhase
{

	public MethodCallEjector(final EjectorContext ejectorContext)
	{
		super(ejectorContext);
	}

	private static Map<MethodCallInfo, List<MethodInsnNode>> analyzeMethodCalls(final MethodNode methodNode, final Frame<AbstractValue>[] frames)
	{
		final Map<MethodCallInfo, List<MethodInsnNode>> result = new HashMap<>();
		final InsnList insnList = methodNode.instructions;
		final ListIterator<AbstractInsnNode> iterator = insnList.iterator();
		while (iterator.hasNext())
		{
			final AbstractInsnNode abstractInsnNode = iterator.next();

			if (!(abstractInsnNode instanceof MethodInsnNode))
				continue;
			final MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
			if ("<init>".equals(methodInsnNode.name))
				continue;

			final Type[] argumentTypes = Type.getArgumentTypes(methodInsnNode.desc);
			if (argumentTypes.length == 0)
				continue;

			final Frame<AbstractValue> frame = frames[insnList.indexOf(methodInsnNode)];
			final int constantArguments = (int) IntStream.range(0, argumentTypes.length).mapToObj(i -> frame.getStack(frame.getStackSize() - argumentTypes.length + i)).filter(value -> value.isConstant() && value.getUsages().size() == 1).count();

			if (constantArguments == 0)
				continue;

			final MethodCallInfo methodCallInfo = new MethodCallInfo(methodInsnNode.owner, methodInsnNode.itf, methodInsnNode.getOpcode(), methodInsnNode.name, methodInsnNode.desc);

			if (!result.containsKey(methodCallInfo))
			{
				final ArrayList<MethodInsnNode> list = new ArrayList<>();
				list.add(methodInsnNode);
				result.put(methodCallInfo, list);
			}
			else
				result.get(methodCallInfo).add(methodInsnNode);
		}
		return result;
	}

	private static MethodNode createProxyMethod(final String name, final MethodCallInfo methodCallInfo)
	{
		final List<Type> arguments = new ArrayList<>();
		if (methodCallInfo.opcode != INVOKESTATIC)
			arguments.add(Type.getType(Object.class));
		arguments.addAll(Arrays.asList(Type.getArgumentTypes(methodCallInfo.desc)));
		arguments.add(Type.INT_TYPE);

		final Type returnType = Type.getReturnType(methodCallInfo.desc);

		final MethodNode methodNode = new MethodNode(getRandomAccess(), name, Type.getMethodDescriptor(returnType, arguments.toArray(new Type[0])), null, null);
		final InsnList insnList = new InsnList();

		int variable = 0;
		for (int i = 0; i < arguments.size() - 1; i++)
		{
			final Type type = arguments.get(i);
			insnList.add(new VarInsnNode(ASMUtils.getVarOpcode(type, false), variable));
			if (i == 0 && methodCallInfo.opcode != INVOKESTATIC)
				insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, methodCallInfo.owner));
			variable += arguments.get(0).getSize();
		}

		insnList.add(new MethodInsnNode(methodCallInfo.opcode, methodCallInfo.owner, methodCallInfo.name, methodCallInfo.desc, methodCallInfo.isInterface));
		insnList.add(new InsnNode(ASMUtils.getReturnOpcode(returnType)));
		methodNode.instructions = insnList;
		return methodNode;
	}

	private static int getLastArgumentVar(final MethodNode methodNode)
	{
		final Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
		return IntStream.range(0, argumentTypes.length - 1).map(i -> argumentTypes[i].getSize()).sum();
	}

	private Map<Integer, InsnList> createJunkArguments(final Type[] argumentTypes, final int offset)
	{
		final Map<Integer, InsnList> junkArguments = new HashMap<>();

		for (int k = 0; k < getJunkArgumentCount(); k++)
		{
			final InsnList junkProxyArgumentFix = new InsnList();
			int junkVariable = 0;
			for (final Type argumentType : argumentTypes)
			{
				if (RandomUtils.getRandomBoolean())
				{
					junkProxyArgumentFix.add(ASMUtils.getRandomValue(argumentType));
					junkProxyArgumentFix.add(new VarInsnNode(ASMUtils.getVarOpcode(argumentType, true), offset + junkVariable));
				}
				junkVariable += argumentType.getSize();
			}
			junkArguments.put(ejectorContext.getNextId(), junkProxyArgumentFix);
		}
		return junkArguments;
	}

	@Override
	public void process(final MethodWrapper methodWrapper, final Frame<AbstractValue>[] frames)
	{
		final ClassWrapper classWrapper = ejectorContext.getClassWrapper();
		final MethodNode methodNode = methodWrapper.getMethodNode();

		final Map<MethodCallInfo, List<MethodInsnNode>> methodCalls = analyzeMethodCalls(methodNode, frames);
		if (methodCalls.isEmpty())
			return;
		methodWrapper.getMethodNode().maxStack++;

		final Map<AbstractInsnNode, InsnList> patches = new HashMap<>();
		methodCalls.forEach((key, value) ->
		{
			final MethodNode proxyMethod = createProxyMethod(getProxyMethodName(methodNode), key);
			final int offset = key.opcode == INVOKESTATIC ? 0 : 1;

			final Map<Integer, InsnList> proxyFixes = new HashMap<>();

			classWrapper.addMethod(proxyMethod);

			for (final MethodInsnNode methodInsnNode : value)
			{
				final int id = ejectorContext.getNextId();

				patches.put(methodInsnNode, ASMUtils.asList(new LdcInsnNode(id), new MethodInsnNode(Opcodes.INVOKESTATIC, classWrapper.getName(), proxyMethod.name, proxyMethod.desc, false)));

				final InsnList proxyArgumentFix = new InsnList();
				final Frame<AbstractValue> frame = frames[methodNode.instructions.indexOf(methodInsnNode)];

				final Type[] argumentTypes = Type.getArgumentTypes(methodInsnNode.desc);

				int variable = 0;
				for (int i = 0, j = argumentTypes.length; i < j; i++)
				{
					final Type argumentType = argumentTypes[i];
					final AbstractValue argumentValue = frame.getStack(frame.getStackSize() - argumentTypes.length + i);
					if (argumentValue.isConstant() && argumentValue.getUsages().size() == 1)
					{
						final AbstractInsnNode valueInsn = ejectorContext.isJunkArguments() ? ASMUtils.getRandomValue(argumentType) : ASMUtils.getDefaultValue(argumentType);
						patches.put(argumentValue.getInsnNode(), ASMUtils.singletonList(valueInsn));

						proxyArgumentFix.add(argumentValue.getInsnNode().clone(null));
						proxyArgumentFix.add(new VarInsnNode(ASMUtils.getVarOpcode(argumentType, true), offset + variable));
					}
					variable += argumentTypes[i].getSize();
				}
				proxyFixes.put(id, proxyArgumentFix);
				ejectorContext.getCounter().incrementAndGet();

				if (ejectorContext.isJunkArguments())
					proxyFixes.putAll(createJunkArguments(argumentTypes, offset));
			}

			final int idVariable = getLastArgumentVar(proxyMethod);
			insertFixes(proxyMethod, proxyFixes, idVariable);
		});

		patches.forEach((abstractInsnNode, insnList) ->
		{
			methodNode.instructions.insertBefore(abstractInsnNode, insnList);
			methodNode.instructions.remove(abstractInsnNode);
		});
	}

	private static class MethodCallInfo
	{
		final String owner;
		final boolean isInterface;
		final int opcode;
		final String name;
		final String desc;

		MethodCallInfo(final String owner, final boolean isInterface, final int opcode, final String name, final String desc)
		{
			this.owner = owner;
			this.isInterface = isInterface;
			this.opcode = opcode;
			this.name = name;
			this.desc = desc;
		}

		@Override
		public boolean equals(final Object o)
		{
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			final MethodCallInfo that = (MethodCallInfo) o;
			return isInterface == that.isInterface && opcode == that.opcode && Objects.equals(owner, that.owner) && Objects.equals(name, that.name) && Objects.equals(desc, that.desc);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(owner, isInterface, opcode, name, desc);
		}
	}
}
