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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.Main;
import me.itzsomebody.radon.asm.StackHeightZeroFinder;
import me.itzsomebody.radon.exceptions.RadonException;
import me.itzsomebody.radon.exceptions.StackEmulationException;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Inserts opaque predicates which always evaluate to false but are meant to insert significantly more edges to a control flow graph. To determine where we should insert the conditions, we use an analyzer to determine where the stack is empty. This leads
 * to less complication when applying obfuscation.
 *
 * @author ItzSomebody
 */
public class BogusJumpInserter extends FlowObfuscation
{
	private static final int PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(classWrapper ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();
			final FieldNode predicate = new FieldNode(PRED_ACCESS, fieldDictionary.uniqueRandomString(), "Z", null, null);

			classWrapper.getMethods().stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				int leeway = mw.getLeewaySize();
				final int varIndex = mw.getMaxLocals();
				mw.getMethodNode().maxLocals++; // Prevents breaking of other transformers which rely on this field.

				final AbstractInsnNode[] untouchedList = insns.toArray();
				final LabelNode labelNode = exitLabel(mw.getMethodNode());
				boolean calledSuper = false;

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

				final Set<AbstractInsnNode> emptyAt = shzf.getEmptyAt();
				for (final AbstractInsnNode insn : untouchedList)
				{
					if (leeway < 10000)
						break;

					// Bad way of detecting if this class was instantiated
					if ("<init>".equals(mw.getName()))
						calledSuper = insn instanceof MethodInsnNode && insn.getOpcode() == INVOKESPECIAL && insn.getPrevious() instanceof VarInsnNode && ((VarInsnNode) insn.getPrevious()).var == 0;
					if (insn != insns.getFirst() && !(insn instanceof LineNumberNode))
					{
						if ("<init>".equals(mw.getName()) && !calledSuper)
							continue;
						if (emptyAt.contains(insn))
						{ // We need to make sure stack is empty before making jumps
							insns.insertBefore(insn, new VarInsnNode(ILOAD, varIndex));
							insns.insertBefore(insn, new JumpInsnNode(IFNE, labelNode));
							leeway -= 4;
							counter.incrementAndGet();
							shouldAdd.set(true);
						}
					}
				}

				if (shouldAdd.get())
				{
					insns.insert(new VarInsnNode(ISTORE, varIndex));
					insns.insert(new FieldInsnNode(GETSTATIC, classWrapper.getName(), predicate.name, "Z"));
				}
			});

			if (shouldAdd.get())
				classWrapper.addField(predicate);
		});

		Main.info("Inserted " + counter.get() + " bogus jumps");
	}

	/**
	 * Generates a generic "escape" pattern to avoid inserting multiple copies of the same bytecode instructions.
	 *
	 * @param  methodNode
	 *                    the {@link MethodNode} we are inserting into.
	 *
	 * @return            a {@link LabelNode} which "escapes" all other flow.
	 */
	private static LabelNode exitLabel(final MethodNode methodNode)
	{
		final LabelNode lb = new LabelNode();
		final LabelNode escapeNode = new LabelNode();

		final InsnList insns = methodNode.instructions;
		final AbstractInsnNode target = insns.getFirst();

		insns.insertBefore(target, new JumpInsnNode(GOTO, escapeNode));
		insns.insertBefore(target, lb);

		switch (Type.getReturnType(methodNode.desc).getSort())
		{
			case Type.VOID:
				insns.insertBefore(target, new InsnNode(RETURN));
				break;
			case Type.BOOLEAN:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomInt(2)));
				insns.insertBefore(target, new InsnNode(IRETURN));
				break;
			case Type.CHAR:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Character.MAX_VALUE + 1)));
				insns.insertBefore(target, new InsnNode(IRETURN));
				break;
			case Type.BYTE:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Byte.MAX_VALUE + 1)));
				insns.insertBefore(target, new InsnNode(IRETURN));
				break;
			case Type.SHORT:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomInt(Short.MAX_VALUE + 1)));
				insns.insertBefore(target, new InsnNode(IRETURN));
				break;
			case Type.INT:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomInt()));
				insns.insertBefore(target, new InsnNode(IRETURN));
				break;
			case Type.LONG:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomLong()));
				insns.insertBefore(target, new InsnNode(LRETURN));
				break;
			case Type.FLOAT:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomFloat()));
				insns.insertBefore(target, new InsnNode(FRETURN));
				break;
			case Type.DOUBLE:
				insns.insertBefore(target, ASMUtils.getNumberInsn(RandomUtils.getRandomDouble()));
				insns.insertBefore(target, new InsnNode(DRETURN));
				break;
			default:
				insns.insertBefore(target, new InsnNode(ACONST_NULL));
				insns.insertBefore(target, new InsnNode(ARETURN));
				break;
		}
		insns.insertBefore(target, escapeNode);

		return lb;
	}
}
