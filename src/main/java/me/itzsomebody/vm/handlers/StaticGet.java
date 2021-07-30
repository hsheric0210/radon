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

import java.lang.reflect.Field;

import me.itzsomebody.vm.VM;
import me.itzsomebody.vm.VMException;
import me.itzsomebody.vm.datatypes.*;

public class StaticGet extends Handler
{
	@Override
	public void handle(final VM vm, final Object... operands) throws Exception
	{
		final String ownerName = (String) operands[0];
		final String name = (String) operands[1];
		final String typeName = (String) operands[2];

		final Class clazz = VM.getClazz(ownerName);
		final Class type = VM.getClazz(typeName);
		final Field field = VM.getField(clazz, name, type);

		if (field == null)
			throw new VMException();

		switch (type.getName())
		{
			case "int":
				vm.push(new JInteger(field.getInt(null)));
				break;
			case "long":
				vm.push(new JLong(field.getLong(null)));
				vm.push(JTop.getTop());
				break;
			case "float":
				vm.push(new JFloat(field.getFloat(null)));
				break;
			case "double":
				vm.push(new JDouble(field.getDouble(null)));
				vm.push(JTop.getTop());
				break;
			case "byte":
				vm.push(new JInteger(field.getByte(null)));
				break;
			case "short":
				vm.push(new JInteger(field.getShort(null)));
				break;
			case "char":
				vm.push(new JInteger(field.getChar(null)));
				break;
			case "boolean":
				vm.push(new JInteger(field.getBoolean(null)));
				break;
			default:
				vm.push(new JObject(field.get(null)));
				break;
		}
	}
}
