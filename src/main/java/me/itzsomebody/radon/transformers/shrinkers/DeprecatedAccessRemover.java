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

package me.itzsomebody.radon.transformers.shrinkers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strips out deprecated access flags.
 *
 * @author ItzSomebody
 */
public class DeprecatedAccessRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			if (cw.access.isDeprecated())
			{
				cw.setAccessFlags(cw.getAccessFlags() & ~ACC_DEPRECATED);
				counter.incrementAndGet();
			}

			cw.methods.stream().filter(mw -> included(mw) && mw.access.isDeprecated()).forEach(mw ->
			{
				mw.setAccessFlags(mw.getAccessFlags() & ~ACC_DEPRECATED);
				counter.incrementAndGet();
			});

			cw.fields.stream().filter(fw -> included(fw) && fw.access.isDeprecated()).forEach(fw ->
			{
				fw.setAccessFlags(fw.getAccessFlags() & ~ACC_DEPRECATED);
				counter.incrementAndGet();
			});
		});

		info(String.format("- Removed %d deprecated access flags.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Useless Access Flags Remover";
	}
}
