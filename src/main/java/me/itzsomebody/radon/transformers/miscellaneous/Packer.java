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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.config.ConfigurationSetting;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Packs classes and resources into a stub file which is unpacked on runtime.
 *
 * @author ItzSomebody.
 */
public class Packer extends Transformer
{
	private String mainClass;
	private boolean packWithGZIP;

	@Override
	public void transform()
	{
		final MemberNames memberNames = new MemberNames();
		final AtomicInteger counter = new AtomicInteger();

		if (packWithGZIP)
			info("+ Using GZIP algorithm");
		else
			info("+ Using DEFLATE algorithm");

		try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
				final DeflaterOutputStream compressedStream = packWithGZIP ? new GZIPOutputStream(bos) : new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION));
				final DataOutputStream out = new DataOutputStream(compressedStream))
		{
			out.writeInt(getClassWrappers().stream().filter(this::included).mapToInt(e -> 1).sum() + getResources().keySet().stream().filter(this::included).mapToInt(e -> 1).sum() - 1);

			final ArrayList<String> toRemove = new ArrayList<>();

			/*
			 * Spec:
			 *
			 * struct Stub { u4 nEntries; entries[nEntries]; };
			 *
			 * struct StubEntry { name; u4 nBytes; bytes[nBytes]; };
			 */
			getClasses().forEach((name, wrapper) ->
			{
				if (!included(wrapper))
					return;

				try
				{
					final byte[] bytes = wrapper.toByteArray(radon);

					out.writeUTF(name + ".class");
					out.writeInt(bytes.length);
					for (final byte b : bytes)
						out.writeByte(b);

					toRemove.add(name);
				}
				catch (final Throwable t)
				{
					t.printStackTrace();
					throw new RadonException();
				}
			});

			getResources().forEach((name, bytes) ->
			{
				if (!included(name))
					return;

				try
				{
					if ("META-INF/MANIFEST.MF".equals(name))
					{
						final ByteArrayOutputStream os = new ByteArrayOutputStream();
						final Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
						final String mainClass = manifest.getMainAttributes().getValue("Main-Class");
						if (mainClass == null)
							throw new RadonException("Could not find OEP(Original Entry Point)");

						this.mainClass = mainClass;
						manifest.getMainAttributes().putValue("Main-Class", memberNames.className.replace('/', '.'));
						manifest.write(os);
						getResources().put(name, os.toByteArray());
						return;
					}

					out.writeUTF(name);
					out.writeInt(bytes.length);
					for (final byte b : bytes)
						out.writeByte(b);

					toRemove.add(name);
				}
				catch (final Throwable t)
				{
					t.printStackTrace();
					throw new RadonException();
				}
			});

			Objects.requireNonNull(mainClass, "Could not find OEP(Original Entry Point)");

			toRemove.forEach(s ->
			{
				getClasses().remove(s);
				getResources().remove(s);
			});

			compressedStream.close();

			getResources().put(memberNames.stubName.substring(1), bos.toByteArray());
			final ClassNode loader = createPackerEntryPoint(memberNames);
			getClasses().put(loader.name, new ClassWrapper(loader, false));

			counter.addAndGet(toRemove.size());
		}
		catch (final IOException e)
		{
			e.printStackTrace();
			throw new RadonException(e);
		}

		info("+ Packed " + counter.get() + " files");
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.PACKER;
	}

	@Override
	public String getName()
	{
		return "Packer";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		packWithGZIP = config.getOrDefault(ConfigurationSetting.PACKER + ".useGZIP", false);
	}

	@SuppressWarnings("Duplicates")
	private ClassNode createPackerEntryPoint(final MemberNames memberNames)
	{
		final ClassNode cw = new ClassNode();
		final FieldVisitor fv;
		MethodVisitor mv;

		cw.visit(V1_5, ACC_PUBLIC | ACC_SUPER, memberNames.className, null, "java/lang/ClassLoader", null);

		fv = cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, memberNames.resourcesFieldName, "Ljava/util/Map;", "Ljava/util/Map<Ljava/lang/String;[B>;", null);
		fv.visitEnd();
		{
			mv = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, new String[]
			{
					"java/io/IOException"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/ClassLoader", "<init>", "()V", false);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitTypeInsn(NEW, packWithGZIP ? "java/util/zip/GZIPInputStream" : "java/util/zip/InflaterInputStream");
			mv.visitInsn(DUP);
			mv.visitLdcInsn(Type.getType("L" + memberNames.className + ";"));
			mv.visitLdcInsn(memberNames.stubName);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
			mv.visitMethodInsn(INVOKESPECIAL, packWithGZIP ? "java/util/zip/GZIPInputStream" : "java/util/zip/InflaterInputStream", "<init>", "(Ljava/io/InputStream;)V", false);
			mv.visitVarInsn(ASTORE, 1);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitTypeInsn(NEW, "java/io/DataInputStream");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESPECIAL, "java/io/DataInputStream", "<init>", "(Ljava/io/InputStream;)V", false);
			mv.visitVarInsn(ASTORE, 2);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
			mv.visitVarInsn(ISTORE, 3);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 4);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitVarInsn(ILOAD, 3);
			final Label l6 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l6);
			final Label l7 = new Label();
			mv.visitLabel(l7);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readUTF", "()Ljava/lang/String;", false);
			mv.visitVarInsn(ASTORE, 5);
			final Label l8 = new Label();
			mv.visitLabel(l8);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readInt", "()I", false);
			mv.visitIntInsn(NEWARRAY, T_BYTE);
			mv.visitVarInsn(ASTORE, 6);
			final Label l9 = new Label();
			mv.visitLabel(l9);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ISTORE, 7);
			final Label l10 = new Label();
			mv.visitLabel(l10);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitVarInsn(ALOAD, 6);
			mv.visitInsn(ARRAYLENGTH);
			final Label l11 = new Label();
			mv.visitJumpInsn(IF_ICMPGE, l11);
			final Label l12 = new Label();
			mv.visitLabel(l12);
			mv.visitVarInsn(ALOAD, 6);
			mv.visitVarInsn(ILOAD, 7);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/DataInputStream", "readByte", "()B", false);
			mv.visitInsn(BASTORE);
			final Label l13 = new Label();
			mv.visitLabel(l13);
			mv.visitIincInsn(7, 1);
			mv.visitJumpInsn(GOTO, l10);
			mv.visitLabel(l11);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.resourcesFieldName, "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 5);
			mv.visitVarInsn(ALOAD, 6);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitInsn(POP);
			final Label l14 = new Label();
			mv.visitLabel(l14);
			mv.visitIincInsn(4, 1);
			mv.visitJumpInsn(GOTO, l5);
			mv.visitLabel(l6);
			mv.visitInsn(RETURN);
			final Label l15 = new Label();
			mv.visitLabel(l15);
			mv.visitMaxs(4, 8);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PROTECTED, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", null, new String[]
			{
					"java/lang/ClassNotFoundException"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.resourcesFieldName, "Ljava/util/Map;");
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitIntInsn(BIPUSH, 46);
			mv.visitIntInsn(BIPUSH, 47);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(CC)Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitLdcInsn(".class");
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(CHECKCAST, "[B");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ASTORE, 2);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			final Label l2 = new Label();
			mv.visitJumpInsn(IFNULL, l2);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitInsn(ARRAYLENGTH);
			mv.visitLdcInsn(Type.getType("L" + memberNames.className + ";"));
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getProtectionDomain", "()Ljava/security/ProtectionDomain;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, memberNames.className, "defineClass", "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;", false);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/ClassLoader", "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitInsn(ARETURN);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitMaxs(6, 3);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC, "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", null, null);
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitFieldInsn(GETSTATIC, memberNames.className, memberNames.resourcesFieldName, "Ljava/util/Map;");
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
			mv.visitTypeInsn(CHECKCAST, "[B");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ASTORE, 2);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			final Label l2 = new Label();
			mv.visitJumpInsn(IFNULL, l2);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitTypeInsn(NEW, "java/io/ByteArrayInputStream");
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
			mv.visitInsn(ARETURN);
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/ClassLoader", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;", false);
			mv.visitInsn(ARETURN);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitMaxs(3, 3);
			mv.visitEnd();
		}
		{
			mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_VARARGS, "main", "([Ljava/lang/String;)V", null, new String[]
			{
					"java/lang/Throwable"
			});
			mv.visitCode();
			final Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitTypeInsn(NEW, memberNames.className);
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, memberNames.className, "<init>", "()V", false);
			mv.visitVarInsn(ASTORE, 1);
			final Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitLdcInsn(mainClass);
			mv.visitMethodInsn(INVOKEVIRTUAL, memberNames.className, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitVarInsn(ASTORE, 2);
			final Label l2 = new Label();
			mv.visitLabel(l2);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitLdcInsn("main");
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			mv.visitLdcInsn(Type.getType("[Ljava/lang/String;"));
			mv.visitInsn(AASTORE);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
			mv.visitVarInsn(ASTORE, 3);
			final Label l3 = new Label();
			mv.visitLabel(l3);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(ICONST_1);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "setAccessible", "(Z)V", false);
			final Label l4 = new Label();
			mv.visitLabel(l4);
			mv.visitVarInsn(ALOAD, 3);
			mv.visitInsn(ACONST_NULL);
			mv.visitInsn(ICONST_1);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
			mv.visitInsn(DUP);
			mv.visitInsn(ICONST_0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitInsn(AASTORE);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
			mv.visitInsn(POP);
			final Label l5 = new Label();
			mv.visitLabel(l5);
			mv.visitInsn(RETURN);
			final Label l6 = new Label();
			mv.visitLabel(l6);
			mv.visitMaxs(6, 4);
			mv.visitEnd();
		}
		mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		final Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitTypeInsn(NEW, "java/util/HashMap");
		mv.visitInsn(DUP);
		mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
		mv.visitFieldInsn(PUTSTATIC, memberNames.className, memberNames.resourcesFieldName, "Ljava/util/Map;");
		mv.visitInsn(RETURN);
		mv.visitMaxs(2, 0);
		mv.visitEnd();
		cw.visitEnd();

		return cw;
	}

	private class MemberNames
	{
		final String className = getClassDictionary(null).nextUniqueString();
		final String resourcesFieldName = getFieldDictionary(className).nextUniqueString();
		final String stubName = '/' + getGenericDictionary().nextUniqueString();

		MemberNames()
		{
		}
	}
}
