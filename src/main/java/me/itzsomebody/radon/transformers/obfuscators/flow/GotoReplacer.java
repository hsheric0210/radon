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

import me.itzsomebody.radon.Main;
import org.objectweb.asm.tree.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces GOTO instructions with an expression which is always true. This does nothing more than adding
 * a one more edge to a control flow graph for every GOTO instruction present.
 *
 * @author ItzSomebody
 */
public class GotoReplacer extends FlowObfuscation
{
	private static final int PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(cw -> !excluded(cw)).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();
			final FieldNode predicate = new FieldNode(PRED_ACCESS, uniqueRandomString(), "Z", null, null);

			cw.getMethods().stream().filter(mw -> !excluded(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				int leeway = mw.getLeewaySize();
				final int varIndex = mw.getMaxLocals();
				mw.getMethodNode().maxLocals++; // Prevents breaking of other transformers which rely on this field.

				for (final AbstractInsnNode insn : insns.toArray())
				{
					if (leeway < 10000)
						break;

					if (insn.getOpcode() == GOTO)
					{
						insns.insertBefore(insn, new VarInsnNode(ILOAD, varIndex));
						insns.insertBefore(insn, new JumpInsnNode(IFEQ, ((JumpInsnNode) insn).label));
						insns.insert(insn, new InsnNode(ATHROW));
						insns.insert(insn, new InsnNode(ACONST_NULL));
						insns.remove(insn);

						leeway -= 10;

						counter.incrementAndGet();
						shouldAdd.set(true);
					}
				}

				if (shouldAdd.get())
				{
					insns.insert(new VarInsnNode(ISTORE, varIndex));
					insns.insert(new FieldInsnNode(GETSTATIC, cw.getName(), predicate.name, "Z"));
				}
			});

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		Main.info("Swapped " + counter.get() + " GOTO instructions");
	}
}
