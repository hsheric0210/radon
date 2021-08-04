package me.itzsomebody.radon.utils;

import java.lang.reflect.Modifier;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Local variable provider / index calculator
 * Original code can found on: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/utils/VariableProvider.java
 *
 * @author superblaubeere27
 */
public class LocalVariableProvider
{
	private int maxLocals;
	private final int maxArgumentLocals;

	public LocalVariableProvider(final MethodNode mn)
	{
		if (!Modifier.isStatic(mn.access))
			registerExisting(0, Type.getType("Ljava/lang/Object;").getSize()); // Register 0 contains reference to 'this'

		for (final Type argumentType : Type.getArgumentTypes(mn.desc))
		{
			final int argTypeSize = argumentType.getSize();
			registerExisting(argTypeSize + maxLocals - 1, argTypeSize);
		}
		maxArgumentLocals = maxLocals;

		for (final AbstractInsnNode abstractInsnNode : mn.instructions.toArray())
			if (abstractInsnNode instanceof VarInsnNode)
				registerExisting(((VarInsnNode) abstractInsnNode).var, ASMUtils.getType((VarInsnNode) abstractInsnNode).getSize());
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
}
