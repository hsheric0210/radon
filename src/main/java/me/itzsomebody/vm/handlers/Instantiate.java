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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Pattern;

import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.vm.VM;
import me.itzsomebody.vm.VMException;
import me.itzsomebody.vm.datatypes.JTop;
import me.itzsomebody.vm.datatypes.JWrapper;

public class Instantiate extends Handler
{
	@Override
	public void handle(final VM vm, final Object... operands) throws Throwable
	{
		final String ownerName = (String) operands[0];
		final String[] paramsAsStrings = PTRN.split((String) operands[1]);
		final Class[] params;
		if ("\u0000\u0000\u0000".equals(paramsAsStrings[0]))
			params = Constants.ZERO_LENGTH_CLASS_ARRAY;
		else
			params = stringsToParams(paramsAsStrings);
		final Object[] args = new Object[params.length];

		final Class clazz = VM.getClazz(ownerName);
		final Constructor constructor = VM.getConstructor(clazz, params);

		if (constructor == null)
			throw new VMException();

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

		final JWrapper ref = vm.pop();
		try
		{
			ref.init(constructor.newInstance(args));
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

	private static final Pattern PTRN = Pattern.compile("\u0001\u0001");
}
