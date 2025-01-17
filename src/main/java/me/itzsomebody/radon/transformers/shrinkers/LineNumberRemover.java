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
import java.util.stream.Stream;

import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Removes line numbers.
 *
 * @author ItzSomebody.
 */
public class LineNumberRemover extends Shrinker
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
				classWrapper.methods.stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).forEach(methodWrapper ->
				{
					final MethodNode methodNode = methodWrapper.methodNode;

					Stream.of(methodNode.instructions.toArray()).filter(insn -> insn instanceof LineNumberNode).forEach(insn ->
					{
						methodNode.instructions.remove(insn);
						counter.incrementAndGet();
					});
				}));

		info(String.format("- Removed %d line numbers.", counter.get()));
	}

	@Override
	public String getName()
	{
		return "Line numbers";
	}
}
