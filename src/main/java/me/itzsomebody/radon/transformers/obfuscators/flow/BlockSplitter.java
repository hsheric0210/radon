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
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.*;

/**
 * This splits a method's block of code into two blocks: P1 and P2 and then inserting P2 behind P1.
 * <p>
 * P1->P2 becomes GOTO_P1->P2->P1->GOTO_P2
 * </p>
 * This is similar in functionality to http://www.sable.mcgill.ca/JBCO/examples.html#GIA but is done recursively on the method to ensure maximum effectiveness.
 *
 * @author ItzSomebody
 */
public class BlockSplitter extends FlowObfuscation
{
	// used to limit number of recursive calls on doSplit()
	private static final int LIMIT_SIZE = 11;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.getMethods().stream().filter(this::included).forEach(mw -> doSplit(mw.getMethodNode(), counter, 0)));

		info("+ Split " + counter.get() + " blocks");
	}

	private static void doSplit(final MethodNode methodNode, final AtomicInteger counter, final int callStackSize)
	{
		final InsnList insns = methodNode.instructions;

		if (insns.size() > 10 && callStackSize < LIMIT_SIZE)
		{
			final LabelNode p1 = new LabelNode();
			final LabelNode p2 = new LabelNode();

			final AbstractInsnNode p1Start = insns.getFirst();
			final int p2StartIndex = (insns.size() - 1) / 2;
			final AbstractInsnNode p2Start = insns.get(p2StartIndex);
			final AbstractInsnNode p2End = insns.getLast();

			// We can't have trap ranges mutilated by block splitting
			if (methodNode.tryCatchBlocks.stream().anyMatch(tcbn -> insns.indexOf(tcbn.start) <= p2StartIndex && p2StartIndex <= insns.indexOf(tcbn.end)))
				return;

			final ArrayList<AbstractInsnNode> insnNodes = new ArrayList<>();
			AbstractInsnNode currentInsn = p1Start;

			final InsnList p1Block = new InsnList();

			while (currentInsn != p2Start)
			{
				insnNodes.add(currentInsn);
				currentInsn = currentInsn.getNext();
			}

			insnNodes.forEach(insn ->
			{
				insns.remove(insn);
				p1Block.add(insn);
			});

			p1Block.insert(p1);
			p1Block.add(new JumpInsnNode(GOTO, p2));

			insns.insert(p2End, p1Block);
			insns.insertBefore(p2Start, new JumpInsnNode(GOTO, p1));
			insns.insertBefore(p2Start, p2);

			counter.incrementAndGet();

			// We might have messed up variable ranges when rearranging the block order.
			if (methodNode.localVariables != null)
				new ArrayList<>(methodNode.localVariables).stream().filter(lvn -> insns.indexOf(lvn.end) < insns.indexOf(lvn.start)).forEach(methodNode.localVariables::remove);

			doSplit(methodNode, counter, callStackSize + 1);
		}
	}
}
