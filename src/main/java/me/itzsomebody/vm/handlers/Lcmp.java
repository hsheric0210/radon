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
import me.itzsomebody.vm.datatypes.JInteger;

public class Lcmp extends Handler
{
	@Override
	public void handle(VM vm, Object[] operands)
	{
		vm.pop();
		long second = vm.pop().asLong();

		vm.pop();
		long first = vm.pop().asLong();

		long result = first - second;

		if (result == 0)
			vm.push(new JInteger(0));
		else if (result > 0)
			vm.push(new JInteger(1));
		else
			vm.push(new JInteger(-1));
	}
}
