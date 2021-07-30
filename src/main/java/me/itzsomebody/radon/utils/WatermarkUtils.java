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

package me.itzsomebody.radon.utils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * used to extract a watermark embedded by Radon. Prepare your eyes for cancer code.
 *
 * @author ItzSomebody
 */
public final class WatermarkUtils
{
	public static String extractIds(final ZipFile zipFile, final String key) throws Throwable
	{
		final Enumeration<? extends ZipEntry> entries = zipFile.entries();
		final Map<String, ClassNode> classes = new HashMap<>();
		try
		{
			while (entries.hasMoreElements())
			{
				final ZipEntry entry = entries.nextElement();
				if (!entry.isDirectory() && entry.getName().endsWith(".class"))
					try
					{
						final ClassReader cr = new ClassReader(zipFile.getInputStream(entry));
						final ClassNode classNode = new ClassNode();
						cr.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
						classes.put(classNode.name, classNode);
					}
					catch (final Throwable t)
					{
						// Ignored
					}
			}
		}
		finally
		{
			zipFile.close();
		}

		final Map<Integer, Character> embedMap = new LinkedHashMap<>();
		for (final ClassNode classNode : classes.values())
			for (final MethodNode methodNode : classNode.methods)
				for (final AbstractInsnNode insn : methodNode.instructions.toArray())
					if (ASMUtils.isIntInsn(insn) && ASMUtils.isIntInsn(insn.getNext()) && ASMUtils.isIntInsn(insn.getNext().getNext()) && ASMUtils.isIntInsn(insn.getNext().getNext().getNext()) && insn.getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE)
					{
						final char character = (char) (ASMUtils.getIntegerFromInsn(insn) ^ ASMUtils.getIntegerFromInsn(insn.getNext()));
						final int index = ASMUtils.getIntegerFromInsn(insn.getNext().getNext()) ^ ASMUtils.getIntegerFromInsn(insn.getNext().getNext().getNext());
						embedMap.put(index, character);
					}
		if (enoughInfo(embedMap))
			return decrypt(constructString(embedMap), key);

		return "No IDs found.";
	}

	private static boolean enoughInfo(final Map<Integer, Character> embedMap)
	{
		if (embedMap.size() < 1)
			return false;

		return IntStream.range(0, embedMap.size()).allMatch(embedMap::containsKey);
	}

	private static String constructString(final Map<Integer, Character> embedMap)
	{
		return IntStream.range(0, embedMap.size()).mapToObj(i -> String.valueOf((char) embedMap.get(i))).collect(Collectors.joining());
	}

	private static String decrypt(final String enc, final String key)
	{
		final char[] messageChars = enc.toCharArray();
		final char[] keyChars = key.toCharArray();

		return IntStream.range(0, messageChars.length).mapToObj(i -> String.valueOf((char) (messageChars[i] ^ keyChars[i % keyChars.length]))).collect(Collectors.joining());
	}

	private WatermarkUtils()
	{
	}
}
