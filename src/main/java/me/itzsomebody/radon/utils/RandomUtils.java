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

package me.itzsomebody.radon.utils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.objectweb.asm.Type;

/**
 * Used to generate various randoms.
 *
 * @author ItzSomebody
 * @author freeasbird
 */
public final class RandomUtils
{
	public static int getRandomInt()
	{
		return ThreadLocalRandom.current().nextInt();
	}

	public static int getRandomInt(final int bounds)
	{
		if (!(bounds > 0))
			throw new IllegalArgumentException("bound " + bounds + " is zero or negative");
		return ThreadLocalRandom.current().nextInt(bounds);
	}

	public static int getRandomInt(final int origin, final int bounds)
	{
		return origin == bounds ? bounds : ThreadLocalRandom.current().nextInt(origin, bounds);

	}

	public static boolean getRandomBoolean()
	{
		return getRandomFloat() > 0.5;
	}

	public static <T> T getRandomElement(final List<T> list)
	{
		return list.get(getRandomInt(list.size()));
	}

	public static int getRandomIntWithExclusion(final int origin, final int bounds, final Collection<Integer> exclusions)
	{
		return ThreadLocalRandom.current().ints(origin, bounds).unordered().filter(value -> !exclusions.contains(value)).findFirst().getAsInt();
	}

	public static long getRandomLong()
	{
		return ThreadLocalRandom.current().nextLong();
	}

	public static long getRandomLong(final long bounds)
	{
		if (!(bounds > 0))
			throw new IllegalArgumentException("bound " + bounds + " is zero or negative");
		return ThreadLocalRandom.current().nextLong(bounds);
	}

	public static long getRandomLong(final long origin, final long bounds)
	{
		return origin == bounds ? origin : ThreadLocalRandom.current().nextLong(origin, bounds);

	}

	public static float getRandomFloat()
	{
		return ThreadLocalRandom.current().nextFloat();
	}

	public static float getRandomFloat(final float bounds)
	{
		if (!(bounds > 0.0F))
			throw new IllegalArgumentException("bound " + bounds + " is zero or negative");
		return (float) ThreadLocalRandom.current().nextDouble(bounds);
	}

	public static float getRandomFloat(final float origin, final float bounds)
	{
		return origin == bounds ? origin : (float) ThreadLocalRandom.current().nextDouble(origin, bounds);
	}

	public static double getRandomDouble()
	{
		return ThreadLocalRandom.current().nextDouble();
	}

	public static double getRandomDouble(final double bounds)
	{
		if (!(bounds > 0.0))
			throw new IllegalArgumentException("bound " + bounds + " is zero or negative");
		return ThreadLocalRandom.current().nextDouble(bounds);
	}

	public static double getRandomDouble(final double origin, final double bounds)
	{
		return origin == bounds ? origin : ThreadLocalRandom.current().nextDouble(origin, bounds);
	}

	public static Object getRandomValue(final Type type)
	{
		switch (type.getSort())
		{
			case Type.BOOLEAN:
				return getRandomInt(0, 2);
			case Type.CHAR:
				return getRandomInt(Character.MIN_VALUE, Character.MAX_VALUE);
			case Type.BYTE:
				return getRandomInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
			case Type.SHORT:
				return getRandomInt(Short.MIN_VALUE, Short.MAX_VALUE);
			case Type.FLOAT:
				return getRandomFloat();
			case Type.LONG:
				return getRandomLong();
			case Type.DOUBLE:
				return getRandomDouble();
			default:
				return getRandomInt();
		}
	}
}
