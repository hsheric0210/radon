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

import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.LocalVariableProvider;

/**
 * https://www.sable.mcgill.ca/JBCO/examples.html#PLVB
 */
public class LocalVariablePacker extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		// Stage 1: 2 ints to a long
		// Stage 2: expand it to char, short

		getClassWrappers().stream().filter(this::included).forEach(cw -> cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
		{
			final MethodNode mn = mw.methodNode;

			final int maxStack = mn.maxStack;
			final int maxLocals = mn.maxLocals;
			mn.maxStack = mn.maxLocals = 1000;

			final Frame<BasicValue>[] frames;
			try
			{
				frames = new Analyzer<>(new BasicInterpreter()).analyze(mn.name, mn);
			}
			catch (final AnalyzerException e)
			{
				warn("Failed to analyze method " + mn.name, e);
				return;
			}
			finally
			{
				mn.maxStack = maxStack;
				mn.maxLocals = maxLocals;
			}

			final LocalVariableProvider provider = new LocalVariableProvider(mn);

			final InsnList insns = mn.instructions;

			final Map<Integer, Set<Type>> intLocalMap = new HashMap<>();
			final List<Integer> newLongVars = new ArrayList<>();

			// KEY: varIndex of int var
			// VALUE: (KEY: varIndex of long var VALUE: bit shift amount (0=most significant bits, 32=least significant bits))
			final Map<Integer, Entry<Integer, Integer>> allocationMap = new HashMap<>();

			for (final AbstractInsnNode insn : insns.toArray())
				if (insn instanceof VarInsnNode)
				{
					final VarInsnNode varInsn = (VarInsnNode) insn;
					final int varIndex = varInsn.var;

					if (provider.isArgumentLocal(varIndex))
						continue;

					// TODO: Expand support range to boolean, byte, char, short
					if (insn.getOpcode() == ILOAD && (!intLocalMap.containsKey(varIndex) || !intLocalMap.get(varIndex).contains(Type.INT_TYPE)))
					{
						verboseWarn(() -> "Unexpected access to uninitialized local variable #" + varIndex + " in class " + cw.getName() + " method " + mw.getName() + mw.getDescription());
						intLocalMap.computeIfAbsent(varIndex, i -> new HashSet<>()).add(Type.INT_TYPE);
					}

					if (insn.getOpcode() == ISTORE)
					{
						final Frame<BasicValue> currentFrame = frames[insns.indexOf(varInsn)];
						final Type operandType = currentFrame.getStack(currentFrame.getStackSize() - 1).getType();
						if (operandType != null && operandType.getSort() == Type.INT && (!intLocalMap.containsKey(varIndex) || !intLocalMap.get(varIndex).contains(operandType)))
							intLocalMap.computeIfAbsent(varIndex, i -> new HashSet<>()).add(operandType);
					}
				}

			final InsnList initialize = new InsnList();

			// Allocation & Initialization
			int bits = 0;
			int longVarIndex = -1;
			for (final int currentVarIndex : intLocalMap.keySet())
			{
				if (bits >= 64)
					bits = 0;

				if (bits == 0)
				{
					newLongVars.add(longVarIndex = provider.allocateVar(2)); // Allocate new long
					initialize.add(ASMUtils.getNumberInsn(0L));
					initialize.add(new VarInsnNode(LSTORE, longVarIndex));
				}

				allocationMap.put(currentVarIndex, new SimpleImmutableEntry<>(longVarIndex, bits));
				bits += 32;

				counter.incrementAndGet();
			}

			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (insn instanceof VarInsnNode)
				{
					final int opcode = insn.getOpcode();
					final VarInsnNode varInsn = (VarInsnNode) insn;
					final int varIndex = varInsn.var;

					if (allocationMap.containsKey(varIndex))
					{
						final Entry<Integer, Integer> entry = allocationMap.get(varIndex);
						final int packedVar = entry.getKey();
						final int shift = entry.getValue();

						final InsnList replace;

						if (opcode == ILOAD)
						{
							// long longmask = (size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index;
							final long longmask = 0xFFFFFFFFL << shift;

							replace = new InsnList();

							replace.add(new VarInsnNode(LLOAD, packedVar));
							replace.add(ASMUtils.getNumberInsn(longmask));
							replace.add(new InsnNode(LAND));
							if (shift > 0)
							{
								// Access to least significant bits
								replace.add(ASMUtils.getNumberInsn(shift));
								replace.add(new InsnNode(LSHR));
							}
							replace.add(new InsnNode(L2I));

							insns.insert(varInsn, replace);
							insns.remove(varInsn);
						}
						else if (opcode == ISTORE)
						{
							// long longmask = ~((size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index);
							final long longmask = ~(0xFFFFFFFFL << shift);

							replace = new InsnList();

							replace.add(new InsnNode(I2L));
							if (shift > 0)
							{
								replace.add(ASMUtils.getNumberInsn(shift));
								replace.add(new InsnNode(LSHL));
							}
							replace.add(ASMUtils.getNumberInsn(~longmask));
							replace.add(new InsnNode(LAND));
							replace.add(new VarInsnNode(LLOAD, packedVar));
							replace.add(ASMUtils.getNumberInsn(longmask));
							replace.add(new InsnNode(LAND));
							replace.add(new InsnNode(LXOR));
							replace.add(new VarInsnNode(LSTORE, packedVar));

							insns.insert(varInsn, replace);
							insns.remove(varInsn);
						}
					}
				}

				if (insn instanceof IincInsnNode)
				{
					final IincInsnNode iinc = (IincInsnNode) insn;
					final int varIndex = iinc.var;

					if (allocationMap.containsKey(varIndex))
					{
						final Entry<Integer, Integer> entry = allocationMap.get(varIndex);
						final int packedVar = entry.getKey();
						final int shift = entry.getValue();

						// long longmask = (size == 1 ? 0x1L : size == 8 ? 0xFFL : size == 16 ? 0xFFFFL : 0xFFFFFFFFL) << index;
						final long longmask = 0xFFFFFFFFL << shift;

						final InsnList replace = new InsnList();

						replace.add(new VarInsnNode(LLOAD, packedVar));
						replace.add(ASMUtils.getNumberInsn(longmask));
						replace.add(new InsnNode(LAND));
						if (shift > 0)
						{
							// Access to least significant bits
							replace.add(ASMUtils.getNumberInsn(shift));
							replace.add(new InsnNode(LSHR));
						}

						replace.add(new InsnNode(L2I));
						replace.add(ASMUtils.getNumberInsn(iinc.incr));
						replace.add(new InsnNode(IADD));
						replace.add(new InsnNode(I2L));

						if (shift > 0)
						{
							replace.add(ASMUtils.getNumberInsn(shift));
							replace.add(new InsnNode(LSHL));
						}
						replace.add(ASMUtils.getNumberInsn(longmask));
						replace.add(new InsnNode(LAND));
						replace.add(new VarInsnNode(LLOAD, packedVar));
						replace.add(ASMUtils.getNumberInsn(~longmask));
						replace.add(new InsnNode(LAND));
						replace.add(new InsnNode(LXOR));
						replace.add(new VarInsnNode(LSTORE, packedVar));

						insns.insert(iinc, replace);
						insns.remove(iinc);
					}
				}
			}

			if (!allocationMap.isEmpty())
				insns.insertBefore(insns.getFirst(), initialize);
		}));

		info("+ Packed " + counter.get() + " local variables");
	}

	@Override
	public String getName()
	{
		return "Local Variable Packer";
	}
}
