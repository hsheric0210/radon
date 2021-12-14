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

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.Radon;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.FieldWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.config.Configuration;
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

	private static Map<String, WrappedDictionary> packageDictionaries;
	private static Map<String, WrappedDictionary> classDictionaries;
	private static Map<String, WrappedDictionary> methodDictionaries;
	private static Map<String, WrappedDictionary> fieldDictionaries;

	public final void init(final Radon radon)
	{
		this.radon = radon;
	}

	protected boolean included(final CharSequence str)
	{
		return !radon.config.exclusionManager.isExcluded(str, getExclusionType());
	}

	protected final boolean included(final ClassWrapper classWrapper)
	{
		return included(classWrapper.originalName);
	}

	protected final boolean included(final MethodWrapper methodWrapper)
	{
		return included(methodWrapper.owner.originalName + '.' + methodWrapper.originalName + methodWrapper.originalDescription);
	}

	protected final boolean included(final FieldWrapper fieldWrapper)
	{
		return included(fieldWrapper.owner.originalName + '.' + fieldWrapper.originalName + '.' + fieldWrapper.originalDescription);
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
		if (radon.config.renamerPresent)
			return getClassDictionary(randomExistingClass().getPackageName()).nextUniqueString(); // FIXME: The generated class always located on the last entry in a package

		final List<String> list = getClasses().entrySet().stream().filter(e -> included(e.getValue())).map(Entry::getKey).distinct().collect(Collectors.toList());

		final String first = RandomUtils.getRandomElement(list);
		String result;
		do
		{
			final String second = RandomUtils.getRandomElement(list);

			String secondName = second.substring(second.lastIndexOf('/') + 1);
			if (secondName.contains("$"))
				secondName = secondName.substring(0, secondName.indexOf('$'));
			result = first + '$' + secondName;
		}
		while (list.contains(result));
		return result;
	}

	public ClassWrapper randomExistingClass()
	{
		return RandomUtils.getRandomElement(getClasses().values().stream().filter(cw -> included(cw) && !cw.access.isInterface()).collect(Collectors.toCollection(() -> new ArrayList<>(getClasses().size()))));
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
		return radon.config.verboseLogging;
	}

	protected final void verboseInfo(final Supplier<String> verboseMessage)
	{
		if (enableVerboseLogging())
			Main.info(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage.get()));
	}

	protected final void verboseInfo(final Function<Object[], String> verboseMessage, final Object... params)
	{
		if (enableVerboseLogging())
			Main.info(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage.apply(params)));
	}

	protected final void verboseInfos(final Supplier<String[]> verboseMessages)
	{
		if (enableVerboseLogging())
			for (final String message : verboseMessages.get())
				Main.info(String.format("[VERBOSE] [%1$s] %2$s", getName(), message));
	}

	protected final void verboseWarn(final Supplier<String> verboseMessage)
	{
		if (enableVerboseLogging())
			Main.warn(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage.get()));
	}

	protected final void verboseWarn(final Function<Object[], String> verboseMessage, final Object... params)
	{
		if (enableVerboseLogging())
			Main.warn(String.format("[VERBOSE] [%1$s] %2$s", getName(), verboseMessage.apply(params)));
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

	public void nowRunningWith(final Set<String> transformerNames)
	{
	}

	public abstract void setConfiguration(Configuration config);

	protected WrappedDictionary getPackageDictionary(final String parentPackagePath)
	{
		final WrappedDictionary packageDictionary = radon.config.packageDictionary;
		if (parentPackagePath == null)
			return packageDictionary;

		if (packageDictionaries == null)
			packageDictionaries = new HashMap<>();
		return packageDictionaries.computeIfAbsent(parentPackagePath, s -> packageDictionary.copy());
	}

	protected WrappedDictionary getClassDictionary(final String packagePath)
	{
		final WrappedDictionary classDictionary = radon.config.classDictionary;
		if (packagePath == null)
			return classDictionary;

		if (classDictionaries == null)
			classDictionaries = new HashMap<>();

		return classDictionaries.computeIfAbsent(packagePath, s -> classDictionary.copy());
	}

	protected WrappedDictionary getMethodDictionary(final String className)
	{
		final WrappedDictionary methodDictionary = radon.config.methodDictionary;
		if (className == null)
			return methodDictionary;

		if (methodDictionaries == null)
			methodDictionaries = new HashMap<>();
		return methodDictionaries.computeIfAbsent(className, s -> methodDictionary.copy());
	}

	protected WrappedDictionary getFieldDictionary(final String className)
	{
		final WrappedDictionary fieldDictionary = radon.config.fieldDictionary;
		if (className == null)
			return fieldDictionary;

		if (fieldDictionaries == null)
			fieldDictionaries = new HashMap<>();
		return fieldDictionaries.computeIfAbsent(className, s -> fieldDictionary.copy());
	}

	public WrappedDictionary getGenericDictionary()
	{
		return radon.config.genericDictionary;
	}
}
