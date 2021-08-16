/*
 * Radon - An open-source Java obfuscator
 * Copyright (C) 2019 ItzSomebody
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package me.itzsomebody.radon.dictionaries;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import me.itzsomebody.radon.Main;

public final class DictionaryFactory
{
	public static Dictionary get(final String s)
	{
		final Dictionary[] dictionaries =
		{
				new AlphaNumDictionary(), new RandomUnicodeDictionary(), new SpacesDictionary(), new UnrecognizedDictionary(), new CreeperDictionary(), new AlphabetDictionary(), new NumericDictionary(), new HypensDictionary(), new SpecialDictionary(), new ReservedDeviceNamesDictionary(), new JavaKeywordsDictionary()
		};

		for (final Dictionary dictionary : dictionaries)
			if (dictionary.getDictionaryName().equals(s))
				return dictionary;

		if (s.toLowerCase(Locale.ENGLISH).startsWith("file:"))
		{
			final String filePath = s.substring(/* "file:".length() */ 5);
			try
			{
				return new CustomDictionary(new File(filePath));
			}
			catch (final IOException ioe)
			{
				Main.severe(String.format("Failed to load dictionary file '%s'", filePath), ioe);
			}
		}

		return new CustomDictionary(s);
	}

	public static Dictionary getCustom(final List<String> charset)
	{
		return new CustomDictionary(charset);
	}

	private DictionaryFactory()
	{
	}
}
