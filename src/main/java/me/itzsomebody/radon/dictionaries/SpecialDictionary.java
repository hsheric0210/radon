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

/**
 * @author hsheric0210
 */
public class SpecialDictionary extends SimpleDictionary
{
	private static final char[] CHARSET;

	static
	{
		CHARSET = new char[24];
		/*
		 * \u0021 - Exclamation Mark
		 * \u0022 - Quotation Mark
		 * \u0023 - Number Sign
		 * \u0024 - Dollar Sign
		 * \u0025 - Percent Sign
		 * \u0026 - Ampersand
		 * \u0027 - Apostrophe
		 * 
		 * \u002A - Asterisk
		 * \u002B - Plus Sign
		 * \u002C - Comma
		 * \u002D - Hyphen-Minus
		 * 
		 * \u003A - Colon
		 *
		 * \u003D - Equals Sign
		 *
		 * \u003F - Question Mark
		 *
		 * \u005C - Reverse Solidus
		 *
		 * \u005E - Circumflex Accent
		 * \u005F - Low Line
		 * \u0060 - Grave Accent
		 *
		 * \u007B - Left Curly Bracket
		 * \u007C - Vertical Line
		 * \u007D - Right Curly Bracket
		 * \u007E - Tilde
		 */

		for (int i = 0; i < 7; i++)
			CHARSET[i] = (char) ('\u0021' + i);

		for (int i = 0; i < 4; i++)
			CHARSET[i + 8] = (char) ('\u002A' + i);

		CHARSET[13] = ':';
		CHARSET[14] = '=';
		CHARSET[15] = '?';
		CHARSET[16] = '\\';

		for (int i = 0; i < 3; i++)
			CHARSET[i + 17] = (char) ('\u005E' + i);

		for (int i = 0; i < 4; i++)
			CHARSET[i + 20] = (char) ('\u007B' + i);
	}

	public SpecialDictionary()
	{
		super("special", CHARSET);
	}
}
