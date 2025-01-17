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

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.ClassWrapper;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;
import me.itzsomebody.radon.utils.Throwables;

/**
 * Traps random instructions using a fake handler.
 * Essentially the same thing as Zelix's exception obfuscation or DashO's fake try catches.
 *
 * <p>TODO: Insert counterfeit codes to exception handler</p>
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
		fakeHandler.name = randomClassName();
		fakeHandler.access = ACC_PUBLIC | ACC_SUPER;
		fakeHandler.version = V1_5;

		final String methodName = getMethodDictionary(fakeHandler.name).nextUniqueString();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper -> classWrapper.methods.stream().filter(mw -> included(mw) && mw.hasInstructions() && !"<init>".equals(mw.originalName)).forEach(methodWrapper ->
		{
			int leeway = methodWrapper.getLeewaySize();
			final InsnList insns = methodWrapper.getInstructions();

			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (leeway < 10000)
					return;
				if (!ASMUtils.isInstruction(insn))
					continue;

				if (RandomUtils.getRandomInt(5) > 3) // 40% chance
				{
					final LabelNode trapStart = new LabelNode();
					final LabelNode trapEnd = new LabelNode();
					final LabelNode catchStart = new LabelNode();
					final LabelNode catchEnd = new LabelNode();

					final InsnList inserted = new InsnList();
					inserted.add(catchStart);
					inserted.add(new InsnNode(DUP));
					inserted.add(new MethodInsnNode(INVOKEVIRTUAL, fakeHandler.name, methodName, "()V", false));
					inserted.add(new InsnNode(ATHROW));
					inserted.add(catchEnd);
					inserted.add(new JumpInsnNode(GOTO, catchEnd));
					inserted.add(trapEnd);

					insns.insertBefore(insn, trapStart);
					insns.insert(insn, inserted);

					methodWrapper.getTryCatchBlocks().add(new TryCatchBlockNode(trapStart, trapEnd, catchStart, fakeHandler.name));

					leeway -= ASMUtils.evaluateMaxSize(inserted);
					counter.incrementAndGet();
				}
			}
		}));
		final ClassWrapper newWrapper = new ClassWrapper(fakeHandler, false);
		getClasses().put(fakeHandler.name, newWrapper);
		getClassPath().put(fakeHandler.name, newWrapper);

		info("+ Inserted " + counter.get() + " fake try catches");
	}

	@Override
	public String getName()
	{
		return "Fake Catch Blocks";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
