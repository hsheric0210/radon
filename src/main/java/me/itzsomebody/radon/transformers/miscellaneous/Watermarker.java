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

package me.itzsomebody.radon.transformers.miscellaneous;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.asm.MethodWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

import static me.itzsomebody.radon.config.ConfigurationSetting.WATERMARK;

/**
 * Embeds a watermark into random classes.
 *
 * @author ItzSomebody.
 */
public class Watermarker extends Transformer
{
	private String message;
	private String key;

	@Override
	public void transform()
	{
		final ArrayList<ClassWrapper> classWrappers = new ArrayList<>(getClassWrappers());

		for (int i = 0; i < 3; i++)
		{ // Two extra injections helps with reliability of watermark to be extracted
			final Deque<Character> watermark = cipheredWatermark();
			while (!watermark.isEmpty())
			{
				ClassWrapper classWrapper;
				int counter = 0;

				do
				{
					classWrapper = classWrappers.get(RandomUtils.getRandomInt(0, classWrappers.size()));
					counter++;

					if (counter > 20)
						throw new IllegalStateException("Radon couldn't find any methods to embed a watermark in after " + counter + " tries.");
				} while (classWrapper.getMethods().size() == 0);

				final MethodWrapper mw = classWrapper.getMethods().get(RandomUtils.getRandomInt(0, classWrapper.getClassNode().methods.size()));
				if (mw.hasInstructions())
				{
					mw.getInstructions().insert(createInstructions(watermark, mw.getMaxLocals()));
					mw.setMaxLocals(mw.getMaxLocals());
				}
			}
		}

		Main.info("Successfully embedded watermark.");
	}

	private static InsnList createInstructions(final Deque<Character> watermark, final int offset)
	{
		final int xorKey = RandomUtils.getRandomInt();
		final int watermarkChar = watermark.pop() ^ xorKey;
		final int indexXorKey = RandomUtils.getRandomInt();
		final int watermarkIndex = watermark.size() ^ indexXorKey;

		final InsnList instructions = new InsnList();
		instructions.add(ASMUtils.getNumberInsn(xorKey));
		instructions.add(ASMUtils.getNumberInsn(watermarkChar));
		instructions.add(ASMUtils.getNumberInsn(indexXorKey));
		instructions.add(ASMUtils.getNumberInsn(watermarkIndex));

		// Local variable x where x is the max locals allowed in method can be the top of a long or double so we add 1
		instructions.add(new VarInsnNode(ISTORE, offset + 1));
		instructions.add(new VarInsnNode(ISTORE, offset + 2));
		instructions.add(new VarInsnNode(ISTORE, offset + 3));
		instructions.add(new VarInsnNode(ISTORE, offset + 4));

		return instructions;
	}

	// Really weak cipher, lul.
	private Deque<Character> cipheredWatermark()
	{
		final char[] messageChars = getMessage().toCharArray();
		final char[] keyChars = getKey().toCharArray();
		final Deque<Character> returnThis = new ArrayDeque<>();

		for (int i = 0; i < messageChars.length; i++)
			returnThis.push((char) (messageChars[i] ^ keyChars[i % keyChars.length]));

		return returnThis;
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.WATERMARKER;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		setMessage(config.getOrDefault(WATERMARK + ".message", "blah"));
		setKey(config.getOrDefault(WATERMARK + ".key", "blah"));
	}

	@Override
	public String getName()
	{
		return "Watermarker";
	}

	private String getMessage()
	{
		return message;
	}

	private void setMessage(final String message)
	{
		this.message = message;
	}

	private String getKey()
	{
		return key;
	}

	private void setKey(final String key)
	{
		this.key = key;
	}
}
