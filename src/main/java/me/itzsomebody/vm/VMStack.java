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

public class VMStack
{
	public JWrapper[] stack;
	private final int maxSize;
	private int pointer;

	public VMStack(final int maxSize)
	{
		stack = new JWrapper[maxSize];
		this.maxSize = maxSize;
		pointer = 0;
	}

	public void push(final JWrapper wrapper)
	{
		stack[pointer++] = wrapper;
	}

	public JWrapper pop()
	{
		final JWrapper wrapper = stack[--pointer];
		stack[pointer] = null;
		return wrapper;
	}

	public void clear()
	{
		stack = new JWrapper[maxSize];
		pointer = 0;
	}
}
