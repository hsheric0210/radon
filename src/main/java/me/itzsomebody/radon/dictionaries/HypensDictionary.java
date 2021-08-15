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
 * Generates strings full of hyphens.
 *
 * @author hsheric0210
 */
public class HypensDictionary extends SimpleDictionary
{
	private static final char[] CHARSET;

	static
	{
		CHARSET = new char[15];
		/*
		 * \u002D - Hyphen-Minus
		 * \u2010 - Hyphen
		 * \u2011 - Non-Breaking Hyphen
		 * \u2012 - Figure Dash
		 * \u2013 - En Dash
		 * \u2014 - Em Dash
		 * \u2015 - Horizontal Bar
		 * \u2212 - Minus Sign
		 * \u2796 - Heavy Minus Sign
		 * \u2F00 - Kangxi Radical One
		 * \u30FC - Katakana-Hiragana Prolonged Sound Mark
		 * \u3161 - Hangul Letter Eu
		 * \u31D0 - CJK Stroke H
		 * \u4E00 - Ideograph one; a, an; alone CJK
		 * \uFF70 - Halfwidth Katakana-Hiragana Prolonged Sound Mark
		 */

		CHARSET[0] = '\u002D';
		for (int i = 0; i < 6; i++)
			CHARSET[i + 1] = (char) ('\u2010' + i);
		CHARSET[7] = '\u2212';
		CHARSET[8] = '\u2796';
		CHARSET[9] = '\u2F00';
		CHARSET[10] = '\u30FC';
		CHARSET[11] = '\u3161';
		CHARSET[12] = '\u31D0';
		CHARSET[13] = '\u4E00';
		CHARSET[14] = '\uFF70';
	}

	public HypensDictionary()
	{
		super("hyphens", CHARSET);
	}
}
