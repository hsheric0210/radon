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

package me.itzsomebody.radon.asm;

import java.util.List;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import me.itzsomebody.radon.asm.accesses.Access;
import me.itzsomebody.radon.asm.accesses.MethodAccess;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;

/**
 * Wrapper for MethodNodes.
 *
 * @author ItzSomebody
 */
public class MethodWrapper
{
	public MethodNode methodNode;
	public final String originalName;
	public final String originalDescription;
	public final List<LocalVariableNode> originalLocals;

	public final Access access;
	public final ClassWrapper owner;

	/**
	 * Creates a MethodWrapper object.
	 *
	 * @param methodNode
	 *                   the {@link MethodNode} this wrapper represents.
	 * @param owner
	 *                   the owner of this represented method.
	 */
	public MethodWrapper(final MethodNode methodNode, final ClassWrapper owner)
	{
		this.methodNode = methodNode;
		originalName = methodNode.name;
		originalDescription = methodNode.desc;
		originalLocals = methodNode.localVariables;
		access = new MethodAccess(this);
		this.owner = owner;
	}

	/**
	 * @return the current name of wrapped {@link MethodNode}.
	 */
	public String getName()
	{
		return methodNode.name;
	}

	/**
	 * @return the current description of wrapped {@link MethodNode}.
	 */
	public String getDescription()
	{
		return methodNode.desc;
	}

	/**
	 * @return the current {@link InsnList} of wrapped {@link MethodNode}.
	 */
	public InsnList getInstructions()
	{
		return methodNode.instructions;
	}

	public void setInstructions(final InsnList instructions)
	{
		methodNode.instructions = instructions;
	}

	/**
	 * @return the current {@link TryCatchBlockNode}s of wrapped {@link MethodNode}.
	 */
	public List<TryCatchBlockNode> getTryCatchBlocks()
	{
		return methodNode.tryCatchBlocks;
	}

	/**
	 * @return raw access flags of wrapped {@link MethodNode}.
	 */
	public int getAccessFlags()
	{
		return methodNode.access;
	}

	/**
	 * @param access
	 *               access flags to set.
	 */
	public void setAccessFlags(final int access)
	{
		methodNode.access = access;
	}

	/**
	 * @return the current max allocation of local variables (registers) of wrapped {@link MethodNode}.
	 */
	public int getMaxLocals()
	{
		return methodNode.maxLocals;
	}

	public void setMaxLocals(final int maxLocals)
	{
		methodNode.maxLocals = maxLocals;
	}

	public int getMaxStack()
	{
		return methodNode.maxStack;
	}

	public void setMaxStack(final int maxStack)
	{
		methodNode.maxStack = maxStack;
	}

	/**
	 * @return true if the wrapped {@link MethodNode} represented by this wrapper contains instructions.
	 */
	public boolean hasInstructions()
	{
		return methodNode.instructions != null && methodNode.instructions.size() > 0;
	}

	public List<LocalVariableNode> getLocals()
	{
		return methodNode.localVariables;
	}

	public boolean hasLocals()
	{
		return getLocals() != null && !getLocals().isEmpty();
	}

	/**
	 * @return computes and returns the size of the wrapped {@link MethodNode}.
	 */
	public int getCodeSize()
	{
		return ASMUtils.evaluateMaxSize(methodNode);
	}

	/**
	 * @return the leeway between the current size of the wrapped {@link MethodNode} and the max allowed size.
	 */
	public int getLeewaySize()
	{
		return Constants.MAX_CODE_SIZE - getCodeSize();
	}
}
