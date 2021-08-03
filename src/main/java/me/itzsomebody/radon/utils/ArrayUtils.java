package me.itzsomebody.radon.utils;

public final class ArrayUtils
{
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

	private ArrayUtils()
	{

	}
}
