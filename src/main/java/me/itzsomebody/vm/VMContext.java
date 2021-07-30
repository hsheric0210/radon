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

package me.itzsomebody.vm;

import me.itzsomebody.vm.datatypes.JWrapper;

public class VMContext
{
	private final VMStack stack;
	private final JWrapper[] registers;
	private final int offset;
	private VMTryCatch[] catches;

	public VMContext(final int maxStack, final int nRegisters, final int offset)
	{
		stack = new VMStack(maxStack);
		registers = new JWrapper[nRegisters];
		this.offset = offset;
	}

	public VMContext(final int maxStack, final int nRegisters, final int offset, final VMTryCatch[] catches)
	{
		this(maxStack, nRegisters, offset);
		this.catches = catches;
	}

	public VMStack getStack()
	{
		return stack;
	}

	public JWrapper[] getRegisters()
	{
		return registers;
	}

	public void initRegister(final JWrapper wrapper, final int index)
	{
		registers[index] = wrapper;
	}

	public int getOffset()
	{
		return offset;
	}

	public VMTryCatch[] getCatches()
	{
		return catches;
	}
}
