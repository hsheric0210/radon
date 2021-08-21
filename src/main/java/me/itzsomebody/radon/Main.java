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

package me.itzsomebody.radon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.logging.*;
import java.util.zip.ZipFile;

import me.itzsomebody.radon.cli.CommandArgumentsParser;
import me.itzsomebody.radon.config.Configuration;
import me.itzsomebody.radon.config.ObfuscationConfiguration;
import me.itzsomebody.radon.utils.Constants;
import me.itzsomebody.radon.utils.CustomOutputStream;
import me.itzsomebody.radon.utils.IOUtils;
import me.itzsomebody.radon.utils.WatermarkUtils;

/**
 * Main class of obfuscator. \o/
 * <p>
 * TODO: Renamer transformer should correct strings used for reflection. (i.e. Class.forName("me.itzsomebody.Thing"))
 * </p>
 * <p>
 * TODO: Clean code up in general.
 * </p>
 *
 * @author ItzSomebody
 */
public final class Main
{
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
	public static final String VERSION = "2.0.0-FORK";
	public static final String[] CONTRIBUTORS =
	{
			"ItzSomebody", "x0ark", "Col-E", "Artel", "kazigk", "Olexorus", "freeasbird", "CertainLach", "xxDark", "vovanre", "hsheric0210"
	};
	public static final String ATTRIBUTION = String.format(String.join(Constants.LINE_SEPARATOR, "Radon is a free and open-source Java obfuscator with contributions from %s.", "Version: %s", "Original website: https://github.com/ItzSomebody/Radon", "Forked version website: https://github.com/hsheric0210/Radon"), formatContributorList(), VERSION);

	public static final String WATERMARK = "RADON" + VERSION;

	public static boolean getBoolean()
	{
		return false;
	}

	private static String formatContributorList()
	{
		final StringBuilder sb = new StringBuilder();

		Arrays.stream(CONTRIBUTORS).forEach(contributor -> sb.append(Constants.LINE_SEPARATOR).append("* ").append(contributor));

		return sb.toString();
	}

	/**
	 * Main method.
	 *
	 * @param args
	 *             arguments from command line.
	 */
	public static void main(final String[] args) throws IOException
	{
		final CustomOutputStream cos = new CustomOutputStream(System.err);
		System.setErr(new PrintStream(cos));

		LOGGER.setUseParentHandlers(false);
		final Handler handler = new ConsoleHandler();
		handler.setFormatter(new Formatter()
		{
			@Override
			public String format(final LogRecord record)
			{
				final String prefix = String.format("[%s] %s: ", new SimpleDateFormat("dd/MM/yyyy-hh:mm:ss").format(new Date(record.getMillis())), record.getLevel().getName());

				final StringBuilder builder = new StringBuilder();
				builder.append(prefix).append(formatMessage(record)).append("\n");

				if (record.getThrown() != null)
				{
					final StringWriter sw = new StringWriter();
					try (final PrintWriter pw = new PrintWriter(sw)
					{
						@Override
						public void println(final Object o)
						{
							printf("%s  %s%n", prefix, o);
						}
					})
					{
						record.getThrown().printStackTrace(pw);
					}
					builder.append(sw);
				}

				return builder.toString();
			}
		});
		LOGGER.addHandler(handler);

		System.err.println(ATTRIBUTION);

		// Registers the switches.
		CommandArgumentsParser.registerCommandSwitch("help", 0);
		CommandArgumentsParser.registerCommandSwitch("license", 0);
		CommandArgumentsParser.registerCommandSwitch("config", 1);
		CommandArgumentsParser.registerCommandSwitch("extract", 2);

		// Parse away!
		final CommandArgumentsParser parser = new CommandArgumentsParser(args);

		// Switch handling.
		if (parser.containsSwitch("help"))
			showHelpMenu();
		else if (parser.containsSwitch("license"))
			showLicense();
		else if (parser.containsSwitch("config"))
		{
			final File file = new File(parser.getSwitchArgs("config")[0]);
			final Configuration config;

			try
			{
				config = new Configuration(new FileInputStream(file));
			}
			catch (final FileNotFoundException exc)
			{
				severe(String.format("Configuration \"%s\" file not found", file.getName()));
				return;
			}

			try
			{
				// Parse the config and let's run Radon.
				final Radon radon = new Radon(ObfuscationConfiguration.from(config));
				radon.run();
			}
			catch (final Throwable t)
			{
				t.printStackTrace();
			}
		}
		else if (parser.containsSwitch("extract"))
		{
			// Watermark extraction.
			final String[] switchArgs = parser.getSwitchArgs("extract");

			// Input file.
			final File leaked = new File(switchArgs[0]);
			if (!leaked.exists())
			{
				severe("Input file not found");
				return;
			}

			try
			{
				// Extract the ids and stick them into the console.
				info(WatermarkUtils.extractIds(new ZipFile(leaked), switchArgs[1]));
			}
			catch (final Throwable t)
			{
				t.printStackTrace();
			}
		}
		else
			showHelpMenu();

		cos.close();
	}

	public static void info(final String msg)
	{
		LOGGER.info(msg);
	}

	public static void infoNewline()
	{
		info("");
	}

	public static void warn(final String msg)
	{
		LOGGER.warning(msg);
	}

	public static void warn(final String msg, final Throwable thrown)
	{
		LOGGER.log(Level.WARNING, msg, thrown);
	}

	public static void severe(final String msg)
	{
		LOGGER.severe(msg);
	}

	public static void severe(final String msg, final Throwable thrown)
	{
		LOGGER.log(Level.SEVERE, msg, thrown);
	}

	private static String getProgramName()
	{
		return new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
	}

	/**
	 * Prints help message into console.
	 */
	private static void showHelpMenu()
	{
		final String name = getProgramName();
		info(String.format("CLI Usage:\t\t\tjava -jar %s --config example.config", name));
		info(String.format("Help Menu:\t\t\tjava -jar %s --help", name));
		info(String.format("License:\t\t\tjava -jar %s --license", name));
		info(String.format("Watermark Extraction:\tjava -jar %s --extract Input.jar exampleKey", name));
	}

	/**
	 * Spams the user's console full of legalese they don't care about whatsoever.
	 */
	private static void showLicense()
	{
		System.out.println(new String(IOUtils.toByteArray(Objects.requireNonNull(Main.class.getResourceAsStream("/license.txt"))), StandardCharsets.UTF_8));
	}

	private static byte dummyByteOp(final byte a)
	{
		return a;
	}

	private static short dummyShortOp(final short a)
	{
		return a;
	}

	private Main()
	{
	}
}
