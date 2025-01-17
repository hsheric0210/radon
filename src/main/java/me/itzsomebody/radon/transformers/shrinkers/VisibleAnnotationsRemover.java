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

import org.objectweb.asm.tree.ClassNode;

/**
 * Removes annotations visible to the runtime.
 *
 * @author ItzSomebody
 */
public class VisibleAnnotationsRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			final ClassNode classNode = classWrapper.classNode;

			if (classNode.visibleAnnotations != null)
			{
				counter.addAndGet(classNode.visibleAnnotations.size());
				classNode.visibleAnnotations = null;
			}

			classWrapper.fields.stream().filter(fieldWrapper -> included(fieldWrapper) && fieldWrapper.fieldNode.visibleAnnotations != null).forEach(fieldWrapper ->
			{
				counter.addAndGet(fieldWrapper.fieldNode.visibleAnnotations.size());
				fieldWrapper.fieldNode.visibleAnnotations = null;
			});

			classWrapper.methods.stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.methodNode.visibleAnnotations != null).forEach(methodWrapper ->
			{
				counter.addAndGet(methodWrapper.methodNode.visibleAnnotations.size());
				methodWrapper.methodNode.visibleAnnotations = null;
			});
		});

		info(String.format("- Removed %d visible annotations.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Visible Annotations Remover";
	}
}
