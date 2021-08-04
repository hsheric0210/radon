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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Essentially the same thing as ADSS (https://www.sable.mcgill.ca/JBCO/examples.html#ADSS)
 */

public class BogusSwitchJumpInserter extends FlowObfuscation
{
	private static final int CLASS_PRED_ACCESS = ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC;
	private static final int INTERFACE_PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();
			final FieldNode predicate = new FieldNode((classWrapper.getAccessFlags() & ACC_INTERFACE) != 0 ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, fieldDictionary.uniqueRandomString(), "I", null, null);

			classWrapper.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				final int leeway = mw.getLeewaySize();
				final int varIndex = mw.getMaxLocals();
				mw.getMethodNode().maxLocals++; // Prevents breaking of other transformers which rely on this field.

				final StackHeightZeroFinder shzf = new StackHeightZeroFinder(mw.getMethodNode(), insns.getLast());
				try
				{
					shzf.execute(false);
				}
				catch (final StackEmulationException e)
				{
					e.printStackTrace();
					throw new RadonException(String.format("Error happened while trying to emulate the stack of %s.%s%s", classWrapper.getName(), mw.getName(), mw.getDescription()));
				}

				final Set<AbstractInsnNode> check = shzf.getEmptyAt();
				final ArrayList<AbstractInsnNode> emptyAt = new ArrayList<>(check);

				if (emptyAt.size() <= 5 || leeway <= 30000)
					return;

				final int nTargets = emptyAt.size() / 2;

				final List<LabelNode> targets = IntStream.range(0, nTargets).mapToObj(i -> new LabelNode()).collect(Collectors.toList());

				final LabelNode back = new LabelNode();
				final LabelNode dflt = new LabelNode();
				final TableSwitchInsnNode tsin = new TableSwitchInsnNode(0, targets.size() - 1, dflt, targets.toArray(Constants.EMPTY_LABEL_NODE_ARRAY));

				final InsnList block = new InsnList();
				block.add(new VarInsnNode(ILOAD, varIndex));
				block.add(new JumpInsnNode(IFEQ, dflt));
				block.add(back);
				block.add(new VarInsnNode(ILOAD, varIndex));
				block.add(tsin);
				block.add(dflt);

				final AbstractInsnNode switchTarget = emptyAt.get(RandomUtils.getRandomInt(emptyAt.size()));

				insns.insertBefore(switchTarget, block);

				for (final LabelNode target : targets)
				{
					final AbstractInsnNode here = insns.getLast();

					final InsnList landing = new InsnList();
					landing.add(target);
					landing.add(ASMUtils.getNumberInsn(RandomUtils.getRandomInt(nTargets)));
					landing.add(new VarInsnNode(ISTORE, varIndex));
					landing.add(new JumpInsnNode(GOTO, targets.get(RandomUtils.getRandomInt(targets.size()))));

					insns.insert(here, landing);
				}

				insns.insert(new VarInsnNode(ISTORE, varIndex));
				insns.insert(new FieldInsnNode(GETSTATIC, classWrapper.getName(), predicate.name, "I"));

				counter.addAndGet(targets.size());
				shouldAdd.set(true);
			});

			if (shouldAdd.get())
				classWrapper.addField(predicate);
		});

		info("+ Inserted " + counter.get() + " bogus switch jumps");
	}

	@Override
	public String getName()
	{
		return "Bogus Switch Jump Inserter";
	}
}
