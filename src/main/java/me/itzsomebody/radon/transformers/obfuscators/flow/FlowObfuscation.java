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

package me.itzsomebody.radon.transformers.obfuscators.flow;

import static me.itzsomebody.radon.config.ConfigurationSetting.FLOW_OBFUSCATION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Abstract class for flow obfuscation transformers.
 *
 * @author ItzSomebody
 */
public class FlowObfuscation extends Transformer
{
	private final List<FlowObfuscation> flowObfuscators = new ArrayList<>();

	@Override
	public void transform()
	{
		flowObfuscators.forEach(flowObfuscator ->
		{
			flowObfuscator.init(radon);
			flowObfuscator.transform();
		});
	}

	@Override
	public String getName()
	{
		return "Flow Obfuscation";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.FLOW_OBFUSCATION;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		Stream.of(FlowObfuscationSetting.values()).map(setting ->
		{
			final String path = FLOW_OBFUSCATION + "." + setting.getName();

			if (config.contains(path))
			{
				final FlowObfuscation obfuscation = setting.getFlowObfuscation();

				final Object value = config.get(path);
				if (value instanceof Boolean && (boolean) value || value instanceof Map)
				{
					// Apply configurations
					obfuscation.setConfiguration(config);
					return obfuscation;
				}
			}

			return null;
		}).filter(Objects::nonNull).forEach(flowObfuscators::add);
	}
}
