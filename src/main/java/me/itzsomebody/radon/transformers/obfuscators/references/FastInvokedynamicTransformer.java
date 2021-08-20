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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.*;

/**
 * Hides INVOKEVIRTUALs and INVOKESTATICs with invokedynamic instructions.
 *
 * TODO: (Random) Paramorphically use bsmArgs instead
 *
 * @author ItzSomebody, hsheric0210
 */
public class FastInvokedynamicTransformer extends ReferenceObfuscation
{
	@Override
	public final void transform()
	{
		final MemberNames memberNames = new MemberNames();
		verboseInfos(memberNames::toStrings);

		final AtomicInteger counter = new AtomicInteger();

		final Handle bsmHandle = new Handle(H_INVOKESTATIC, memberNames.className, memberNames.bootstrapMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

		getClassWrappers().stream().filter(cw -> included(cw) && !"java/lang/Enum".equals(cw.getSuperName()) && cw.allowsIndy()).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final InsnList insnList = mw.getInstructions();

			Stream.of(insnList.toArray()).filter(insn -> insn.getOpcode() == INVOKEVIRTUAL || insn.getOpcode() == INVOKESTATIC).map(insn -> (MethodInsnNode) insn).forEach(method ->
			{
				if (!method.name.isEmpty() && method.name.charAt(0) == '<')
					return;

				String descriptor = method.desc;
				if (method.getOpcode() != INVOKESTATIC)
					descriptor = Constants.OPENING_BRACE_PATTERN.matcher(descriptor).replaceAll(Matcher.quoteReplacement("(Ljava/lang/Object;")); // To send the reference to self
				descriptor = ASMUtils.getGenericMethodDesc(descriptor);

				boolean flag = method.getOpcode() == INVOKESTATIC;
				if (memberNames.invertIdentifierVerifySystem)
					flag = !flag;

				final int flagKey = RandomUtils.getRandomInt(Character.MAX_VALUE);
				final String flagString = String.valueOf((char) (flag ? flagKey | memberNames.invokeStaticFlag : flagKey & ~memberNames.invokeStaticFlag));

				final String plain = String.join(memberNames.separator, ArrayUtils.reorder(new String[]
				{
						method.owner.replace('/', '.'), method.name, method.desc, flagString
				}, memberNames.identifierOrder, String.class));
				final int plainLength = plain.length();
				final char[] dest = new char[plainLength];
				final char[] name = new char[plainLength + 1];
				name[0] = (char) (encrypt(plain, dest, memberNames) + memberNames.offsetOfOffset);
				System.arraycopy(dest, 0, name, 1, plainLength);
				final InvokeDynamicInsnNode invDyn = new InvokeDynamicInsnNode(new String(name), descriptor, bsmHandle);
				insnList.set(method, invDyn);

				counter.incrementAndGet();
			});
		}));

		final ClassNode decryptor = createBootstrap(memberNames);
		getClasses().put(decryptor.name, new ClassWrapper(decryptor, false));

		info("+ Hid API " + counter.get() + " references using fast invokedynamic");
	}

	private int encrypt(final String plain, final char[] dest, final MemberNames memberNames)
	{
		final char[] plainChars = plain.toCharArray();
		final char[] encryptedChars = new char[plainChars.length];

		final int decryptorClassHC = memberNames.className.replace('/', '.').hashCode();
		final int bootstrapMethodHC = memberNames.bootstrapMethodName.hashCode();
		final int decryptMethodHC = memberNames.decryptMethodName.hashCode();

		for (int i = 0, j = plainChars.length; i < j; i++)
			switch (i % 3)
			{
				case 0:
					encryptedChars[i] = (char) (plainChars[i] ^ decryptorClassHC); // Encrypt with decryptor class name HC
					break;
				case 1:
					encryptedChars[i] = (char) (plainChars[i] ^ bootstrapMethodHC); // Encrypt with bootstrap method name HC
					break;
				default:
					encryptedChars[i] = (char) (plainChars[i] ^ decryptMethodHC); // Encrypt with decrypt method name HC
			}

		final char[] separatorChars = memberNames.separator.toCharArray();
		int offset = 0;
		while (checkIllegal(encryptedChars, separatorChars, offset))
			offset++;
		ArrayUtils.increment(encryptedChars, 0, encryptedChars.length, offset);

		System.arraycopy(encryptedChars, 0, dest, 0, encryptedChars.length);
		return offset;
	}

	public static boolean checkIllegal(final char[] arr, final char[] extraExclusion, final int offset)
	{
		for (final char ch : arr)
			if (ASMUtils.isIllegalMethodName((char) (ch + offset)) || ArrayUtils.indexOf(extraExclusion, (char) (ch + offset)) > 0)
				return true;
		return false;
	}

	private static ClassNode createBootstrap(final MemberNames memberNames)
	{
		final ClassNode cw = new ClassNode();
		MethodVisitor mv;

		cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, memberNames.className, null, "java/lang/Object", null);

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
			mv.visitInsn(ICONST_1);
			mv.visitInsn(ISUB);
			mv.visitIntInsn(NEWARRAY, T_CHAR);
			mv.visitVarInsn(ASTORE, 3);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(ICONST_0);
			mv.visitInsn(CALOAD);
			if (memberNames.offsetOfOffset > 0)
			{
				ASMUtils.getNumberInsn(memberNames.offsetOfOffset).accept(mv);
				mv.visitInsn(ISUB);
			}
			mv.visitVarInsn(ISTORE, 4);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 5);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 6);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 7);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 8);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitVarInsn(ISTORE, 9);
			final Label l9 = new Label();
			mv.visitLabel(l9);
			final Label l10 = new Label();
			mv.visitJumpInsn(GOTO, l10);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitInsn(ICONST_3);
			mv.visitInsn(IREM);
			final Label l12 = new Label();
			final Label l13 = new Label();
			final Label l14 = new Label();
			final Label l15 = new Label();
			mv.visitTableSwitchInsn(0, 2, l15, l12, l13, l14);
			mv.visitLabel(l12);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(IADD);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(ISUB);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			final Label l16 = new Label();
			mv.visitLabel(l16);
			mv.visitJumpInsn(GOTO, l15);
			mv.visitLabel(l13);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(IADD);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(ISUB);
			mv.visitVarInsn(ILOAD, 6);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			final Label l17 = new Label();
			mv.visitLabel(l17);
			mv.visitJumpInsn(GOTO, l15);
			mv.visitLabel(l14);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(IADD);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(ISUB);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitInsn(CASTORE);
			mv.visitLabel(l15);
			mv.visitIincInsn(8, 1);
			mv.visitLabel(l10);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitVarInsn(ILOAD, 9);
			mv.visitJumpInsn(IF_ICMPLT, l11);
			final Label l18 = new Label();
			mv.visitLabel(l18);
			mv.visitTypeInsn(NEW, "java/lang/String");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
			mv.visitInsn(ARETURN);
			final Label l19 = new Label();
			mv.visitLabel(l19);
			mv.visitMaxs(5, 10);
			mv.visitEnd();
		}

		final int[] argsOrder = memberNames.getMethodHandleMethodArgumentOrder;
		{
			final int[] argsIndex = ArrayUtils.toIndexArray(argsOrder);
			mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, memberNames.getMethodHandleMethodName, memberNames.getMethodHandleMethodDescriptor, null, new String[]
			{
					"java/lang/Exception"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ILOAD, argsIndex[4]); // Call Flag
			ASMUtils.getNumberInsn(memberNames.invokeStaticFlag).accept(mv);
			mv.visitInsn(IAND); // if ((identifier & FLAG_INVOKESTATIC) != 0) decodeInvokeStatic else decodeInvokeVirtual
			final Label l1 = new Label(); // l1 : INVOKEVIRTUAL
			mv.visitJumpInsn(memberNames.invertIdentifierVerifySystem ? IFNE : IFEQ, l1);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, argsIndex[0]); // Method Lookup
			mv.visitVarInsn(ALOAD, argsIndex[1]); // Method Owner
			final Label l3 = new Label(); // l3 : INVOKESTATIC
			mv.visitLabel(l3);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ALOAD, argsIndex[2]); // Method Name
			mv.visitVarInsn(ALOAD, argsIndex[3]); // Method Descriptor
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
			mv.visitVarInsn(ALOAD, argsIndex[0]);
			mv.visitVarInsn(ALOAD, argsIndex[1]);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ALOAD, argsIndex[2]);
			mv.visitVarInsn(ALOAD, argsIndex[3]);
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
		mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, memberNames.bootstrapMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", null, null);
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
		mv.visitVarInsn(ASTORE, 3);
		final Label l3 = new Label();
		mv.visitLabel(l3);

		final int[] idOrder = memberNames.identifierOrder;
		final int[] idIndex = ArrayUtils.toIndexArray(idOrder);

		Consumer<MethodVisitor>[] argumentPushes = new Consumer[]
		{
				(Consumer<MethodVisitor>) _mv ->
				{
					// Argument (MethodHandles$Lookup) #0 - Method handle lookup
					_mv.visitVarInsn(ALOAD, 0);
				}, (Consumer<MethodVisitor>) _mv ->
				{
					// Argument #1 (String) - Method Owner
					_mv.visitVarInsn(ALOAD, 3);
					_mv.visitInsn(idIndex[0] + ICONST_0);
					_mv.visitInsn(AALOAD);
				}, (Consumer<MethodVisitor>) _mv ->
				{
					// Argument #2 (String) - Method Name
					_mv.visitVarInsn(ALOAD, 3);
					_mv.visitInsn(idIndex[1] + ICONST_0);
					_mv.visitInsn(AALOAD);
				}, (Consumer<MethodVisitor>) _mv ->
				{
					// Argument #3 (String) - Method Descriptor
					_mv.visitVarInsn(ALOAD, 3);
					_mv.visitInsn(idIndex[2] + ICONST_0);
					_mv.visitInsn(AALOAD);
				}, (Consumer<MethodVisitor>) _mv ->
				{
					// Argument #4 (char) - Method Opcode Identifier
					_mv.visitVarInsn(ALOAD, 3);
					_mv.visitInsn(idIndex[3] + ICONST_0);
					_mv.visitInsn(AALOAD);
					_mv.visitInsn(ICONST_0);
					_mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
				}
		};

		argumentPushes = ArrayUtils.reorder(argumentPushes, argsOrder, Consumer.class);
		for (int i = 0, j = argsOrder.length; i < j; i++)
			argumentPushes[i].accept(mv);

		mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.getMethodHandleMethodName, memberNames.getMethodHandleMethodDescriptor, false);
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
		final int[] getMethodHandleMethodArgumentOrder;
		final String getMethodHandleMethodDescriptor;

		final String bootstrapMethodName;

		final char invokeStaticFlag;

		final String separator;

		final boolean invertIdentifierVerifySystem;
		final int[] identifierOrder;
		final int offsetOfOffset;

		MemberNames()
		{
			className = randomClassName();

			final WrappedDictionary methodDictionary = getMethodDictionary(className);

			bootstrapMethodName = methodDictionary.nextUniqueString();

			getMethodHandleMethodName = methodDictionary.nextUniqueString();
			getMethodHandleMethodArgumentOrder = ArrayUtils.randomIntArrayOf(0, 5);
			String[] descs =
			{
					"Ljava/lang/invoke/MethodHandles$Lookup;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "C"
			};
			descs = ArrayUtils.reorder(descs, getMethodHandleMethodArgumentOrder, String.class);

			getMethodHandleMethodDescriptor = Arrays.stream(descs).collect(Collectors.joining("", "(", ")Ljava/lang/invoke/MethodHandle;"));

			decryptMethodName = methodDictionary.nextUniqueString();

			invokeStaticFlag = (char) RandomUtils.getRandomInt(Character.MIN_VALUE + 1, Character.MAX_VALUE);
			separator = "\u0000\u0000";

			invertIdentifierVerifySystem = RandomUtils.getRandomBoolean();

			identifierOrder = ArrayUtils.randomIntArrayOf(0, 4);

			offsetOfOffset =  RandomUtils.getRandomInt(']' + 1, 1024);
		}

		public String[] toStrings()
		{
			final String[] strings = new String[10];
			strings[0] = "Decryptor class name: " + className;
			strings[1] = "Bootstrap method name: " + bootstrapMethodName;
			strings[2] = "GetMethodHandle method name: " + getMethodHandleMethodName;
			strings[3] = "GetMethodHandle method descriptor: " + getMethodHandleMethodDescriptor;

			strings[4] = "GetMethodHandle method argument order: " + Strings.serializeOrder(getMethodHandleMethodArgumentOrder) + " -> (" + Arrays.toString(ArrayUtils.reorder(new String[]
			{
					"MethodHandles-Lookup", "Method-Owner", "Method-Name", "Method-Descriptor", "Call-Flag"
			}, getMethodHandleMethodArgumentOrder, String.class)) + ")";
			strings[5] = "Decrypt method name: " + decryptMethodName;
			strings[6] = (invertIdentifierVerifySystem ? "INVOKEVIRTUAL" : "INVOKESTATIC") + " opcode flag: " + Strings.intToHexByte(invokeStaticFlag, 4);
			strings[7] = "Separator : '" + separator + "' (" + Strings.stringToHexBytes(separator) + ")";

			strings[8] = "Identifier order: " + Strings.serializeOrder(identifierOrder) + " -> (" + Arrays.toString(ArrayUtils.reorder(new String[]
			{
					"Method-Owner", "Method-Name", "Method-Descriptor", "Call-Flag"
			}, identifierOrder, String.class)) + ")";
			strings[9] = "Offset of offset: " + offsetOfOffset;
			return strings;
		}
	}
}
