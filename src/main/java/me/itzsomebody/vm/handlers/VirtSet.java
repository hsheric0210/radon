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
import me.itzsomebody.vm.datatypes.JTop;
import me.itzsomebody.vm.datatypes.JWrapper;

public class VirtSet extends Handler
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

		JWrapper value = vm.pop();

		if (value instanceof JTop)
			value = vm.pop();

		final Object ref = vm.pop().asObj();

		if ("int".equals(ownerName))
			field.setInt(ref, value.asInt());
		else if ("long".equals(ownerName))
			field.setLong(ref, value.asLong());
		else if ("float".equals(ownerName))
			field.setFloat(ref, value.asFloat());
		else if ("double".equals(ownerName))
			field.setDouble(ref, value.asDouble());
		else if ("byte".equals(ownerName))
			field.setByte(ref, value.asByte());
		else if ("short".equals(ownerName))
			field.setShort(ref, value.asShort());
		else if ("char".equals(ownerName))
			field.setChar(ref, value.asChar());
		else if ("boolean".equals(ownerName))
			field.setBoolean(ref, value.asBool());
		else
			field.set(ref, value.asObj());
	}
}
