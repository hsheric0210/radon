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

package me.itzsomebody.radon.asm;

import java.util.Map;
import java.util.Optional;

import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Custom implementation of ASM's SimpleRemapper taking in account for field descriptions.
 *
 * @author ItzSomebody
 */
public class MemberRemapper extends SimpleRemapper
{
	public MemberRemapper(final Map<String, String> mappings)
	{
		super(mappings);
	}

	// TODO: Upgrade remapper
	@Override
	public String mapFieldName(final String owner, final String name, final String desc)
	{
		final String remappedName = map(String.join(".", owner, name, desc));
		return Optional.ofNullable(remappedName).orElse(name);
	}
}
