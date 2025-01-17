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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Adds {@code @} annotation to all methods Fernflower refuses to decompile the class.
 * WARNING: Java will crash on attempt to parse annotations.
 *
 * @author xDark
 */
public class BadAnnotation extends Transformer
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final ClassNode cn = cw.classNode;

			if (cn.visibleAnnotations == null)
				cn.visibleAnnotations = new ArrayList<>();
			if (cn.invisibleAnnotations == null)
				cn.invisibleAnnotations = new ArrayList<>();

			cn.visibleAnnotations.add(new AnnotationNode("@"));
			cn.invisibleAnnotations.add(new AnnotationNode("@"));

			counter.incrementAndGet();

			cw.methods.stream().filter(this::included).forEach(mw ->
			{
				final MethodNode mn = mw.methodNode;

				if (mn.visibleAnnotations == null)
					mn.visibleAnnotations = new ArrayList<>();
				if (mn.invisibleAnnotations == null)
					mn.invisibleAnnotations = new ArrayList<>();

				mn.visibleAnnotations.add(new AnnotationNode("@"));
				mn.invisibleAnnotations.add(new AnnotationNode("@"));

				counter.incrementAndGet();
			});

			cw.fields.stream().filter(this::included).forEach(fw ->
			{
				final FieldNode fn = fw.fieldNode;

				if (fn.visibleAnnotations == null)
					fn.visibleAnnotations = new ArrayList<>();
				if (fn.invisibleAnnotations == null)
					fn.invisibleAnnotations = new ArrayList<>();

				fn.visibleAnnotations.add(new AnnotationNode("@"));
				fn.invisibleAnnotations.add(new AnnotationNode("@"));

				counter.incrementAndGet();
			});
		});

		info("+ Added " + counter.get() + " bad annotations");
	}

	@Override
	public String getName()
	{
		return "Bad Annotations";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.BAD_ANNOTATIONS;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
