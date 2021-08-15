package me.itzsomebody.radon.utils;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public final class Strings
{
	public static final String START_LOAD_CP = ": -------------- -{ Start loading class-paths }- -------------- :";
	public static final String END_LOAD_CP = ": -------------- -{ End loading class-paths }- -------------- :";

	public static final String START_LOAD_INPUT = ": -------------- -{ Start loading input-file }- -------------- :";
	public static final String END_LOAD_INPUT = ": -------------- -{ End loading input-file }- -------------- :";

	public static final String START_EXECUTION = ": -------------- -{ Execution start }- -------------- :";
	public static final String EXECUTION_SEPARATOR = "-------------- -( * )- --------------";
	public static final String END_EXECUTION = ": -------------- -{ Execution end }- -------------- :";

	public static final String START_WRITING = ": -------------- -{ Start writing output-file }- -------------- :";
	public static final String END_WRITING = ": -------------- -{ End writing output-file }- -------------- :";

	public static String stringToHexBytes(final String str)
	{
		final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		final StringBuilder builder = new StringBuilder(bytes.length * 5 - 1);
		for (final byte b : bytes)
			builder.append(' ').append(intToHexByte(b, 4));
		return builder.substring(1);
	}

	public static String intToHexByte(final int i, final int leadingZeroCount)
	{
		final StringBuilder builder = new StringBuilder(2 + leadingZeroCount);
		builder.append("0x");
		final String hexStr = Integer.toHexString(i);
		for (int j = 0, k = leadingZeroCount - hexStr.length(); j < k; j++)
			builder.append('0');
		builder.append(hexStr);
		return builder.toString();
	}

	public static String serializeOrder(final int[] order)
	{
		final StringJoiner identifierOrderBuilder = new StringJoiner(", ", "[", "]");
		for (final int i : order)
			identifierOrderBuilder.add(Integer.toString(i));
		return identifierOrderBuilder.toString();
	}

	private Strings()
	{
	}
}
