package me.itzsomebody.radon.asm;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.utils.ASMUtils;

/**
 * Local variable provider / index calculator
 * Original code can found on: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/utils/VariableProvider.java
 *
 * @author superblaubeere27
 */
public class LocalVariableProvider implements Opcodes
{
	public int maxLocals;
	private final int maxArgumentLocals;
	public AbstractInsnNode first;
	public List<Local> localVariables;

	public LocalVariableProvider(final MethodWrapper mw)
	{
		final MethodNode mn = mw.methodNode;

		if (!Modifier.isStatic(mn.access))
			registerExisting(0, Type.getType("Ljava/lang/Object;").getSize()); // Register 0 contains reference to 'this'

		for (final Type argumentType : Type.getArgumentTypes(mn.desc))
		{
			final int argTypeSize = argumentType.getSize();
			registerExisting(argTypeSize + maxLocals - 1, argTypeSize);
		}
		maxArgumentLocals = maxLocals;

		final InsnList insns = mn.instructions;
		for (final AbstractInsnNode insn : insns.toArray())
			if (insn instanceof VarInsnNode)
			{
				if (first == null)
					first = insn;

				final VarInsnNode varInsn = (VarInsnNode) insn;
				registerExisting(varInsn.var, ASMUtils.getType(varInsn).getSize());
			}

		localVariables = new ArrayList<>();

		if (mw.originalLocals != null)
			for (final LocalVariableNode local : mw.originalLocals)
				if (!isArgumentLocal(local.index))
					localVariables.add(new Local(local.index, Type.getType(local.desc), local.start, local.end, insns));
	}

	private void registerExisting(final int varIndex, final int typeSize)
	{
		if (varIndex >= maxLocals)
			maxLocals = varIndex + typeSize;
	}

	public boolean isUnallocated(final int varIndex)
	{
		return varIndex >= maxLocals;
	}

	public boolean isArgumentLocal(final int varIndex)
	{
		return varIndex < maxArgumentLocals;
	}

	public int allocateVar(final int typeSize)
	{
		int ret = maxLocals;
		if (typeSize > 0)
		{
			ret++;
			maxLocals += typeSize;
		}
		return ret;
	}

	public Type getLocalType(final int varIndex, final AbstractInsnNode insn)
	{
		return localVariables.stream().filter(entry -> entry.varIndex == varIndex && entry.isAvailableOn(insn)).findFirst().map(entry -> entry.type).orElse(null);
	}

	public static class Local
	{
		public int varIndex;
		public Type type;
		public int typeSort;
		public int size;
		private final Set<AbstractInsnNode> availableOn;

		public Local(final int varIndex, final Type type, final LabelNode start, final LabelNode end, final InsnList insns)
		{
			this.varIndex = varIndex;
			this.type = type;

			if (insns != null && start != null && end != null)
			{
				if (!insns.contains(start))
					throw new IllegalArgumentException("insns doesn't contains start label");
				if (!insns.contains(end))
					throw new IllegalArgumentException("insns doesn't contains end label");

				// Converted InsnList -> ArrayList
				final List<AbstractInsnNode> insnList = Arrays.asList(insns.toArray());

				// A list which only contain LabelNode's
				final List<LabelNode> labelList = insnList.stream().filter(insn -> insn instanceof LabelNode).map(insn -> (LabelNode) insn).collect(Collectors.toList());
				final List<AbstractInsnNode> availableOn = insnList.subList(insnList.indexOf(labelList.get(Math.max(labelList.indexOf(start) - 1, 0))), insns.indexOf(end));

				this.availableOn = new HashSet<>(availableOn.size());
				this.availableOn.addAll(availableOn);
			}
			else
				availableOn = new HashSet<>(0);

			this.type = type;
			typeSort = type.getSort();
			switch (typeSort)
			{
				case Type.BOOLEAN:
					size = 1;
					break;
				case Type.CHAR:
				case Type.SHORT:
					size = 16;
					break;
				case Type.BYTE:
					size = 8;
					break;
				case Type.LONG:
				case Type.DOUBLE:
					size = 64;
					break;
				default:
					size = 32;
			}
		}

		public boolean isAvailableOn(final AbstractInsnNode insn)
		{
			return availableOn.contains(insn);
		}
	}
}
