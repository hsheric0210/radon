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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import me.itzsomebody.radon.asm.*;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.Constants;
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
	private Map<String, String> reverseMappings; // To check member duplications
	private List<String> mappingsToDump;

	private static boolean methodCanBeRenamed(final MethodWrapper wrapper)
	{
		return !wrapper.access.isNative() && !"main".equals(wrapper.originalName) && !"premain".equals(wrapper.originalName) && !(!wrapper.originalName.isEmpty() && wrapper.originalName.charAt(0) == '<');
	}

	@Override
	public void transform()
	{
		radon.buildInheritance();
		mappings = new HashMap<>();
		reverseMappings = new HashMap<>();

		if (dumpMappings)
			mappingsToDump = new ArrayList<>();

		final Map<String, String> packageMappings = new HashMap<>();

		info("Generating mappings.");
		long current = System.nanoTime();

		getClassWrappers().forEach(classWrapper ->
		{
			classWrapper.methods.stream().filter(Renamer::methodCanBeRenamed).forEach(methodWrapper ->
			{
				final Set<String> visited = new HashSet<>();

				if (!cannotRenameMethod(radon.getTree(classWrapper.originalName), methodWrapper, visited))
					genMethodMappings(methodWrapper, methodWrapper.owner.originalName, getMethodDictionary(null /* TODO: Override methods agressively */).nextUniqueString());
			});

			classWrapper.fields.forEach(fieldWrapper ->
			{
				final Set<String> visited = new HashSet<>();

				if (!cannotRenameField(radon.getTree(classWrapper.originalName), fieldWrapper, visited))
					genFieldMappings(fieldWrapper, fieldWrapper.owner.originalName, getFieldDictionary(null /* TODO: Override fields agressively */).nextUniqueString());
			});

			if (included(classWrapper))
			{
				String newName;

				if (repackageName == null)
				{
					final String originalPackageName = classWrapper.getPackageName();
					final String realOriginalPackageName = originalPackageName.substring(0, originalPackageName.length() - 1); // Drop the trailing '/' character

					// Generate package mappings
					final StringJoiner mappingKeyBuilder = new StringJoiner("/");
					for (final String packagePiece : Constants.SLASH_PATTERN.split(realOriginalPackageName))
					{
						final String parentPackage = mappingKeyBuilder.toString(); // Empty if the parent is ROOT

						mappingKeyBuilder.add(packagePiece);
						final String key = mappingKeyBuilder.toString();
						if (!packageMappings.containsKey(key))
						{
							packageMappings.put(key, getPackageDictionary(parentPackage).nextUniqueString());
							verboseInfo(() -> "Package '" + key + "' mapped to '" + packageMappings.get(key) + "'");
						}
					}

					// Retrieve and apply package mappings
					int index = 0;
					final StringJoiner newNameJoiner = new StringJoiner("/");
					for (final String packagePiece : Constants.SLASH_PATTERN.split(realOriginalPackageName))
					{
						index += packagePiece.length() + 1; // '+ 1' is required because of the trailing '/' char
						final String key = originalPackageName.substring(0, index);
						newNameJoiner.add(packageMappings.getOrDefault(key.substring(0, key.length() - 1), packagePiece));
					}

					newName = newNameJoiner.toString();

//					newName = packageMappings.computeIfAbsent(classWrapper.getPackageName(), n -> getPackageDictionary(classWrapper.getOriginalPackageName()).nextUniqueString());
				}
				else
					newName = repackageName;

				if (newName.isEmpty())
					newName = getClassDictionary(newName).nextUniqueString();
				else
					newName += '/' + getClassDictionary(newName).nextUniqueString();

				mappings.put(classWrapper.originalName, newName);
				if (dumpMappings)
					mappingsToDump.add(String.format("Class: %1$s -> %2$s", classWrapper.originalName, newName));
			}
		});

		info(String.format("Finished generated mappings. [%s]", tookThisLong(current)));
		info("Applying mappings.");
		current = System.nanoTime();

		// Apply mappings
		final Remapper simpleRemapper = new MemberRemapper(mappings);
		new ArrayList<>(getClassWrappers()).forEach(classWrapper ->
		{
			final ClassNode classNode = classWrapper.classNode;

			final ClassNode copy = new ClassNode();
			classNode.accept(new ClassRemapper(copy, simpleRemapper));

			// In order to preserve the original names to prevent exclusions from breaking, we update the MethodNode/FieldNode/ClassNode each wrapper wraps instead.
			IntStream.range(0, copy.methods.size()).forEach(i -> classWrapper.methods.get(i).methodNode = copy.methods.get(i));
			IntStream.range(0, copy.fields.size()).forEach(i -> classWrapper.fields.get(i).fieldNode = copy.fields.get(i));

			classWrapper.classNode = copy;

			getClasses().remove(classWrapper.originalName);
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
							stringVer = stringVer.replaceAll("(?<=[: ])" + original, Matcher.quoteReplacement(mappings.get(mapping).replace("/", ".")));
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
		final String originalName = mw.originalName;
		final String originalDesc = mw.originalDescription;
		final String key = owner + '.' + originalName + originalDesc;
		final String reverseKey = owner + '.' + newName + originalDesc;

		// This (supposedly) will always stop the recursion because the tree was already renamed
		if (mappings.containsKey(key))
			return;

		mappings.put(key, newName);
		reverseMappings.put(reverseKey, originalName);

		final ClassTree tree = radon.getTree(owner);

		if (dumpMappings)
		{
			final ClassWrapper cw = tree.classWrapper;
			if (included(cw) && cw.methods.stream().filter(this::included).anyMatch(m -> originalName.equals(m.originalName) && originalDesc.equals(m.originalDescription)))
				mappingsToDump.add(String.format("Method: %1$s.%2$s%3$s -> %4$s", owner, originalName, originalDesc, newName));
		}

		// Static methods can't be overridden, BUT sometimes it can be accessed via instance.
		tree.parentClasses.forEach(parentClass -> genMethodMappings(mw, parentClass, newName));
		tree.subClasses.forEach(subClass -> genMethodMappings(mw, subClass, newName));
	}

	private boolean cannotRenameMethod(final ClassTree tree, final MethodWrapper wrapper, final Set<? super String> visited)
	{
		final ClassWrapper cw = tree.classWrapper;
		final String originalClassName = cw.originalName;
		final String originalName = wrapper.originalName;
		final String originalDesc = wrapper.originalDescription;

		final String check = originalClassName + '.' + originalName + originalDesc;

		// Don't check these
		if (visited.contains(check))
			return false;

		visited.add(check);

		// If excluded, we don't want to rename.
		if (!included(check) && cw.methods.stream().anyMatch(m -> originalName.equals(m.originalName) && originalDesc.equals(m.originalDescription)) || mappings.containsKey(check))
			return true;

		// Methods which are static don't need to be checked for inheritance
		if (!wrapper.access.isStatic())
		// We can't rename members which inherit methods from external libraries
		{
			return cw != wrapper.owner && cw.libraryNode && cw.methods.stream().anyMatch(mw -> mw.originalName.equals(originalName) && mw.originalDescription.equals(originalDesc)) || tree.parentClasses.stream().anyMatch(parent -> cannotRenameMethod(radon.getTree(parent), wrapper, visited)) || tree.subClasses.stream().anyMatch(sub -> cannotRenameMethod(radon.getTree(sub), wrapper, visited));
		}

		// Enum.valueOf() and Enum.values() are must not be renamed
		return cw.access.isEnum() && ("valueOf".equals(originalName) || "values".equals(originalName));
	}

	private void genFieldMappings(final FieldWrapper fw, final String owner, final String newName)
	{
		final String originalName = fw.originalName;
		final String originalDesc = fw.originalDescription;
		final String check = String.join(".", owner, originalName, originalDesc);

		// This (supposedly) will always stop the recursion because the tree was already renamed
		if (mappings.containsKey(check))
			return;

		mappings.put(check, newName);

		final ClassTree tree = radon.getTree(owner);

		if (dumpMappings)
		{
			final ClassWrapper cw = tree.classWrapper;
			if (included(cw) && cw.fields.stream().filter(this::included).anyMatch(f -> originalName.equals(f.originalName) && originalDesc.equals(f.originalDescription)))
				mappingsToDump.add(String.format("Field: %1$s.%2$s.%3$s -> %4$s", owner, originalName, originalDesc, newName));
		}

		// Static fields can't be inherited, BUT sometimes it can be accessed via instance.
		tree.parentClasses.forEach(parentClass -> genFieldMappings(fw, parentClass, newName));
		tree.subClasses.forEach(subClass -> genFieldMappings(fw, subClass, newName));
	}

	private boolean cannotRenameField(final ClassTree tree, final FieldWrapper wrapper, final Set<? super String> visited)
	{
		final ClassWrapper cw = tree.classWrapper;
		final String originalName = wrapper.originalName;
		final String originalDesc = wrapper.originalDescription;
		final String check = cw.originalName + '.' + originalName + '.' + originalDesc;

		// Don't check these
		if (visited.contains(check))
			return false;

		visited.add(check);

		// If excluded, we don't want to rename.
		return !included(check) && cw.fields.stream().anyMatch(f -> originalName.equals(f.originalName) && originalDesc.equals(f.originalDescription))
				// If we already mapped the tree, we don't want to waste time doing it again.
				|| mappings.containsKey(check)
				// Fields which are static don't need to be checked for inheritance
				|| !wrapper.access.isStatic()
						// We can't rename members which inherit methods from external libraries
						&& (cw != wrapper.owner && cw.libraryNode && cw.fields.stream().anyMatch(fw -> fw.originalName.equals(originalName) && fw.originalDescription.equals(originalDesc))
								// Recursively check parents and sub-fields
								|| tree.parentClasses.stream().anyMatch(parent -> cannotRenameField(radon.getTree(parent), wrapper, visited)) || tree.subClasses.stream().anyMatch(sub -> cannotRenameField(radon.getTree(sub), wrapper, visited)));
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
			file.createNewFile();
			final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));

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
}
