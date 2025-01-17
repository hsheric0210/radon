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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.utils.ASMUtils;
import me.itzsomebody.radon.utils.BogusJumps;
import me.itzsomebody.radon.utils.CodeGenerator;
import me.itzsomebody.radon.utils.RandomUtils;

/**
 * Replaces {@code GOTO} instructions with an expression which is always true.
 * This does nothing more than adding a one more edge to a control flow graph for every {@code GOTO} instruction present.
 *
 * <p>
 * TODO: 단순히 미리 만들어둔 trap label로 점프시키는 것이 아니라, if문의 'else' branch 내의 코드의 내용을 교묘하게 변조한 후 그 코드로 점프하게 함으로써 더 헷갈리게 할 수 있지 않을까?
 * </p>
 *
 * @author ItzSomebody
 */
public class GotoReplacer extends FlowObfuscation
{
	private static final int CLASS_PRED_ACCESS = ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
	private static final int INTERFACE_PRED_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_FINAL;

	@Override
	public void transform()
	{
		final AtomicInteger counter = new AtomicInteger();

		getClassWrappers().stream().filter(this::included).forEach(cw ->
		{
			final AtomicBoolean shouldAdd = new AtomicBoolean();

			final Type predicateType = ASMUtils.getRandomType();
			final String predicateDescriptor = predicateType.getDescriptor();
			final Object predicateInitialValue = RandomUtils.getRandomFloat() > 0.2F ? RandomUtils.getRandomValue(predicateType) : null;

			final FieldNode predicate = new FieldNode(cw.access.isInterface() ? INTERFACE_PRED_ACCESS : CLASS_PRED_ACCESS, getFieldDictionary(cw.originalName).nextUniqueString(), predicateDescriptor, null, predicateInitialValue);

			cw.methods.stream().filter(mw -> included(mw) && mw.hasInstructions()).forEach(mw ->
			{
				final InsnList insns = mw.getInstructions();

				int leeway = mw.getLeewaySize();
				final int varIndex = mw.getMaxLocals();
				mw.methodNode.maxLocals += predicateType.getSize(); // Prevents breaking of other transformers which rely on this field.

				final boolean isCtor = "<init>".equals(mw.getName());
				boolean calledSuper = false;
				for (final AbstractInsnNode insn : insns.toArray())
				{
					if (leeway < 10000)
						break;

					// Bad way of detecting if this class was instantiated
					if (isCtor && !calledSuper)
						calledSuper = ASMUtils.isSuperInitializerCall(insn);

					if (insn.getOpcode() == GOTO && !(isCtor && !calledSuper))
					{
						final InsnList bogusJump = new InsnList();
						bogusJump.add(BogusJumps.createBogusJump(varIndex, predicateType, predicateInitialValue, ((JumpInsnNode) insn).label, true));
						bogusJump.add(CodeGenerator.generateTrapInstructions(mw.methodNode));
						leeway -= ASMUtils.evaluateMaxSize(bogusJump);

						insns.insert(insn, bogusJump);
						insns.remove(insn);

						counter.incrementAndGet();
						shouldAdd.set(true);
					}
				}

				if (shouldAdd.get())
				{
					final InsnList initializer = new InsnList();
					initializer.add(new FieldInsnNode(GETSTATIC, cw.getName(), predicate.name, predicateDescriptor));
					switch (predicateType.getSort())
					{
						case Type.FLOAT:
							initializer.add(new VarInsnNode(FSTORE, varIndex));
							break;
						case Type.LONG:
							initializer.add(new VarInsnNode(LSTORE, varIndex));
							break;
						case Type.DOUBLE:
							initializer.add(new VarInsnNode(DSTORE, varIndex));
							break;
						default:
							initializer.add(new VarInsnNode(ISTORE, varIndex));
							break;
					}

					ASMUtils.insertAfterConstructorCall(mw.methodNode, initializer);
				}
			});

			if (shouldAdd.get())
				cw.addField(predicate);
		});

		info("+ Swapped " + counter.get() + " GOTO instructions");
	}

	@Override
	public String getName()
	{
		return "GOTO Replacer";
	}

	@Override
	public void setConfiguration(final Configuration config)
	{
		// Not needed
	}
}
