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

import java.util.ArrayList;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Not really a transformer. This "transformer" generates unused classes full of random bytecode.
 *
 * @author ItzSomebody
 */
public class TrashClasses extends Transformer
{
	private static final ArrayList<String> DESCRIPTORS = new ArrayList<>();

	static
	{
		DESCRIPTORS.add("Z");
		DESCRIPTORS.add("C");
		DESCRIPTORS.add("B");
		DESCRIPTORS.add("S");
		DESCRIPTORS.add("I");
		DESCRIPTORS.add("F");
		DESCRIPTORS.add("J");
		DESCRIPTORS.add("D");
		DESCRIPTORS.add("V");
	}

	@Override
	public void transform()
	{
		final ArrayList<String> classNames = new ArrayList<>(getClassPath().keySet());
		for (int i = 0; i < classNames.size() % 20; i++)
			DESCRIPTORS.add("L" + classNames.get(RandomUtils.getRandomInt(classNames.size())) + ";");

		for (int i = 0; i < radon.getConfig().getnTrashClasses(); i++)
		{
			final ClassNode classNode = generateClass();
			final ClassWriter cw = new ClassWriter(0);
			cw.newUTF8("RADON" + Main.VERSION);
			classNode.accept(cw);

			getResources().put(classNode.name + ".class", cw.toByteArray());
		}

		Main.info(String.format("Generated %d trash classes.", radon.getConfig().getnTrashClasses()));
	}

	private ClassNode generateClass()
	{
		final ClassNode classNode = createClass(randomClassName());
		final int methodsToGenerate = RandomUtils.getRandomInt(3) + 2;

		for (int i = 0; i < methodsToGenerate; i++)
			classNode.methods.add(methodGen());

		return classNode;
	}

	private ClassNode createClass(final String className)
	{
		final ClassNode classNode = new ClassNode();
		classNode.visit(49, ACC_SUPER + ACC_PUBLIC, className, null, "java/lang/Object", null);

		final MethodVisitor mv = classNode.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitInsn(RETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();

		classNode.visitEnd();

		return classNode;
	}

	private MethodNode methodGen()
	{
		final String randDesc = descGen();
		final MethodNode method = new MethodNode(ACC_STATIC + ACC_PRIVATE, methodDictionary.randomString(), randDesc, null, null);
		final int instructions = RandomUtils.getRandomInt(30) + 30;

		final InsnList insns = new InsnList();

		for (int i = 0; i < instructions; ++i)
			insns.add(junkInstructions());

		final Type returnType = Type.getReturnType(randDesc);
		switch (returnType.getSort())
		{
			case Type.VOID:
				insns.add(new InsnNode(RETURN));
				break;
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				if (RandomUtils.getRandomInt(10) % 2 == 1)
					insns.add(new InsnNode(ICONST_0));
				else
					insns.add(new InsnNode(ICONST_1));

				insns.add(new InsnNode(IRETURN));
				break;
			case Type.FLOAT:
				insns.add(ASMUtils.getNumberInsn(RandomUtils.getRandomFloat()));
				insns.add(new InsnNode(FRETURN));
				break;
			case Type.LONG:
				insns.add(ASMUtils.getNumberInsn(RandomUtils.getRandomLong()));
				insns.add(new InsnNode(LRETURN));
				break;
			case Type.DOUBLE:
				insns.add(ASMUtils.getNumberInsn(RandomUtils.getRandomDouble()));
				insns.add(new InsnNode(DRETURN));
				break;
			default:
				insns.add(new VarInsnNode(ALOAD, RandomUtils.getRandomInt(30)));
				insns.add(new InsnNode(ARETURN));
				break;
		}

		method.instructions = insns;
		return method;
	}

	private String descGen()
	{
		final StringBuilder sb = new StringBuilder("(");

		for (int i = 0; i < RandomUtils.getRandomInt(7); i++)
			sb.append(DESCRIPTORS.get(RandomUtils.getRandomInt(DESCRIPTORS.size())));

		sb.append(")");
		sb.append(DESCRIPTORS.get(RandomUtils.getRandomInt(DESCRIPTORS.size())));

		return sb.toString();
	}

	private AbstractInsnNode junkInstructions()
	{
		final int index = RandomUtils.getRandomInt(20);
		switch (index)
		{
			case 0:
				return new MethodInsnNode(INVOKESTATIC, classDictionary.randomString(), methodDictionary.randomString(), "(Ljava/lang/String;)V", false);
			case 1:
				return new FieldInsnNode(GETFIELD, classDictionary.randomString(), fieldDictionary.randomString(), "I");
			case 2:
				return new InsnNode(RandomUtils.getRandomInt(16));
			case 3:
				return new VarInsnNode(ALOAD, RandomUtils.getRandomInt(30));
			case 4:
				return new IntInsnNode(BIPUSH, RandomUtils.getRandomInt(255));
			case 5:
				return new IntInsnNode(SIPUSH, RandomUtils.getRandomInt(25565));
			case 6:
			case 7:
			case 8:
				return new InsnNode(RandomUtils.getRandomInt(5));
			case 9:
				return new LdcInsnNode(genericDictionary.randomString());
			case 10:
				return new IincInsnNode(RandomUtils.getRandomInt(16), RandomUtils.getRandomInt(16));
			case 11:
				return new MethodInsnNode(INVOKESPECIAL, classDictionary.randomString(), methodDictionary.randomString(), "()V", false);
			case 12:
				return new MethodInsnNode(INVOKEVIRTUAL, classDictionary.randomString(), methodDictionary.randomString(), "(Ljava/lang/Object;)Ljava/lang/Object;", false);
			case 13:
				return new VarInsnNode(ILOAD, RandomUtils.getRandomInt(30));
			case 14:
				return new InsnNode(ATHROW);
			case 15:
				return new MethodInsnNode(INVOKEINTERFACE, classDictionary.randomString(), methodDictionary.randomString(), "(I)I", false);
			case 16:
				final Handle handle = new Handle(6, classDictionary.randomString(), methodDictionary.randomString(), genericDictionary.randomString(), false);
				return new InvokeDynamicInsnNode(methodDictionary.randomString(), genericDictionary.randomString(), handle, RandomUtils.getRandomInt(5), RandomUtils.getRandomInt(5), RandomUtils.getRandomInt(5), RandomUtils.getRandomInt(5), RandomUtils.getRandomInt(5));
			case 17:
				return new IntInsnNode(ANEWARRAY, RandomUtils.getRandomInt(30));
			case 18:
				return new VarInsnNode(ASTORE, RandomUtils.getRandomInt(30));
			case 19:
			default:
				return new VarInsnNode(ISTORE, RandomUtils.getRandomInt(30));
		}
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.TRASH_CLASSES;
	}

	@Override
	public String getName()
	{
		return "Trash classes";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
