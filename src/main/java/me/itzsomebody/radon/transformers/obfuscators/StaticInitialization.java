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

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;

/**
 * Moves initialization of all static fields into {@code <clinit>} of the class
 *
 * @author ItzSomebody
 * @author superblaubeere27
 */
public class StaticInitialization extends Transformer
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			final MethodNode clinit = classWrapper.getOrCreateStaticBlock();

			classWrapper.fields.stream().filter(fieldWrapper -> included(fieldWrapper) && Modifier.isStatic(fieldWrapper.fieldNode.access) && fieldWrapper.fieldNode.value != null).forEach(fieldWrapper ->
			{
				final FieldNode fieldNode = fieldWrapper.fieldNode;
				final Object val = fieldNode.value;

				exit:
				{
					final InsnList toAdd = new InsnList();

					if (val instanceof String)
						toAdd.insert(new LdcInsnNode(val));
					else if (val instanceof Integer)
						toAdd.insert(ASMUtils.getNumberInsn((Integer) val));
					else if (val instanceof Long)
						toAdd.insert(ASMUtils.getNumberInsn((Long) val));
					else if (val instanceof Float)
						toAdd.insert(ASMUtils.getNumberInsn((Float) val));
					else if (val instanceof Double)
						toAdd.insert(ASMUtils.getNumberInsn((Double) val));
					else
						break exit;

					toAdd.add(new FieldInsnNode(PUTSTATIC, classWrapper.getName(), fieldNode.name, fieldNode.desc));
					clinit.instructions.insert(toAdd);
					fieldNode.value = null;

					counter.incrementAndGet();
				}
			});
		});

		info("+ Moved " + counter.get() + " field values into static block.");
	}

	@Override
	public String getName()
	{
		return "Static Initialization";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.STATIC_INITIALIZATION;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
