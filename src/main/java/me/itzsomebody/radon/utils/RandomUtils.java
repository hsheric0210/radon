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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

	public static int getRandomInt(final int endExclusive)
	{
		if (!(endExclusive > 0))
			throw new IllegalArgumentException("bound " + endExclusive + " is zero or negative");
		if (endExclusive == 1)
			return 0;
		return ThreadLocalRandom.current().nextInt(endExclusive);
	}

	public static int getRandomInt(final int startInclusive, final int endExclusive)
	{
		return startInclusive == endExclusive ? endExclusive : ThreadLocalRandom.current().nextInt(startInclusive, endExclusive);

	}

	public static boolean getRandomBoolean()
	{
		return ThreadLocalRandom.current().nextBoolean();
	}

	public static <T> T getRandomElement(final List<T> list)
	{
		return list.get(getRandomInt(list.size()));
	}

	@SafeVarargs
	public static <T> T getRandomElement(final T... arr)
	{
		return arr[getRandomInt(arr.length)];
	}

	public static int getRandomIntWithExclusion(final int startInclusive, final int endExclusive, final Collection<Integer> exclusions)
	{
		final List<Integer> list = IntStream.range(startInclusive, endExclusive).boxed().collect(Collectors.toList());
		list.removeAll(exclusions);
		return getRandomElement(list);
	}

	public static long getRandomLong()
	{
		return ThreadLocalRandom.current().nextLong();
	}

	public static long getRandomLong(final long endExclusive)
	{
		if (!(endExclusive > 0))
			throw new IllegalArgumentException("bound " + endExclusive + " is zero or negative");
		return ThreadLocalRandom.current().nextLong(endExclusive);
	}

	public static long getRandomLong(final long startInclusive, final long endExclusive)
	{
		return startInclusive == endExclusive ? startInclusive : ThreadLocalRandom.current().nextLong(startInclusive, endExclusive);

	}

	public static float getRandomFloat()
	{
		return ThreadLocalRandom.current().nextFloat();
	}

	public static float getRandomFloat(final float endExclusive)
	{
		if (!(endExclusive > 0.0F))
			throw new IllegalArgumentException("bound " + endExclusive + " is zero or negative");
		return (float) ThreadLocalRandom.current().nextDouble(endExclusive);
	}

	public static float getRandomFloat(final float startInclusive, final float endExclusive)
	{
		return startInclusive == endExclusive ? startInclusive : (float) ThreadLocalRandom.current().nextDouble(startInclusive, endExclusive);
	}

	public static double getRandomDouble()
	{
		return ThreadLocalRandom.current().nextDouble();
	}

	public static double getRandomDouble(final double endExclusive)
	{
		if (!(endExclusive > 0.0))
			throw new IllegalArgumentException("bound " + endExclusive + " is zero or negative");
		return ThreadLocalRandom.current().nextDouble(endExclusive);
	}

	public static double getRandomDouble(final double startInclusive, final double endExclusive)
	{
		return startInclusive == endExclusive ? startInclusive : ThreadLocalRandom.current().nextDouble(startInclusive, endExclusive);
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

	private RandomUtils()
	{
	}
}
