package me.itzsomebody.radon.dictionaries;

import java.util.Collection;
import java.util.HashSet;

import me.itzsomebody.radon.utils.RandomUtils;

/**
 * https://en.wikipedia.org/wiki/DOS#Reserved_device_names
 * 
 * @author hsheric0210
 */
public class ReservedDeviceNamesDictionary implements Dictionary
{
	private static final String[] NAMES =
	{
			// nul
			"nul",
			"Nul",
			"nUl",
			"nuL",
			"NUl",
			"nUL",
			"NuL",
			"NUL",

			// aux
			"aux",
			"Aux",
			"aUx",
			"auX",
			"AUx",
			"aUX",
			"AuX",
			"AUX",

			// con
			"con",
			"Con",
			"cOn",
			"coN",
			"COn",
			"cON",
			"CoN",
			"CON",

			// prn
			"prn",
			"Prn",
			"pRn",
			"prN",
			"PRn",
			"pRN",
			"PrN",
			"PRN",

			// com1
			"com1",
			"Com1",
			"cOm1",
			"coM1",
			"COm1",
			"cOM1",
			"CoM1",
			"COM1",

			// com2
			"com2",
			"Com2",
			"cOm2",
			"coM2",
			"COm2",
			"cOM2",
			"CoM2",
			"COM2",

			// com3
			"com3",
			"Com3",
			"cOm3",
			"coM3",
			"COm3",
			"cOM3",
			"CoM3",
			"COM3",

			// com4
			"com4",
			"Com4",
			"cOm4",
			"coM4",
			"COm4",
			"cOM4",
			"CoM4",
			"COM4",

			// lpt1
			"lpt1",
			"Lpt1",
			"lPt1",
			"lpT1",
			"LPt1",
			"lPT1",
			"LpT1",
			"LPT1",

			// lpt2
			"lpt2",
			"Lpt2",
			"lPt2",
			"lpT2",
			"LPt2",
			"lPT2",
			"LpT2",
			"LPT2",

			// lpt3
			"lpt3",
			"Lpt3",
			"lPt3",
			"lpT3",
			"LPt3",
			"lPT3",
			"LpT3",
			"LPT3",

			// lpt4
			"lpt4",
			"Lpt4",
			"lPt4",
			"lpT4",
			"LPt4",
			"lPT4",
			"LpT4",
			"LPT4"
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
		return "reservednames";
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
		return new ReservedDeviceNamesDictionary();
	}
}