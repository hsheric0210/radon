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

package me.itzsomebody.radon.transformers.obfuscators.flow;

import java.util.concurrent.atomic.AtomicInteger;

import me.itzsomebody.radon.utils.Throwables;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Traps random instructions using a fake handler. Essentially the same thing as Zelix's exception obfuscation or Dasho's fake try catches.
 *
 * @author ItzSomebody
 */
public class FakeCatchBlocks extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		final ClassNode fakeHandler = new ClassNode();
		fakeHandler.superName = RandomUtils.getRandomElement(Throwables.getRandomThrowable());
		fakeHandler.name = classDictionary.uniqueRandomString();
		fakeHandler.access = ACC_PUBLIC | ACC_SUPER;
		fakeHandler.version = V1_5;

		final String methodName = methodDictionary.uniqueRandomString();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions() && !"<init>".equals(mw.getOriginalName())).forEach(methodWrapper ->
		{
			int leeway = methodWrapper.getLeewaySize();
			final InsnList insns = methodWrapper.getInstructions();

			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (leeway < 10000)
					return;
				if (!ASMUtils.isInstruction(insn))
					continue;

				if (RandomUtils.getRandomInt(5) > 3)
				{
					final LabelNode trapStart = new LabelNode();
					final LabelNode trapEnd = new LabelNode();
					final LabelNode catchStart = new LabelNode();
					final LabelNode catchEnd = new LabelNode();

					final InsnList catchBlock = new InsnList();
					catchBlock.add(catchStart);
					catchBlock.add(new InsnNode(DUP));
					catchBlock.add(new MethodInsnNode(INVOKEVIRTUAL, fakeHandler.name, methodName, "()V", false));
					catchBlock.add(new InsnNode(ATHROW));
					catchBlock.add(catchEnd);

					insns.insertBefore(insn, trapStart);
					insns.insert(insn, catchBlock);
					insns.insert(insn, new JumpInsnNode(GOTO, catchEnd));
					insns.insert(insn, trapEnd);

					methodWrapper.getTryCatchBlocks().add(new TryCatchBlockNode(trapStart, trapEnd, catchStart, fakeHandler.name));

					leeway -= 15;
					counter.incrementAndGet();
				}
			}
		}));
		final ClassWrapper newWrapper = new ClassWrapper(fakeHandler, false);
		getClasses().put(fakeHandler.name, newWrapper);
		getClassPath().put(fakeHandler.name, newWrapper);

		Main.info("+ Inserted " + counter.get() + " fake try catches");
	}
}
