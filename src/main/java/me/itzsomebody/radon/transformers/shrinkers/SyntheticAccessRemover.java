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
 * Strips out synthetic/bridge access flags.
 *
 * @author ItzSomebody
 */
public class SyntheticAccessRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			if (cw.access.isSynthetic())
			{
				cw.setAccessFlags(cw.getAccessFlags() & ~ACC_SYNTHETIC);
				counter.incrementAndGet();
			}

			cw.methods.stream().filter(this::included).filter(mw -> mw.access.isSynthetic() || mw.access.isBridge()).forEach(mw ->
			{
				mw.setAccessFlags(mw.getAccessFlags() & ~(ACC_SYNTHETIC | ACC_BRIDGE));
				counter.incrementAndGet();
			});

			cw.fields.stream().filter(this::included).filter(fw -> fw.access.isSynthetic()).forEach(fw ->
			{
				fw.setAccessFlags(fw.getAccessFlags() & ~ACC_SYNTHETIC);
				counter.incrementAndGet();
			});
		});

		info(String.format("- Removed %d synthetic/bridge access flags.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Synthetic Access Remover";
	}
}
