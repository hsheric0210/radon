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

import static me.itzsomebody.radon.transformers.obfuscators.virtualizer.VMOpcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import me.itzsomebody.vm.datatypes.JObject;
import me.itzsomebody.vm.datatypes.JTop;
import me.itzsomebody.vm.datatypes.JWrapper;
import me.itzsomebody.vm.handlers.*;

public class VM
{
	private static final Map<String, Method> METHOD_CACHE;
	private static final Map<String, Field> FIELD_CACHE;
	private static final Map<String, Constructor> CONSTRUCTOR_CACHE;
	private static final Handler[] HANDLERS;
	private static final Stub STUB;
	private final VMStack stack;
	private final JWrapper[] registers;
	private final Instruction[] instructions;
	private final VMTryCatch[] catches;
	private int pc;
	private boolean executing;

	static
	{
		METHOD_CACHE = new HashMap<>();
		FIELD_CACHE = new HashMap<>();
		CONSTRUCTOR_CACHE = new HashMap<>();
		try
		{
			STUB = new Stub();
		}
		catch (final Exception e)
		{
			throw new VMException();
		}

		HANDLERS = new Handler[61];
		HANDLERS[VM_NULL_PUSH] = new NullPush();
		HANDLERS[VM_INT_PUSH] = new IntPush();
		HANDLERS[VM_LONG_PUSH] = new LongPush();
		HANDLERS[VM_FLOAT_PUSH] = new FloatPush();
		HANDLERS[VM_DOUBLE_PUSH] = new DoublePush();
		HANDLERS[VM_OBJ_PUSH] = new ObjPush();
		HANDLERS[VM_ADD] = new Add();
		HANDLERS[VM_SUB] = new Sub();
		HANDLERS[VM_MUL] = new Mul();
		HANDLERS[VM_DIV] = new Div();
		HANDLERS[VM_MOD] = new Mod();
		HANDLERS[VM_AND] = new And();
		HANDLERS[VM_OR] = new Or();
		HANDLERS[VM_XOR] = new Xor();
		HANDLERS[VM_SHL] = new Shl();
		HANDLERS[VM_SHR] = new Shr();
		HANDLERS[VM_USHR] = new Ushr();
		HANDLERS[VM_LOAD] = new Load();
		HANDLERS[VM_STORE] = new Store();
		HANDLERS[VM_ARR_LOAD] = new ArrLoad();
		HANDLERS[VM_ARR_STORE] = new ArrStore();
		HANDLERS[VM_POP] = new Pop();
		HANDLERS[VM_POP2] = new Pop2();
		HANDLERS[VM_DUP] = new Dup();
		HANDLERS[VM_SWAP] = new Swap();
		HANDLERS[VM_INC] = new Inc();
		HANDLERS[VM_PRIM_CAST] = new PrimCast();
		HANDLERS[VM_LCMP] = new Lcmp();
		HANDLERS[VM_FCMPL] = new Fcmpl();
		HANDLERS[VM_FCMPG] = new Fcmpg();
		HANDLERS[VM_DCMPL] = new Dcmpl();
		HANDLERS[VM_DCMPG] = new Dcmpg();
		HANDLERS[VM_JZ] = new Jz();
		HANDLERS[VM_JNZ] = new Jnz();
		HANDLERS[VM_JLT] = new Jlt();
		HANDLERS[VM_JLE] = new Jle();
		HANDLERS[VM_JGT] = new Jgt();
		HANDLERS[VM_JGE] = new Jge();
		HANDLERS[VM_JEQ] = new Jeq();
		HANDLERS[VM_JMP] = new Jmp();
		HANDLERS[VM_JSR] = new Jsr();
		HANDLERS[VM_RET] = new Ret();
		HANDLERS[VM_VIRT_GET] = new VirtGet();
		HANDLERS[VM_STATIC_GET] = new StaticGet();
		HANDLERS[VM_VIRT_SET] = new VirtSet();
		HANDLERS[VM_STATIC_SET] = new StaticSet();
		HANDLERS[VM_VIRT_CALL] = new VirtCall();
		HANDLERS[VM_STATIC_CALL] = new StaticCall();
		HANDLERS[VM_INSTANTIATE] = new Instantiate();
		HANDLERS[VM_NEW_ARR] = new NewArr();
		HANDLERS[VM_ARR_LENGTH] = new ArrLength();
		HANDLERS[VM_THROW] = new Throw();
		HANDLERS[VM_CHECKCAST] = new Checkcast();
		HANDLERS[VM_INSTANCE_OF] = new Instanceof();
		HANDLERS[VM_MONITOR] = new Monitor();
		HANDLERS[VM_JN] = new Jn();
		HANDLERS[VM_JNN] = new Jnn();
		HANDLERS[VM_NOP] = new Nop();
		HANDLERS[VM_KILL] = new Kill();
		HANDLERS[VM_NEG] = new Neg();
		HANDLERS[VM_JNE] = new Jne();
	}

	public VM(final VMContext context)
	{
		stack = context.getStack();
		registers = context.getRegisters();
		instructions = STUB.instructions[context.getOffset()];
		catches = context.getCatches();
		pc = 0;
		executing = true;
	}

	public void push(final JWrapper wrapper)
	{
		stack.push(wrapper);
	}

	public JWrapper pop()
	{
		return stack.pop();
	}

	public JWrapper loadRegister(final int index)
	{
		return registers[index];
	}

	public void storeRegister(final JWrapper wrapper, final int index)
	{
		registers[index] = wrapper;
	}

	public int getPc()
	{
		return pc;
	}

	public void setPc(final int pc)
	{
		this.pc = pc;
	}

	public void setExecuting(final boolean executing)
	{
		this.executing = executing;
	}

	public JWrapper execute() throws Throwable
	{
		while (executing)
			try
			{
				final Instruction instruction = instructions[pc];

				final Handler handler = HANDLERS[instruction.getOpcode()];
				handler.handle(this, instruction.getOperands());

				pc++;
			}
			catch (final Throwable t)
			{
				boolean unhandled = true;

				if (catches != null)
					for (final VMTryCatch vmCatch : catches)
						if ((vmCatch.getType() == null || Class.forName(vmCatch.getType()).isInstance(t)) && pc >= vmCatch.getStartPc() && pc < vmCatch.getEndPc())
						{
							stack.clear();
							push(new JObject(t));

							pc = vmCatch.getHandlerPc();
							unhandled = false;
							break;
						}

				if (unhandled)
					throw t;
			}

		final JWrapper result = pop();

		if (result instanceof JTop)
			return pop();

		return result;
	}

	private static String parametersToString(final Class<?>... params)
	{
		return Arrays.stream(params).map(param -> param.getName() + ' ').collect(Collectors.joining()).trim();
	}

	public static Class<?> getClazz(final String name) throws ClassNotFoundException
	{
		if ("int".equals(name))
			return int.class;
		if ("long".equals(name))
			return long.class;
		if ("float".equals(name))
			return float.class;
		if ("double".equals(name))
			return double.class;
		if ("char".equals(name))
			return char.class;
		if ("short".equals(name))
			return short.class;
		if ("byte".equals(name))
			return byte.class;
		if ("boolean".equals(name))
			return boolean.class;
		if ("void".equals(name))
			return void.class;

		return Class.forName(name);
	}

	public static Method getMethod(final Class<?> clazz, final String name, final Class<?>... params)
	{
		if (METHOD_CACHE.containsKey(clazz.getName() + '.' + name + '(' + parametersToString(params) + ')'))
			return METHOD_CACHE.get(clazz.getName() + '.' + name + '(' + parametersToString(params) + ')');

		final Method[] methods = clazz.getDeclaredMethods();
		for (final Method method : methods)
			if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), params))
			{
				method.setAccessible(true);
				METHOD_CACHE.put(clazz.getName() + '.' + name + '(' + parametersToString(params) + ')', method);
				return method;
			}

		if (clazz.getSuperclass() != null)
		{
			final Class<?> superClass = clazz.getSuperclass();

			final Method method = getMethod(superClass, name, params);

			if (method != null)
			{
				method.setAccessible(true);
				METHOD_CACHE.put(clazz.getName() + '.' + name + '(' + parametersToString(params) + ')', method);
				return method;
			}
		}

		final Class<?>[] interfaces = clazz.getInterfaces();

		for (final Class<?> anInterface : interfaces)
		{
			final Method method = getMethod(anInterface, name, params);

			if (method != null)
			{
				method.setAccessible(true);
				METHOD_CACHE.put(clazz.getName() + '.' + name + '(' + parametersToString(params) + ')', method);
				return method;
			}
		}

		return null;
	}

	public static Constructor<?> getConstructor(final Class<?> clazz, final Class<?>... params)
	{
		if (CONSTRUCTOR_CACHE.containsKey(clazz.getName() + '(' + parametersToString(params) + ')'))
			return CONSTRUCTOR_CACHE.get(clazz.getName() + '(' + parametersToString(params) + ')');

		final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		for (final Constructor<?> constructor : constructors)
			if (Arrays.equals(constructor.getParameterTypes(), params))
			{
				constructor.setAccessible(true);
				CONSTRUCTOR_CACHE.put(clazz.getName() + '(' + parametersToString(params) + ')', constructor);
				return constructor;
			}

		if (clazz.getSuperclass() != null)
		{
			final Class<?> superClass = clazz.getSuperclass();

			final Constructor<?> constructor = getConstructor(superClass, params);

			if (constructor != null)
			{
				constructor.setAccessible(true);
				CONSTRUCTOR_CACHE.put(clazz.getName() + '(' + parametersToString(params) + ')', constructor);
				return constructor;
			}
		}

		final Class<?>[] interfaces = clazz.getInterfaces();

		for (final Class<?> anInterface : interfaces)
		{
			final Constructor<?> constructor = getConstructor(anInterface, params);

			if (constructor != null)
			{
				constructor.setAccessible(true);
				CONSTRUCTOR_CACHE.put(clazz.getName() + '(' + parametersToString(params) + ')', constructor);
				return constructor;
			}
		}

		return null;
	}

	public static Field getField(final Class<?> clazz, final String name, final Class<?> type)
	{
		if (FIELD_CACHE.containsKey(clazz.getName() + '.' + name + '(' + type.getName() + ')'))
			return FIELD_CACHE.get(clazz.getName() + '.' + name + '(' + type.getName() + ')');

		final Field[] fields = clazz.getDeclaredFields();
		for (final Field field : fields)
			if (field.getName().equals(name) && field.getType() == type)
			{
				field.setAccessible(true);
				FIELD_CACHE.put(clazz.getName() + '.' + name + '(' + type.getName() + ')', field);
				return field;
			}

		if (clazz.getSuperclass() != null)
		{
			final Class<?> superClass = clazz.getSuperclass();

			final Field field = getField(superClass, name, type);

			if (field != null)
			{
				FIELD_CACHE.put(clazz.getName() + '.' + name + '(' + type.getName() + ')', field);
				return field;
			}
		}

		final Class<?>[] interfaces = clazz.getInterfaces();

		for (final Class<?> anInterface : interfaces)
		{
			final Field field = getField(anInterface, name, type);

			if (field != null)
			{
				FIELD_CACHE.put(clazz.getName() + '.' + name + '(' + type.getName() + ')', field);
				return field;
			}
		}

		return null;
	}
}
