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

public class Instruction
{
	private final int opcode;
	private Object[] operands;

	public Instruction(final int opcode, final Object... operands)
	{
		this.opcode = opcode;
		this.operands = operands;
	}

	public int getOpcode()
	{
		return opcode;
	}

	public void setOperands(final Object... operands)
	{
		this.operands = operands;
	}

	public Object[] getOperands()
	{
		return operands;
	}
}
