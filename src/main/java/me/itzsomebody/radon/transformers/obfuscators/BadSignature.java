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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.LocalVariableNode;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;

/**
 * Sets the class signature to a random string. A known trick to work on JD, CFR, Procyon and Javap.
 *
 * @author ItzSomebody
 */
public class BadSignature extends Transformer
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();
		final WrappedDictionary dictionary = getGenericDictionary();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			cw.classNode.signature = dictionary.randomString();
			counter.incrementAndGet();

			cw.methods.stream().filter(mw -> included(mw) && !ASMUtils.hasAnnotations(mw.methodNode)).forEach(mw ->
			{
				final List<LocalVariableNode> locals = mw.methodNode.localVariables;
				if (locals != null && !locals.isEmpty())
					for (final LocalVariableNode node : locals)
						node.signature = dictionary.randomString();

				mw.methodNode.signature = dictionary.randomString();
				counter.incrementAndGet();
			});

			cw.fields.stream().filter(fw -> included(fw) && !ASMUtils.hasAnnotations(fw.fieldNode)).filter(fw -> !fw.access.isSynthetic()).forEach(fw ->
			{
				fw.fieldNode.signature = dictionary.randomString();
				counter.incrementAndGet();
			});
		});

		info(String.format("+ Added %d bad signatures.", counter.get()));
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.BAD_SIGNATURE;
	}

	@Override
	public String getName()
	{
		return "Bad Signature";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
	}
}
