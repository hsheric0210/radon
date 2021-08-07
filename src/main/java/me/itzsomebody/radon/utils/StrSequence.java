package me.itzsomebody.radon.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import me.itzsomebody.radon.exceptions.RadonException;

/**
 * @author cookiedragon234 02/Nov/2019
 */
public class StrSequence
{
	private final String[] sequence;
	private final boolean generateOrdered;

	public StrSequence(final CharSequence pattern)
	{
		sequence = fromPattern(pattern);
		generateOrdered = false;
	}

	private static String[] fromPattern(final CharSequence pattern)
	{
		// Calculate total count
		int totalCount = 0;
		for (int index = 0, stringLength = pattern.length(); index < stringLength;)
			// try to read [<startChar>-<endChar>] format
			if (pattern.length() >= 5 && pattern.charAt(index) == '[' && (index == 0 || pattern.charAt(index - 1) != '\\') && pattern.charAt(index + 4) == ']')
			{
				totalCount += pattern.charAt(index + 3) - pattern.charAt(index + 1) + 1;
				index += 5 /* "[A-B]".length() */;
			}
			else
			{
				totalCount++;
				index++;
			}

		// Combine dictionary to array
		final String[] strings = new String[totalCount];
		int stringIndex = 0;
		for (int index = 0, stringLength = pattern.length(); index < stringLength;)
			// try to read [<startChar>-<endChar>] format
			if (pattern.length() >= 5 && pattern.charAt(index) == '[' && (index == 0 || pattern.charAt(index - 1) != '\\') && pattern.charAt(index + 4) == ']')
			{
				for (char c = pattern.charAt(index + 1), end = pattern.charAt(index + 3); c <= end; c++)
					strings[stringIndex++] = new String(new char[]
					{
							c
					});
				index += 5;
			}
			else
			{
				strings[stringIndex++] = new String(new char[]
				{
						pattern.charAt(index)
				});
				index++;
			}
		return strings;
	}

	private StrSequence(final String[] sequence, final boolean generateOrdered)
	{
		this.sequence = sequence;
		this.generateOrdered = generateOrdered;
	}

	public StrSequence(final Collection<? extends CharSequence> collection, final boolean generateOrdered)
	{
		if (collection == null)
			throw new IllegalArgumentException(new NullPointerException("collection"));

		sequence = collection.stream().map(CharSequence::toString).toArray(String[]::new);
		this.generateOrdered = generateOrdered;
	}

	public StrSequence(final File file, final boolean generateOrdered) throws IOException
	{
		if (!file.exists())
			throw new FileNotFoundException(file.getPath());

		try (final FileInputStream fis = new FileInputStream(file); final InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8); final BufferedReader br = new BufferedReader(isr);)
		{
			final String[] strArr = br.lines().filter(line -> !line.isEmpty()).toArray(String[]::new);
			if (strArr.length < 1)
				throw new RadonException(String.format("Dictionary file '%s' is empty!", file.getPath()));

			if (strArr.length == 1)
			{
				sequence = fromPattern(strArr[0]);
				this.generateOrdered = false;
			}
			else
			{
				sequence = strArr;
				this.generateOrdered = generateOrdered;
			}
		}
	}

	public final int length()
	{
		return sequence.length;
	}

	public final String strAt(final int index)
	{
		return sequence[index];
	}

	public final StrSequence subSequence(final int start, final int end)
	{
		final String[] out = new String[end - start];
		System.arraycopy(sequence, start, out, 0, end - start);
		return new StrSequence(out, generateOrdered);
	}

	public final String[] getSequence()
	{
		return sequence;
	}

	public final boolean canGenerateOrdered()
	{
		return generateOrdered;
	}

	@Override
	public final String toString()
	{
		return String.join("", sequence);
	}
}
