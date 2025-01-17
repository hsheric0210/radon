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

import static me.itzsomebody.radon.config.ConfigurationSetting.HIDE_CODE;

import java.util.concurrent.atomic.AtomicInteger;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;

/**
 * Adds a synthetic modifier and bridge modifier if possible to attempt to hide code against some lower-quality decompilers.
 *
 * @author ItzSomebody
 */
public class HideCode extends Transformer
{
	private boolean hideClassesEnabled;
	private boolean hideMethodsEnabled;
	private boolean hideFieldsEnabled;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			if (hideClassesEnabled)
				if (!cw.access.isSynthetic() && !ASMUtils.hasAnnotations(cw.classNode))
				{
					cw.setAccessFlags(cw.getAccessFlags() | ACC_SYNTHETIC);
					counter.incrementAndGet();
				}
			if (hideMethodsEnabled)
			{
				cw.methods.stream().filter(mw -> included(mw) && !ASMUtils.hasAnnotations(mw.methodNode)).forEach(mw ->
				{
					boolean atLeastOnce = false;

					if (!mw.access.isSynthetic())
					{
						mw.setAccessFlags(mw.getAccessFlags() | ACC_SYNTHETIC);
						atLeastOnce = true;
					}
					if (!(!mw.getName().isEmpty() && mw.getName().charAt(0) == '<') && !mw.access.isBridge())
					{
						mw.setAccessFlags(mw.getAccessFlags() | ACC_BRIDGE);
						atLeastOnce = true;
					}

					if (atLeastOnce)
						counter.incrementAndGet();
				});
			}
			if (hideFieldsEnabled)
				cw.fields.stream().filter(fw -> included(fw) && !ASMUtils.hasAnnotations(fw.fieldNode)).filter(fw -> !fw.access.isSynthetic()).forEach(fw ->
				{
					fw.setAccessFlags(fw.getAccessFlags() | ACC_SYNTHETIC);
					counter.incrementAndGet();
				});
		});

		info(String.format("+ Hid %d members.", counter.get()));
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.HIDE_CODE;
	}

	@Override
	public String getName()
	{
		return "Hide code";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		hideClassesEnabled = config.getOrDefault(HIDE_CODE + ".hide_classes", false);
		hideFieldsEnabled = config.getOrDefault(HIDE_CODE + ".hide_fields", false);
		hideMethodsEnabled = config.getOrDefault(HIDE_CODE + ".hide_methods", false);
	}
}
