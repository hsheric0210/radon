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
import java.util.Collection;
import java.util.HashSet;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.utils.*;
import me.itzsomebody.radon.utils.Constants;

/**
 * Hides INVOKESTATICs, INVOKEVIRTUALs, INVOKESPECIALs, GETSTATIC, PUTSTATIC, GETFIELD and PUTFIELD operations by swapping them out with an invokedynamic instruction.
 *
 * FIXME
 * 
 * <pre>
 *     java.lang.IllegalArgumentException: no argument type to remove[()int, 2, [class java.lang.String, long], 2, 0]
 *         at java.base/java.lang.invoke.MethodHandleStatics.newIllegalArgumentException(MethodHandleStatics.java:134)
 *         at java.base/java.lang.invoke.MethodHandles.dropArgumentChecks(MethodHandles.java:5263)
 *         at java.base/java.lang.invoke.MethodHandles.dropArguments0(MethodHandles.java:5244)
 *         at java.base/java.lang.invoke.MethodHandles.dropArguments(MethodHandles.java:5316)
 *         at InvokedynamicDecryptor.resolveMethodHandle(Unknown Source)
 *         ... 5 more
 * </pre>
 *
 * <p>
 * TODO: Support INVOKESPECIAL with {@code MethodHandles.findSpecial}(it doesn't works well with constructors!) and {@code MethodHandles.findConstructor}(it only works with constructors)
 * </p>
 *
 * @author ItzSomebody
 */
public class InvokedynamicTransformer extends ReferenceObfuscation
{
	@Override
	public final void transform()
	{
		final MemberNames memberNames = new MemberNames();
		verboseInfos(memberNames::toStrings);

		final AtomicInteger counter = new AtomicInteger();

		final Handle bootstrapHandle = new Handle(H_INVOKESTATIC, memberNames.className, memberNames.bootstrapMethodName, "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);

		getClassWrappers().stream().filter(cw -> included(cw) && !"java/lang/Enum".equals(cw.getSuperName()) && cw.allowsIndy()).forEach(cw ->
		{
			if (!cw.access.isInterface())
				cw.fields.stream().forEach(fw -> fw.setAccessFlags(fw.getAccessFlags() & ~ACC_FINAL)); // J16 checks 'final' flags

			cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				Stream.of(insns.toArray()).forEach(insn ->
				{
					if (insn instanceof MethodInsnNode)
					{
						final MethodInsnNode m = (MethodInsnNode) insn;

						if (!m.name.isEmpty() && m.name.charAt(0) == '<')
							return;

						String newDesc = Constants.CLOSING_BRACE_PATTERN.matcher(m.desc).replaceAll(Matcher.quoteReplacement("Ljava/lang/String;J)"));
						if (m.getOpcode() != INVOKESTATIC)
							newDesc = Constants.OPENING_BRACE_PATTERN.matcher(newDesc).replaceAll(Matcher.quoteReplacement("(Ljava/lang/Object;"));

						newDesc = ASMUtils.getGenericMethodDesc(newDesc);

						final InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(memberNames.generateMethodName(insn.getOpcode() == INVOKESTATIC ? 0 : 1, true), newDesc, bootstrapHandle);
						insns.insertBefore(m, new LdcInsnNode(m.owner.replace('/', '.')));
						insns.insertBefore(m, ASMUtils.getNumberInsn(hash(m.desc) & 0xffffffffL | (long) m.name.hashCode() << 32));
						insns.set(m, indy);

						counter.incrementAndGet();
					}
					else if (insn instanceof FieldInsnNode && !"<init>".equals(mw.getName()))
					{
						final FieldInsnNode f = (FieldInsnNode) insn;

						final boolean isStatic = f.getOpcode() == GETSTATIC || f.getOpcode() == PUTSTATIC;
						final boolean isSetter = f.getOpcode() == PUTFIELD || f.getOpcode() == PUTSTATIC;

						if (!(isSetter && cw.access.isInterface()))
						{
							String newDesc = isSetter ? "(" + f.desc + "Ljava/lang/String;J)V" : "(Ljava/lang/String;J)" + f.desc;
							if (!isStatic)
								newDesc = Constants.OPENING_BRACE_PATTERN.matcher(newDesc).replaceAll(Matcher.quoteReplacement("(Ljava/lang/Object;"));

							final InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(memberNames.generateMethodName(insn.getOpcode() - GETSTATIC, false), newDesc, bootstrapHandle);
							insns.insertBefore(f, new LdcInsnNode(f.owner.replace('/', '.')));
							insns.insertBefore(f, ASMUtils.getNumberInsn(hashType(f.desc) & 0xffffffffL | (long) f.name.hashCode() << 32));
							insns.set(f, indy);

							counter.incrementAndGet();
						}
					}
				});
			});
		});

		final ClassNode decryptor = createBootstrapClass(memberNames);
		getClasses().put(decryptor.name, new ClassWrapper(decryptor, false));

		info("+ Hid API " + counter.get() + " references using invokedynamic");
	}

	private static int hashType(final String sType)
	{
		final Type type = Type.getType(sType);

		return (type.getSort() == Type.ARRAY ? type.getInternalName().replace('/', '.') : type.getClassName()).hashCode();
	}

	private static int hash(final String methodDescriptor)
	{

		final Type[] types = Type.getArgumentTypes(methodDescriptor);

		int hash = Arrays.stream(types).mapToInt(type -> (type.getSort() == Type.ARRAY ? type.getInternalName().replace('/', '.') : type.getClassName()).hashCode()).reduce(0, (a, b) -> a ^ b);

		final Type returnType = Type.getReturnType(methodDescriptor);
		hash ^= (returnType.getSort() == Type.ARRAY ? returnType.getInternalName().replace('/', '.') : returnType.getClassName()).hashCode();

		return hash;
	}

	@SuppressWarnings("Duplicates")
	private ClassNode createBootstrapClass(final MemberNames memberNames)
	{
		final ClassNode cw = new ClassNode();
		MethodVisitor mv;

		cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, memberNames.className, null, "java/lang/Object", null);

		FieldVisitor fv = cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, memberNames.methodCacheFieldName, "Ljava/util/Map;", null, null);
		fv.visitEnd();
		fv = cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, memberNames.fieldCacheFieldName, "Ljava/util/Map;", null, null);
		fv.visitEnd();
		{
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			mv.visitInsn(RETURN);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, memberNames.hashMethodName, "(Ljava/lang/reflect/Method;)I", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 1);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 2);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getParameterCount", "()I", false);
			final Label l3 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l3);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getParameterTypes", "()[Ljava/lang/Class;", false);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 1);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitIincInsn(2, 1);
			mv.visitJumpInsn(GOTO, l2);
			mv.visitLabel(l3);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 1);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitInsn(IRETURN);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitMaxs(3, 3);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, memberNames.findMethodMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Method;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", false);
			mv.visitVarInsn(ASTORE, 3);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 5);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(ARRAYLENGTH);
			final Label l3 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l3);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ILOAD, 1);
			final Label l5 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l5);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.hashMethodName, "(Ljava/lang/reflect/Method;)I", false);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitJumpInsn(IF_ICMPNE, l5);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitVarInsn(ASTORE, 4);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "setAccessible", "(Z)V", false);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l5);
			mv.visitIincInsn(5, 1);
			mv.visitJumpInsn(GOTO, l2);
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;", false);
			final Label l9 = new Label();
			mv.visitJumpInsn(IFNULL, l9);
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 5);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitVarInsn(ISTORE, 6);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 7);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitVarInsn(ILOAD, 6);
			mv.visitJumpInsn(IF_ICMPGE, l9);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitInsn(AALOAD);
			mv.visitVarInsn(ASTORE, 8);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitVarInsn(ALOAD, 8);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findMethodMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Method;", false);
			mv.visitVarInsn(ASTORE, 4);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitVarInsn(ALOAD, 4);
			final Label l14 = new Label();
			mv.visitJumpInsn(IFNULL, l14);
			final Label l15 = new Label();
			mv.visitLabel(l15);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "setAccessible", "(Z)V", false);
			final Label l16 = new Label();
			mv.visitLabel(l16);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l14);
			mv.visitIincInsn(7, 1);
			mv.visitJumpInsn(GOTO, l11);
			mv.visitLabel(l9);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
			final Label l17 = new Label();
			mv.visitJumpInsn(IFNULL, l17);
			final Label l18 = new Label();
			mv.visitLabel(l18);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 0);
			final Label l19 = new Label();
			mv.visitLabel(l19);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findMethodMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Method;", false);
			mv.visitVarInsn(ASTORE, 4);
			final Label l20 = new Label();
			mv.visitLabel(l20);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitJumpInsn(IFNULL, l9);
			final Label l21 = new Label();
			mv.visitLabel(l21);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "setAccessible", "(Z)V", false);
			final Label l22 = new Label();
			mv.visitLabel(l22);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l17);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			final Label l23 = new Label();
			mv.visitLabel(l23);
			mv.visitMaxs(3, 9);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, memberNames.findFieldMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Field;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;", false);
			mv.visitVarInsn(ASTORE, 3);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 5);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(ARRAYLENGTH);
			final Label l3 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l3);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ILOAD, 1);
			final Label l5 = new Label();
			mv.visitJumpInsn(IF_ICMPNE, l5);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitJumpInsn(IF_ICMPNE, l5);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(AALOAD);
			mv.visitVarInsn(ASTORE, 4);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l5);
			mv.visitIincInsn(5, 1);
			mv.visitJumpInsn(GOTO, l2);
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;", false);
			final Label l9 = new Label();
			mv.visitJumpInsn(IFNULL, l9);
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 5);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitVarInsn(ISTORE, 6);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 7);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitVarInsn(ILOAD, 6);
			mv.visitJumpInsn(IF_ICMPGE, l9);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitInsn(AALOAD);
			mv.visitVarInsn(ASTORE, 8);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitVarInsn(ALOAD, 8);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findFieldMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Field;", false);
			mv.visitVarInsn(ASTORE, 4);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitVarInsn(ALOAD, 4);
			final Label l14 = new Label();
			mv.visitJumpInsn(IFNULL, l14);
			final Label l15 = new Label();
			mv.visitLabel(l15);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
			final Label l16 = new Label();
			mv.visitLabel(l16);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l14);
			mv.visitIincInsn(7, 1);
			mv.visitJumpInsn(GOTO, l11);
			mv.visitLabel(l9);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
			final Label l17 = new Label();
			mv.visitJumpInsn(IFNULL, l17);
			final Label l18 = new Label();
			mv.visitLabel(l18);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 0);
			final Label l19 = new Label();
			mv.visitLabel(l19);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ILOAD, 1);
			mv.visitVarInsn(ILOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findFieldMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Field;", false);
			mv.visitVarInsn(ASTORE, 4);
			final Label l20 = new Label();
			mv.visitLabel(l20);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitJumpInsn(IFNULL, l9);
			final Label l21 = new Label();
			mv.visitLabel(l21);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
			final Label l22 = new Label();
			mv.visitLabel(l22);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l17);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			final Label l23 = new Label();
			mv.visitLabel(l23);
			mv.visitMaxs(3, 9);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, memberNames.resolveMethodHandleMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MutableCallSite;[Ljava/lang/Object;)Ljava/lang/Object;", null, new String[]
			{
					"java/lang/Throwable"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitLdcInsn(memberNames.separator);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
			mv.visitVarInsn(ASTORE, 5);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitInsn(ICONST_0);
			mv.visitInsn(AALOAD);
			mv.visitInsn(ICONST_0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
			mv.visitVarInsn(ISTORE, 6);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitVarInsn(ALOAD, 5);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(AALOAD);
			mv.visitInsn(ICONST_0);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(ISUB);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
			mv.visitVarInsn(ISTORE, 7);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitInsn(ICONST_1);
			mv.visitInsn(ISUB);
			mv.visitInsn(AALOAD);
			mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
			mv.visitVarInsn(LSTORE, 8);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(LLOAD, 8);
			mv.visitIntInsn(BIPUSH, 32);
			mv.visitInsn(LSHR);
			mv.visitInsn(L2I);
			mv.visitVarInsn(ISTORE, 10);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitVarInsn(LLOAD, 8);
			mv.visitInsn(L2I);
			mv.visitVarInsn(ISTORE, 11);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(ISUB);
			mv.visitInsn(AALOAD);
			mv.visitTypeInsn(CHECKCAST, "java/lang/String");
			mv.visitVarInsn(ASTORE, 12);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 13);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ILOAD, 6);
			ASMUtils.getNumberInsn(memberNames.methodAccessFlag).accept(mv);
			mv.visitInsn(IAND);
			final Label l9 = new Label();
			mv.visitJumpInsn(IFEQ, l9);
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitVarInsn(ALOAD, 13);
			mv.visitVarInsn(ILOAD, 10);
			mv.visitVarInsn(ILOAD, 11);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findMethodMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Method;", false);
			mv.visitVarInsn(ASTORE, 15);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitVarInsn(ALOAD, 15);
			final Label l12 = new Label();
			mv.visitJumpInsn(IFNONNULL, l12);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitTypeInsn(NEW, "java/lang/NoSuchMethodException");
			mv.visitInsn(DUP);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitIntInsn(BIPUSH, 32);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitIntInsn(BIPUSH, 32);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ILOAD, 11);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NoSuchMethodException", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitInsn(ATHROW);
			mv.visitLabel(l12);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;", false);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getParameterTypes", "()[Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitVarInsn(ASTORE, 16);
			final Label l14 = new Label();
			mv.visitLabel(l14);
			mv.visitVarInsn(ILOAD, 7);
			ASMUtils.getNumberInsn(memberNames.nameFlags[0]).accept(mv);
			mv.visitInsn(IAND);
			final Label l15 = new Label();
			mv.visitJumpInsn(IFEQ, l15);
			final Label l16 = new Label();
			mv.visitLabel(l16);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflect", "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 16);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l17 = new Label();
			mv.visitLabel(l17);
			final Label l18 = new Label();
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l15);
			mv.visitVarInsn(ILOAD, 7);
			ASMUtils.getNumberInsn(memberNames.nameFlags[1]).accept(mv);
			mv.visitInsn(IAND);
			final Label l19 = new Label();
			mv.visitJumpInsn(IFEQ, l19);
			final Label l20 = new Label();
			mv.visitLabel(l20);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflect", "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 16);
			mv.visitInsn(ICONST_0);
			final Label l21 = new Label();
			mv.visitLabel(l21);
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			final Label l22 = new Label();
			mv.visitLabel(l22);
			mv.visitLdcInsn(Type.getType("Ljava/lang/Object;"));
			mv.visitInsn(AASTORE);
			final Label l23 = new Label();
			mv.visitLabel(l23);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "insertParameterTypes", "(I[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l24 = new Label();
			mv.visitLabel(l24);
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l19);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitLdcInsn(Type.getType("L" + memberNames.className + ";"));
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectSpecial", "(Ljava/lang/reflect/Method;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 16);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l25 = new Label();
			mv.visitLabel(l25);
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l9);
			mv.visitVarInsn(ALOAD, 13);
			mv.visitVarInsn(ILOAD, 10);
			mv.visitVarInsn(ILOAD, 11);
			mv.visitMethodInsn(INVOKESTATIC, memberNames.className, memberNames.findFieldMethodName, "(Ljava/lang/Class;II)Ljava/lang/reflect/Field;", false);
			mv.visitVarInsn(ASTORE, 15);
			final Label l26 = new Label();
			mv.visitLabel(l26);
			mv.visitVarInsn(ALOAD, 15);
			final Label l27 = new Label();
			mv.visitJumpInsn(IFNONNULL, l27);
			final Label l28 = new Label();
			mv.visitLabel(l28);
			mv.visitTypeInsn(NEW, "java/lang/NoSuchFieldException");
			mv.visitInsn(DUP);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitIntInsn(BIPUSH, 32);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitIntInsn(BIPUSH, 32);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitVarInsn(ILOAD, 11);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NoSuchFieldException", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitInsn(ATHROW);
			mv.visitLabel(l27);
			mv.visitVarInsn(ILOAD, 7);
			ASMUtils.getNumberInsn(memberNames.nameFlags[0]).accept(mv);
			mv.visitInsn(IAND);
			final Label l29 = new Label();
			mv.visitJumpInsn(IFEQ, l29);
			final Label l30 = new Label();
			mv.visitLabel(l30);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectGetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l31 = new Label();
			mv.visitLabel(l31);
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l29);
			mv.visitVarInsn(ILOAD, 7);
			ASMUtils.getNumberInsn(memberNames.nameFlags[1]).accept(mv);
			mv.visitInsn(IAND);
			final Label l32 = new Label();
			mv.visitJumpInsn(IFEQ, l32);
			final Label l33 = new Label();
			mv.visitLabel(l33);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectSetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l34 = new Label();
			mv.visitLabel(l34);
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l32);
			mv.visitVarInsn(ILOAD, 7);
			ASMUtils.getNumberInsn(memberNames.nameFlags[2]).accept(mv);
			mv.visitInsn(IAND);
			final Label l35 = new Label();
			mv.visitJumpInsn(IFEQ, l35);
			final Label l36 = new Label();
			mv.visitLabel(l36);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectGetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitInsn(ICONST_0);
			final Label l37 = new Label();
			mv.visitLabel(l37);
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			final Label l38 = new Label();
			mv.visitLabel(l38);
			mv.visitLdcInsn(Type.getType("Ljava/lang/Object;"));
			mv.visitInsn(AASTORE);
			final Label l39 = new Label();
			mv.visitLabel(l39);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "insertParameterTypes", "(I[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l40 = new Label();
			mv.visitLabel(l40);
			mv.visitJumpInsn(GOTO, l18);
			mv.visitLabel(l35);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "unreflectSetter", "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
			mv.visitVarInsn(ALOAD, 15);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitInsn(ICONST_0);
			final Label l41 = new Label();
			mv.visitLabel(l41);
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			final Label l42 = new Label();
			mv.visitLabel(l42);
			mv.visitLdcInsn(Type.getType("Ljava/lang/Object;"));
			mv.visitInsn(AASTORE);
			final Label l43 = new Label();
			mv.visitLabel(l43);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "insertParameterTypes", "(I[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ASTORE, 14);
			mv.visitLabel(l18);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "parameterCount", "()I", false);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(ISUB);
			final Label l44 = new Label();
			mv.visitLabel(l44);
			mv.visitInsn(ICONST_2);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			final Label l45 = new Label();
			mv.visitLabel(l45);
			mv.visitLdcInsn(Type.getType("Ljava/lang/String;"));
			mv.visitInsn(AASTORE);
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_1);
			mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
			mv.visitInsn(AASTORE);
			final Label l46 = new Label();
			mv.visitLabel(l46);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "dropArguments", "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
			final Label l47 = new Label();
			mv.visitLabel(l47);
			mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;"));
			mv.visitVarInsn(ALOAD, 4);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asSpreader", "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;", false);
			final Label l48 = new Label();
			mv.visitLabel(l48);
			mv.visitInsn(ARETURN);
			final Label l49 = new Label();
			mv.visitLabel(l49);
			mv.visitMaxs(7, 17);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, memberNames.bootstrapMethodName, "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			final Label l1 = new Label();
			final Label l2 = new Label();
			mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitTypeInsn(NEW, "java/lang/invoke/MutableCallSite");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodType");
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/MutableCallSite", "<init>", "(Ljava/lang/invoke/MethodType;)V", false);
			mv.visitVarInsn(ASTORE, 3);
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 3);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitLdcInsn(new Handle(H_INVOKESTATIC, memberNames.className, memberNames.resolveMethodHandleMethodName, "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MutableCallSite;[Ljava/lang/Object;)Ljava/lang/Object;", false));
			mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;"));
			mv.visitVarInsn(ALOAD, 2);
			mv.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodType");
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "parameterCount", "()I", false);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asCollector", "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitInsn(ICONST_0);
			mv.visitInsn(ICONST_4);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitInsn(AASTORE);
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_1);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitInsn(AASTORE);
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_2);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(AASTORE);
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_3);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(AASTORE);
			final Label l9 = new Label();
			mv.visitLabel(l9);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "insertArguments", "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;", false);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitTypeInsn(CHECKCAST, "java/lang/invoke/MethodType");
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "explicitCastArguments", "(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MutableCallSite", "setTarget", "(Ljava/lang/invoke/MethodHandle;)V", false);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/CallSite", "getTarget", "()Ljava/lang/invoke/MethodHandle;", false);
			mv.visitLabel(l1);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l2);
			mv.visitVarInsn(ASTORE, 4);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitVarInsn(ALOAD, 4);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
			final Label l14 = new Label();
			mv.visitLabel(l14);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ARETURN);
			final Label l15 = new Label();
			mv.visitLabel(l15);
			mv.visitMaxs(7, 5);
			mv.visitEnd();
		}
		mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		final Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitTypeInsn(NEW, "java/util/HashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
		mv.visitFieldInsn(PUTSTATIC, memberNames.className, memberNames.methodCacheFieldName, "Ljava/util/Map;");
		final Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitTypeInsn(NEW, "java/util/HashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
		mv.visitFieldInsn(PUTSTATIC, memberNames.className, memberNames.fieldCacheFieldName, "Ljava/util/Map;");
		mv.visitInsn(RETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
		cw.visitEnd();

		return cw;
	}

	private class MemberNames
	{
		final String className;
		final String methodCacheFieldName;
		final String fieldCacheFieldName;
		final String hashMethodName;
		final String findMethodMethodName;
		final String findFieldMethodName;
		final String resolveMethodHandleMethodName;
		final String bootstrapMethodName;
		final int nameLength;
		final String separator;
		final int[] nameFlags;
		final int methodAccessFlag;

		MemberNames()
		{
			className = randomClassName();

			final WrappedDictionary fieldDictionary = getFieldDictionary(className);
			final WrappedDictionary methodDictionary = getMethodDictionary(className);
			methodCacheFieldName = fieldDictionary.nextUniqueString();
			fieldCacheFieldName = fieldDictionary.nextUniqueString();
			hashMethodName = methodDictionary.nextUniqueString();
			findMethodMethodName = methodDictionary.nextUniqueString();
			findFieldMethodName = methodDictionary.nextUniqueString();
			resolveMethodHandleMethodName = methodDictionary.nextUniqueString();
			bootstrapMethodName = methodDictionary.nextUniqueString();
			nameLength = 5; // Make customizable
			separator = "\u0000\u0000";

			// method / field
			// 0: INVOKESTATIC / GETSTATIC
			// 1: INVOKEVIRTUAL / PUTSTATIC
			// 2: INVOKESPECIAL / GETFIELD
			// else -> PUTFIELD
			nameFlags = new int[3];

			final Collection<Integer> rngExclusions = new HashSet<>(3);
			for (int i = 0; i < 3; i++)
				nameFlags[i] = 1 << RandomUtils.getRandomIntWithExclusion(0, 16, rngExclusions);

			methodAccessFlag = 1 << RandomUtils.getRandomInt(16);
		}

		String generateMethodName(final int nameFlagIndex, final boolean isMethodAccess)
		{
			final char[] separatorChars = separator.toCharArray();

			char positionOfTrueFlag;
			do
				positionOfTrueFlag = (char) RandomUtils.getRandomInt(nameLength);
			while (ASMUtils.isIllegalMethodName(positionOfTrueFlag) || ArrayUtils.indexOf(separatorChars, positionOfTrueFlag) > 0);

			final char[] chars = new char[nameLength];

			char accessFlag = (char) RandomUtils.getRandomInt(Character.MAX_VALUE);
			if (isMethodAccess)
				accessFlag |= methodAccessFlag;
			else
				accessFlag &= ~methodAccessFlag;

			char opTypeValue = 0;
			for (int i = 0; i < 4; i++)
			{
				final int flag = i < 3 ? nameFlags[i] : 0;
				if (i == nameFlagIndex)
					opTypeValue |= flag;
				else
					opTypeValue &= ~flag;
			}

			// Hide the true value between fake values
			for (int i = 0; i < nameLength; i++)
				if (i == positionOfTrueFlag)
					chars[i] = opTypeValue;
				else
				{
					char randomChar;
					do
						randomChar = (char) RandomUtils.getRandomInt(Character.MAX_VALUE);
					while (ASMUtils.isIllegalMethodName(randomChar) || ArrayUtils.indexOf(separatorChars, randomChar) > 0);

					chars[i] = randomChar;
				}

			return accessFlag + separator + (char) (positionOfTrueFlag + 1) + separator + String.valueOf(chars);
		}

		public String[] toStrings()
		{
			final String[] strings = new String[11];
			strings[0] = "Decryptor class name: " + className;
			strings[1] = "Method cache field name: " + methodCacheFieldName;
			strings[2] = "Field cache field name: " + fieldCacheFieldName;
			strings[3] = "Hash method name: " + hashMethodName;
			strings[4] = "Find method method name: " + findMethodMethodName;
			strings[5] = "Find field method name: " + findFieldMethodName;
			strings[6] = "Resolve method handle method name: " + resolveMethodHandleMethodName;
			strings[7] = "Bootstrap method name: " + bootstrapMethodName;
			strings[8] = "Name length: " + nameLength;
			strings[9] = "Invocation data separator : '" + separator + "' (" + Strings.stringToHexBytes(separator) + ")";

			final StringJoiner identifierOrderBuilder = new StringJoiner(", ", "[", "]");
			for (final int i : nameFlags)
				identifierOrderBuilder.add(Strings.intToHexByte(i, 4));
			strings[10] = "Method name flags : " + identifierOrderBuilder;
			return strings;
		}
	}
}
