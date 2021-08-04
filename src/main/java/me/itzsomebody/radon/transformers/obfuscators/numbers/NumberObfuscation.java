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

package me.itzsomebody.radon.transformers.obfuscators.numbers;

import static me.itzsomebody.radon.config.ConfigurationSetting.NUMBER_OBFUSCATION;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Abstract class for number obfuscation transformers.
 *
 * @author ItzSomebody
 */
public class NumberObfuscation extends Transformer
{
	private final List<NumberObfuscation> numberObfuscators = new ArrayList<>();
	private boolean integerTamperingEnabled;
	private boolean longTamperingEnabled;
	private boolean floatTamperingEnabled;
	private boolean doubleTamperingEnabled;
	private int minIteration;
	private int maxIteration;
	private boolean poolInteger;
	private boolean poolLong;
	private boolean poolFloat;
	private boolean poolDouble;
	private boolean numberPoolerRandomOrder;
	private boolean numberPoolerGlobal;
	private boolean numberPoolerInjectGlobalPool;

	protected NumberObfuscation master;

	@Override
	public void transform()
	{
		numberObfuscators.forEach(numberObfuscation ->
		{
			numberObfuscation.init(radon);
			numberObfuscation.initMaster(this);
			numberObfuscation.transform();
		});
	}

	@Override
	public String getName()
	{
		return "Number obfuscation";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.NUMBER_OBFUSCATION;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		Stream.of(NumberObfuscationSetting.values()).filter(setting ->
		{
			final String path = NUMBER_OBFUSCATION + "." + setting.getName();

			if (config.contains(path))
				return config.get(path);

			return false;
		}).forEach(setting -> numberObfuscators.add(setting.getNumberObfuscation()));

		doubleTamperingEnabled = config.getOrDefault(NUMBER_OBFUSCATION + ".double_tampering", false);
		floatTamperingEnabled = config.getOrDefault(NUMBER_OBFUSCATION + ".float_tampering", false);
		integerTamperingEnabled = config.getOrDefault(NUMBER_OBFUSCATION + ".integer_tampering", false);
		longTamperingEnabled = config.getOrDefault(NUMBER_OBFUSCATION + ".long_tampering", false);

		poolInteger = config.getOrDefault(NUMBER_OBFUSCATION + ".pool_integer", false);
		poolLong = config.getOrDefault(NUMBER_OBFUSCATION + ".pool_long", false);
		poolFloat = config.getOrDefault(NUMBER_OBFUSCATION + ".pool_float", false);
		poolDouble = config.getOrDefault(NUMBER_OBFUSCATION + ".pool_double", false);

		numberPoolerRandomOrder = config.getOrDefault(NUMBER_OBFUSCATION + ".numberpooler_randomorder", false);
		numberPoolerGlobal = config.getOrDefault(NUMBER_OBFUSCATION + ".numberpooler_global", false);
		numberPoolerInjectGlobalPool = config.getOrDefault(NUMBER_OBFUSCATION + ".numberpooler_globalinject", false);

		minIteration = config.getOrDefault(NUMBER_OBFUSCATION + ".min_iteration", 2);
		maxIteration = config.getOrDefault(NUMBER_OBFUSCATION + ".max_iteration", 3);
	}

	private void initMaster(final NumberObfuscation master)
	{
		this.master = master;
	}

	protected static int randomInt(final int bounds)
	{
		if (bounds <= 0)
			return RandomUtils.getRandomInt(Integer.MAX_VALUE);

		return RandomUtils.getRandomInt(bounds);
	}

	protected static long randomLong(final long bounds)
	{
		if (bounds <= 0)
			return RandomUtils.getRandomLong(Long.MAX_VALUE);

		return RandomUtils.getRandomLong(bounds);
	}

	protected static float randomFloat(final float bounds)
	{
		if (bounds <= 0)
			return RandomUtils.getRandomFloat(Float.MAX_VALUE);

		return RandomUtils.getRandomFloat(bounds);
	}

	protected static double randomDouble(final double bounds)
	{
		if (bounds <= 0)
			return RandomUtils.getRandomDouble(Double.MAX_VALUE);

		return RandomUtils.getRandomDouble(bounds);
	}

	protected boolean isIntegerTamperingEnabled()
	{
		return integerTamperingEnabled;
	}

	protected void setIntegerTamperingEnabled(final boolean integerTamperingEnabled)
	{
		this.integerTamperingEnabled = integerTamperingEnabled;
	}

	protected boolean isLongTamperingEnabled()
	{
		return longTamperingEnabled;
	}

	protected void setLongTamperingEnabled(final boolean longTamperingEnabled)
	{
		this.longTamperingEnabled = longTamperingEnabled;
	}

	protected boolean isFloatTamperingEnabled()
	{
		return floatTamperingEnabled;
	}

	protected void setFloatTamperingEnabled(final boolean floatTamperingEnabled)
	{
		this.floatTamperingEnabled = floatTamperingEnabled;
	}

	protected boolean isDoubleTamperingEnabled()
	{
		return doubleTamperingEnabled;
	}

	protected void setDoubleTamperingEnabled(final boolean doubleTamperingEnabled)
	{
		this.doubleTamperingEnabled = doubleTamperingEnabled;
	}

	final int getIterationCount()
	{
		return RandomUtils.getRandomInt(master.minIteration, master.maxIteration);
	}

	protected int getMinIteration()
	{
		return minIteration;
	}

	protected int getMaxIteration()
	{
		return maxIteration;
	}

	protected boolean isNumberPoolerRandomOrder()
	{
		return numberPoolerRandomOrder;
	}

	protected boolean canPoolInteger()
	{
		return poolInteger;
	}

	protected boolean canPoolLong()
	{
		return poolLong;
	}

	protected boolean canPoolFloat()
	{
		return poolFloat;
	}

	protected boolean canPoolDouble()
	{
		return poolDouble;
	}

	protected boolean canNumberPoolerGlobal()
	{
		return numberPoolerGlobal;
	}

	protected void setNumberPoolerGlobal(final boolean numberPoolerGlobal)
	{
		this.numberPoolerGlobal = numberPoolerGlobal;
	}

	protected boolean canNumberPoolerInjectGlobalPool()
	{
		return numberPoolerInjectGlobalPool;
	}

	protected void setNumberPoolerInjectGlobalPool(final boolean numberPoolerInjectGlobalPool)
	{
		this.numberPoolerInjectGlobalPool = numberPoolerInjectGlobalPool;
	}
}
