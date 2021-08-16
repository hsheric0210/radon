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

package me.itzsomebody.radon.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import me.itzsomebody.radon.dictionaries.Dictionary;
import me.itzsomebody.radon.dictionaries.DictionaryFactory;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.Exclusion;
import me.itzsomebody.radon.exclusions.ExclusionManager;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.FileUtils;

import static me.itzsomebody.radon.config.ConfigurationSetting.*;

public final class ObfuscationConfiguration
{
	public static ObfuscationConfiguration from(final Configuration config)
	{
		final ObfuscationConfiguration obfConfig = new ObfuscationConfiguration();

		// INPUT / OUTPUT

		if (!config.contains(INPUT))
			throw new RadonException("No input file was specified in the config");
		if (!config.contains(OUTPUT))
			throw new RadonException("No output file was specified in the config");

		obfConfig.input = new File((String) config.get(INPUT));
		obfConfig.output = new File((String) config.get(OUTPUT));

		// LIBRARIES

		final List<File> libraries = new ArrayList<>();
		final List<String> libPaths = config.getOrDefault(LIBRARIES, Collections.emptyList());
		libPaths.stream().map(File::new).forEach(f ->
		{
			if (f.isDirectory())
				FileUtils.getSubDirectoryFiles(f, libraries);
			else
				libraries.add(f);
		});
		obfConfig.libraries = libraries;

		// EXCLUSIONS

		final ExclusionManager manager = new ExclusionManager();
		final List<String> exclusionPatterns = config.getOrDefault(EXCLUSIONS, Collections.emptyList());
		exclusionPatterns.forEach(s -> manager.addExclusion(new Exclusion(s)));
		obfConfig.exclusionManager = manager;

		// DICTIONARY

		Dictionary genericDictionary;
		try
		{
			genericDictionary = DictionaryFactory.get(config.getOrDefault(GENERIC_DICTIONARY, "alphanumeric"));
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(GENERIC_DICTIONARY, Collections.emptyList());
			genericDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.genericDictionary = new WrappedDictionary(genericDictionary, config.getOrDefault(GENERIC_MIN_RANDOMIZED_STRING_LENGTH, 16), config.getOrDefault(GENERIC_MAX_RANDOMIZED_STRING_LENGTH, 16));

		Dictionary packageDictionary;
		try
		{
			final String dictionaryName = config.getOrDefault(PACKAGE_DICTIONARY, "alphanumeric");
			packageDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(PACKAGE_DICTIONARY, Collections.emptyList());
			packageDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.packageDictionary = new WrappedDictionary(packageDictionary, config.getOrDefault(PACKAGE_MIN_RANDOMIZED_STRING_LENGTH, 16), config.getOrDefault(PACKAGE_MAX_RANDOMIZED_STRING_LENGTH, 16));

		Dictionary classDictionary;
		try
		{
			final String dictionaryName = config.getOrDefault(CLASS_DICTIONARY, "alphanumeric");
			classDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(CLASS_DICTIONARY, Collections.emptyList());
			classDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.classDictionary = new WrappedDictionary(classDictionary, config.getOrDefault(CLASS_MIN_RANDOMIZED_STRING_LENGTH, 16), config.getOrDefault(CLASS_MAX_RANDOMIZED_STRING_LENGTH, 16));

		Dictionary methodDictionary;
		try
		{
			final String dictionaryName = config.getOrDefault(METHOD_DICTIONARY, "alphanumeric");
			methodDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(METHOD_DICTIONARY, Collections.emptyList());
			methodDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.methodDictionary = new WrappedDictionary(methodDictionary, config.getOrDefault(METHOD_MIN_RANDOMIZED_STRING_LENGTH, 16), config.getOrDefault(METHOD_MAX_RANDOMIZED_STRING_LENGTH, 16));

		Dictionary fieldDictionary;
		try
		{
			final String dictionaryName = config.getOrDefault(FIELD_DICTIONARY, "alphanumeric");
			fieldDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(FIELD_DICTIONARY, Collections.emptyList());
			fieldDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.fieldDictionary = new WrappedDictionary(fieldDictionary, config.getOrDefault(FIELD_MIN_RANDOMIZED_STRING_LENGTH, 16), config.getOrDefault(FIELD_MAX_RANDOMIZED_STRING_LENGTH, 16));

		// MISC.

		obfConfig.compressionLevel = config.getOrDefault(COMPRESSION_LEVEL, Deflater.BEST_COMPRESSION);
		obfConfig.verify = config.getOrDefault(VERIFY, false);
		obfConfig.corruptCrc = config.getOrDefault(CORRUPT_CRC, false);
		obfConfig.nTrashClasses = config.getOrDefault(TRASH_CLASSES, 0);
		obfConfig.verboseLogging = config.getOrDefault(VERBOSE_LOGGING, false);

		obfConfig.renamerPresent = false;

		// TRANSFORMERS

		final List<Transformer> transformers = new ArrayList<>();
		Stream.of(values()).filter(setting -> setting.transformer != null).filter(config::contains).forEach(setting ->
		{
			final Transformer transformer = setting.transformer;

			if (setting == RENAMER)
				obfConfig.renamerPresent = true;

			if (config.get(setting) instanceof Map)
			{
				transformer.setConfiguration(config);
				transformers.add(transformer);
			}
			else if (config.get(setting) instanceof Boolean && (boolean) config.get(setting))
				transformers.add(transformer);
		});

		obfConfig.transformers = transformers;

		return obfConfig;
	}

	public File input;
	public File output;
	public List<File> libraries;
	public ExclusionManager exclusionManager;
	public int compressionLevel;
	public boolean verify;
	public boolean corruptCrc;
	public int nTrashClasses;
	public boolean verboseLogging;

	public WrappedDictionary genericDictionary;
	public WrappedDictionary packageDictionary;
	public WrappedDictionary classDictionary;
	public WrappedDictionary methodDictionary;
	public WrappedDictionary fieldDictionary;

	public List<Transformer> transformers;
	public boolean renamerPresent;
}
