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

package me.itzsomebody.radon.transformers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.objectweb.asm.Opcodes;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.Radon;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.FieldWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.config.ObfuscationConfiguration;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Abstract transformer for all the transformers. \o/
 *
 * @author ItzSomebody
 */
public abstract class Transformer implements Opcodes
{
	protected Radon radon;
	protected WrappedDictionary genericDictionary;
	protected WrappedDictionary packageDictionary;
	protected WrappedDictionary classDictionary;
	protected WrappedDictionary methodDictionary;
	protected WrappedDictionary fieldDictionary;

	public final void init(final Radon radon)
	{
		this.radon = radon;
		final ObfuscationConfiguration config = radon.getConfig();
		genericDictionary = new WrappedDictionary(config.getGenericDictionary(), config.getGenericMinRandomizedStringLength(), config.getGenericMaxRandomizedStringLength());
		packageDictionary = new WrappedDictionary(config.getPackageDictionary(), config.getPackageMinRandomizedStringLength(), config.getPackageMaxRandomizedStringLength());
		classDictionary = new WrappedDictionary(config.getClassDictionary(), config.getClassMinRandomizedStringLength(), config.getClassMaxRandomizedStringLength());
		methodDictionary = new WrappedDictionary(config.getMethodDictionary(), config.getMethodMinRandomizedStringLength(), config.getMethodMaxRandomizedStringLength());
		fieldDictionary = new WrappedDictionary(config.getFieldDictionary(), config.getFieldMinRandomizedStringLength(), config.getFieldMaxRandomizedStringLength());
	}

	protected final boolean included(final String str)
	{
		return !radon.getConfig().getExclusionManager().isExcluded(str, getExclusionType());
	}

	protected final boolean included(final ClassWrapper classWrapper)
	{
		return included(classWrapper.getOriginalName());
	}

	protected final boolean included(final MethodWrapper methodWrapper)
	{
		return included(methodWrapper.getOwner().getOriginalName() + '.' + methodWrapper.getOriginalName() + methodWrapper.getOriginalDescription());
	}

	protected final boolean included(final FieldWrapper fieldWrapper)
	{
		return included(fieldWrapper.getOwner().getOriginalName() + '.' + fieldWrapper.getOriginalName() + '.' + fieldWrapper.getOriginalDescription());
	}

	public static String tookThisLong(final long nanoTime)
	{
		final long nanoSeconds = System.nanoTime() - nanoTime;
		final long microSeconds = TimeUnit.NANOSECONDS.toMicros(nanoSeconds);
		final long milliSeconds = TimeUnit.NANOSECONDS.toMillis(nanoSeconds);
		return String.format("Took %dns, %sus, %dms", nanoSeconds, microSeconds, milliSeconds);
	}

	public String randomClassName()
	{
		// TODO: Better class name generation algorithm
		final List<String> list = new ArrayList<>(getClasses().keySet());

		final String first = RandomUtils.getRandomElement(list);
		final String second = RandomUtils.getRandomElement(list);

		return first + '$' + second.substring(second.lastIndexOf('/') + 1);
	}

	protected final Map<String, ClassWrapper> getClasses()
	{
		return radon.classes;
	}

	protected final Collection<ClassWrapper> getClassWrappers()
	{
		return radon.classes.values();
	}

	protected final Map<String, ClassWrapper> getClassPath()
	{
		return radon.classPath;
	}

	protected final Map<String, byte[]> getResources()
	{
		return radon.resources;
	}

	protected boolean enableVerboseLogging()
	{
		return radon.getConfig().enableVerboseLogging();
	}

	protected final void verboseInfo(final Supplier<String> verboseMessage)
	{
		if (enableVerboseLogging())
			Main.info(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage.get()));
	}

	protected final void verboseInfos(final Supplier<String[]> verboseMessages)
	{
		if (enableVerboseLogging())
			for (final String message : verboseMessages.get())
				Main.info(String.format("[VERBOSE] [%1$s] %2$s", getName(), message));
	}

	protected final void verboseWarn(final String verboseMessage)
	{
		if (enableVerboseLogging())
			Main.warn(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage));
	}

	protected final void info(final String message)
	{
		Main.info(String.format("[%1$s] %2$s", getName(), message));
	}

	protected final void warn(final String message)
	{
		Main.warn(String.format("[%1$s] %2$s", getName(), message));
	}

	protected final void warn(final String message, final Throwable thrown)
	{
		Main.warn(String.format("[%1$s] %2$s", getName(), message), thrown);
	}

	protected final void severe(final String message, final Throwable thrown)
	{
		Main.severe(String.format("[%1$s] %2$s", getName(), message), thrown);
	}

	public abstract void transform();

	public abstract String getName();

	public abstract ExclusionType getExclusionType();

	public abstract void setConfiguration(Configuration config);
}
