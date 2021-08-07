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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import me.itzsomebody.radon.asm.*;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.FileUtils;

import static me.itzsomebody.radon.config.ConfigurationSetting.RENAMER;

/**
 * Transformer which renames classes and their members.
 *
 * @author ItzSomebody
 */
public class Renamer extends Transformer
{
	private List<String> adaptTheseResources;
	private boolean dumpMappings;
	private String repackageName;
	private Map<String, String> mappings;
	private List<String> mappingsToDump;

	private static boolean methodCanBeRenamed(final MethodWrapper wrapper)
	{
		return !wrapper.getAccess().isNative() && !"main".equals(wrapper.getOriginalName()) && !"premain".equals(wrapper.getOriginalName()) && !(!wrapper.getOriginalName().isEmpty() && wrapper.getOriginalName().charAt(0) == '<');
	}

	@Override
	public void transform()
	{
		radon.buildInheritance();
		mappings = new HashMap<>();
		if (dumpMappings)
			mappingsToDump = new ArrayList<>();
		final Map<String, String> packageMappings = new HashMap<>();

		info("Generating mappings.");
		long current = System.nanoTime();

		getClassWrappers().forEach(classWrapper ->
		{
			classWrapper.getMethods().stream().filter(Renamer::methodCanBeRenamed).forEach(methodWrapper ->
			{
				final Set<String> visited = new HashSet<>();

				if (!cannotRenameMethod(radon.getTree(classWrapper.getOriginalName()), methodWrapper, visited))
					genMethodMappings(methodWrapper, methodWrapper.getOwner().getOriginalName(), methodDictionary.nextUniqueString());
			});

			classWrapper.getFields().forEach(fieldWrapper ->
			{
				final Set<String> visited = new HashSet<>();

				if (!cannotRenameField(radon.getTree(classWrapper.getOriginalName()), fieldWrapper, visited))
					genFieldMappings(fieldWrapper, fieldWrapper.getOwner().getOriginalName(), fieldDictionary.nextUniqueString());
			});

			if (included(classWrapper))
			{
				String newName;

				if (repackageName == null)
				{
					final String mappedPackageName = packageDictionary.randomString();

					packageMappings.putIfAbsent(classWrapper.getPackageName(), mappedPackageName);
					newName = packageMappings.get(classWrapper.getPackageName());
				}
				else
					newName = repackageName;

				if (!newName.isEmpty())
					newName += '/' + classDictionary.nextUniqueString();
				else
					newName = classDictionary.nextUniqueString();

				mappings.put(classWrapper.getOriginalName(), newName);
				if (dumpMappings)
					mappingsToDump.add(String.format("Class: %1$s -> %2$s", classWrapper.getOriginalName(), newName));
			}
		});

		info(String.format("Finished generated mappings. [%s]", tookThisLong(current)));
		info("Applying mappings.");
		current = System.nanoTime();

		// Apply mappings
		final Remapper simpleRemapper = new MemberRemapper(mappings);
		new ArrayList<>(getClassWrappers()).forEach(classWrapper ->
		{
			final ClassNode classNode = classWrapper.getClassNode();

			final ClassNode copy = new ClassNode();
			classNode.accept(new ClassRemapper(copy, simpleRemapper));

			// In order to preserve the original names to prevent exclusions from breaking, we update the MethodNode/FieldNode/ClassNode each wrapper wraps instead.
			IntStream.range(0, copy.methods.size()).forEach(i -> classWrapper.getMethods().get(i).setMethodNode(copy.methods.get(i)));
			IntStream.range(0, copy.fields.size()).forEach(i -> classWrapper.getFields().get(i).setFieldNode(copy.fields.get(i)));

			classWrapper.setClassNode(copy);

			getClasses().remove(classWrapper.getOriginalName());
			getClasses().put(classWrapper.getName(), classWrapper);
			getClassPath().put(classWrapper.getName(), classWrapper);
		});

		info(String.format("Mapped %d members. [%s]", mappings.size(), tookThisLong(current)));
		current = System.nanoTime();

		// Now we gotta fix those resources because we probably screwed up random files.
		info("Attempting to map class names in resources");
		final AtomicInteger fixed = new AtomicInteger();
		getResources().forEach((name, byteArray) -> adaptTheseResources.forEach(s ->
		{
			final Pattern pattern = Pattern.compile(s);

			if (pattern.matcher(name).matches())
			{
				String stringVer = new String(byteArray, StandardCharsets.UTF_8);

				for (final String mapping : mappings.keySet())
				{
					final String original = mapping.replace("/", ".");
					// Regex that ensures that class names that match words in the manifest don't break the
					// manifest.
					// Example: name == Main
					if (stringVer.contains(original))
						if ("META-INF/MANIFEST.MF".equals(name) // Manifest
								|| "plugin.yml".equals(name) // Spigot plugin
								|| "bungee.yml".equals(name)) // Bungeecord plugin
							stringVer = stringVer.replaceAll("(?<=[: ])" + original, mappings.get(mapping).replace("/", "."));
						else
							stringVer = stringVer.replace(original, mappings.get(mapping).replace("/", "."));
				}

				getResources().put(name, stringVer.getBytes(StandardCharsets.UTF_8));
				fixed.incrementAndGet();
			}
		}));

		info(String.format("Mapped %d names in resources. [%s]", fixed.get(), tookThisLong(current)));

		if (dumpMappings)
			dumpMappings();
	}

	private void genMethodMappings(final MethodWrapper mw, final String owner, final String newName)
	{
		final String originalName = mw.getOriginalName();
		final String originalDesc = mw.getOriginalDescription();
		final String key = owner + '.' + originalName + originalDesc;

		// This (supposedly) will always stop the recursion because the tree was already renamed
		if (mappings.containsKey(key))
			return;

		mappings.put(key, newName);

		if (dumpMappings)
		{
			final ClassWrapper classWrapper = radon.getTree(owner).getClassWrapper();
			if (included(classWrapper) && classWrapper.getMethods().stream().filter(this::included).anyMatch(m -> originalName.equals(m.getOriginalName()) && originalDesc.equals(m.getOriginalDescription())))
				mappingsToDump.add(String.format("Method: %1$s.%2$s%3$s -> %4$s", owner, originalName, originalDesc, newName));
		}

		final ClassTree tree = radon.getTree(owner);

		// Static methods can't be overridden, BUT sometimes it's accessed via instance.
		// Recursively rename parents and sub-methods
		tree.getParentClasses().forEach(parentClass -> genMethodMappings(mw, parentClass, newName));
		tree.getSubClasses().forEach(subClass -> genMethodMappings(mw, subClass, newName));
	}

	private boolean cannotRenameMethod(final ClassTree tree, final MethodWrapper wrapper, final Set<? super String> visited)
	{
		final ClassWrapper cw = tree.getClassWrapper();
		final String originalName = wrapper.getOriginalName();
		final String originalDesc = wrapper.getOriginalDescription();

		final String check = cw.getOriginalName() + '.' + originalName + originalDesc;

		// Don't check these
		if (visited.contains(check))
			return false;

		visited.add(check);

		// If excluded, we don't want to rename.
		if (!included(check)
				// If we already mapped the tree, we don't want to waste time doing it again.
				|| mappings.containsKey(check))
			return true;

		// Methods which are static don't need to be checked for inheritance
		if (!wrapper.getAccess().isStatic())
			// We can't rename members which inherit methods from external libraries
			return cw != wrapper.getOwner() && cw.isLibraryNode() && cw.getMethods().stream().anyMatch(mw -> mw.getOriginalName().equals(originalName) && mw.getOriginalDescription().equals(originalDesc))
					// Recursively check parents and sub-methods
					|| tree.getParentClasses().stream().anyMatch(parent -> cannotRenameMethod(radon.getTree(parent), wrapper, visited)) || tree.getSubClasses().stream().anyMatch(sub -> cannotRenameMethod(radon.getTree(sub), wrapper, visited));

		// Enum.valueOf() and Enum.values() are must not be renamed
		return cw.getAccess().isEnum() && ("valueOf".equals(originalName) || "values".equals(originalName));
	}

	private void genFieldMappings(final FieldWrapper fw, final String owner, final String newName)
	{
		final String originalName = fw.getOriginalName();
		final String originalDesc = fw.getOriginalDescription();
		final String check = String.join(".", owner, originalName, originalDesc);

		// This (supposedly) will always stop the recursion because the tree was already renamed
		if (mappings.containsKey(check))
			return;

		mappings.put(check, newName);

		if (dumpMappings)
		{
			final ClassWrapper classWrapper = radon.getTree(owner).getClassWrapper();
			if (included(classWrapper) && classWrapper.getFields().stream().filter(this::included).anyMatch(m -> originalName.equals(m.getOriginalName()) && originalDesc.equals(m.getOriginalDescription())))
				mappingsToDump.add(String.format("Field: %1$s.%2$s.%3$s -> %4$s", owner, originalName, originalDesc, newName));
		}

		// Static fields can't be inherited
		if (!fw.getAccess().isStatic())
		{
			final ClassTree tree = radon.getTree(owner);

			// Recursively check parents and sub-fields
			tree.getParentClasses().forEach(parentClass -> genFieldMappings(fw, parentClass, newName));
			tree.getSubClasses().forEach(subClass -> genFieldMappings(fw, subClass, newName));
		}
	}

	private boolean cannotRenameField(final ClassTree tree, final FieldWrapper wrapper, final Set<? super String> visited)
	{
		final String check = tree.getClassWrapper().getOriginalName() + '.' + wrapper.getOriginalName() + '.' + wrapper.getOriginalDescription();

		// Don't check these
		if (visited.contains(check))
			return false;

		visited.add(check);

		// If excluded, we don't want to rename.
		return !included(check)
				// If we already mapped the tree, we don't want to waste time doing it again.
				|| mappings.containsKey(check)
				// Fields which are static don't need to be checked for inheritance
				|| !wrapper.getAccess().isStatic()
						// We can't rename members which inherit methods from external libraries
						&& (tree.getClassWrapper() != wrapper.getOwner() && tree.getClassWrapper().isLibraryNode() && tree.getClassWrapper().getFields().stream().anyMatch(fw -> fw.getOriginalName().equals(wrapper.getOriginalName()) && fw.getOriginalDescription().equals(wrapper.getOriginalDescription()))
								// Recursively check parents and sub-fields
								|| tree.getParentClasses().stream().anyMatch(parent -> cannotRenameField(radon.getTree(parent), wrapper, visited)) || tree.getSubClasses().stream().anyMatch(sub -> cannotRenameField(radon.getTree(sub), wrapper, visited)));
	}

	private void dumpMappings()
	{
		final long current = System.currentTimeMillis();
		info("Dumping mappings.");
		final File file = new File("mappings.txt");
		if (file.exists())
			FileUtils.renameExistingFile(file);

		try
		{
			file.createNewFile(); // TODO: handle this properly
			final BufferedWriter bw = new BufferedWriter(new FileWriter(file));

			mappingsToDump.forEach(str ->
			{
				try
				{
					bw.append(str).append('\n');
				}
				catch (final IOException ioe)
				{
					severe(String.format("Ran into an error trying to append \"%s\"", str), ioe);
				}
			});

			bw.close();
			info(String.format("Finished dumping mappings at %s. [%s]", file.getAbsolutePath(), tookThisLong(current)));
		}
		catch (final Throwable t)
		{
			severe("Ran into an error trying to create the mappings file.", t);
		}
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.RENAMER;
	}

	@Override
	public String getName()
	{
		return "Renamer";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		adaptTheseResources = config.getOrDefault(RENAMER + ".adapt_these_resources", Collections.emptyList());
		dumpMappings = config.getOrDefault(RENAMER + ".dump_mappings", false);
		repackageName = config.getOrDefault(RENAMER + ".repackage_name", null);
	}

	public List<String> getAdaptTheseResources()
	{
		return adaptTheseResources;
	}

	public void setAdaptTheseResources(final List<String> adaptTheseResources)
	{
		this.adaptTheseResources = adaptTheseResources;
	}

	private boolean isDumpMappings()
	{
		return dumpMappings;
	}

	private void setDumpMappings(final boolean dumpMappings)
	{
		this.dumpMappings = dumpMappings;
	}

	private String getRepackageName()
	{
		return repackageName;
	}

	private void setRepackageName(final String repackageName)
	{
		this.repackageName = repackageName;
	}
}
