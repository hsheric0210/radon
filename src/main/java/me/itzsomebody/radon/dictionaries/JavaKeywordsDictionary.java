package me.itzsomebody.radon.dictionaries;

import java.util.Collection;
import java.util.HashSet;

import me.itzsomebody.radon.utils.RandomUtils;

/**
 * https://en.wikipedia.org/wiki/List_of_Java_keywords
 * 
 * @author hsheric0210
 */
public class JavaKeywordsDictionary implements Dictionary
{
	private static final String[] NAMES =
	{
			"abstract",
			"assert",
			"boolean",
			"break",
			"byte",
			"case",
			"catch",
			"char",
			"class",
			"const",
			"continue",
			"default",
			"do",
			"double",
			"else",
			"enum",
			"extends",
			"final",
			"finally",
			"float",
			"for",
			"goto",
			"if",
			"implements",
			"import",
			"instanceof",
			"int",
			"interface",
			"long",
			"native",
			"new",
			"package",
			"private",
			"protected",
			"public",
			"return",
			"abstract",
			"abstract",
			"short",
			"static",
			"strictfp",
			"super",
			"switch",
			"synchronized",
			"this",
			"throw",
			"throws",
			"transient",
			"try",
			"void",
			"volatile",
			"while",

			"permits",
			"record",
			"sealed",
			"var",
			"yield",

			"true",
			"false",
			"null",

			"@interface",
			"_"
	};

	private int index;
	private int loop;

	private final Collection<String> cache = new HashSet<>();
	private String lastGenerated;

	@Override
	public String randomString(final int length)
	{
		StringBuilder out = new StringBuilder();
		do
			out.append(NAMES[RandomUtils.getRandomInt(NAMES.length)]);
		while (out.length() < length);

		if (out.length() > length)
			out = new StringBuilder(out.substring(0, length));

		return out.toString();
	}

	@Override
	public String uniqueRandomString(final int length)
	{
		String s;
		do
			s = randomString(length);
		while (!cache.add(s));

		return s;
	}

	public String nextUniqueString0(final int index, final int loop)
	{
		return NAMES[index] + (loop > 0 ? Integer.toString(loop) : "");
	}

	@Override
	public String nextUniqueString(final int index, final int length)
	{
		return nextUniqueString0(index % NAMES.length, index);
	}

	@Override
	public String nextUniqueString(final int length)
	{
		if (index >= NAMES.length)
		{
			index = 0;
			loop++;
		}
		lastGenerated = nextUniqueString0(index, loop);
		index++;
		return lastGenerated;
	}

	@Override
	public String lastUniqueString()
	{
		return lastGenerated;
	}

	@Override
	public String getDictionaryName()
	{
		return "javakeywords";
	}

	@Override
	public void reset()
	{
		index = 0;
		loop = 0;
	}

	@Override
	public Dictionary copy()
	{
		return new JavaKeywordsDictionary();
	}
}
