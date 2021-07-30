package me.itzsomebody.radon.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cookiedragon234 02/Nov/2019
 */
public class StrSequence
{
	private final String[] sequence;

	public StrSequence(final CharSequence sequence)
	{
		this(sequence.toString().toCharArray());
	}

	public StrSequence(final char[] sequence)
	{

		this(new String(sequence).split(""));
	}

	public StrSequence(final String[] sequence)
	{
		this.sequence = sequence;
	}

	public StrSequence(final Iterable<? extends CharSequence> collection)
	{
		final List<String> strList = new ArrayList<>();
		for (final CharSequence charSequence : collection)
		{
			strList.add(charSequence.toString());
		}
		sequence = strList.toArray(new String[0]);
	}

	public int length()
	{
		return sequence.length;
	}

	public String strAt(final int index)
	{
		return sequence[index];
	}

	public StrSequence subSequence(final int start, final int end)
	{
		final String[] out = new String[end - start];
		System.arraycopy(sequence, start, out, 0, end - start);
		return new StrSequence(out);
	}

	public String[] getSequence()
	{
		return sequence;
	}

	@Override
	public String toString()
	{
		return String.join("", sequence);
	}
}
