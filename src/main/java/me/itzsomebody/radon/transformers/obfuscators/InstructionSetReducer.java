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

package me.itzsomebody.radon.transformers.obfuscators;

import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.exclusions.ExclusionType;
import me.itzsomebody.radon.transformers.Transformer;

/**
 * Removes tableswitches and *const_*
 */
public class InstructionSetReducer extends Transformer
{
	@Override
	public void transform()
	{
		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(this::included).forEach(mw ->
		{
			final InsnList newInsns = new InsnList();
			insn:
			for (final AbstractInsnNode abstractInsnNode : mw.getInstructions().toArray())
			{
				if (abstractInsnNode instanceof TableSwitchInsnNode)
				{
					final LabelNode trampolineStart = new LabelNode();
					final InsnNode cleanStack = new InsnNode(POP);
					final JumpInsnNode jmpDefault = new JumpInsnNode(GOTO, ((TableSwitchInsnNode) abstractInsnNode).dflt);
					final LabelNode endOfTrampoline = new LabelNode();
					final JumpInsnNode skipTrampoline = new JumpInsnNode(GOTO, endOfTrampoline);

					// Goto default trampoline
					newInsns.add(skipTrampoline);
					newInsns.add(trampolineStart);
					newInsns.add(cleanStack);
					newInsns.add(jmpDefault);
					newInsns.add(endOfTrampoline);

					// < min
					// I(val)
					newInsns.add(new InsnNode(DUP));
					// I(val) I(val)
					newInsns.add(new LdcInsnNode(-((TableSwitchInsnNode) abstractInsnNode).min));
					// I(val) I(val) I(-min)
					newInsns.add(new InsnNode(IADD));
					// I(val) I(val-min)
					newInsns.add(new JumpInsnNode(IFLT, trampolineStart));
					// I(val)
					// > max
					newInsns.add(new InsnNode(DUP));
					// I(val) I(val)
					newInsns.add(new LdcInsnNode(-((TableSwitchInsnNode) abstractInsnNode).max));
					// I(val) I(val) I(-max)
					newInsns.add(new InsnNode(IADD));
					// I(val) I(val-max)
					newInsns.add(new JumpInsnNode(IFGT, trampolineStart));
					// I(val)
					// = VAL
					newInsns.add(new InsnNode(DUP));
					// I(val) I(val)
					newInsns.add(new LdcInsnNode(-((TableSwitchInsnNode) abstractInsnNode).min));
					// I(val) I(val) I(-min)
					newInsns.add(new InsnNode(IADD));
					// I(val) I(val-min) => 0 = first label, 1 = second label...

					int labelIndex = 0;
					for (final LabelNode label : ((TableSwitchInsnNode) abstractInsnNode).labels)
					{
						final LabelNode nextBranch = new LabelNode();
						newInsns.add(new InsnNode(DUP));
						newInsns.add(new JumpInsnNode(IFNE, nextBranch));
						newInsns.add(new InsnNode(POP));
						newInsns.add(new InsnNode(POP));
						newInsns.add(new JumpInsnNode(GOTO, label));

						newInsns.add(nextBranch);
						if (labelIndex + 1 != ((TableSwitchInsnNode) abstractInsnNode).labels.size())
						{
							newInsns.add(new LdcInsnNode(-1));
							newInsns.add(new InsnNode(IADD));
						}

						labelIndex++;
					}
					// I(val) I(val-min-totalN)
					newInsns.add(new InsnNode(POP));
					// newInsns.add(new InsnNode(POP));
					newInsns.add(new JumpInsnNode(GOTO, trampolineStart));
					// I(val)
				}
				else
					switch (abstractInsnNode.getOpcode())
					{
						case ICONST_M1:
						case ICONST_0:
						case ICONST_1:
						case ICONST_2:
						case ICONST_3:
						case ICONST_4:
						case ICONST_5:
							newInsns.add(new LdcInsnNode(abstractInsnNode.getOpcode() - 3));
							continue insn;
						case LCONST_0:
						case LCONST_1:
							newInsns.add(new LdcInsnNode(abstractInsnNode.getOpcode() - 9L));
							continue insn;
						case FCONST_0:
						case FCONST_1:
						case FCONST_2:
							newInsns.add(new LdcInsnNode(abstractInsnNode.getOpcode() - 11.0F));
							continue insn;
						case DCONST_0:
						case DCONST_1:
							newInsns.add(new LdcInsnNode(abstractInsnNode.getOpcode() - 14.0D));
							continue insn;
					}

				newInsns.add(abstractInsnNode);
			}

			mw.setInstructions(newInsns);
		}));
	}

	@Override
	public String getName()
	{
		return "Instruction Set Reducer";
	}

	@Override
	public ExclusionType getExclusionType()
	{
		return ExclusionType.INSTRUCTION_SET_REDUCER;
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
