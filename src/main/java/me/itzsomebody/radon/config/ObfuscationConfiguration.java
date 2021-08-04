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

import static me.itzsomebody.radon.config.ConfigurationSetting.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import me.itzsomebody.radon.dictionaries.Dictionary;
import me.itzsomebody.radon.dictionaries.DictionaryFactory;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.Exclusion;
import me.itzsomebody.radon.exclusions.ExclusionManager;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.FileUtils;

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

		try
		{
			obfConfig.genericDictionary = DictionaryFactory.get(config.getOrDefault(GENERIC_DICTIONARY, "alphanumeric"));
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(GENERIC_DICTIONARY, Collections.emptyList());
			obfConfig.genericDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.genericMinRandomizedStringLength = config.getOrDefault(GENERIC_MIN_RANDOMIZED_STRING_LENGTH, 16);
		obfConfig.genericMaxRandomizedStringLength = config.getOrDefault(GENERIC_MAX_RANDOMIZED_STRING_LENGTH, 16);

		try
		{
			final String dictionaryName = config.getOrDefault(PACKAGE_DICTIONARY, "alphanumeric");
			obfConfig.packageDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(PACKAGE_DICTIONARY, Collections.emptyList());
			obfConfig.packageDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.packageMinRandomizedStringLength = config.getOrDefault(PACKAGE_MIN_RANDOMIZED_STRING_LENGTH, 16);
		obfConfig.packageMaxRandomizedStringLength = config.getOrDefault(PACKAGE_MAX_RANDOMIZED_STRING_LENGTH, 16);

		try
		{
			final String dictionaryName = config.getOrDefault(CLASS_DICTIONARY, "alphanumeric");
			obfConfig.classDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(CLASS_DICTIONARY, Collections.emptyList());
			obfConfig.classDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.classMinRandomizedStringLength = config.getOrDefault(CLASS_MIN_RANDOMIZED_STRING_LENGTH, 16);
		obfConfig.classMaxRandomizedStringLength = config.getOrDefault(CLASS_MAX_RANDOMIZED_STRING_LENGTH, 16);

		try
		{
			final String dictionaryName = config.getOrDefault(METHOD_DICTIONARY, "alphanumeric");
			obfConfig.methodDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(METHOD_DICTIONARY, Collections.emptyList());
			obfConfig.methodDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.methodMinRandomizedStringLength = config.getOrDefault(METHOD_MIN_RANDOMIZED_STRING_LENGTH, 16);
		obfConfig.methodMaxRandomizedStringLength = config.getOrDefault(METHOD_MAX_RANDOMIZED_STRING_LENGTH, 16);

		try
		{
			final String dictionaryName = config.getOrDefault(FIELD_DICTIONARY, "alphanumeric");
			obfConfig.fieldDictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(FIELD_DICTIONARY, Collections.emptyList());
			obfConfig.fieldDictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}
		obfConfig.fieldMinRandomizedStringLength = config.getOrDefault(FIELD_MIN_RANDOMIZED_STRING_LENGTH, 16);
		obfConfig.fieldMaxRandomizedStringLength = config.getOrDefault(FIELD_MAX_RANDOMIZED_STRING_LENGTH, 16);

		// MISC.

		obfConfig.compressionLevel = config.getOrDefault(COMPRESSION_LEVEL, Deflater.BEST_COMPRESSION);
		obfConfig.verify = config.getOrDefault(VERIFY, false);
		obfConfig.corruptCrc = config.getOrDefault(CORRUPT_CRC, false);
		obfConfig.nTrashClasses = config.getOrDefault(TRASH_CLASSES, 0);
		obfConfig.verboseLogging = config.getOrDefault(VERBOSE_LOGGING, false);

		// TRANSFORMERS

		final List<Transformer> transformers = new ArrayList<>();
		Stream.of(values()).filter(setting -> setting.getTransformer() != null).filter(config::contains).forEach(setting ->
		{
			final Transformer transformer = setting.getTransformer();
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

	private File input;
	private File output;
	private List<File> libraries;
	private ExclusionManager exclusionManager;
	private int compressionLevel;
	private boolean verify;
	private boolean corruptCrc;
	private int nTrashClasses;
	private boolean verboseLogging;

	private Dictionary genericDictionary;
	private int genericMinRandomizedStringLength;
	private int genericMaxRandomizedStringLength;

	private Dictionary packageDictionary;
	private int packageMinRandomizedStringLength;
	private int packageMaxRandomizedStringLength;

	private Dictionary classDictionary;
	private int classMinRandomizedStringLength;
	private int classMaxRandomizedStringLength;

	private Dictionary methodDictionary;
	private int methodMinRandomizedStringLength;
	private int methodMaxRandomizedStringLength;

	private Dictionary fieldDictionary;
	private int fieldMinRandomizedStringLength;
	private int fieldMaxRandomizedStringLength;

	private List<Transformer> transformers;

	public File getInput()
	{
		return input;
	}

	public void setInput(final File input)
	{
		this.input = input;
	}

	public File getOutput()
	{
		return output;
	}

	public void setOutput(final File output)
	{
		this.output = output;
	}

	public List<File> getLibraries()
	{
		return libraries;
	}

	public void setLibraries(final List<File> libraries)
	{
		this.libraries = libraries;
	}

	public ExclusionManager getExclusionManager()
	{
		return exclusionManager;
	}

	public void setExclusionManager(final ExclusionManager exclusionManager)
	{
		this.exclusionManager = exclusionManager;
	}

	public Dictionary getGenericDictionary()
	{
		return genericDictionary;
	}

	public void setGenericDictionary(final Dictionary dictionary)
	{
		genericDictionary = dictionary;
	}

	public Dictionary getPackageDictionary()
	{
		return packageDictionary;
	}

	public void setPackageDictionary(final Dictionary dictionary)
	{
		packageDictionary = dictionary;
	}

	public Dictionary getClassDictionary()
	{
		return classDictionary;
	}

	public void setClassDictionary(final Dictionary dictionary)
	{
		classDictionary = dictionary;
	}

	public Dictionary getMethodDictionary()
	{
		return methodDictionary;
	}

	public void setMethodDictionary(final Dictionary dictionary)
	{
		methodDictionary = dictionary;
	}

	public Dictionary getFieldDictionary()
	{
		return fieldDictionary;
	}

	public void setFieldDictionary(final Dictionary dictionary)
	{
		fieldDictionary = dictionary;
	}

	public int getGenericMinRandomizedStringLength()
	{
		return genericMinRandomizedStringLength;
	}

	public void setGenericMinRandomizedStringLength(final int genericMinRandomizedStringLength)
	{
		this.genericMinRandomizedStringLength = genericMinRandomizedStringLength;
	}

	public int getGenericMaxRandomizedStringLength()
	{
		return genericMaxRandomizedStringLength;
	}

	public void setGenericMaxRandomizedStringLength(final int genericMaxRandomizedStringLength)
	{
		this.genericMaxRandomizedStringLength = genericMaxRandomizedStringLength;
	}

	public int getPackageMinRandomizedStringLength()
	{
		return packageMinRandomizedStringLength;
	}

	public void setPackageMinRandomizedStringLength(final int packageMinRandomizedStringLength)
	{
		this.packageMinRandomizedStringLength = packageMinRandomizedStringLength;
	}

	public int getPackageMaxRandomizedStringLength()
	{
		return packageMaxRandomizedStringLength;
	}

	public void setPackageMaxRandomizedStringLength(final int packageMaxRandomizedStringLength)
	{
		this.packageMaxRandomizedStringLength = packageMaxRandomizedStringLength;
	}

	public int getClassMinRandomizedStringLength()
	{
		return classMinRandomizedStringLength;
	}

	public void setClassMinRandomizedStringLength(final int classMinRandomizedStringLength)
	{
		this.classMinRandomizedStringLength = classMinRandomizedStringLength;
	}

	public int getClassMaxRandomizedStringLength()
	{
		return classMaxRandomizedStringLength;
	}

	public void setClassMaxRandomizedStringLength(final int classMaxRandomizedStringLength)
	{
		this.classMaxRandomizedStringLength = classMaxRandomizedStringLength;
	}

	public int getMethodMinRandomizedStringLength()
	{
		return methodMinRandomizedStringLength;
	}

	public void setMethodMinRandomizedStringLength(final int methodMinRandomizedStringLength)
	{
		this.methodMinRandomizedStringLength = methodMinRandomizedStringLength;
	}

	public int getMethodMaxRandomizedStringLength()
	{
		return methodMaxRandomizedStringLength;
	}

	public void setMethodMaxRandomizedStringLength(final int methodMaxRandomizedStringLength)
	{
		this.methodMaxRandomizedStringLength = methodMaxRandomizedStringLength;
	}

	public int getFieldMinRandomizedStringLength()
	{
		return fieldMinRandomizedStringLength;
	}

	public void setFieldMinRandomizedStringLength(final int fieldMinRandomizedStringLength)
	{
		this.fieldMinRandomizedStringLength = fieldMinRandomizedStringLength;
	}

	public int getFieldMaxRandomizedStringLength()
	{
		return fieldMaxRandomizedStringLength;
	}

	public void setFieldMaxRandomizedStringLength(final int fieldMaxRandomizedStringLength)
	{
		this.fieldMaxRandomizedStringLength = fieldMaxRandomizedStringLength;
	}

	public int getCompressionLevel()
	{
		return compressionLevel;
	}

	public void setCompressionLevel(final int compressionLevel)
	{
		this.compressionLevel = compressionLevel;
	}

	public boolean isVerify()
	{
		return verify;
	}

	public void setVerify(final boolean verify)
	{
		this.verify = verify;
	}

	public boolean isCorruptCrc()
	{
		return corruptCrc;
	}

	public void setCorruptCrc(final boolean corruptCrc)
	{
		this.corruptCrc = corruptCrc;
	}

	public int getnTrashClasses()
	{
		return nTrashClasses;
	}

	public void setnTrashClasses(final int nTrashClasses)
	{
		this.nTrashClasses = nTrashClasses;
	}

	public List<Transformer> getTransformers()
	{
		return transformers;
	}

	public void setTransformers(final List<Transformer> transformers)
	{
		this.transformers = transformers;
	}

	public boolean enableVerboseLogging()
	{
		return verboseLogging;
	}

	public void setVerboseLogging(final boolean verboseLogging)
	{
		this.verboseLogging = verboseLogging;
	}
}
