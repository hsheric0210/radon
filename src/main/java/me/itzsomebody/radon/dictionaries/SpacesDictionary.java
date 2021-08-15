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
 * Generates strings full of spaces.
 *
 * @author ItzSomebody
 */
public class SpacesDictionary extends SimpleDictionary
{
	private static final char[] CHARSET;

	static
	{
		CHARSET = new char[21];
		/*
		 * \u115F - Hangul Choseong Filler
		 * \u2000 - En Quad
		 * \u2001 - Em Quad
		 * \u2002 - En Space
		 * \u2003 - Em Space
		 * \u2004 - Three-Per-Em Space
		 * \u2005 - Four-Per-Em Space
		 * \u2006 - Six-Per-Em Space
		 * \u2007 - Figure Space
		 * \u2008 - Punctuation Space
		 * \u2009 - Thin Space
		 * \u200A - Hair Space
		 * \u200B - Zero Width Space
		 * \u200C - Zero Width Non-Joiner
		 * \u200D - Zero Width Joiner
		 * \u200E - Left-To-Right Mark
		 * \u200F - Right-To-Left Mark
		 * \u205F - Medium Mathematical Space
		 * \u3000 - Ideographic Space
		 * \u3164 - Hangul Filler
		 * \uFEFF - Zero Width No-Break Space
		 */

		CHARSET[0] = '\u115F';
		for (int i = 0; i < 16; i++)
			CHARSET[i + 1] = (char) ('\u2000' + i);
		CHARSET[17] = '\u205F';
		CHARSET[18] = '\u3000';
		CHARSET[19] = '\u3164';
		CHARSET[20] = '\uFEFF';
	}

	public SpacesDictionary()
	{
		super("spaces", CHARSET);
	}
}
