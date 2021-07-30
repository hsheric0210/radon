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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class Stub
{
	public Instruction[][] instructions;

	public Stub() throws IOException, ClassNotFoundException, VMException
	{
		final GZIPInputStream gzip = new GZIPInputStream(Stub.class.getResourceAsStream("/radon.vm"));
		try (final DataInputStream din = new DataInputStream(gzip))
		{
			final int nFunctions = din.readShort();
			instructions = new Instruction[nFunctions][];

			for (int i = 0; i < nFunctions; i++)
			{
				final int nInstructions = din.readInt();
				final Instruction[] funcInstructions = new Instruction[nInstructions];

				for (int j = 0; j < nInstructions; j++)
				{
					final int opcode = din.readByte();
					final int nOperands = din.readByte();

					final Object[] operands = new Object[nOperands];

					for (int k = 0; k < nOperands; k++)
					{
						final int operandType = din.readByte();

						switch (operandType)
						{
							case 0: // INT
								operands[k] = din.readInt();
								break;
							case 1: // LONG
								operands[k] = din.readLong();
								break;
							case 2: // FLOAT
								operands[k] = Float.intBitsToFloat(din.readInt());
								break;
							case 3: // DOUBLE
								operands[k] = Double.longBitsToDouble(din.readLong());
								break;
							case 4: // STRING
								operands[k] = din.readUTF();
								break;
							case 5: // CLASS
								operands[k] = VM.getClazz(din.readUTF());
								break;
							default:
								throw new VMException();
						}
					}

					funcInstructions[j] = new Instruction(opcode, operands);
				}

				instructions[i] = funcInstructions;
			}
		}
	}
}
