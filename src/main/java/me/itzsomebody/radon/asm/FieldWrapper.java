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

import org.objectweb.asm.tree.FieldNode;

import me.itzsomebody.radon.asm.accesses.Access;
import me.itzsomebody.radon.asm.accesses.FieldAccess;

/**
 * Wrapper for FieldNodes.
 *
 * @author ItzSomebody.
 */
public class FieldWrapper
{
	public FieldNode fieldNode;
	public final String originalName;
	public final String originalDescription;

	public final Access access;
	public final ClassWrapper owner;

	/**
	 * Creates a FieldWrapper object.
	 *
	 * @param fieldNode
	 *                  the {@link FieldNode} attached to this FieldWrapper.
	 * @param owner
	 *                  the owner of this represented field.
	 */
	public FieldWrapper(final FieldNode fieldNode, final ClassWrapper owner)
	{
		this.fieldNode = fieldNode;
		originalName = fieldNode.name;
		originalDescription = fieldNode.desc;
		access = new FieldAccess(this);
		this.owner = owner;
	}

	/**
	 * @return the current name of the wrapped {@link FieldNode}.
	 */
	public String getName()
	{
		return fieldNode.name;
	}

	/**
	 * @return the current description of the wrapped {@link FieldNode}.
	 */
	public String getDescription()
	{
		return fieldNode.desc;
	}

	/**
	 * @return raw access flags of wrapped {@link FieldNode}.
	 */
	public int getAccessFlags()
	{
		return fieldNode.access;
	}

	/**
	 * @param access
	 *               access flags to set.
	 */
	public void setAccessFlags(final int access)
	{
		fieldNode.access = access;
	}
}
