package me.itzsomebody.radon.dictionaries;

import me.itzsomebody.radon.utils.RandomUtils;

public class WrappedDictionary
{
	private final Dictionary dictionary;
	private final int minLength;
	private final int maxLength;

	public WrappedDictionary(final Dictionary dictionary, final int minLength, final int maxLength)
	{
		this.dictionary = dictionary;
		this.minLength = minLength;
		this.maxLength = maxLength;
	}

	@SuppressWarnings("unused")
	public final String getLastGeneratedString()
	{
		return dictionary.lastUniqueString();
	}

	public final String randomString()
	{
		return dictionary.randomString(RandomUtils.getRandomInt(minLength, maxLength));
	}

	public final String randomString(final int length)
	{
		return dictionary.randomString(length);
	}

	public final String uniqueRandomString()
	{
		return dictionary.uniqueRandomString(RandomUtils.getRandomInt(minLength, maxLength));
	}

	public final String nextUniqueString(final int index)
	{
		return dictionary.nextUniqueString(index, RandomUtils.getRandomInt(minLength, maxLength));
	}

	public final String nextUniqueString()
	{
		final String s = dictionary.nextUniqueString(RandomUtils.getRandomInt(minLength, maxLength));
		if (s == null || s.isEmpty())
			throw new IllegalStateException("Dictionary " + dictionary + ".nextUniqueString() thrown an empty string!!!");
		return s;
	}

	public final void reset()
	{
		dictionary.reset();
	}

	public final WrappedDictionary copy()
	{
		return new WrappedDictionary(dictionary.copy(), minLength, maxLength);
	}

	public final String toString()
	{
		return String.format("WrappedDictionary[dictionary=\"%s\",preferredLength=%d ~ %d]", dictionary.getDictionaryName(), minLength, maxLength);
	}
}
