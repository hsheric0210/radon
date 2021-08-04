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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Renames bundled JAR resources to make their purpose less obvious.
 *
 * @author ItzSomebody
 */
public class ResourceRenamer extends Transformer
{
	private Map<String, String> mappings;

	@Override
	public void transform()
	{
		mappings = new HashMap<>();
		final AtomicInteger counter = new AtomicInteger();
		final Set<String> resourceNames = getResources().keySet();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.getMethods().stream().filter(methodWrapper -> included(methodWrapper) && methodWrapper.hasInstructions()).forEach(methodWrapper ->
		{
			final MethodNode methodNode = methodWrapper.getMethodNode();

			Stream.of(methodNode.instructions.toArray()).filter(insn -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String).forEach(insn ->
			{
				final String s = (String) ((LdcInsnNode) insn).cst;
				final String resourceName;

				if (!s.isEmpty() && s.charAt(0) == '/')
					resourceName = s.substring(1);
				else
					resourceName = classWrapper.getOriginalName().substring(0, classWrapper.getOriginalName().lastIndexOf('/') + 1) + s;

				if (resourceNames.contains(resourceName))
					if (mappings.containsKey(resourceName))
						((LdcInsnNode) insn).cst = mappings.get(resourceName);
					else
					{
						final String newName = '/' + genericDictionary.uniqueRandomString();
						((LdcInsnNode) insn).cst = newName;
						mappings.put(resourceName, newName);
					}
			});
		}));

		new HashMap<>(getResources()).forEach((name, b) ->
		{
			if (mappings.containsKey(name))
			{
				getResources().remove(name);
				getResources().put(mappings.get(name).substring(1), b);

				counter.incrementAndGet();
			}
		});

		info("+ Renamed " + counter.get() + " resources");
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.RESOURCE_RENAMER;
	}

	@Override
	public String getName()
	{
		return "Resource Renamer";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
