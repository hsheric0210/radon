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
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.LocalVariableProvider;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Pack all local variables into array(s) and randomizes its index
 * Original source code: https://github.com/superblaubeere27/obfuscator/blob/master/obfuscator-core/src/main/java/me/superblaubeere27/jobf/processors/flowObfuscation/LocalVariableMangler.java
 *
 * @author superblaubeere27
 */
public class LocalVariableMutilator extends FlowObfuscation
{
	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

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

			// Map of local variables and their types. They are added if the type of the variable is double, float, int or long
			final Map<Integer, Set<Type>> localVarMap = new HashMap<>();

			// KEY: Type of array
			// VALUE: Array local variable index
			final Map<Type, Integer> typeArrayVarIndexMap = new HashMap<>();

			// KEY: Original Local variable
			// VALUE:
			// - KEY: Type of the local variable
			// - VALUE: [0]: Local variable index of the array, [1]: Array index
			final Map<Integer, Map<Type, int[]>> slotMap = new HashMap<>();

			// KEY: Array local variable index
			// VALUE: Current highest array index
			final Map<Integer, Integer> arrayIndices = new HashMap<>();

			final Map<Integer, Collection<Integer>> arrayIndexRNGExclusions = new HashMap<>();

			final InsnList insns = mn.instructions;
			for (final AbstractInsnNode insn : insns.toArray())
				if (insn instanceof VarInsnNode)
				{
					final VarInsnNode varInsn = (VarInsnNode) insn;
					final int varIndex = varInsn.var;

					if (provider.isArgumentLocal(varIndex))
						continue;

					final int opcode = varInsn.getOpcode();

					if (opcode >= ILOAD && opcode <= DLOAD)
					{
						final Type type;
						switch (opcode - ILOAD)
						{
							case 0:
								type = Type.INT_TYPE;
								break;
							case LLOAD - ILOAD:
								type = Type.LONG_TYPE;
								break;
							case FLOAD - ILOAD:
								type = Type.FLOAT_TYPE;
								break;
							default:
								type = Type.DOUBLE_TYPE;
								break;
						}

						if (!localVarMap.containsKey(varIndex) || !localVarMap.get(varIndex).contains(type))
							localVarMap.computeIfAbsent(varIndex, i -> new HashSet<>()).add(type);
					}

					if (opcode >= ISTORE && opcode <= DSTORE)
					{
						final Frame<BasicValue> currentFrame = frames[insns.indexOf(varInsn)];
						final Type operandType = currentFrame.getStack(currentFrame.getStackSize() - 1).getType();
						if (operandType != null)
						{
							final int operandTypeSort = operandType.getSort();
							if (operandTypeSort >= Type.CHAR && operandTypeSort <= Type.DOUBLE)
							{
								final Type newType = operandTypeSort < Type.INT ? Type.INT_TYPE : operandType;
								if (!localVarMap.containsKey(varIndex) || !localVarMap.get(varIndex).contains(newType))
									localVarMap.computeIfAbsent(varIndex, i -> new HashSet<>()).add(newType);
							}
						}
					}
				}

			for (final Set<Type> types : localVarMap.values())
				for (final Type type : types)
				{
					typeArrayVarIndexMap.computeIfAbsent(type, i -> provider.allocateVar(1));
					final int arrayVarIndex = typeArrayVarIndexMap.get(type);
					arrayIndices.put(arrayVarIndex, arrayIndices.getOrDefault(arrayVarIndex, 0) + 1);
				}

			for (final Entry<Integer, Set<Type>> entry : localVarMap.entrySet())
				for (final Type type : entry.getValue())
				{
					final int arrayVarIndex = typeArrayVarIndexMap.get(type);
					arrayIndexRNGExclusions.computeIfAbsent(arrayVarIndex, i -> new HashSet<>());
					final int arrayIndex;
					slotMap.computeIfAbsent(entry.getKey(), i -> new HashMap<>()).put(type, new int[]
					{
							arrayVarIndex, arrayIndex = RandomUtils.getRandomIntWithExclusion(0, arrayIndices.get(arrayVarIndex), arrayIndexRNGExclusions.get(arrayVarIndex))
					});
					arrayIndexRNGExclusions.get(arrayVarIndex).add(arrayIndex);

					counter.incrementAndGet();
				}

			final InsnList initialize = new InsnList();

			for (final Entry<Type, Integer> typeArrayVarIndexEntry : typeArrayVarIndexMap.entrySet())
			{
				final int arrayTypeSort = typeArrayVarIndexEntry.getKey().getSort();

				final int arrayType;
				switch (arrayTypeSort)
				{
					case Type.INT:
						arrayType = T_INT;
						break;
					case Type.LONG:
						arrayType = T_LONG;
						break;
					case Type.DOUBLE:
						arrayType = T_DOUBLE;
						break;
					case Type.FLOAT:
						arrayType = T_FLOAT;
						break;
					default:
						verboseWarn(() -> String.format("! Unknown array type: %d", arrayTypeSort));
						arrayType = -1;
						break;
				}

				if (arrayType < 0)
					continue;

				final Integer varIndex = typeArrayVarIndexEntry.getValue();
				initialize.add(ASMUtils.getNumberInsn(arrayIndices.get(varIndex)));
				initialize.add(new IntInsnNode(NEWARRAY, arrayType));
				initialize.add(new VarInsnNode(ASTORE, varIndex));
			}

			for (final AbstractInsnNode insn : insns.toArray())
			{
				if (insn instanceof VarInsnNode)
				{
					final int opcode = insn.getOpcode();
					final VarInsnNode varInsn = (VarInsnNode) insn;
					final int varIndex = varInsn.var;

					if (slotMap.containsKey(varIndex))
					{
						final Set<Type> types = localVarMap.get(varIndex);
						final Map<Type, int[]> typeMap = slotMap.get(varIndex);

						if (opcode >= ILOAD && opcode <= DLOAD)
						{
							final Optional<Type> optType = types.stream().filter(type -> type.getOpcode(ILOAD) == opcode).findAny();
							if (optType.isPresent())
							{
								final Type type = optType.get();
								final int[] value = typeMap.get(type);

								final InsnList replace = new InsnList();
								replace.add(new VarInsnNode(ALOAD, value[0]));
								replace.add(ASMUtils.getNumberInsn(value[1]));
								replace.add(new InsnNode(type.getOpcode(IALOAD)));
								insns.insert(varInsn, replace);
								insns.remove(varInsn);
							}
						}

						if (opcode >= ISTORE && opcode <= DSTORE)
						{
							final Optional<Type> optType = types.stream().filter(type -> type.getOpcode(ISTORE) == opcode).findAny();
							if (optType.isPresent())
							{
								final Type type = optType.get();
								final boolean isWide = type.getSize() > 1;
								final int[] value = typeMap.get(type);

								final InsnList replace = new InsnList();
								replace.add(new VarInsnNode(ALOAD, value[0]));
								if (isWide)
								{
									if (mn.maxStack < 4)
										mn.maxStack = 4;

									replace.add(new InsnNode(DUP_X2));
									replace.add(new InsnNode(POP));
								}
								else
									replace.add(new InsnNode(SWAP));
								replace.add(ASMUtils.getNumberInsn(value[1]));
								if (isWide)
								{
									replace.add(new InsnNode(DUP_X2));
									replace.add(new InsnNode(POP));
								}
								else
									replace.add(new InsnNode(SWAP));
								replace.add(new InsnNode(type.getOpcode(IASTORE)));
								insns.insert(varInsn, replace);
								insns.remove(varInsn);
							}
						}
					}
				}

				if (insn instanceof IincInsnNode)
				{
					final IincInsnNode iinc = (IincInsnNode) insn;
					final int varIndex = iinc.var;

					if (slotMap.containsKey(varIndex))
					{
						final int[] value = slotMap.get(varIndex).get(Type.INT_TYPE); // IINC is only applicable with 'int'

						final InsnList replace = new InsnList();

						replace.add(new VarInsnNode(ALOAD, value[0]));
						replace.add(ASMUtils.getNumberInsn(value[1]));

						replace.add(new VarInsnNode(ALOAD, value[0]));
						replace.add(ASMUtils.getNumberInsn(value[1]));
						replace.add(new InsnNode(IALOAD));

						replace.add(ASMUtils.getNumberInsn(iinc.incr));

						replace.add(new InsnNode(IADD));

						replace.add(new InsnNode(IASTORE));

						insns.insert(iinc, replace);
						insns.remove(iinc);
					}
				}
			}

			if (!localVarMap.isEmpty())
				insns.insertBefore(insns.getFirst(), initialize);
		}));

		info("+ Mutilated " + counter.get() + " local variables");
	}

	@Override
	public String getName()
	{
		return "Local Variable Mutilator";
	}

}
