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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import me.itzsomebody.radon.utils.RandomUtils;
import me.itzsomebody.radon.utils.StrSequence;

/**
 * Generates strings based on custom user-defined dictionary.
 *
 * @author ItzSomebody
 */
public class CustomDictionary implements Dictionary
{
	private final StrSequence CHARSET;
	private final Collection<String> cache = new HashSet<>();
	private int cachedLength;
	private String lastGenerated;

	public CustomDictionary(final String charset)
	{
		this(new StrSequence(charset));
	}

	public CustomDictionary(final List<String> charset)
	{
		this(new StrSequence(charset, true));
		charset.remove(0); // ?
	}

	private CustomDictionary(final StrSequence strSequence)
	{
		CHARSET = strSequence;
	}

	public CustomDictionary(final File dictionaryFile) throws IOException
	{
		this(new StrSequence(dictionaryFile, true));
	}

	@Override
	public final String randomString(final int length)
	{
		return String.join("", IntStream.range(0, length).mapToObj(i -> CHARSET.strAt(RandomUtils.getRandomInt(CHARSET.length()))).toArray(String[]::new));
	}

	@Override
	public final String uniqueRandomString(int length)
	{
		if (cachedLength > length)
			length = cachedLength;

		int count = 0;
		final int arrLen = CHARSET.length();
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
		lastGenerated = s;
		return s;
	}

	@Override
	public final String nextUniqueString()
	{
		return uniqueRandomString(cachedLength);
	}

	/**
	 * @param  index
	 *                 A unique positive integer
	 * @param  charset
	 *                 A dictionary to permutate through
	 *
	 * @return         A unique string from for the given integer using permutations of the given charset
	 */
	private static String intToStr(int index, final StrSequence charset)
	{
		final String[] buf;
		try
		{
			buf = new String[100];
		}
		catch (final OutOfMemoryError e)
		{
			e.printStackTrace();
			return "";
		}

		int charPos = 99;

		index = -index; // Negate

		final int charsetLength = charset.length();
		while (index <= -charsetLength)
		{
			buf[charPos--] = charset.strAt(-(index % charsetLength));
			index /= charsetLength;
		}
		buf[charPos] = charset.strAt(-index);

		final String[] out = new String[100 - charPos];
		System.arraycopy(buf, charPos, out, 0, 100 - charPos);
		return String.join("", out);
	}

	@Override
	public final String lastUniqueString()
	{
		return lastGenerated;
	}

	@Override
	public final String getDictionaryName()
	{
		return CHARSET.toString();
	}

	@Override
	public final void reset()
	{
		cache.clear();
		lastGenerated = null;
	}

	@Override
	public final Dictionary copy()
	{
		return new CustomDictionary(CHARSET);
	}

	private static final char chooseChar(final String pattern)
	{
		final List<Entry<Character, Double>> candidates = new ArrayList<>();
		final int stringLength = pattern.length();
		int totalCount = 0;

		// Calculate total count
		for (int index = 0; index < stringLength;)
			// try to read [<startChar>-<endChar>] format
			if (pattern.length() >= 5 && pattern.charAt(index) == '[' && (index == 0 || pattern.charAt(index - 1) != '\\') && pattern.charAt(index + 4) == ']')
			{
				totalCount += pattern.charAt(index + 3) - pattern.charAt(index + 1) + 1;
				index += 5;
			}
			else
			{
				totalCount++;
				index++;
			}

		// Register candidates
		for (int index = 0; index < stringLength;)
			if (pattern.length() >= 5 && pattern.charAt(index) == '[' && (index == 0 || pattern.charAt(index - 1) != '\\') && pattern.charAt(index + 2) == '-' && pattern.charAt(index + 4) == ']')
			{
				final char start = pattern.charAt(index + 1);
				final char end = pattern.charAt(index + 3);
				final int length = end - start + 1;

				candidates.add(new SimpleImmutableEntry<>((char) (start + RandomUtils.getRandomInt(length)), /* 1.0 / partCount */(double) length / totalCount));
				index += 5;
			}
			else
			{
				candidates.add(new SimpleImmutableEntry<>(pattern.charAt(index), 1.0 / stringLength));
				index++;
			}

		if (candidates.isEmpty())
			return '\0';

		candidates.sort(Comparator.comparingDouble(Entry::getValue));

		// 출처: https://skyfe.tistory.com/entry/확률을-적용한-랜덤값-선택하기
		final double random = RandomUtils.getRandomDouble();
		double cumulative = 0.0;
		for (final Entry<Character, Double> candidate : candidates)
		{
			cumulative += candidate.getValue();
			if (random <= cumulative)
				return candidate.getKey();
		}

		return '\0'; // Dead code
	}
}
