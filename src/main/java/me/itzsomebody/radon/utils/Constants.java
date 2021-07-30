package me.itzsomebody.radon.utils;

import java.util.regex.Pattern;

public interface Constants
{
	Pattern EMPTY_PATTERN = Pattern.compile("", Pattern.LITERAL);

	Pattern OPENING_BRACE_PATTERN = Pattern.compile(String.valueOf('(') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern CLOSING_BRACE_PATTERN = Pattern.compile(String.valueOf(')') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern SLASH_PATTERN = Pattern.compile("/", Pattern.LITERAL);
	Pattern VIRTCALL_PARAMETER_DELIMITER_PATTERN = Pattern.compile("\u0001\u0001", Pattern.LITERAL);

	Class[] ZERO_LENGTH_CLASS_ARRAY = new Class[0];
}
