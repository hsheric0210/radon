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

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Original source code: https://github.com/GraxCode/threadtear/blob/master/core/src/main/java/me/nov/threadtear/execution/paramorphism/BadAttributeRemover.java
 *
 * @author GraxCode, hsheric0210
 */
public class BadAttributes extends Transformer
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final ClassNode cn = cw.classNode;
			cn.innerClasses = new ArrayList<>();
			final WrappedDictionary classDictionary = getClassDictionary(cw.getPackageName());
			final WrappedDictionary methodDictionary = getMethodDictionary(null);
			for (int i = 0, j = RandomUtils.getRandomInt(10); i < j; i++)
				cn.innerClasses.add(new InnerClassNode(classDictionary.randomString(), classDictionary.randomString(), classDictionary.randomString(), ACC_PUBLIC));
			cn.outerClass = classDictionary.randomString();
			cn.outerMethod = methodDictionary.randomString();
			cn.outerMethodDesc = "(L" + classDictionary.randomString() + ";)L" + classDictionary.randomString() + ";";

			counter.incrementAndGet();
		});

		info("+ Added " + counter.get() + " bad attributes");
	}

	@Override
	public String getName()
	{
		return "Bad Attributes";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.BAD_ATTRIBUTES;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
