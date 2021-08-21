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

package me.itzsomebody.radon.asm;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.Radon;
import me.itzsomebody.radon.asm.accesses.Access;
import me.itzsomebody.radon.asm.accesses.ClassAccess;

/**
 * Wrapper for ClassNodes.
 *
 * @author ItzSomebody
 */
public class ClassWrapper
{
	private static final int LIB_FLAGS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE;
	private static final int INPUT_FLAGS = ClassReader.SKIP_FRAMES;
	private static final String DEFAULT_ENTRY_PREFIX = "";

	public ClassNode classNode;
	public final String originalName;
	public final boolean libraryNode;

	public String entryPrefix;
	public final Access access;
	public final List<MethodWrapper> methods = new ArrayList<>();
	public final List<FieldWrapper> fields = new ArrayList<>();
	public final List<String> strConsts = new ArrayList<>();

	public ClassWrapper(final ClassReader cr, final boolean libraryNode)
	{
		final ClassNode classNode = new ClassNode();
		cr.accept(classNode, libraryNode ? LIB_FLAGS : INPUT_FLAGS);

		this.classNode = classNode;
		originalName = classNode.name;
		this.libraryNode = libraryNode;

		entryPrefix = DEFAULT_ENTRY_PREFIX;
		access = new ClassAccess(this);
		classNode.methods.forEach(methodNode -> methods.add(new MethodWrapper(methodNode, this)));
		classNode.fields.forEach(fieldNode -> fields.add(new FieldWrapper(fieldNode, this)));
	}

	public ClassWrapper(final ClassNode classNode, final boolean libraryNode)
	{
		this.classNode = classNode;
		originalName = classNode.name;
		this.libraryNode = libraryNode;

		entryPrefix = DEFAULT_ENTRY_PREFIX;
		access = new ClassAccess(this);
		classNode.methods.forEach(methodNode -> methods.add(new MethodWrapper(methodNode, this)));
		classNode.fields.forEach(fieldNode -> fields.add(new FieldWrapper(fieldNode, this)));
	}

	public void addMethod(final MethodNode methodNode)
	{
		classNode.methods.add(methodNode);
		methods.add(new MethodWrapper(methodNode, this));
	}

	public void addField(final FieldNode fieldNode)
	{
		classNode.fields.add(fieldNode);
		fields.add(new FieldWrapper(fieldNode, this));
	}

	/**
	 * @param s
	 *          constant literal to add to constant pool.
	 */
	public void addStringConst(final String s)
	{
		strConsts.add(s);
	}

	public MethodNode getMethod(final String name, final String desc)
	{
		return classNode.methods.stream().filter(methodNode -> name.equals(methodNode.name) && desc.equals(methodNode.desc)).findAny().orElse(null);
	}

	public MethodNode getOrCreateStaticBlock()
	{
		MethodNode clinit = getMethod("<clinit>", "()V");

		if (clinit == null)
		{
			clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			clinit.instructions.add(new InsnNode(Opcodes.RETURN));
			addMethod(clinit);
		}

		return clinit;
	}

	public boolean isMethodPresent(final String name, final String desc)
	{
		return classNode.methods.stream().anyMatch(methodNode -> methodNode.name.equals(name) && methodNode.desc.equals(desc));
	}

	public boolean isFieldPresent(final String name, final String desc)
	{
		return classNode.fields.stream().anyMatch(fieldNode -> fieldNode.name.equals(name) && fieldNode.desc.equals(desc));
	}

	/**
	 * @return current name of wrapped {@link ClassNode}.
	 */
	public String getName()
	{
		return classNode.name;
	}

	/**
	 * @return current package name of wrapped {@link ClassNode}.
	 */
	public String getPackageName()
	{
		return classNode.name.substring(0, classNode.name.lastIndexOf('/') + 1);
	}

	public final String getOriginalPackageName()
	{
		return originalName.substring(0, originalName.lastIndexOf('/') + 1);
	}

	/**
	 * @return current super class name of wrapped {@link ClassNode}.
	 */
	public String getSuperName()
	{
		return classNode.superName;
	}

	/**
	 * @return current interfaces of wrapped {@link ClassNode}.
	 */
	public List<String> getInterfaces()
	{
		return classNode.interfaces;
	}

	/**
	 * @return raw access flags of wrapped {@link ClassNode}.
	 */
	public int getAccessFlags()
	{
		return classNode.access;
	}

	/**
	 * @param access
	 *               access flags to set.
	 */
	public void setAccessFlags(final int access)
	{
		classNode.access = access;
	}

	/**
	 * @return the current class version of the wrapped {@link ClassNode}.
	 */
	public int getVersion()
	{
		return classNode.version;
	}

	/**
	 * See https://docs.oracle.com/javase/specs/jvms/se12/html/jvms-4.html#jvms-4.9.1
	 *
	 * @return true if the wrapped {@link ClassNode} supports JSR instructions.
	 */
	public boolean allowsJSR()
	{
		return classNode.version <= Opcodes.V1_5 || classNode.version == Opcodes.V1_1;
	}

	/**
	 * J7 and up include support for INVOKEDYNAMIC instructions.
	 *
	 * @return true if the wrapped {@link ClassNode} supports INVOKEDYNAMIC instructions.
	 */
	public boolean allowsIndy()
	{
		return classNode.version >= Opcodes.V1_7 && classNode.version != Opcodes.V1_1;
	}

	/**
	 * @return the computed current constant pool size of the wrapped {@link ClassNode}.
	 */
	public int computeConstantPoolSize(final Radon radon)
	{
		return new ClassReader(toByteArray(radon)).getItemCount();
	}

	public byte[] toByteArray(final Radon radon)
	{
		// Construct byte writer
		ClassWriter writer = new CustomClassWriter(ClassWriter.COMPUTE_FRAMES, radon);

		try
		{
			writer.newUTF8(Main.WATERMARK);

			// Populate writer with class info
			classNode.accept(writer);

			// Insert manually-specified constant pool strings
			strConsts.forEach(writer::newUTF8);

			return writer.toByteArray();
		}
		catch (final Throwable e)
		{
			Main.warn(String.format("*** Error writing class %s. Skipping frames (might cause runtime errors).", getName() + ".class"), e);

			writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			writer.newUTF8(Main.WATERMARK);

			classNode.accept(writer);
			strConsts.forEach(writer::newUTF8);

			return writer.toByteArray();
		}
	}

	public String getEntryName()
	{
		return entryPrefix + classNode.name + ".class";
	}

	}
