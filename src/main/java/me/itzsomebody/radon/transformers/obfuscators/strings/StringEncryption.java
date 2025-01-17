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

package me.itzsomebody.radon.transformers.obfuscators.strings;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.dictionaries.WrappedDictionary;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.ArrayUtils;
import me.itzsomebody.radon.utils.RandomUtils;

import static me.itzsomebody.radon.config.ConfigurationSetting.STRING_ENCRYPTION;

/**
 * Abstract class for string encryption transformers.
 *
 * @author ItzSomebody
 */
public class StringEncryption extends Transformer
{
	private List<String> exemptedStrings;

	/**
	 * String Encryption
	 */
	private boolean contextCheckingEnabled;

	/**
	 * String Pooler
	 */
	boolean stringPoolerEnabled;
	boolean stringPoolerRandomOrder;
	boolean stringPoolerGlobal;
	boolean stringPoolerInjectGlobalPool;

	@Override
	public void transform()
	{
		if (stringPoolerEnabled)
		{
			final StringPooler pooler = new StringPooler(this);
			pooler.init(radon);
			pooler.transform();
		}

		final MemberNames memberNames = new MemberNames();
		verboseInfos(memberNames::toStrings);

		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.methods.stream().filter(this::included).forEach(methodWrapper ->
		{
			int leeway = methodWrapper.getLeewaySize();

			for (final AbstractInsnNode insn : methodWrapper.methodNode.instructions.toArray())
			{
				if (leeway < 10000)
				{
					final int finalLeeway = leeway;
					verboseWarn(() -> "! Skipped method " + methodWrapper.originalName + " because of insufficient leeway (leeway: " + finalLeeway + ")");
					break;
				}

				if (insn instanceof LdcInsnNode)
				{
					final LdcInsnNode ldc = (LdcInsnNode) insn;
					if (ldc.cst instanceof String)
					{
						final String string = (String) ldc.cst;
						if (excludedString(string))
							continue;

						final int callerClassHC = classWrapper.getName().replace('/', '.').hashCode();
						final int callerMethodHC = methodWrapper.methodNode.name.replace('/', '.').hashCode();
						final int decryptorClassHC = memberNames.className.replace('/', '.').hashCode();
						final int decryptorMethodHC = memberNames.decryptMethodName.replace('/', '.').hashCode();

						final int[] randomKeys =
						{
								RandomUtils.getRandomInt(), RandomUtils.getRandomInt(), RandomUtils.getRandomInt()
						};

						final int[] keys =
						{
								(contextCheckingEnabled ? decryptorClassHC + callerClassHC + callerMethodHC : 0) ^ randomKeys[0] ^ randomKeys[1], // RC2 ^ RC3
								(contextCheckingEnabled ? callerMethodHC + decryptorMethodHC + callerClassHC : 0) ^ randomKeys[1] ^ randomKeys[2], // RC1 ^ RC2
								(contextCheckingEnabled ? decryptorClassHC + callerClassHC + callerMethodHC : 0) ^ randomKeys[0] ^ randomKeys[2], // RC1 ^ RC3
								(contextCheckingEnabled ? decryptorMethodHC + callerClassHC + decryptorClassHC : 0) ^ randomKeys[0] ^ randomKeys[1] ^ randomKeys[2] // RC1 ^ RC2 ^ RC3
						};

						for (int i = 0; i < 3; i++)
							ArrayUtils.swap(randomKeys, i, memberNames.randomKeyOrder[i]);
						for (int i = 0; i < 4; i++)
							ArrayUtils.swap(keys, i, memberNames.keyOrder[i]);

						ldc.cst = encrypt(string, keys[0], keys[1], keys[2], keys[3]);

						final InsnList decryptorCall = new InsnList();
						decryptorCall.add(ASMUtils.getNumberInsn(randomKeys[0]));
						decryptorCall.add(ASMUtils.getNumberInsn(randomKeys[1]));
						decryptorCall.add(ASMUtils.getNumberInsn(randomKeys[2]));
						decryptorCall.add(new MethodInsnNode(INVOKESTATIC, memberNames.className, memberNames.decryptMethodName, "(Ljava/lang/Object;III)Ljava/lang/String;", false));
						methodWrapper.getInstructions().insert(ldc, decryptorCall);

						leeway -= ASMUtils.evaluateMaxSize(decryptorCall);
						counter.incrementAndGet();
					}
				}
			}
		}));

		final ClassNode decryptor = createDecryptor(memberNames);
		getClasses().put(decryptor.name, new ClassWrapper(decryptor, false));

		info("+ Encrypted " + counter.get() + " strings");
	}

	@Override
	public String getName()
	{
		return "String Encryption";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.STRING_ENCRYPTION;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		exemptedStrings = config.getOrDefault(STRING_ENCRYPTION + ".exempted_strings", Collections.emptyList());
		contextCheckingEnabled = config.getOrDefault(STRING_ENCRYPTION + ".check_context", false);
		stringPoolerEnabled = config.getOrDefault(STRING_ENCRYPTION + ".pool_strings", false);
		stringPoolerRandomOrder = config.getOrDefault(STRING_ENCRYPTION + ".stringpooler_randomorder", false);
		stringPoolerGlobal = config.getOrDefault(STRING_ENCRYPTION + ".stringpooler_global", false);
		stringPoolerInjectGlobalPool = config.getOrDefault(STRING_ENCRYPTION + ".stringpooler_globalinject", false);
	}

	protected boolean excludedString(final String str)
	{
		return exemptedStrings.stream().anyMatch(str::contains);
	}

	private static String encrypt(final String s, final int key1, final int key2, final int key3, final int key4)
	{
		final char[] chars = s.toCharArray();
		final int charCount = chars.length;
		final StringBuilder sb = new StringBuilder(charCount);
		for (int i = 0; i < charCount; i++)
			switch (i % 4)
			{
				case 0:
					sb.append((char) (chars[i] ^ key1));
					break;
				case 1:
					sb.append((char) (chars[i] ^ key2));
					break;
				case 2:
					sb.append((char) (chars[i] ^ key3));
					break;
				default:
					sb.append((char) (chars[i] ^ key4));
					break;
			}

		return sb.toString();
	}

	@SuppressWarnings("Duplicates")
	private ClassNode createDecryptor(final MemberNames memberNames)
	{
		final int[] randomKeyOrder = memberNames.randomKeyOrder;
		final int[] keyOrder = memberNames.keyOrder;

		final ClassNode cw = new ClassNode();
		MethodVisitor mv;

		cw.visit(V1_5, ACC_PUBLIC | ACC_SUPER, memberNames.className, null, "java/lang/Object", null);

		FieldVisitor fv = cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, memberNames.cacheFieldName, "Ljava/util/Map;", null, null);
		fv.visitEnd();
		fv = cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, memberNames.bigBoizFieldName, "[J", null, null);
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
			mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, memberNames.decryptMethodName, "(Ljava/lang/Object;III)Ljava/lang/String;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			final Label l1 = new Label();
			final Label l2 = new Label();
			mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Throwable");
			final Label l3 = new Label();
			final Label l4 = new Label();
			final Label l5 = new Label();
			mv.visitTryCatchBlock(l3, l4, l5, "java/lang/Throwable");
			final Label l6 = new Label();
			final Label l7 = new Label();
			final Label l8 = new Label();
			mv.visitTryCatchBlock(l6, l7, l8, "java/lang/Throwable");
			final Label l9 = new Label();
			mv.visitTryCatchBlock(l9, l4, l8, "java/lang/Throwable");
			final Label l10 = new Label();
			mv.visitTryCatchBlock(l5, l10, l8, "java/lang/Throwable");
			final Label l11 = new Label();
			mv.visitLabel(l11);
			mv.visitLdcInsn("\u9081\u76e1\uaffe\u6721\u45f9\ud627\u0f38\u2c54\u49c6\u5700");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 4);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitLdcInsn("\u6dcf\ucd2e\u739c\u6cec\u5344\u34aa\u873a\u6248\u66fd?");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 5);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitLdcInsn("\ue465\u1c76\u4ea0\u4eb5\u675e\uac6b\u976b\u5d9b\u851f\u6619");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 6);
			final Label l14 = new Label();
			mv.visitLabel(l14);
			mv.visitLdcInsn("\u4e92\u8f0f\uab1e\ud035\u80a1\u77ef\u7501\u0773\u3acf\ub9f2");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 7);
			final Label l15 = new Label();
			mv.visitLabel(l15);
			mv.visitLdcInsn("\uf0f4\u838d\u947d\u854d\u0d3e?\u98ee\ub733\uf42f\u3315");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 8);
			final Label l16 = new Label();
			mv.visitLabel(l16);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitTypeInsn(CHECKCAST, "java/lang/String");
			mv.visitVarInsn(ASTORE, 9);
			final Label l17 = new Label();
			mv.visitLabel(l17);
			mv.visitTypeInsn(NEW, "java/util/concurrent/atomic/AtomicInteger");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitMethodInsn(INVOKESPECIAL, "java/util/concurrent/atomic/AtomicInteger", "<init>", "(I)V", false);
			mv.visitVarInsn(ASTORE, 10);
			final Label l18 = new Label();
			mv.visitLabel(l18);
			mv.visitVarInsn(ALOAD, 10);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicInteger", "incrementAndGet", "()I", false);
			mv.visitInsn(POP);
			final Label l19 = new Label();
			mv.visitLabel(l19);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.cacheFieldName, "Ljava/util/Map;");
			final Label l20 = new Label();
			mv.visitJumpInsn(IFNONNULL, l20);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_0);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l20);
			mv.visitVarInsn(ILOAD, 5);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_1);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l20);
			mv.visitVarInsn(ILOAD, 6);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(3L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_2);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l20);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(6L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_3);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l20);
			mv.visitVarInsn(ILOAD, 8);
			mv.visitInsn(ICONST_3);
			mv.visitInsn(IREM);
			mv.visitInsn(I2L);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_4);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l20);
			final Label l21 = new Label();
			mv.visitLabel(l21);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
			mv.visitVarInsn(ASTORE, 12);
			final Label l22 = new Label();
			mv.visitLabel(l22);
			mv.visitVarInsn(ALOAD, 9);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
			mv.visitVarInsn(ASTORE, 13);
			mv.visitLabel(l0);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l23 = new Label();
			mv.visitLabel(l23);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false);
			mv.visitVarInsn(ASTORE, 15);
			final Label l24 = new Label();
			mv.visitLabel(l24);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 15);
			mv.visitInsn(ICONST_2);
			mv.visitInsn(AALOAD);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
			mv.visitInsn(IADD);
			mv.visitVarInsn(ISTORE, 16);
			final Label l25 = new Label();
			mv.visitLabel(l25);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 17);
			final Label l26 = new Label();
			mv.visitLabel(l26);
			mv.visitVarInsn(ILOAD, 17);
			mv.visitVarInsn(ALOAD, 13);
			mv.visitInsn(ARRAYLENGTH);
			final Label l27 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l27);
			final Label l28 = new Label();
			mv.visitLabel(l28);
			mv.visitVarInsn(ALOAD, 13);
			mv.visitVarInsn(ILOAD, 17);
			mv.visitInsn(CALOAD);
			mv.visitVarInsn(ISTORE, 18);
			final Label l29 = new Label();
			mv.visitLabel(l29);
			mv.visitVarInsn(ILOAD, 17);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(IREM);
			final Label l30 = new Label();
			final Label l31 = new Label();
			final Label l32 = new Label();
			final Label l33 = new Label();
			final Label l34 = new Label();
			mv.visitTableSwitchInsn(0, 3, l34, l30, l31, l32, l33);
			mv.visitLabel(l30);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHL);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(IUSHR);
			mv.visitInsn(IOR);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 19);
			final Label l35 = new Label();
			mv.visitLabel(l35);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(IXOR);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ISTORE, 20);
			final Label l36 = new Label();
			mv.visitLabel(l36);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 16);
			mv.visitInsn(IAND);
			mv.visitVarInsn(ISTORE, 21);
			final Label l37 = new Label();
			mv.visitLabel(l37);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 20);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ILOAD, 21);
			mv.visitInsn(IXOR);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l38 = new Label();
			mv.visitLabel(l38);
			mv.visitJumpInsn(GOTO, l34);
			mv.visitLabel(l31);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 19);
			final Label l39 = new Label();
			mv.visitLabel(l39);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(ISHL);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitLdcInsn(65535);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(IXOR);
			mv.visitInsn(IOR);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ISTORE, 20);
			final Label l40 = new Label();
			mv.visitLabel(l40);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 16);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ISTORE, 21);
			final Label l41 = new Label();
			mv.visitLabel(l41);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 20);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ILOAD, 21);
			mv.visitInsn(IXOR);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l42 = new Label();
			mv.visitLabel(l42);
			mv.visitJumpInsn(GOTO, l34);
			mv.visitLabel(l32);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHL);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(IUSHR);
			mv.visitInsn(IOR);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 19);
			final Label l43 = new Label();
			mv.visitLabel(l43);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IADD);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(IXOR);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ISTORE, 20);
			final Label l44 = new Label();
			mv.visitLabel(l44);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 16);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 21);
			final Label l45 = new Label();
			mv.visitLabel(l45);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 20);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ILOAD, 21);
			mv.visitInsn(IXOR);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l46 = new Label();
			mv.visitLabel(l46);
			mv.visitJumpInsn(GOTO, l34);
			mv.visitLabel(l33);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 19);
			final Label l47 = new Label();
			mv.visitLabel(l47);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(ISHL);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IREM);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitInsn(IXOR);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ISTORE, 20);
			final Label l48 = new Label();
			mv.visitLabel(l48);
			mv.visitVarInsn(ILOAD, 18);
			mv.visitVarInsn(ILOAD, 16);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IAND);
			mv.visitVarInsn(ISTORE, 21);
			final Label l49 = new Label();
			mv.visitLabel(l49);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitVarInsn(ILOAD, 19);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 20);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ILOAD, 21);
			mv.visitInsn(IXOR);
			mv.visitLdcInsn(65535);
			mv.visitInsn(IAND);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			mv.visitLabel(l34);
			mv.visitIincInsn(17, 1);
			mv.visitJumpInsn(GOTO, l26);
			mv.visitLabel(l27);
			mv.visitVarInsn(ALOAD, 12);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitLabel(l1);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l2);
			mv.visitVarInsn(ASTORE, 14);
			final Label l50 = new Label();
			mv.visitLabel(l50);
			mv.visitVarInsn(ALOAD, 9);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l20);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.cacheFieldName, "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(CHECKCAST, "java/lang/String");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ASTORE, 11);
			final Label l51 = new Label();
			mv.visitLabel(l51);
			final Label l52 = new Label();
			mv.visitJumpInsn(IFNULL, l52);
			final Label l53 = new Label();
			mv.visitLabel(l53);
			mv.visitVarInsn(ALOAD, 11);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l52);
			mv.visitLdcInsn("\u6b40\u0304\u6293\u06b0\u6835\u1870\u7e9f\u811b\u7d58\ub1db");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 12);
			final Label l54 = new Label();
			mv.visitLabel(l54);
			mv.visitLdcInsn("\u0db1\ue04a\ua586\u7651\u8ae3\u6b16\u936d\ub649\u04e8\u38fa");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 13);
			final Label l55 = new Label();
			mv.visitLabel(l55);
			mv.visitVarInsn(ALOAD, 9);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
			mv.visitVarInsn(ASTORE, 14);
			final Label l56 = new Label();
			mv.visitLabel(l56);
			mv.visitLdcInsn("\ua91e\u4d22\ua711\u961f\uf7da\u72f4\u302e\u4562\u6adb\ub288");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 15);
			final Label l57 = new Label();
			mv.visitLabel(l57);
			mv.visitLdcInsn("\ube16\u9e52\u35f2\u6697\u0898\ue5e6\u914e\u2e51\uc9e8\uf3d2");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
			mv.visitVarInsn(ISTORE, 16);
			final Label l58 = new Label();
			mv.visitLabel(l58);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitIntInsn(NEWARRAY, T_CHAR);
			mv.visitVarInsn(ASTORE, 17);
			mv.visitLabel(l6);

			// <editor-fold desc="key1">
			if (contextCheckingEnabled)
			{
				// TODO: 반드시 'Object.hashCode()'를 쓸 필요는 없잖아? 'String.length()'도 있고... 'String.indexOf(int)'도 있고...
				// TODO: 해시코드끼리 XOR연산을 하거나, Objects.hash(Object[])을(를) 이용함으로써 2개 이상의 해시코드를 합쳐서 하나의 키로 사용하는 것도 나쁘지 않을 것 같다.

				mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
				mv.visitVarInsn(ASTORE, 18);
				final Label l59 = new Label();
				mv.visitLabel(l59);
				mv.visitVarInsn(ALOAD, 18);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false);
				mv.visitVarInsn(ASTORE, 19);
				final Label l60_key1 = new Label();
				mv.visitLabel(l60_key1);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_1);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);// TODO: 여기에 굳이 ADD 말고도 다른 연산자들도 사용할 수 있지 않을까? (SUB, MUL, DIV, MOD, AND, OR, XOR 등등...)
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
			}

			final int[] randomKeyVar =
			{
					1, 2, 3
			};
			for (int i = 0; i < 3; i++)
				ArrayUtils.swap(randomKeyVar, i, randomKeyOrder[i]);

			// TODO: 반드시 KEY1 ^ KEY2, KEY1 ^ KEY2 ^ KEY3처럼 2~3개의 키를 섞어서 사용하라는 법은 없잖아? KEY1, KEY2 이런식으로 키 1개만 사용해도 되고...
			mv.visitVarInsn(ILOAD, randomKeyVar[0]); // Random Key #1
			if (contextCheckingEnabled)
				mv.visitInsn(IXOR); // TODO: 단순히 XOR로 끝나지 않고, 다른 bitwise 연산들(AND, OR, NOT 등등)도 사용하도록 개선하기!
			mv.visitVarInsn(ILOAD, randomKeyVar[1]); // Random Key #2
			mv.visitInsn(IXOR);

			mv.visitVarInsn(ISTORE, 20);
			// </editor-fold>

			// <editor-fold desc="key2">
			if (contextCheckingEnabled)
			{
				final Label l61_key2 = new Label();
				mv.visitLabel(l61_key2);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_1);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
			}

			mv.visitVarInsn(ILOAD, randomKeyVar[1]); // Random Key #2
			if (contextCheckingEnabled)
				mv.visitInsn(IXOR);
			mv.visitVarInsn(ILOAD, randomKeyVar[2]); // Random Key #3
			mv.visitInsn(IXOR);

			mv.visitVarInsn(ISTORE, 21);
			// </editor-fold>
			final Label l62_key3 = new Label();
			mv.visitLabel(l62_key3);

			// <editor-fold desc="key3">
			if (contextCheckingEnabled)
			{
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_1);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
			}

			mv.visitVarInsn(ILOAD, randomKeyVar[0]); // Random Key #1
			if (contextCheckingEnabled)
				mv.visitInsn(IXOR);
			mv.visitVarInsn(ILOAD, randomKeyVar[2]); // Random Key #3
			mv.visitInsn(IXOR);

			mv.visitVarInsn(ISTORE, 22);
			// </editor-fold>
			final Label l63_key4 = new Label();
			mv.visitLabel(l63_key4);

			// <editor-fold desc="key4">
			if (contextCheckingEnabled)
			{
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_1);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_2);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
				mv.visitVarInsn(ALOAD, 19);
				mv.visitInsn(ICONST_1);
				mv.visitInsn(AALOAD);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
				mv.visitInsn(IADD);
			}

			mv.visitVarInsn(ILOAD, randomKeyVar[0]); // Random Key #1
			if (contextCheckingEnabled)
				mv.visitInsn(IXOR);
			mv.visitVarInsn(ILOAD, randomKeyVar[1]); // Random Key #2
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ILOAD, randomKeyVar[2]); // Random Key #3
			mv.visitInsn(IXOR);

			mv.visitVarInsn(ISTORE, 23);
			// </editor-fold>
			final Label l64 = new Label();
			mv.visitLabel(l64);
			mv.visitVarInsn(ILOAD, 12);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitInsn(ICONST_5);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l9);
			mv.visitVarInsn(ILOAD, 13);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitIntInsn(BIPUSH, 6);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l9);
			mv.visitVarInsn(ILOAD, 15);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitIntInsn(BIPUSH, 7);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l9);
			mv.visitVarInsn(ILOAD, 16);
			mv.visitInsn(I2L);
			mv.visitLdcInsn(2L);
			mv.visitInsn(LDIV);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
			mv.visitIntInsn(BIPUSH, 7);
			mv.visitInsn(LALOAD);
			mv.visitInsn(LCMP);
			mv.visitJumpInsn(IFGT, l9);
			final Label l65 = new Label();
			mv.visitLabel(l65);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 24);
			final Label l66 = new Label();
			mv.visitLabel(l66);
			mv.visitVarInsn(ILOAD, 24);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitInsn(ARRAYLENGTH);
			final Label l67 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l67);
			final Label l68 = new Label();
			mv.visitLabel(l68);
			mv.visitVarInsn(ILOAD, 24);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(IREM);
			final Label l69_key1 = new Label();
			final Label l70_key2 = new Label();
			final Label l71_key3 = new Label();
			final Label l72_key4 = new Label();
			final List<Label> labels = Arrays.asList(l69_key1, l70_key2, l71_key3, l72_key4);
			for (int i = 0; i < 4; i++)
				Collections.swap(labels, i, keyOrder[i]);
			final Label l73 = new Label();
			mv.visitTableSwitchInsn(0, 3, l73, labels.get(0), labels.get(1), labels.get(2), labels.get(3));
			for (int i = 0, j = keyOrder.length; i < j; i++)
			{
				switch (ArrayUtils.indexOf(keyOrder, i))
				{
					case 0:
					{
						// Key #1
						mv.visitLabel(l69_key1);
						mv.visitVarInsn(ALOAD, 17);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitVarInsn(ALOAD, 14);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitInsn(CALOAD);
						mv.visitVarInsn(ILOAD, 20);
						mv.visitInsn(IXOR);
						mv.visitLdcInsn(65535);
						mv.visitInsn(IAND);
						mv.visitInsn(I2C);
						mv.visitInsn(CASTORE);
						break;
					}

					case 1:
					{
						// Key #2
						mv.visitLabel(l70_key2);
						mv.visitVarInsn(ALOAD, 17);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitVarInsn(ALOAD, 14);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitInsn(CALOAD);
						mv.visitVarInsn(ILOAD, 21);
						mv.visitInsn(IXOR);
						mv.visitLdcInsn(65535);
						mv.visitInsn(IAND);
						mv.visitInsn(I2C);
						mv.visitInsn(CASTORE);
						break;
					}

					case 2:
					{
						// Key #3
						mv.visitLabel(l71_key3);
						mv.visitVarInsn(ALOAD, 17);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitVarInsn(ALOAD, 14);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitInsn(CALOAD);
						mv.visitVarInsn(ILOAD, 22);
						mv.visitInsn(IXOR);
						mv.visitLdcInsn(65535);
						mv.visitInsn(IAND);
						mv.visitInsn(I2C);
						mv.visitInsn(CASTORE);
						break;
					}

					case 3:
					{
						// Key #4
						mv.visitLabel(l72_key4);
						mv.visitVarInsn(ALOAD, 17);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitVarInsn(ALOAD, 14);
						mv.visitVarInsn(ILOAD, 24);
						mv.visitInsn(CALOAD);
						mv.visitVarInsn(ILOAD, 23);
						mv.visitInsn(IXOR);
						mv.visitLdcInsn(65535);
						mv.visitInsn(IAND);
						mv.visitInsn(I2C);
						mv.visitInsn(CASTORE);
						break;
					}
				}

				if (i < j - 1) // Last case doesn't need break statement (fall-through)
				{
					final Label breakLabel = new Label();
					mv.visitLabel(breakLabel);
					mv.visitJumpInsn(GOTO, l73);
				}
			}
			mv.visitLabel(l73);
			mv.visitIincInsn(24, 1);
			mv.visitJumpInsn(GOTO, l66);
			mv.visitLabel(l67);
			mv.visitTypeInsn(NEW, "java/lang/String");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 17);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
			mv.visitVarInsn(ASTORE, 24);
			final Label l77 = new Label();
			mv.visitLabel(l77);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.cacheFieldName, "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 9);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitInsn(POP);
			final Label l78 = new Label();
			mv.visitLabel(l78);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitLabel(l7);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l9);
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
			mv.visitVarInsn(ASTORE, 24);
			mv.visitLabel(l3);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 25);
			final Label l79 = new Label();
			mv.visitLabel(l79);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitInsn(ARRAYLENGTH);
			final Label l80 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l80);
			final Label l81 = new Label();
			mv.visitLabel(l81);
			// mv.visitVarInsn(ALOAD, 18);
			mv.visitInsn(ACONST_NULL);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getId", "()J", false);
			mv.visitInsn(L2I);
			mv.visitVarInsn(ISTORE, 26);
			final Label l82 = new Label();
			mv.visitLabel(l82);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Runtime", "getRuntime", "()Ljava/lang/Runtime;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Runtime", "availableProcessors", "()I", false);
			mv.visitVarInsn(ISTORE, 27);
			final Label l83 = new Label();
			mv.visitLabel(l83);
			mv.visitVarInsn(ALOAD, 10);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicInteger", "get", "()I", false);
			mv.visitVarInsn(ILOAD, randomKeyVar[0] + 1);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ISTORE, 28);
			final Label l84 = new Label();
			mv.visitLabel(l84);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitInsn(ICONST_4);
			mv.visitInsn(IREM);
			final Label l85 = new Label();
			final Label l86 = new Label();
			final Label l87 = new Label();
			final Label l88 = new Label();
			final Label l89 = new Label();
			mv.visitTableSwitchInsn(0, 3, l89, l85, l86, l87, l88);
			mv.visitLabel(l85);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitVarInsn(ILOAD, 26);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitVarInsn(ILOAD, 27);
			mv.visitInsn(IREM);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 28);
			mv.visitInsn(IAND);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitInsn(CALOAD);
			mv.visitInsn(IAND);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l90 = new Label();
			mv.visitLabel(l90);
			mv.visitJumpInsn(GOTO, l89);
			mv.visitLabel(l86);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitVarInsn(ILOAD, 26);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitVarInsn(ILOAD, 27);
			mv.visitInsn(IMUL);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 28);
			mv.visitInsn(IXOR);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitInsn(CALOAD);
			mv.visitInsn(IOR);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l91 = new Label();
			mv.visitLabel(l91);
			mv.visitJumpInsn(GOTO, l89);
			mv.visitLabel(l87);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitVarInsn(ILOAD, 26);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitVarInsn(ILOAD, 27);
			mv.visitInsn(IDIV);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 28);
			mv.visitInsn(IOR);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitInsn(CALOAD);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			final Label l92 = new Label();
			mv.visitLabel(l92);
			mv.visitJumpInsn(GOTO, l89);
			mv.visitLabel(l88);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitVarInsn(ILOAD, 26);
			mv.visitIntInsn(BIPUSH, 16);
			mv.visitVarInsn(ILOAD, 27);
			mv.visitInsn(IADD);
			mv.visitInsn(ISHR);
			mv.visitVarInsn(ILOAD, 28);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IAND);
			mv.visitVarInsn(ALOAD, 14);
			mv.visitVarInsn(ILOAD, 25);
			mv.visitInsn(CALOAD);
			mv.visitInsn(ICONST_M1);
			mv.visitInsn(IXOR);
			mv.visitInsn(IXOR);
			mv.visitInsn(I2C);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
			mv.visitInsn(POP);
			mv.visitLabel(l89);
			mv.visitIincInsn(25, 1);
			mv.visitJumpInsn(GOTO, l79);
			mv.visitLabel(l80);
			mv.visitVarInsn(ALOAD, 24);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitVarInsn(ASTORE, 25);
			final Label l93 = new Label();
			mv.visitLabel(l93);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.cacheFieldName, "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 9);
			mv.visitVarInsn(ALOAD, 25);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitInsn(POP);
			final Label l94 = new Label();
			mv.visitLabel(l94);
			mv.visitVarInsn(ALOAD, 25);
			mv.visitLabel(l4);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l5);
			mv.visitVarInsn(ASTORE, 25);
			final Label l95 = new Label();
			mv.visitLabel(l95);
			mv.visitVarInsn(ALOAD, 9);
			mv.visitLabel(l10);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l8);
			mv.visitVarInsn(ASTORE, 18);
			final Label l96 = new Label();
			mv.visitLabel(l96);
			mv.visitVarInsn(ALOAD, 9);
			mv.visitInsn(ARETURN);
			final Label l97 = new Label();
			mv.visitLabel(l97);
			mv.visitMaxs(7, 29);
			mv.visitEnd();
		}
		mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		final Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitTypeInsn(NEW, "java/util/HashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
		mv.visitFieldInsn(PUTSTATIC, memberNames.className, memberNames.cacheFieldName, "Ljava/util/Map;");
		final Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitIntInsn(BIPUSH, 13);
		mv.visitIntInsn(NEWARRAY, T_LONG);
		mv.visitFieldInsn(PUTSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		final Label l2 = new Label();
		mv.visitLabel(l2);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_0);
		mv.visitLdcInsn(8829304729L);
		mv.visitInsn(LASTORE);
		final Label l3 = new Label();
		mv.visitLabel(l3);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_1);
		mv.visitLdcInsn(4848002993994L);
		mv.visitInsn(LASTORE);
		final Label l4 = new Label();
		mv.visitLabel(l4);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_2);
		mv.visitLdcInsn(8844039203925L);
		mv.visitInsn(LASTORE);
		final Label l5 = new Label();
		mv.visitLabel(l5);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_3);
		mv.visitLdcInsn(77493848003273L);
		mv.visitInsn(LASTORE);
		final Label l6 = new Label();
		mv.visitLabel(l6);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_4);
		mv.visitLdcInsn(1777293846418288384L);
		mv.visitInsn(LASTORE);
		final Label l7 = new Label();
		mv.visitLabel(l7);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitInsn(ICONST_5);
		mv.visitLdcInsn(48830029394L);
		mv.visitInsn(LASTORE);
		final Label l8 = new Label();
		mv.visitLabel(l8);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 6);
		mv.visitLdcInsn(19949830293L);
		mv.visitInsn(LASTORE);
		final Label l9 = new Label();
		mv.visitLabel(l9);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 7);
		mv.visitLdcInsn(848039293975993L);
		mv.visitInsn(LASTORE);
		final Label l10 = new Label();
		mv.visitLabel(l10);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 8);
		mv.visitLdcInsn(18717729394885L);
		mv.visitInsn(LASTORE);
		final Label l11 = new Label();
		mv.visitLabel(l11);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 9);
		mv.visitLdcInsn(28838847379432L);
		mv.visitInsn(LASTORE);
		final Label l12 = new Label();
		mv.visitLabel(l12);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 10);
		mv.visitLdcInsn(9991828838749L);
		mv.visitInsn(LASTORE);
		final Label l13 = new Label();
		mv.visitLabel(l13);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 11);
		mv.visitLdcInsn(47774434991928L);
		mv.visitInsn(LASTORE);
		final Label l14 = new Label();
		mv.visitLabel(l14);
		mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.bigBoizFieldName, "[J");
		mv.visitIntInsn(BIPUSH, 12);
		mv.visitLdcInsn(1111144744434L);
		mv.visitInsn(LASTORE);
		final Label l15 = new Label();
		mv.visitLabel(l15);
		mv.visitInsn(RETURN);
		mv.visitMaxs(4, 0);
		mv.visitEnd();
		cw.visitEnd();

		return cw;
	}

	private class MemberNames
	{
		final String className;

		final String cacheFieldName;
		final String bigBoizFieldName;
		final String decryptMethodName;
		final int[] randomKeyOrder;
		final int[] keyOrder;

		MemberNames()
		{
			className = randomClassName();

			final WrappedDictionary fieldDictionary = getFieldDictionary(className);
			cacheFieldName = fieldDictionary.nextUniqueString();
			bigBoizFieldName = fieldDictionary.nextUniqueString();
			decryptMethodName = getMethodDictionary(className).nextUniqueString();
			randomKeyOrder = ArrayUtils.randomIntArrayOf(0, 3);
			keyOrder = ArrayUtils.randomIntArrayOf(0, 4);
		}

		public String[] toStrings()
		{
			final String[] strings = new String[6];
			strings[0] = "Decryptor class name: " + className;
			strings[1] = "Cache field name: " + cacheFieldName;
			strings[2] = "BigBoiz field name: " + bigBoizFieldName;
			strings[3] = "Decrypt method name: " + decryptMethodName;

			final StringJoiner randomKeyOrderBuilder = new StringJoiner(", ", "[", "]");
			for (final int i : randomKeyOrder)
				randomKeyOrderBuilder.add(Integer.toString(i));
			strings[4] = "Random key order: " + randomKeyOrderBuilder;

			final StringJoiner keyOrderBuilder = new StringJoiner(", ", "[", "]");
			for (final int i : keyOrder)
				keyOrderBuilder.add(Integer.toString(i));
			strings[5] = "Key order: " + keyOrderBuilder;
			return strings;
		}
	}
}
