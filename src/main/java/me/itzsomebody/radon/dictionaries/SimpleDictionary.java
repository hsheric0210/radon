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

package me.itzsomebody.radon.dictionaries;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import me.itzsomebody.radon.utils.RandomUtils;

/**
 * @author ItzSomebody, hsheric0210
 */
public class SimpleDictionary implements Dictionary
{
	private final String name;
	private final char[] charset;
	private final int charsetLength;

	private final Collection<String> cache = new HashSet<>();
	private int index;
	private int cachedLength;
	private String lastGenerated;

	public SimpleDictionary(final String name, final char[] charset)
	{
		this.name = name;
		this.charset = charset;
		charsetLength = charset.length;
	}

	@Override
	public String randomString(final int length)
	{
		final char[] chars = new char[length];

		for (int i = 0; i < length; i++)
			chars[i] = charset[RandomUtils.getRandomInt(charsetLength)];

		return new String(chars);
	}

	@Override
	public String uniqueRandomString(int length)
	{
		if (cachedLength > length)
			length = cachedLength;

		int tryCount = 0;
		String s;

		do
		{
			s = randomString(length);

			if (tryCount++ >= charsetLength)
			{
				length++;
				tryCount = 0;
			}
		}
		while (!cache.add(s));

		cachedLength = length;
		return s;
	}

	// FIXME
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] p
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] q
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] r
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] s
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] t
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] u
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] v
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] w
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] x
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] y
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] z
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 1
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 2
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 3
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 4
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 5
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 6
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 7
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 8
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 9
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] 0
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BA <- FIXME: Expected 'AA' but got 'BA' // Cause: A = 0, B = 1, C = 2 ...
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BB
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BC
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BD
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BE
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BF
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BG
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BH
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BI
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BJ
	// [10/08/2021-10:24:07] INFO: [VERBOSE] [Renamer] BK
	// Original code: https://github.com/Guardsquare/proguard/blob/master/base/src/main/java/proguard/obfuscate/SimpleNameFactory.java

	@Override
	public String nextUniqueString(final int index, final int length)
	{
		final int baseIndex = index / charsetLength;
		final int offset = index % charsetLength;

		final char newChar = charset[offset];

		return baseIndex == 0 ? String.valueOf(newChar) : nextUniqueString(baseIndex - 1, length) + newChar;
	}

	@Override
	public String nextUniqueString(final int length)
	{
		return lastGenerated = nextUniqueString(index++, length);
	}

	@Override
	public String lastUniqueString()
	{
		return lastGenerated;
	}

	@Override
	public String getDictionaryName()
	{
		return name;
	}

	@Override
	public void reset()
	{
		cache.clear();
		index = 0;
		lastGenerated = null;
	}

	@Override
	public Dictionary copy()
	{
		return new SimpleDictionary(name, Arrays.copyOf(charset, charset.length));
	}
}
