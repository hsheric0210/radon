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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.vm.VM;
import me.itzsomebody.vm.VMException;
import me.itzsomebody.vm.datatypes.*;

public class StaticCall extends Handler
{
	@Override
	public void handle(final VM vm, final Object... operands) throws Throwable
	{
		final String ownerName = (String) operands[0];
		final String name = (String) operands[1];
		final String[] paramsAsStrings = PTRN2.split((String) operands[2]);
		final Class[] params;
		if ("\u0000\u0000\u0000".equals(paramsAsStrings[0]))
			params = Constants.ZERO_LENGTH_CLASS_ARRAY;
		else
			params = stringsToParams(paramsAsStrings);
		final Object[] args = new Object[params.length];

		final Class clazz = VM.getClazz(ownerName);
		final Method method = VM.getMethod(clazz, name, params);

		if (method == null)
			throw new VMException();

		final String returnType = method.getReturnType().getName();

		for (int i = params.length - 1; i >= 0; i--)
		{
			final Class param = params[i];
			JWrapper arg = vm.pop();

			if (arg instanceof JTop)
				arg = vm.pop();

			if (param == boolean.class)
				args[i] = arg.asBool();
			else if (param == char.class)
				args[i] = arg.asChar();
			else if (param == short.class)
				args[i] = arg.asShort();
			else if (param == byte.class)
				args[i] = arg.asByte();
			else
				args[i] = arg.asObj();
		}

		try
		{
			if (!"void".equals(returnType))
				if ("int".equals(returnType))
					vm.push(new JInteger((Integer) method.invoke(null, args)));
				else if ("long".equals(returnType))
				{
					vm.push(new JLong((Long) method.invoke(null, args)));
					vm.push(JTop.getTop());
				}
				else if ("float".equals(returnType))
					vm.push(new JFloat((Float) method.invoke(null, args)));
				else if ("double".equals(returnType))
				{
					vm.push(new JDouble((Double) method.invoke(null, args)));
					vm.push(JTop.getTop());
				}
				else if ("byte".equals(returnType))
					vm.push(new JInteger((Byte) method.invoke(null, args)));
				else if ("char".equals(returnType))
					vm.push(new JInteger((Character) method.invoke(null, args)));
				else if ("short".equals(returnType))
					vm.push(new JInteger((Short) method.invoke(null, args)));
				else if ("boolean".equals(returnType))
					vm.push(new JInteger((Boolean) method.invoke(null, args)));
				else
					vm.push(new JObject(method.invoke(null, args)));
			else
				method.invoke(null, args);
		}
		catch (final InvocationTargetException e)
		{
			throw e.getTargetException();
		}
	}

	private static Class[] stringsToParams(final String... s) throws ClassNotFoundException
	{
		final Class[] classes = new Class[s.length];
		for (int i = 0, j = s.length; i < j; i++)
			classes[i] = VM.getClazz(s[i]);

		return classes;
	}

	private static final Pattern PTRN2 = Pattern.compile("\u0001\u0001");
}
