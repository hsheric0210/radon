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

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.utils.Constants;
import org.objectweb.asm.Attribute;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Removes all unknown attributes from the classes.
 *
 * @author ItzSomebody
 */
public class UnknownAttributesRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(classWrapper -> included(classWrapper) && classWrapper.getClassNode().attrs != null).map(ClassWrapper::getClassNode).forEach(classNode -> Stream.of(classNode.attrs.toArray(Constants.EMPTY_ATTRIBUTE_ARRAY)).filter(Attribute::isUnknown).forEach(attr ->
		{
			classNode.attrs.remove(attr);
			counter.incrementAndGet();
		}));

		info(String.format("- Removed %d attributes.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Attributes Remover";
	}
}
