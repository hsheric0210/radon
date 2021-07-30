package me.itzsomebody.radon.utils;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.regex.Pattern;

public interface Constants
{
	String LINE_SEPARATOR = System.lineSeparator();

	Pattern EMPTY_PATTERN = Pattern.compile("", Pattern.LITERAL);

	Pattern OPENING_BRACE_PATTERN = Pattern.compile(String.valueOf('(') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern CLOSING_BRACE_PATTERN = Pattern.compile(String.valueOf(')') /* Workaround do not simplify */, Pattern.LITERAL);
	Pattern SLASH_PATTERN = Pattern.compile("/", Pattern.LITERAL);
	Pattern VIRTCALL_PARAMETER_DELIMITER_PATTERN = Pattern.compile("\u0001\u0001", Pattern.LITERAL);

	Class[] ZERO_LENGTH_CLASS_ARRAY = new Class[0];
	String[] ZERO_LENGTH_STRING_ARRAY = new String[0];
	Attribute[] ZERO_SIZE_ATTRIBUTE_ARRAY = new Attribute[0];
	ArrayList[] ZERO_LENGTH_LIST_ARRAY = new ArrayList[0];
	Type[] ZERO_SIZE_TYPE_ARRAY = new Type[0];
}
