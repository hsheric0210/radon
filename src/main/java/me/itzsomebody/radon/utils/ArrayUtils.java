package me.itzsomebody.radon.utils;

import java.util.Random;

public final class ArrayUtils
{
	private static Random random;

	public static void swap(final int[] arr, final int index1, final int index2)
	{
		if (arr.length <= index1 || arr.length <= index2)
			return;

		final int index2Element = arr[index2];
		arr[index2] = arr[index1];
		arr[index1] = index2Element;
	}

	public static <T> void swap(final T[] arr, final int index1, final int index2)
	{
		if (arr.length <= index1 || arr.length <= index2)
			return;

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

	private ArrayUtils()
	{

	}
}
