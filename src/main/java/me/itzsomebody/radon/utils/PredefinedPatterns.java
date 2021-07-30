package me.itzsomebody.radon.utils;

import java.util.regex.Pattern;

public interface PredefinedPatterns
{
	Pattern EMPTY = Pattern.compile("", Pattern.LITERAL);

	Pattern OPENING_BRACE = Pattern.compile(String.valueOf('(') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern CLOSING_BRACE = Pattern.compile(String.valueOf(')') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern SLASH = Pattern.compile("/", Pattern.LITERAL);
}
