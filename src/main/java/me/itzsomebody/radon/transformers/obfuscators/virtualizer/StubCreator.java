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

package me.itzsomebody.radon.transformers.obfuscators.virtualizer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.objectweb.asm.Type;

import me.itzsomebody.vm.Instruction;

public class StubCreator
{
	private static final int INT = 0;
	private static final int LONG = 1;
	private static final int FLOAT = 2;
	private static final int DOUBLE = 3;
	private static final int STRING = 4;
	private static final int CLASS = 5;

	private final List<List<Instruction>> instructionLists;
	private final ByteArrayOutputStream out;

	public StubCreator()
	{
		instructionLists = new ArrayList<>();
		out = new ByteArrayOutputStream();
	}

	public void addInstructionList(final List<Instruction> list)
	{
		instructionLists.add(list);
	}

	public byte[] createStub() throws IOException
	{
		final GZIPOutputStream gzip = new GZIPOutputStream(out);
		final DataOutputStream dos = new DataOutputStream(gzip);

		dos.writeShort(instructionLists.size());

		for (final List<Instruction> list : instructionLists)
		{
			dos.writeInt(list.size());

			for (final Instruction instruction : list)
			{
				dos.writeByte(instruction.getOpcode());
				dos.writeByte(instruction.getOperands().length);

				for (final Object operand : instruction.getOperands())
					if (operand instanceof Integer)
					{
						dos.writeByte(INT);
						dos.writeInt((Integer) operand);
					}
					else if (operand instanceof Long)
					{
						dos.writeByte(LONG);
						dos.writeLong((Long) operand);
					}
					else if (operand instanceof Float)
					{
						dos.writeByte(FLOAT);
						dos.writeFloat((Float) operand);
					}
					else if (operand instanceof Double)
					{
						dos.writeByte(DOUBLE);
						dos.writeDouble((Double) operand);
					}
					else if (operand instanceof String)
					{
						dos.writeByte(STRING);
						dos.writeUTF((String) operand);
					}
					else if (operand instanceof Type)
					{
						dos.writeByte(CLASS);

						final Type type = (Type) operand;

						if (type.getSort() == Type.ARRAY)
							dos.writeUTF(type.getInternalName());
						else
							dos.writeUTF(type.getClassName());
					}
			}
		}

		gzip.close();

		return out.toByteArray();
	}
}
