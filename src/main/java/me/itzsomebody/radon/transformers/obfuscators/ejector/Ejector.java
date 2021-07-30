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

package me.itzsomebody.radon.transformers.obfuscators.ejector;

import static me.itzsomebody.radon.config.ConfigurationSetting.EJECTOR;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.analysis.constant.ConstantAnalyzer;
import me.itzsomebody.radon.analysis.constant.values.AbstractValue;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.transformers.obfuscators.ejector.phases.AbstractEjectPhase;
import me.itzsomebody.radon.transformers.obfuscators.ejector.phases.FieldSetEjector;
import me.itzsomebody.radon.transformers.obfuscators.ejector.phases.MethodCallEjector;

/**
 * Extracts parts of code to individual methods.
 *
 * @author vovanre
 */
public class Ejector extends Transformer
{
	private boolean ejectMethodCalls;
	private boolean ejectFieldSet;
	private boolean junkArguments;
	private int junkArgumentStrength;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> processClass(classWrapper, counter));

		Main.info(String.format("+ Ejected %d regions.", counter.get()));
	}

	private List<AbstractEjectPhase> getPhases(final EjectorContext ejectorContext)
	{
		final List<AbstractEjectPhase> phases = new ArrayList<>();
		if (ejectMethodCalls)
			phases.add(new MethodCallEjector(ejectorContext));
		if (ejectFieldSet)
			phases.add(new FieldSetEjector(ejectorContext));
		return phases;
	}

	private void processClass(final ClassWrapper classWrapper, final AtomicInteger counter)
	{
		new ArrayList<>(classWrapper.getMethods()).stream().filter(this::included).filter(methodWrapper -> !"<init>".equals(methodWrapper.getMethodNode().name)).forEach(methodWrapper ->
		{
			final EjectorContext ejectorContext = new EjectorContext(counter, classWrapper, junkArguments, junkArgumentStrength);
			getPhases(ejectorContext).forEach(ejectPhase ->
			{
				final ConstantAnalyzer constantAnalyzer = new ConstantAnalyzer();
				try
				{
					Main.info("Analyze: " + classWrapper.getOriginalName() + "::" + methodWrapper.getOriginalName() + methodWrapper.getOriginalDescription());
					final Frame<AbstractValue>[] frames = constantAnalyzer.analyze(classWrapper.getName(), methodWrapper.getMethodNode());

					ejectPhase.process(methodWrapper, frames);
				}
				catch (final AnalyzerException e)
				{
					Main.severe("Can't analyze method: " + classWrapper.getOriginalName() + "::" + methodWrapper.getOriginalName() + methodWrapper.getOriginalDescription());
					Main.severe(e.toString());
				}
			});
		});
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.EJECTOR;
	}

	@Override
	public String getName()
	{
		return "Ejector";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		ejectMethodCalls = config.getOrDefault(EJECTOR + ".eject_call", false);
		ejectFieldSet = config.getOrDefault(EJECTOR + ".eject_field_set", false);
		junkArguments = config.getOrDefault(EJECTOR + ".junk_arguments", false);
		setJunkArgumentStrength(config.getOrDefault(EJECTOR + ".junk_argument_strength", 5));
	}

	private boolean isEjectMethodCalls()
	{
		return ejectMethodCalls;
	}

	private void setEjectMethodCalls(final boolean ejectMethodCalls)
	{
		this.ejectMethodCalls = ejectMethodCalls;
	}

	private boolean isEjectFieldSet()
	{
		return ejectFieldSet;
	}

	private void setEjectFieldSet(final boolean ejectFieldSet)
	{
		this.ejectFieldSet = ejectFieldSet;
	}

	private void setJunkArguments(final boolean junkArguments)
	{
		this.junkArguments = junkArguments;
	}

	private void setJunkArgumentStrength(final int junkArgumentStrength)
	{
		this.junkArgumentStrength = Math.min(junkArgumentStrength, 50);
	}
}
