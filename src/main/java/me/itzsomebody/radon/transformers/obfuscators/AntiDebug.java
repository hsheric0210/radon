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

import static me.itzsomebody.radon.config.ConfigurationSetting.ANTI_DEBUG;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Blocks debugging options on the commandline.
 *
 * @author vovanre
 */
public class AntiDebug extends Transformer
{
	private static final String[] DEBUG_OPTIONS =
	{
			"-agentlib:jdwp", "-Xdebug", "-Xrunjdwp:", "-javaagent:"
	};

	private AtomicInteger debugOptionIndex;
	private String message;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();
		debugOptionIndex = new AtomicInteger();

		getClassWrappers().stream().filter(cw -> !cw.access.isInterface() && included(cw)).map(ClassWrapper::getOrCreateStaticBlock).forEach(clinit ->
		{
			final int checkCount = RandomUtils.getRandomInt(1, DEBUG_OPTIONS.length);
			for (int i = 0; i < checkCount; i++)
			{
				clinit.instructions.insert(generateCheck());
				counter.incrementAndGet();
			}
		});

		info("+ Injected " + counter.get() + " anti-debugging checks");
	}

	private InsnList generateCheck()
	{
		final LabelNode notDebugLabel = new LabelNode();
		final InsnList insnList = new InsnList();
		insnList.add(createIsDebugList());
		insnList.add(new JumpInsnNode(IFEQ, notDebugLabel));

		if (RandomUtils.getRandomBoolean())
		{
			if (message != null)
			{
				insnList.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
				insnList.add(new LdcInsnNode(message));
				insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
			}
			if (RandomUtils.getRandomBoolean())
			{
				insnList.add(new LdcInsnNode(RandomUtils.getRandomInt()));
				insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "exit", "(I)V", false));
			}
			else
			{
				insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;", false));
				insnList.add(new LdcInsnNode(RandomUtils.getRandomInt()));
				insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Runtime", "halt", "(I)V", false));
			}
		}
		else
		{
			String message = this.message;
			if (message == null)
				message = getGenericDictionary().randomString();

			insnList.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
			insnList.add(new InsnNode(DUP));
			insnList.add(new LdcInsnNode(message));
			insnList.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false));
			insnList.add(new InsnNode(ATHROW));
		}
		insnList.add(notDebugLabel);
		return insnList;
	}

	private InsnList createIsDebugList()
	{
		final boolean isUpper = RandomUtils.getRandomBoolean();
		String argument = DEBUG_OPTIONS[debugOptionIndex.incrementAndGet() % DEBUG_OPTIONS.length];
		if (isUpper)
			argument = argument.toUpperCase(Locale.ENGLISH);
		else
			argument = argument.toLowerCase(Locale.ENGLISH);

		final InsnList insnList = new InsnList();
		insnList.add(new MethodInsnNode(INVOKESTATIC, "java/lang/management/ManagementFactory", "getRuntimeMXBean", "()Ljava/lang/management/RuntimeMXBean;", false));
		insnList.add(new MethodInsnNode(INVOKEINTERFACE, "java/lang/management/RuntimeMXBean", "getInputArguments", "()Ljava/util/List;", true));
		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false));
		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", isUpper ? "toUpperCase" : "toLowerCase", "()Ljava/lang/String;", false));
		insnList.add(new LdcInsnNode(argument));
		insnList.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false));

		return insnList;
	}

	@Override
	public String getName()
	{
		return "Anti-Debug";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.ANTI_DEBUG;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		message = config.getOrDefault(ANTI_DEBUG + ".message", "Debugger properties detected");
	}
}
