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
 * Removes annotations invisible to the runtime from classes, methods and fields.
 *
 * @author ItzSomebody
 */
public class InvisibleAnnotationsRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			final ClassNode classNode = classWrapper.classNode;

			if (classNode.invisibleAnnotations != null)
			{
				counter.addAndGet(classNode.invisibleAnnotations.size());
				classNode.invisibleAnnotations = null;
			}

			classWrapper.fields.stream().filter(fieldWrapper -> included(fieldWrapper) && fieldWrapper.fieldNode.invisibleAnnotations != null).forEach(fieldWrapper ->
			{
				counter.addAndGet(fieldWrapper.fieldNode.invisibleAnnotations.size());
				fieldWrapper.fieldNode.invisibleAnnotations = null;
			});

			classWrapper.methods.stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.methodNode.invisibleAnnotations != null).forEach(methodWrapper ->
			{
				counter.addAndGet(methodWrapper.methodNode.invisibleAnnotations.size());
				methodWrapper.methodNode.invisibleAnnotations = null;
			});
		});

		info(String.format("- Removed %d invisible annotations.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Invisible Annotations Remover";
	}
}
