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

package me.itzsomebody.vm.handlers;

import me.itzsomebody.vm.VM;
import me.itzsomebody.vm.VMException;
import me.itzsomebody.vm.datatypes.JInteger;
import me.itzsomebody.vm.datatypes.JLong;
import me.itzsomebody.vm.datatypes.JTop;
import me.itzsomebody.vm.datatypes.JWrapper;

public class And extends Handler
{
	@Override
	public void handle(final VM vm, final Object... operands)
	{
		JWrapper wrapper = vm.pop();
		if (wrapper instanceof JTop)
			wrapper = vm.pop();

		if (wrapper instanceof JInteger)
		{
			final int first = vm.pop().asInt();
			final int second = wrapper.asInt();

			vm.push(new JInteger(first & second));
			return;
		}
		if (wrapper instanceof JLong)
		{
			vm.pop();
			final long first = vm.pop().asLong();
			final long second = wrapper.asLong();

			vm.push(new JLong(first & second));
			vm.push(JTop.getTop());
			return;
		}

		throw new VMException();
	}
}
