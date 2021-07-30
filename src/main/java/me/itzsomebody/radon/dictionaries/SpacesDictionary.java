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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Generates strings full of spaces.
 *
 * @author ItzSomebody
 */
public class SpacesDictionary implements Dictionary
{
	private static final char[] CHARSET = new char[0xF + 1];
	private final Collection<String> cache = new HashSet<>();
	private int index;
	private int cachedLength;
	private String lastGenerated;

	static
	{
		for (int i = 0, j = CHARSET.length; i < j; i++)
			CHARSET[i] = (char) ('\u2000' + i);
	}

	@Override
	public String randomString(final int length)
	{
		final char[] c = new char[length];

		for (int i = 0; i < length; i++)
			c[i] = CHARSET[RandomUtils.getRandomInt(CHARSET.length)];

		return new String(c);
	}

	@Override
	public String uniqueRandomString(int length)
	{
		if (cachedLength > length)
			length = cachedLength;

		int count = 0;
		final int arrLen = CHARSET.length;
		String s;

		do
		{
			s = randomString(length);

			if (count++ >= arrLen)
			{
				length++;
				count = 0;
			}
		}
		while (cache.contains(s));

		cache.add(s);
		cachedLength = length;
		return s;
	}

	@Override
	public String nextUniqueString()
	{
		// copy-pasted from Integer.toString(int i, int radix)
		final int charsetLength = CHARSET.length;
		int i = index;
		final char[] buf = new char[33];
		final boolean negative = i < 0;
		int charPos = 32;

		if (!negative)
			i = -i;

		while (i <= -charsetLength)
		{
			buf[charPos--] = CHARSET[-(i % charsetLength)];
			i /= charsetLength;
		}
		buf[charPos] = CHARSET[-i];

		final String s = new String(buf, charPos, 33 - charPos);
		lastGenerated = s;
		index++;
		return s;
	}

	@Override
	public String lastUniqueString()
	{
		return lastGenerated;
	}

	@Override
	public String getDictionaryName()
	{
		return "spaces";
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
		return new SpacesDictionary();
	}
}
