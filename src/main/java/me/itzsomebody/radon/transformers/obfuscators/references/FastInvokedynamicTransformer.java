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

package me.itzsomebody.radon.transformers.obfuscators.references;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Hides INVOKEVIRTUALs and INVOKESTATICs with invokedynamic instructions.
 *
 * @author ItzSomebody
 * @fixer  eric0210
 */
public class FastInvokedynamicTransformer extends ReferenceObfuscation
{
	@Override
	public final void transform()
	{
		final MemberNames memberNames = new MemberNames();
		final AtomicInteger counter = new AtomicInteger();

		final Handle bootstrapHandle = new Handle(H_INVOKESTATIC, memberNames.className, memberNames.bootstrapMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

		getClassWrappers().stream().filter(cw -> included(cw) && !"java/lang/Enum".equals(cw.getSuperName()) && cw.allowsIndy()).forEach(classWrapper -> classWrapper.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final InsnList insnList = mw.getInstructions();

			Stream.of(insnList.toArray()).filter(insn -> insn instanceof MethodInsnNode).map(insn -> (MethodInsnNode) insn).forEach(method ->
			{
				final boolean superCall = method.getOpcode() == INVOKESPECIAL && method.getPrevious() instanceof VarInsnNode && ((VarInsnNode) method.getPrevious()).var == 0;
				// INVOKESPECIAL is not fully supported and so buggy :(
				// This is a workaround for "super." calls got replaced to "this." calls. This is obvious bug.

				if (!method.name.isEmpty() && method.name.charAt(0) == '<' || superCall)
					return;

				String newDesc = method.desc;

				if (method.getOpcode() != INVOKESTATIC)
					newDesc = Constants.OPENING_BRACE_PATTERN.matcher(newDesc).replaceAll(Matcher.quoteReplacement("(Ljava/lang/Object;"));

				newDesc = ASMUtils.getGenericMethodDesc(newDesc);
				// fixme: j11 doesn't like null bytes

				/* <method owner>\u0000\u0000<method name>\u0000\u0000<method descriptor>\u0000\u0000<opcode identifier> */
				final char invokeStaticIdentifier = memberNames.invertIdentifierVerifySystem ? memberNames.generateRandomID() : memberNames.preservedId;
				final char invokeVirtualIdentifier = memberNames.invertIdentifierVerifySystem ? memberNames.preservedId : memberNames.generateRandomID();

				final int builderCapacity = method.owner.length() + method.name.length() + method.desc.length() + memberNames.separator.length() * 3 + 1; // +1 for identifier character
				final StringBuilder nameBuilder = new StringBuilder(builderCapacity);

				if (memberNames.invertIdentifierPosition) // Start with identifier
				{
					nameBuilder.append(method.getOpcode() == INVOKESTATIC ? invokeStaticIdentifier : invokeVirtualIdentifier);
					nameBuilder.append(memberNames.separator);
				}

				nameBuilder.append(method.owner.replace('/', '.')).append(memberNames.separator).append(method.name).append(memberNames.separator).append(method.desc);

				if (!memberNames.invertIdentifierPosition) // End with identifier
				{
					nameBuilder.append(memberNames.separator);
					nameBuilder.append(method.getOpcode() == INVOKESTATIC ? invokeStaticIdentifier : invokeVirtualIdentifier);
				}

				final InvokeDynamicInsnNode invDyn = new InvokeDynamicInsnNode(encrypt(nameBuilder.toString(), memberNames), newDesc, bootstrapHandle);

				insnList.set(method, invDyn);

				counter.incrementAndGet();
			});
		}));

		final ClassNode decryptor = createBootstrap(memberNames);
		getClasses().put(decryptor.name, new ClassWrapper(decryptor, false));

		Main.info("+ Hid API " + counter.get() + " references using fast invokedynamic");
	}

	private static String encrypt(final String plain, final MemberNames memberNames)
	{
		final char[] plainChars = plain.toCharArray();
		final char[] encryptedChars = new char[plainChars.length];

		for (int i = 0, j = plainChars.length; i < j; i++)
			switch (i % 3)
			{
				case 0:
					encryptedChars[i] = (char) (plainChars[i] ^ memberNames.className.replace('/', '.').hashCode()); // Encrypt with decryptor class name HC
					break;
				case 1:
					encryptedChars[i] = (char) (plainChars[i] ^ memberNames.bootstrapMethodName.hashCode()); // Encrypt with bootstrap method name HC
					break;
				default:
					encryptedChars[i] = (char) (plainChars[i] ^ memberNames.decryptMethodName.hashCode()); // Encrypt with decrypt method name HC
					break;
			}

		return new String(encryptedChars);
	}

	private static ClassNode createBootstrap(final MemberNames memberNames)
	{
		final ClassNode cw = new ClassNode();
		MethodVisitor mv;

		cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, memberNames.className, null, "java/lang/Object", null);

		{
			mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, memberNames.decryptMethodName, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false);
			mv.visitVarInsn(ASTORE, 1);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
			mv.visitVarInsn(ASTORE, 2);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitIntInsn(NEWARRAY, T_CHAR);
			mv.visitVarInsn(ASTORE, 3);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 4);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(ARRAYLENGTH);
			final Label l5 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l5);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(ICONST_3);
			mv.visitInsn(IREM);
			final Label l7 = new Label();
			final Label l8 = new Label();
			final Label l9 = new Label();
			final Label l10 = new Label();
			mv.visitTableSwitchInsn(0, 2, l10, l7, l8, l9);
			mv.visitLabel(l7);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitJumpInsn(GOTO, l10);
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitJumpInsn(GOTO, l10);
			mv.visitLabel(l9);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			mv.visitLabel(l10);
			mv.visitIincInsn(4, 1);
			mv.visitJumpInsn(GOTO, l4);
			mv.visitLabel(l5);
			mv.visitTypeInsn(NEW, "java/lang/String");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
			mv.visitInsn(ARETURN);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitMaxs(5, 5);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, memberNames.getMethodHandleMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;C)Ljava/lang/invoke/MethodHandle;", null, new String[]
			{
					"java/lang/Exception"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ILOAD, 4);
//			mv.visitIntInsn(BIPUSH, 97);
			ASMUtils.getNumberInsn(memberNames.preservedId).accept(mv);
			final Label l1 = new Label();
			mv.visitJumpInsn(memberNames.invertIdentifierVerifySystem ? IF_ICMPEQ : IF_ICMPNE, l1);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitLdcInsn(Type.getType("L" + memberNames.className + ";"));
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitLdcInsn(Type.getType("L" + memberNames.className + ";"));
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
			final Label l9 = new Label();
			mv.visitLabel(l9);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitInsn(ARETURN);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitMaxs(5, 5);
			mv.visitEnd();
		}
		mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, memberNames.bootstrapMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", null, null);
		mv.visitCode();
		final Label l0 = new Label();
		final Label l1 = new Label();
		final Label l2 = new Label();
		mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
		mv.visitLabel(l0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.decryptMethodName, "(Ljava/lang/String;)Ljava/lang/String;", false);
		mv.visitLdcInsn(memberNames.separator);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
		mv.visitVarInsn(ASTORE, 3); // splitten string stored to register 3
		final Label l3 = new Label();
		mv.visitLabel(l3);
		mv.visitVarInsn(ALOAD, 0);

		// split[0]
		mv.visitVarInsn(ALOAD, 3);
		mv.visitInsn(memberNames.invertIdentifierPosition ? ICONST_3 : ICONST_0); // split[3]
		mv.visitInsn(AALOAD);

		// split[1]
		mv.visitVarInsn(ALOAD, 3);
		mv.visitInsn(ICONST_1);
		mv.visitInsn(AALOAD);

		// split[2]
		mv.visitVarInsn(ALOAD, 3);
		mv.visitInsn(ICONST_2);
		mv.visitInsn(AALOAD);

		// split[3]
		mv.visitVarInsn(ALOAD, 3);
		mv.visitInsn(memberNames.invertIdentifierPosition ? ICONST_0 : ICONST_3); // split[0]
		mv.visitInsn(AALOAD);

		mv.visitInsn(ICONST_0);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
		mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.getMethodHandleMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;C)Ljava/lang/invoke/MethodHandle;", false);
		mv.visitVarInsn(ASTORE, 4);
		final Label l4 = new Label();
		mv.visitLabel(l4);
		mv.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 4);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
		mv.visitLabel(l1);
		mv.visitInsn(ARETURN);
		mv.visitLabel(l2);
		mv.visitVarInsn(ASTORE, 3);
		final Label l5 = new Label();
		mv.visitLabel(l5);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
		final Label l6 = new Label();
		mv.visitLabel(l6);
		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		final Label l7 = new Label();
		mv.visitLabel(l7);
		mv.visitMaxs(6, 5);
		mv.visitEnd();
		cw.visitEnd();

		return cw;
	}

	private class MemberNames
	{
		final String className;

		final String decryptMethodName;

		final String getMethodHandleMethodName;

		final String bootstrapMethodName;

		final char preservedId;

		final String separator;

		final boolean invertIdentifierVerifySystem;
		final boolean invertIdentifierPosition;

		MemberNames()
		{
			className = randomClassName();

			bootstrapMethodName = methodDictionary.uniqueRandomString();
			getMethodHandleMethodName = methodDictionary.uniqueRandomString();
			decryptMethodName = methodDictionary.uniqueRandomString();

			preservedId = (char) RandomUtils.getRandomInt(Character.MIN_VALUE + 1, Character.MAX_VALUE);
			separator = "\u0000\u0000";

			invertIdentifierVerifySystem = RandomUtils.getRandomBoolean();
			invertIdentifierPosition = RandomUtils.getRandomBoolean();
		}

		char generateRandomID()
		{
			char id;

			do
				id = (char) RandomUtils.getRandomInt(Character.MIN_VALUE + 1, Character.MAX_VALUE);
			while (id == preservedId);

			return id;
		}
	}
}
