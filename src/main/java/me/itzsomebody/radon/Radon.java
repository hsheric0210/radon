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

package me.itzsomebody.radon;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.MethodNode;

import me.itzsomebody.radon.asm.ClassTree;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.ObfuscationConfiguration;
import me.itzsomebody.radon.exceptions.MissingClassException;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.transformers.miscellaneous.TrashClasses;
import me.itzsomebody.radon.utils.FileUtils;
import me.itzsomebody.radon.utils.IOUtils;
import me.itzsomebody.radon.utils.RandomUtils;
import me.itzsomebody.radon.utils.Strings;

/**
 * This class is how Radon processes the provided {@link ObfuscationConfiguration} to produce an obfuscated jar.
 *
 * TODO: Radon's Motto: 진짜로 작정하고 뚫으려면 뚫을 수는 있지만, 그 과정이 너무나도 험난한, 마치 RSA 암호와 같은 java 난독화 프로그램
 * * Deobfuscator 제작이 어렵도록 가능한 한 모든 과정을 그때그때 변하도록 Randomize시킨다. 예시로 매개 변수 같은 것들의 순서를 랜덤하게 바꿔버린다던지...
 * *-* 거짓말 하나도 안 보태고, 지금 Radon이 가지고 있는 몇 안되는 obfuscation technique들도 싹다 랜덤하게 섞어버린다면 충분히 deobfuscate가 불가능한 수준까지 만들 수 있다. 진짜로.
 * * String decryptor, Invokedynamic Decryptor같은 들키면 안되는 중요한 클래스들은 내부 코드를 가능한 한 꼴 수 있는 데까지 꼬와 놓아 사람뿐만 아니라 기계도 헷갈리게 (그렇다고 해서 프로그램의 실행 성능이 떨어지만 안된다) 만들어 버리기
 * * Number obfuscation float, double형의 overflow, infinite 오류 고치기
 * * ASM Crasher 원리 알아내고 적용하기
 * * config 파일을 자동으로 만들어주는 기능 추가해보기 (어렵다면, '아무 parameter 없이 실행 시 default config file을 자동으로 만들어주는 기능'이라도 추가하기)
 *
 * * '모든 enum들을 int형으로 바꿔버리는' 기능 추가해보기 (이름은 'EnumMagicNumberizer'이 어떨까? https://namu.wiki/w/%EB%A7%A4%EC%A7%81%EB%84%98%EB%B2%84)
 * {@code
 * final ModeType mode = getMode()
 * switch(mode)
 * {
 *     ModeType.A:
 *     ...
 *     break;
 *     
 *     ModeType.B:
 *     ...
 *     break;
 *     
 *     ModeType.C:
 *     ...
 *     break;
 * }
 * }
 *
 * ^ 위와 같은 코드를 아래처럼 v
 *
 * {@code
 *
 * enum형이 int형으로 바뀐 모습
 *        v
 * final int mode = getMode()
 * switch(mode)
 * {
 *     3: (각각의 enum value에 대응되는 int형으로 바꾸기)
 *     ...
 *     break;
 *
 *     1: (이때 각각의 enum value에 대응되는 int형은, (그냥 enum에 대응되는 순서대로 가면 ㅈ되고) 반드시 랜덤하게 다시 배정시켜 준 후에 할당해야 한다.)
 *     ...
 *     break;
 *
 *     2:
 *     ...
 *     break;
 * }
 * }
 *
 * * 클래스, 메서드, 필드 등의 Generic 정보를 단순히 없애버리는 것이 아니라, 이상한 값으로 바꿔버리는 식으로 난독화하는 방법 한번 연구해보기 (아, 이거 혹시 BadSignature로 이미 구현되어있나?)
 * * (AntiTamper로부터 영감 얻음) Constant pool size를 이용한 암호화/난독화 방법들에 대해 연구해보기
 * -- 이때 만약 constant pool size를 통해서 무언가 암호화/난독화 하는 로직을 *추가했다면* 이는 곧 constant pool size가 추가된 로직들에 의해 늘어났다는 것이다. 이를 반드시 생각해야 한다.
 * * Transformer.notifyRunningWith(final Transformer transformer) 메서드를 만들고, 이를 통해 한 transformer에 "지금부터 얘네얘네들이랑 같이 실행할 거니까 준비해!"라고 알려주기
 * * LocalVariableTable을 효과적으로 망가뜨릴 수 있는 방법 고민해보기
 *
 * *-* 이외에도 코드를 난독화할 기발하거나 창의적인 방법이나 아이디어가 떠오른다면 그 즉시 하던 것들을 중단하고 손에 잡히는 아무 것에다가라도 연필 등으로 기록해놨다가 나중에 적용하기
 * *-*-* 단순한 것이라도 좋으니, 코드의 가독성을 개떡같이 만들고, 보는 사람 입장에서 "뭐야 이건?!"이라는 소리가 절로 나오도록
 * *-*-* 가역적인 훼손보다는 비가역적인 훼손일수록 더 좋다.
 *
 * @author ItzSomebody
 */
public class Radon
{
	public final ObfuscationConfiguration config;
	private final Map<String, ClassTree> hierarchy = new HashMap<>();
	public final Map<String, ClassWrapper> classes = new HashMap<>();
	public final Map<String, ClassWrapper> classPath = new HashMap<>();
	public final Map<String, byte[]> resources = new HashMap<>();

	public Radon(final ObfuscationConfiguration config)
	{
		this.config = config;
	}

	/**
	 * Execution order. Feel free to modify.
	 */
	public void run()
	{
		Main.info(Strings.START_LOAD_CP);
		Main.infoNewline();
		loadClassPath();
		Main.infoNewline();
		Main.info(Strings.END_LOAD_CP);
		Main.infoNewline();

		Main.infoNewline();
		Main.info(Strings.START_LOAD_INPUT);
		Main.infoNewline();
		loadInput();
		Main.infoNewline();
		Main.info(Strings.END_LOAD_INPUT);
		Main.infoNewline();

		final List<Transformer> transformers = config.transformers;

		if (config.nTrashClasses > 0)
			transformers.add(new TrashClasses());
		if (transformers.isEmpty())
			throw new RadonException("No transformers are enabled.");

		transformers.sort((t1, t2) ->
		{
			final ExclusionType type1 = t1.getExclusionType();
			final ExclusionType type2 = t2.getExclusionType();

			// In the event I forget to add an exclusion type
			Objects.requireNonNull(type1, () -> t1.getName() + " has a null exclusion type");
			Objects.requireNonNull(type2, () -> t2.getName() + " has a null exclusion type");

			return Integer.compare(type1.ordinal(), type2.ordinal());
		});

		Main.infoNewline();
		Main.info(Strings.START_EXECUTION);
		Main.infoNewline();
		transformers.stream().filter(Objects::nonNull).forEach(transformer ->
		{
			final long nanoTime = System.nanoTime();
			Main.info(String.format("+ Running %s transformer.", transformer.getName()));
			Main.infoNewline();
			transformer.init(this);
			transformer.transform();
			Main.infoNewline();
			Main.info(String.format("+ Finished running %s transformer. [%s]", transformer.getName(), Transformer.tookThisLong(nanoTime)));
			Main.infoNewline();
			Main.info(Strings.EXECUTION_SEPARATOR);
			Main.infoNewline();
		});
		Main.info(Strings.END_EXECUTION);
		Main.infoNewline();

		Main.infoNewline();
		Main.info(Strings.START_WRITING);
		Main.infoNewline();
		writeOutput();
		Main.infoNewline();
		Main.info(Strings.END_WRITING);
		Main.infoNewline();
	}

	private void writeOutput()
	{
		final File output = config.output;
		final long nanoTime = System.nanoTime();
		Main.info(String.format("+ Writing output to \"%s\".", output.getAbsolutePath()));

		if (output.exists())
			Main.info(String.format("*** Output file already exists, renamed to %s.", FileUtils.renameExistingFile(output)));

		try
		{
			final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
			zos.setLevel(config.compressionLevel);
			Main.info(String.format("*** Output jar compression level is %d.", config.compressionLevel));

			if (config.corruptCrc)
				try
				{
					final Field crcField = ZipOutputStream.class.getDeclaredField("crc");
					crcField.setAccessible(true);
					crcField.set(zos, new CRC32Corrupter());

					Main.info("+ Injected CRC corrupter.");
				}
				catch (final Exception e)
				{
					Main.severe("*** Failed to inject CRC corrupter.", e);
				}

			classes.values().forEach(classWrapper ->
			{
				try
				{
					final ZipEntry entry = new ZipEntry(classWrapper.getEntryName());

					zos.putNextEntry(entry);
					zos.write(classWrapper.toByteArray(this));
					zos.closeEntry();
				}
				catch (final Throwable e)
				{
					Main.severe(String.format("*** Error writing class %s. Skipping.", classWrapper.getName() + ".class"), e);
				}
			});

			resources.forEach((name, bytes) ->
			{
				try
				{
					final ZipEntry entry = new ZipEntry(name);

					zos.putNextEntry(entry);
					zos.write(bytes);
					zos.closeEntry();
				}
				catch (final Throwable ioe)
				{
					Main.severe(String.format("*** Error writing resource %s. Skipping.", name), ioe);
				}
			});

			zos.setComment(Main.ATTRIBUTION);
			zos.close();
		}
		catch (final IOException ioe)
		{
			throw new RadonException(ioe);
		}

		Main.info(Transformer.tookThisLong(nanoTime));
	}

	private void loadClassPath()
	{
		config.libraries.forEach(file ->
		{
			if (file.exists())
			{
				Main.info(String.format("+ Loading library \"%s\".", file.getAbsolutePath()));

				try (final ZipFile zipFile = new ZipFile(file))
				{
					final Enumeration<? extends ZipEntry> entries = zipFile.entries();

					while (entries.hasMoreElements())
					{
						final ZipEntry entry = entries.nextElement();

						if (!entry.isDirectory() && entry.getName().endsWith(".class"))
							try
							{
								final ClassWrapper cw = new ClassWrapper(new ClassReader(zipFile.getInputStream(entry)), true);
								classPath.put(cw.getName(), cw);
							}
							catch (final Throwable t)
							{
								Main.severe(String.format("*** Error while loading library class \"%s\".", entry.getName()));
								t.printStackTrace();
							}
					}
				}
				catch (final ZipException e)
				{
					Main.severe(String.format("*** Library \"%s\" could not be opened as a zip file.", file.getAbsolutePath()));
					e.printStackTrace();
				}
				catch (final IOException e)
				{
					Main.severe(String.format("*** IOException happened while trying to load classes from \"%s\".", file.getAbsolutePath()));
					e.printStackTrace();
				}
			}
			else
				Main.warn(String.format("*** Library \"%s\" could not be found and will be ignored.", file.getAbsolutePath()));
		});
	}

	private void loadInput()
	{
		final File input = config.input;

		if (input.exists())
		{
			Main.info(String.format("+ Loading input \"%s\".", input.getAbsolutePath()));

			try (final ZipFile zipFile = new ZipFile(input))
			{
				final Enumeration<? extends ZipEntry> entries = zipFile.entries();

				while (entries.hasMoreElements())
				{
					final ZipEntry entry = entries.nextElement();
					final InputStream in = zipFile.getInputStream(entry);

					if (!entry.isDirectory())
						if (entry.getName().endsWith(".class"))
							try
							{
								final ClassWrapper cw = new ClassWrapper(new ClassReader(in), false);

								if (cw.getVersion() <= Opcodes.V1_5)
								{
									IntStream.range(0, cw.methods.size()).forEach(i ->
									{
										final MethodNode methodNode = cw.methods.get(i).methodNode;
										final JSRInlinerAdapter adapter = new JSRInlinerAdapter(methodNode, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
										methodNode.accept(adapter);
										cw.methods.get(i).methodNode = adapter;
									});
								}

								classPath.put(cw.getName(), cw);
								classes.put(cw.getName(), cw);

								final String entryName = entry.getName();
								final String wrapperEntryName = cw.getEntryName();
								if (entryName.endsWith(wrapperEntryName) && !entryName.equals(wrapperEntryName))
									cw.entryPrefix = entry.getName().substring(0, entryName.length() - wrapperEntryName.length());
							}
							catch (final Throwable t)
							{
								Main.warn(String.format("*** Could not load %s as a class and will be loaded as resource.", entry.getName()));
								resources.put(entry.getName(), IOUtils.toByteArray(in));
							}
						else
							resources.put(entry.getName(), IOUtils.toByteArray(in));
				}
			}
			catch (final ZipException e)
			{
				Main.severe(String.format("*** Input file \"%s\" could not be opened as a zip file.", input.getAbsolutePath()));
				e.printStackTrace();
				throw new RadonException(e);
			}
			catch (final IOException e)
			{
				Main.severe(String.format("*** IOException happened while trying to load classes from \"%s\".", input.getAbsolutePath()));
				e.printStackTrace();
				throw new RadonException(e);
			}
		}
		else
		{
			Main.severe(String.format("*** Unable to find file \"%s\".", input.getAbsolutePath()));
			throw new RadonException(new FileNotFoundException(input.getAbsolutePath()));
		}
	}

	/**
	 * Finds {@link ClassWrapper} with given name.
	 *
	 * @return                {@link ClassWrapper}.
	 *
	 * @throws RadonException
	 *                        if not found.
	 */
	public ClassWrapper getClassWrapper(final String ref)
	{
		if (!classPath.containsKey(ref))
			throw new RadonException("Could not find " + ref);

		return classPath.get(ref);
	}

	/**
	 * Finds {@link ClassTree} with given name.
	 *
	 * @return                {@link ClassTree}.
	 *
	 * @throws RadonException
	 *                        if there are missing classes needed to build the inheritance tree.
	 */
	public ClassTree getTree(final String ref)
	{
		if (!hierarchy.containsKey(ref))
		{
			final ClassWrapper wrapper = getClassWrapper(ref);
			buildHierarchy(wrapper, null);
		}

		return hierarchy.get(ref);
	}

	private void buildHierarchy(final ClassWrapper wrapper, final ClassWrapper sub)
	{
		if (hierarchy.get(wrapper.getName()) == null)
		{
			final ClassTree tree = new ClassTree(wrapper);

			if (wrapper.getSuperName() != null)
			{
				tree.parentClasses.add(wrapper.getSuperName());

				buildHierarchy(getClassWrapper(wrapper.getSuperName()), wrapper);
			}
			if (wrapper.getInterfaces() != null)
				wrapper.getInterfaces().forEach(s ->
				{
					tree.parentClasses.add(s);

					buildHierarchy(getClassWrapper(s), wrapper);
				});

			hierarchy.put(wrapper.getName(), tree);
		}

		if (sub != null)
			hierarchy.get(wrapper.getName()).subClasses.add(sub.getName());
	}

	public void buildInheritance()
	{
		classes.values().forEach(classWrapper -> buildHierarchy(classWrapper, null));
	}

	/**
	 * Equivalent to the following: Class clazz1 = something; Class class2 = somethingElse; return class1.isAssignableFrom(class2);
	 */
	public boolean isAssignableFrom(final String type1, final String type2)
	{
		if ("java/lang/Object".equals(type1))
			return true;
		if (type1.equals(type2))
			return true;

		getClassWrapper(type1);
		getClassWrapper(type2);

		final ClassTree firstTree = getTree(type1);
		if (firstTree == null)
			throw new MissingClassException("Could not find " + type1 + " in the built class hierarchy");

		final Collection<String> allChildren = new HashSet<>();
		final Deque<String> toProcess = new ArrayDeque<>(firstTree.subClasses);
		while (!toProcess.isEmpty())
		{
			final String s = toProcess.poll();

			if (allChildren.add(s))
			{
				getClassWrapper(s);
				final ClassTree tempTree = getTree(s);
				toProcess.addAll(tempTree.subClasses);
			}
		}
		return allChildren.contains(type2);
	}

	private static class CRC32Corrupter extends CRC32
	{
		@Override
		public void update(final byte[] b, final int off, final int len)
		{
			// Don't update the CRC
		}

		@Override
		public final long getValue()
		{
			return RandomUtils.getRandomLong(0xFFFFFFFFL);
		}

		CRC32Corrupter()
		{
		}
	}
}
