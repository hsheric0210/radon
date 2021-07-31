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

package me.itzsomebody.radon;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.MethodNode;

import me.itzsomebody.radon.asm.ClassTree;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.ObfuscationConfiguration;
import me.itzsomebody.radon.exceptions.MissingClassException;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.transformers.miscellaneous.TrashClasses;
import me.itzsomebody.radon.utils.*;

/**
 * This class is how Radon processes the provided {@link ObfuscationConfiguration} to produce an obfuscated jar.
 *
 * @author ItzSomebody
 */
public class Radon
{
	private final ObfuscationConfiguration config;
	private final Map<String, ClassTree> hierarchy = new HashMap<>();
	public final Map<String, ClassWrapper> classes = new HashMap<>();
	public final Map<String, ClassWrapper> classPath = new HashMap<>();
	public final Map<String, byte[]> resources = new HashMap<>();

	public Radon(final ObfuscationConfiguration config)
	{
		this.config = config;
	}

	/**
	 * Execution order. Feel free to modify.
	 */
	public void run()
	{
		Main.info(Strings.START_LOAD_CP);
		Main.infoNewline();
		loadClassPath();
		Main.infoNewline();
		Main.info(Strings.END_LOAD_CP);
		Main.infoNewline();

		Main.infoNewline();
		Main.info(Strings.START_LOAD_INPUT);
		Main.infoNewline();
		loadInput();
		Main.infoNewline();
		Main.info(Strings.END_LOAD_INPUT);
		Main.infoNewline();

		final List<Transformer> transformers = config.getTransformers();

		if (config.getnTrashClasses() > 0)
			transformers.add(new TrashClasses());
		if (transformers.isEmpty())
			throw new RadonException("No transformers are enabled.");

		transformers.sort((t1, t2) ->
		{
			final ExclusionType type1 = t1.getExclusionType();
			final ExclusionType type2 = t2.getExclusionType();

			// In the event I forget to add an exclusion type
			Objects.requireNonNull(type1, () -> t1.getName() + " has a null exclusion type");
			Objects.requireNonNull(type2, () -> t2.getName() + " has a null exclusion type");

			return Integer.compare(type1.ordinal(), type2.ordinal());
		});

		Main.infoNewline();
		Main.info(Strings.START_EXECUTION);
		Main.infoNewline();
		transformers.stream().filter(Objects::nonNull).forEach(transformer ->
		{
			final long nanoTime = System.nanoTime();
			Main.info(String.format("Running %s transformer.", transformer.getName()));
			Main.infoNewline();
			transformer.init(this);
			transformer.transform();
			Main.infoNewline();
			Main.info(String.format("Finished running %s transformer. [%s]", transformer.getName(), Transformer.tookThisLong(nanoTime)));
			Main.infoNewline();
			Main.info(Strings.EXECUTION_SEPARATOR);
			Main.infoNewline();
		});
		Main.info(Strings.END_EXECUTION);
		Main.infoNewline();

		Main.infoNewline();
		Main.info(Strings.START_WRITING);
		Main.infoNewline();
		writeOutput();
		Main.infoNewline();
		Main.info(Strings.END_WRITING);
		Main.infoNewline();
	}

	private void writeOutput()
	{
		final File output = config.getOutput();
		final long nanoTime = System.nanoTime();
		Main.info(String.format("Writing output to \"%s\".", output.getAbsolutePath()));

		if (output.exists())
			Main.info(String.format("*** Output file already exists, renamed to %s.", FileUtils.renameExistingFile(output)));

		try
		{
			final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
			zos.setLevel(config.getCompressionLevel());
			Main.info(String.format("+ Output jar compression level is %d.", config.getCompressionLevel()));

			if (config.isCorruptCrc())
				try
				{
					final Field crcField = ZipOutputStream.class.getDeclaredField("crc");
					crcField.setAccessible(true);
					crcField.set(zos, new CRC32Corrupter());

					Main.info("+ Injected CRC corrupter.");
				}
				catch (final Exception e)
				{
					e.printStackTrace();
					Main.severe("*** Failed to inject CRC corrupter.");
				}

			classes.values().forEach(classWrapper ->
			{
				try
				{
					final ZipEntry entry = new ZipEntry(classWrapper.getEntryName());

					zos.putNextEntry(entry);
					zos.write(classWrapper.toByteArray(this));
					zos.closeEntry();
				}
				catch (final Exception t)
				{
					Main.severe(String.format("*** Error writing class %s. Skipping.", classWrapper.getName() + ".class"));
					t.printStackTrace();
				}
			});

			resources.forEach((name, bytes) ->
			{
				try
				{
					final ZipEntry entry = new ZipEntry(name);

					zos.putNextEntry(entry);
					zos.write(bytes);
					zos.closeEntry();
				}
				catch (final IOException ioe)
				{
					Main.severe(String.format("*** Error writing resource %s. Skipping.", name));
					ioe.printStackTrace();
				}
			});

			zos.setComment(Main.ATTRIBUTION);
			zos.close();
		}
		catch (final IOException ioe)
		{
			throw new RadonException(ioe);
		}

		Main.info(Transformer.tookThisLong(nanoTime));
	}

	private void loadClassPath()
	{
		config.getLibraries().forEach(file ->
		{
			if (file.exists())
			{
				Main.info(String.format("+ Loading library \"%s\".", file.getAbsolutePath()));

				try (final ZipFile zipFile = new ZipFile(file))
				{
					final Enumeration<? extends ZipEntry> entries = zipFile.entries();

					while (entries.hasMoreElements())
					{
						final ZipEntry entry = entries.nextElement();

						if (!entry.isDirectory() && entry.getName().endsWith(".class"))
							try
							{
								final ClassWrapper cw = new ClassWrapper(new ClassReader(zipFile.getInputStream(entry)), true);
								classPath.put(cw.getName(), cw);
							}
							catch (final Throwable t)
							{
								Main.severe(String.format("*** Error while loading library class \"%s\".", entry.getName()));
								t.printStackTrace();
							}
					}
				}
				catch (final ZipException e)
				{
					Main.severe(String.format("*** Library \"%s\" could not be opened as a zip file.", file.getAbsolutePath()));
					e.printStackTrace();
				}
				catch (final IOException e)
				{
					Main.severe(String.format("*** IOException happened while trying to load classes from \"%s\".", file.getAbsolutePath()));
					e.printStackTrace();
				}
			}
			else
				Main.warning(String.format("*** Library \"%s\" could not be found and will be ignored.", file.getAbsolutePath()));
		});
	}

	private void loadInput()
	{
		final File input = config.getInput();

		if (input.exists())
		{
			Main.info(String.format("+ Loading input \"%s\".", input.getAbsolutePath()));

			try (final ZipFile zipFile = new ZipFile(input))
			{
				final Enumeration<? extends ZipEntry> entries = zipFile.entries();

				while (entries.hasMoreElements())
				{
					final ZipEntry entry = entries.nextElement();
					final InputStream in = zipFile.getInputStream(entry);

					if (!entry.isDirectory())
						if (entry.getName().endsWith(".class"))
							try
							{
								final ClassWrapper cw = new ClassWrapper(new ClassReader(in), false);

								if (cw.getVersion() <= Opcodes.V1_5)
									IntStream.range(0, cw.getMethods().size()).forEach(i ->
									{
										final MethodNode methodNode = cw.getMethods().get(i).getMethodNode();
										final JSRInlinerAdapter adapter = new JSRInlinerAdapter(methodNode, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
										methodNode.accept(adapter);
										cw.getMethods().get(i).setMethodNode(adapter);
									});

								classPath.put(cw.getName(), cw);
								classes.put(cw.getName(), cw);

								final String entryName = entry.getName();
								final String wrapperEntryName = cw.getEntryName();
								if (entryName.endsWith(wrapperEntryName) && !entryName.equals(wrapperEntryName))
									cw.setEntryPrefix(entry.getName().substring(0, entryName.length() - wrapperEntryName.length()));
							}
							catch (final Throwable t)
							{
								Main.warning(String.format("*** Could not load %s as a class and will be loaded as resource.", entry.getName()));
								resources.put(entry.getName(), IOUtils.toByteArray(in));
							}
						else
							resources.put(entry.getName(), IOUtils.toByteArray(in));
				}
			}
			catch (final ZipException e)
			{
				Main.severe(String.format("*** Input file \"%s\" could not be opened as a zip file.", input.getAbsolutePath()));
				e.printStackTrace();
				throw new RadonException(e);
			}
			catch (final IOException e)
			{
				Main.severe(String.format("*** IOException happened while trying to load classes from \"%s\".", input.getAbsolutePath()));
				e.printStackTrace();
				throw new RadonException(e);
			}
		}
		else
		{
			Main.severe(String.format("*** Unable to find file \"%s\".", input.getAbsolutePath()));
			throw new RadonException(new FileNotFoundException(input.getAbsolutePath()));
		}
	}

	/**
	 * Finds {@link ClassWrapper} with given name.
	 *
	 * @return                {@link ClassWrapper}.
	 *
	 * @throws RadonException
	 *                        if not found.
	 */
	public ClassWrapper getClassWrapper(final String ref)
	{
		if (!classPath.containsKey(ref))
			throw new RadonException("Could not find " + ref);

		return classPath.get(ref);
	}

	/**
	 * Finds {@link ClassTree} with given name.
	 *
	 * @return                {@link ClassTree}.
	 *
	 * @throws RadonException
	 *                        if there are missing classes needed to build the inheritance tree.
	 */
	public ClassTree getTree(final String ref)
	{
		if (!hierarchy.containsKey(ref))
		{
			final ClassWrapper wrapper = getClassWrapper(ref);
			buildHierarchy(wrapper, null);
		}

		return hierarchy.get(ref);
	}

	private void buildHierarchy(final ClassWrapper wrapper, final ClassWrapper sub)
	{
		if (hierarchy.get(wrapper.getName()) == null)
		{
			final ClassTree tree = new ClassTree(wrapper);

			if (wrapper.getSuperName() != null)
			{
				tree.getParentClasses().add(wrapper.getSuperName());

				buildHierarchy(getClassWrapper(wrapper.getSuperName()), wrapper);
			}
			if (wrapper.getInterfaces() != null)
				wrapper.getInterfaces().forEach(s ->
				{
					tree.getParentClasses().add(s);

					buildHierarchy(getClassWrapper(s), wrapper);
				});

			hierarchy.put(wrapper.getName(), tree);
		}

		if (sub != null)
			hierarchy.get(wrapper.getName()).getSubClasses().add(sub.getName());
	}

	public void buildInheritance()
	{
		classes.values().forEach(classWrapper -> buildHierarchy(classWrapper, null));
	}

	/**
	 * Equivalent to the following: Class clazz1 = something; Class class2 = somethingElse; return class1.isAssignableFrom(class2);
	 */
	public boolean isAssignableFrom(final String type1, final String type2)
	{
		if ("java/lang/Object".equals(type1))
			return true;
		if (type1.equals(type2))
			return true;

		getClassWrapper(type1);
		getClassWrapper(type2);

		final ClassTree firstTree = getTree(type1);
		if (firstTree == null)
			throw new MissingClassException("Could not find " + type1 + " in the built class hierarchy");

		final Collection<String> allChildren = new HashSet<>();
		final Deque<String> toProcess = new ArrayDeque<>(firstTree.getSubClasses());
		while (!toProcess.isEmpty())
		{
			final String s = toProcess.poll();

			if (allChildren.add(s))
			{
				getClassWrapper(s);
				final ClassTree tempTree = getTree(s);
				toProcess.addAll(tempTree.getSubClasses());
			}
		}
		return allChildren.contains(type2);
	}

	public ObfuscationConfiguration getConfig()
	{
		return config;
	}

	private static class CRC32Corrupter extends CRC32
	{
		@Override
		public void update(final byte[] b, final int off, final int len)
		{
			// Don't update the CRC
		}

		@Override
		public final long getValue()
		{
			return RandomUtils.getRandomLong(0xFFFFFFFFL);
		}

		CRC32Corrupter()
		{
		}
	}
}
