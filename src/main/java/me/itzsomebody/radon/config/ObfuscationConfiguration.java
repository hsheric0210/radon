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
		libPaths.forEach(s ->
		{
			final File f = new File(s);

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
			final String dictionaryName = config.getOrDefault(DICTIONARY, "alphanumeric");
			obfConfig.dictionary = DictionaryFactory.get(dictionaryName);
		}
		catch (final ClassCastException e)
		{
			// String array charset
			final List<String> dictionaryCharset = config.getOrDefault(DICTIONARY, Collections.emptyList());
			obfConfig.dictionary = DictionaryFactory.getCustom(dictionaryCharset);
		}

		// MISC.

		obfConfig.randomizedStringLength = config.getOrDefault(RANDOMIZED_STRING_LENGTH, 1);
		obfConfig.compressionLevel = config.getOrDefault(COMPRESSION_LEVEL, Deflater.BEST_COMPRESSION);
		obfConfig.verify = config.getOrDefault(VERIFY, false);
		obfConfig.corruptCrc = config.getOrDefault(CORRUPT_CRC, false);
		obfConfig.nTrashClasses = config.getOrDefault(TRASH_CLASSES, 0);

		// TRANSFORMERS

		final List<Transformer> transformers = new ArrayList<>();
		Stream.of(values()).filter(setting -> setting.getTransformer() != null).forEach(setting ->
		{
			if (config.contains(setting))
			{
				final Transformer transformer = setting.getTransformer();

				if (config.get(setting) instanceof Map)
				{
					transformer.setConfiguration(config);
					transformers.add(transformer);
				}
				else if (config.get(setting) instanceof Boolean && (boolean) config.get(setting))
					transformers.add(transformer);
			}
		});

		obfConfig.transformers = transformers;

		return obfConfig;
	}

	private File input;
	private File output;
	private List<File> libraries;
	private ExclusionManager exclusionManager;

	private Dictionary dictionary;
	private int randomizedStringLength;
	private int compressionLevel;
	private boolean verify;
	private boolean corruptCrc;
	private int nTrashClasses;

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

	public Dictionary getDictionary()
	{
		return dictionary;
	}

	public void setDictionary(final Dictionary dictionary)
	{
		this.dictionary = dictionary;
	}

	public int getRandomizedStringLength()
	{
		return randomizedStringLength;
	}

	public void setRandomizedStringLength(final int randomizedStringLength)
	{
		this.randomizedStringLength = randomizedStringLength;
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
}
