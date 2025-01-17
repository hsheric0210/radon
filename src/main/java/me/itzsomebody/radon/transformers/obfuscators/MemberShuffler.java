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

import static me.itzsomebody.radon.config.ConfigurationSetting.MEMBER_SHUFFLER;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Randomizes the order of methods and fields in a class.
 *
 * @author ItzSomebody
 */
public class MemberShuffler extends Transformer
{
	private boolean shuffleMethodsEnabled;
	private boolean shuffleFieldsEnabled;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			if (shuffleMethodsEnabled)
			{
				Collections.shuffle(classWrapper.classNode.methods);
				counter.addAndGet(classWrapper.classNode.methods.size());
			}

			if (shuffleFieldsEnabled && classWrapper.classNode.fields != null)
			{
				Collections.shuffle(classWrapper.classNode.fields);
				counter.addAndGet(classWrapper.classNode.fields.size());
			}
		});

		info(String.format("+ Shuffled %d members.", counter.get()));
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.SHUFFLER;
	}

	@Override
	public String getName()
	{
		return "Member Shuffler";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		shuffleFieldsEnabled = config.getOrDefault(MEMBER_SHUFFLER + ".shuffle_fields", false);
		shuffleMethodsEnabled = config.getOrDefault(MEMBER_SHUFFLER + ".shuffle_methods", false);
	}
}
