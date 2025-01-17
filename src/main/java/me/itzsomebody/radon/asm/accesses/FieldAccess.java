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

package me.itzsomebody.radon.asm.accesses;

import me.itzsomebody.radon.asm.FieldWrapper;
import me.itzsomebody.radon.exceptions.RadonException;

/**
 * Wrapper for FieldNode access flags.
 *
 * @author ItzSomebody
 */
public class FieldAccess implements Access
{
	private final FieldWrapper wrapper;

	public FieldAccess(final FieldWrapper wrapper)
	{
		this.wrapper = wrapper;
	}

	@Override
	public boolean isPublic()
	{
		return (ACC_PUBLIC & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isPrivate()
	{
		return (ACC_PRIVATE & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isProtected()
	{
		return (ACC_PROTECTED & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isStatic()
	{
		return (ACC_STATIC & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isFinal()
	{
		return (ACC_FINAL & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isSuper()
	{
		return badAccessCheck("SYNCHRONIZED");
	}

	@Override
	public boolean isSynchronized()
	{
		return badAccessCheck("SYNCHRONIZED");
	}

	@Override
	public boolean isOpen()
	{
		return badAccessCheck("OPEN");
	}

	@Override
	public boolean isTransitive()
	{
		return badAccessCheck("TRANSITIVE");
	}

	@Override
	public boolean isVolatile()
	{
		return (ACC_VOLATILE & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isBridge()
	{
		return badAccessCheck("BRIDGE");
	}

	@Override
	public boolean isStaticPhase()
	{
		return badAccessCheck("STATIC_PHASE");
	}

	@Override
	public boolean isVarargs()
	{
		return badAccessCheck("VARARGS");
	}

	@Override
	public boolean isTransient()
	{
		return (ACC_TRANSIENT & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isNative()
	{
		return badAccessCheck("NATIVE");
	}

	@Override
	public boolean isInterface()
	{
		return badAccessCheck("INTERFACE");
	}

	@Override
	public boolean isAbstract()
	{
		return badAccessCheck("ABSTRACT");
	}

	@Override
	public boolean isStrict()
	{
		return badAccessCheck("STRICT");
	}

	@Override
	public boolean isSynthetic()
	{
		return (ACC_SYNTHETIC & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean isAnnotation()
	{
		return badAccessCheck("ANNOTATION");
	}

	@Override
	public boolean isEnum()
	{
		return badAccessCheck("ENUM");
	}

	@Override
	public boolean isMandated()
	{
		return badAccessCheck("MANDATED");
	}

	@Override
	public boolean isModule()
	{
		return badAccessCheck("MODULE");
	}

	@Override
	public boolean isDeprecated()
	{
		return (ACC_DEPRECATED & wrapper.getAccessFlags()) != 0;
	}

	@Override
	public boolean badAccessCheck(final String type)
	{
		throw new RadonException(String.format("%s.%s with type %s is a field and cannot be checked for the access flag %s", wrapper.owner.originalName, wrapper.originalName, wrapper.originalDescription, type));
	}
}
