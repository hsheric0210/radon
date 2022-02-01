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

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.utils.ASMUtils;

/**
 * Duplicates vars. Uglifies opcodes / stack
 * 
 * <ul>
 * <li>Original source code of Javari VarDuplicator: https://github.com/whosnero/Javari/blob/master/roman/finn/javari/obfmethods/VarDuplicator.java</li>
 * <li>Original source code of SkidSuite duplicateVars: https://github.com/GenericException/SkidSuite/blob/master/archive/skidsuite-2/obfu/src/main/java/me/lpk/obfuscation/MiscAnti.java</li>
 * </ul>
 * 
 * @author Roman, GenericException
 */
public class VariableDuplicator extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && !mw.access.isAbstract() && mw.hasInstructions()).forEach(mw ->
		{
			int leeway = mw.getLeewaySize();

			for (final AbstractInsnNode insn : mw.getInstructions().toArray())
			{
				if (leeway < 10000)
					break;

				if (insn instanceof VarInsnNode && insn.getOpcode() == ASTORE)
				{
					final InsnList insertBefore = new InsnList();
					final InsnList insertAfter = new InsnList();

					insertBefore.add(new InsnNode(DUP));
					insertBefore.add(new InsnNode(ACONST_NULL));
					insertBefore.add(new InsnNode(SWAP));

					insertAfter.add(new InsnNode(POP));
					insertAfter.add(new VarInsnNode(ASTORE, ((VarInsnNode) insn).var));

					leeway -= ASMUtils.evaluateMaxSize(insertBefore) + ASMUtils.evaluateMaxSize(insertAfter);
				}
			}
		}));

		info("+ Duplicated " + counter.get() + " local variable stores");
	}

	@Override
	public String getName()
	{
		return "Variable Duplicator";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
