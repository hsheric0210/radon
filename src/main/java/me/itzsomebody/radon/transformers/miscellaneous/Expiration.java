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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

import static me.itzsomebody.radon.config.ConfigurationSetting.EXPIRATION;

/**
 * Inserts an expiration block of instructions in each constructor method.
 *
 * @author ItzSomebody
 */
public class Expiration extends Transformer
{
	private String message;
	private long expires;
	private boolean injectJOptionPaneEnabled;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).map(classWrapper -> classWrapper.classNode).forEach(classNode -> classNode.methods.stream().filter(methodNode -> "<init>".equals(methodNode.name)).forEach(methodNode ->
		{
			final InsnList expirationCode = createExpirationInstructions();
			methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), expirationCode);
			counter.incrementAndGet();
		}));

		info(String.format("+ Added %d expiration code blocks.", counter.get()));
	}

	private InsnList createExpirationInstructions()
	{
		final InsnList insns = new InsnList();
		final LabelNode injectedLabel = new LabelNode(new Label());

		insns.add(new TypeInsnNode(NEW, "java/util/Date"));
		insns.add(new InsnNode(DUP));
		insns.add(new MethodInsnNode(INVOKESPECIAL, "java/util/Date", "<init>", "()V", false));
		insns.add(new TypeInsnNode(NEW, "java/util/Date"));
		insns.add(new InsnNode(DUP));
		insns.add(new LdcInsnNode(expires));
		insns.add(new MethodInsnNode(INVOKESPECIAL, "java/util/Date", "<init>", "(J)V", false));
		insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/util/Date", "after", "(Ljava/util/Date;)Z", false));
		insns.add(new JumpInsnNode(IFEQ, injectedLabel));
		insns.add(new TypeInsnNode(NEW, "java/lang/Throwable"));
		insns.add(new InsnNode(DUP));
		insns.add(new LdcInsnNode(message));
		if (injectJOptionPaneEnabled)
		{
			insns.add(new InsnNode(DUP));
			insns.add(new InsnNode(ACONST_NULL));
			insns.add(new InsnNode(SWAP));
			insns.add(new MethodInsnNode(INVOKESTATIC, "javax/swing/JOptionPane", "showMessageDialog", "(Ljava/awt/Component;Ljava/lang/Object;)V", false));
		}
		insns.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Throwable", "<init>", "(Ljava/lang/String;)V", false));
		insns.add(new InsnNode(ATHROW));
		insns.add(injectedLabel);

		return insns;
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.EXPIRATION;
	}

	@Override
	public String getName()
	{
		return "Expiration";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		try
		{
			expires = new SimpleDateFormat("MM/dd/yyyy").parse(config.getOrDefault(EXPIRATION.getConfigName() + ".expiration_date", "12/31/2021")).getTime();
		}
		catch (final ParseException e)
		{
			severe("Error while parsing expiration date.", e);
			throw new RadonException(e);
		}
		injectJOptionPaneEnabled = config.getOrDefault(EXPIRATION.getConfigName() + ".inject_joptionpane", false);
		message = config.getOrDefault(EXPIRATION.getConfigName() + ".expiration_message", "Your trial has expired!");
	}
}
