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

package me.itzsomebody.radon.analysis.constant.values;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Objects;

public final class ConstantValue extends AbstractValue
{
	private final Object value;

	private ConstantValue(final AbstractInsnNode insnNode, final Type type, final Object value)
	{
		super(insnNode, type);
		this.value = value;
	}

	public static ConstantValue fromInteger(final AbstractInsnNode insnNode, final int value)
	{
		return new ConstantValue(insnNode, Type.INT_TYPE, value);
	}

	public static ConstantValue fromLong(final AbstractInsnNode insnNode, final long value)
	{
		return new ConstantValue(insnNode, Type.LONG_TYPE, value);
	}

	public static ConstantValue fromFloat(final AbstractInsnNode insnNode, final float value)
	{
		return new ConstantValue(insnNode, Type.FLOAT_TYPE, value);
	}

	public static ConstantValue fromDouble(final AbstractInsnNode insnNode, final double value)
	{
		return new ConstantValue(insnNode, Type.DOUBLE_TYPE, value);
	}

	public static ConstantValue fromString(final AbstractInsnNode insnNode, final String value)
	{
		return new ConstantValue(insnNode, Type.getType(String.class), value);
	}

	public int intValue()
	{
		return (Integer) value;
	}

	public long longValue()
	{
		return (Long) value;
	}

	public float floatValue()
	{
		return (Float) value;
	}

	public double doubleValue()
	{
		return (Double) value;
	}

	public String stringValue()
	{
		return (String) value;
	}

	public Object getValue()
	{
		return value;
	}

	@Override
	public boolean isConstant()
	{
		return true;
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o) return true;
		if (!(o instanceof ConstantValue)) return false;
		if (!super.equals(o)) return false;
		final ConstantValue that = (ConstantValue) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(super.hashCode(), value);
	}
}
