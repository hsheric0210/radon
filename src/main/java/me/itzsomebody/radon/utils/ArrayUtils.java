package me.itzsomebody.radon.utils;

import java.lang.reflect.Array;
import java.util.Random;
import java.util.stream.IntStream;

public final class ArrayUtils
{
	private static Random random;

	public static void swap(final int[] arr, final int index1, final int index2)
	{
		if (index1 == index2)
			return;

		if (arr.length <= index1)
			throw new ArrayIndexOutOfBoundsException("Array index1 out of range: " + index1);
		if (arr.length <= index2)
			throw new ArrayIndexOutOfBoundsException("Array index2 out of range: " + index2);

		final int index2Element = arr[index2];
		arr[index2] = arr[index1];
		arr[index1] = index2Element;
	}

	public static <T> void swap(final T[] arr, final int index1, final int index2)
	{
		if (index1 == index2)
			return;

		if (arr.length <= index1)
			throw new ArrayIndexOutOfBoundsException("Array index1 out of range: " + index1);
		if (arr.length <= index2)
			throw new ArrayIndexOutOfBoundsException("Array index2 out of range: " + index2);

		final T index2Element = arr[index2];
		arr[index2] = arr[index1];
		arr[index1] = index2Element;
	}

	public static void shuffle(final int[] arr)
	{
		Random r = random;
		if (r == null)
			random = r = new Random();
		shuffle(arr, r);
	}

	public static void shuffle(final int[] arr, final Random random)
	{
		for (int i = arr.length; i > 1; i--)
			swap(arr, i - 1, random.nextInt(i));
	}

	public static int[] intArrayOf(final int startInclusive, final int endExclusive)
	{
		final int[] arr = new int[endExclusive - startInclusive];
		for (int i = startInclusive; i < endExclusive; i++)
			arr[i] = i;
		return arr;
	}

	public static int[] randomIntArrayOf(final int startInclusive, final int endExclusive)
	{
		final int[] arr = intArrayOf(startInclusive, endExclusive);
		shuffle(arr);
		return arr;
	}

	public static int indexOf(final int[] arr, final int value)
	{
		for (int i = 0, j = arr.length; i < j; i++)
			if (arr[i] == value)
				return i;
		return -1;
	}

	public static int indexOf(final char[] arr, final char value)
	{
		for (int i = 0, j = arr.length; i < j; i++)
			if (arr[i] == value)
				return i;
		return -1;
	}

	public static <T> int indexOf(final T[] arr, final T value)
	{
		for (int i = 0, j = arr.length; i < j; i++)
			if (arr[i] == value)
				return i;
		return -1;
	}

	public static void increment(final char[] arr, final int offset, final int length, final int inc)
	{
		for (int i = offset; i < length; i++)
			arr[i] += inc;
	}

	public static <T> T[] reorder(final T[] arr, final int[] order, final Class<T> clazz)
	{
		// Check array length
		final int length = arr.length;
		if (length <= 0)
			throw new IllegalArgumentException("Empty array");
		if (length != order.length)
			throw new IllegalArgumentException("Array length mismatch");

		return IntStream.range(0, length).mapToObj(i -> arr[order[i]]).toArray(i -> (T[]) Array.newInstance(clazz, i));
	}

	public static int[] reorder(final int[] arr, final int[] order)
	{
		// Check array length
		final int length = arr.length;
		if (length <= 0)
			throw new IllegalArgumentException("Empty array");
		if (length != order.length)
			throw new IllegalArgumentException("Array length mismatch");

		return IntStream.range(0, length).map(i -> arr[order[i]]).toArray();
	}

	public static int[] toIndexArray(final int[] arr)
	{
		final int length = arr.length;
		if (length <= 0)
			throw new IllegalArgumentException("Empty array");
		return IntStream.range(0, length).map(i -> indexOf(arr, i)).toArray();
	}

	private ArrayUtils()
	{

	}
}
