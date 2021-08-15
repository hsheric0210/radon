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

package me.itzsomebody.radon.transformers.obfuscators;

import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Original source code: https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/transformers/general/removers/IllegalVarargsRemover.java
 *
 * @author samczsun
 */
public class BadVarargs extends Transformer
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(this::included).forEach(mw ->
		{
			final Type[] params = Type.getArgumentTypes(mw.getDescription());
			if (params.length > 0 && params[params.length - 1].getSort() != Type.ARRAY)
			{
				mw.setAccessFlags(mw.getAccessFlags() | ACC_VARARGS);
				counter.incrementAndGet();
			}
		}));

		info("+ Added " + counter.get() + " bad varargs access flags");
	}

	@Override
	public String getName()
	{
		return "Bad Varargs";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.BAD_VARARGS;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
