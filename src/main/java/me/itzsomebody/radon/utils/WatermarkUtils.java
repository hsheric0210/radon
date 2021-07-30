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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

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
		classes.values().forEach(classNode -> classNode.methods.forEach(methodNode -> Arrays.stream(methodNode.instructions.toArray()).filter(insn -> ASMUtils.isIntInsn(insn) && ASMUtils.isIntInsn(insn.getNext()) && ASMUtils.isIntInsn(insn.getNext().getNext()) && ASMUtils.isIntInsn(insn.getNext().getNext().getNext()) && insn.getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE && insn.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null && insn.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE).forEach(insn ->
		{
			final char character = (char) (ASMUtils.getIntegerFromInsn(insn) ^ ASMUtils.getIntegerFromInsn(insn.getNext()));
			final int index = ASMUtils.getIntegerFromInsn(insn.getNext().getNext()) ^ ASMUtils.getIntegerFromInsn(insn.getNext().getNext().getNext());
			embedMap.put(index, character);
		})));

		return enoughInfo(embedMap) ? decrypt(constructString(embedMap), key) : "No IDs found.";
	}

	private static boolean enoughInfo(final Map<Integer, Character> embedMap)
	{
		return embedMap.size() >= 1 && IntStream.range(0, embedMap.size()).allMatch(embedMap::containsKey);
	}

	private static String constructString(final Map<Integer, Character> embedMap)
	{
		return IntStream.range(0, embedMap.size()).mapToObj(i -> String.valueOf((char) embedMap.get(i))).collect(Collectors.joining());
	}

	private static String decrypt(final String enc, final String key)
	{
		final char[] messageChars = enc.toCharArray();
		final char[] keyChars = key.toCharArray();
		final StringBuilder sb = new StringBuilder();

		for (int i = 0, j = messageChars.length; i < j; i++)
			sb.append((char) (messageChars[i] ^ keyChars[i % keyChars.length]));

		return sb.toString();
	}

	private WatermarkUtils()
	{
	}
}
