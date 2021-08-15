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

package me.itzsomebody.radon.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class CustomOutputStream extends OutputStream
{
	private final Writer bw;
	private final OutputStream err;

	public CustomOutputStream(final OutputStream err) throws IOException
	{
		final File log = new File("Radon.log");
		if (!log.exists() && !log.createNewFile())
			throw new IOException("Can't create Radon.log");

		bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log), StandardCharsets.UTF_8));
		this.err = err;
	}

	@Override
	public void write(final int b) throws IOException
	{
		bw.write(b);
		err.write(b);
	}

	@Override
	public void close() throws IOException
	{
		bw.close();
	}
}
