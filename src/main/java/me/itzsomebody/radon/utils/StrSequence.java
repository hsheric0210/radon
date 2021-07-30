package me.itzsomebody.radon.utils;

import java.util.Collection;

/**
 * @author cookiedragon234 02/Nov/2019
 */
public class StrSequence
{
	private final String[] sequence;
	private final boolean generateOrdered;

	public StrSequence(final CharSequence sequence)
	{
		this(sequence.toString().toCharArray());
	}

	public StrSequence(final char[] sequence)
	{
		this(PredefinedPatterns.EMPTY.split(new String(sequence)), false);
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
