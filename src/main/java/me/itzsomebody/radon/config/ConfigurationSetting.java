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

package me.itzsomebody.radon.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.itzsomebody.radon.transformers.Transformer;
import me.itzsomebody.radon.transformers.obfuscators.BadSignature;
import me.itzsomebody.radon.transformers.miscellaneous.Expiration;
import me.itzsomebody.radon.transformers.miscellaneous.Packer;
import me.itzsomebody.radon.transformers.miscellaneous.Watermarker;
import me.itzsomebody.radon.transformers.obfuscators.*;
import me.itzsomebody.radon.transformers.obfuscators.ejector.Ejector;
import me.itzsomebody.radon.transformers.obfuscators.flow.FlowObfuscation;
import me.itzsomebody.radon.transformers.obfuscators.numbers.NumberObfuscation;
import me.itzsomebody.radon.transformers.obfuscators.references.ReferenceObfuscation;
import me.itzsomebody.radon.transformers.obfuscators.strings.StringEncryption;
import me.itzsomebody.radon.transformers.obfuscators.virtualizer.Virtualizer;
import me.itzsomebody.radon.transformers.optimizers.Optimizer;
import me.itzsomebody.radon.transformers.shrinkers.Shrinker;

/**
 * An {@link Enum} containing all the allowed standalone configuration keys allowed.
 *
 * @author ItzSomebody
 */
public enum ConfigurationSetting
{
	// ============ stuff
	INPUT(String.class, null),
	OUTPUT(String.class, null),
	LIBRARIES(List.class, null),
	EXCLUSIONS(List.class, null),
	COMPRESSION_LEVEL(Integer.class, null),
	VERIFY(Boolean.class, null),
	CORRUPT_CRC(Boolean.class, null),
	TRASH_CLASSES(Integer.class, null),
	VERBOSE_LOGGING(Boolean.class, null),

	// ============ random string generator

	GENERIC_DICTIONARY(String.class, null),
	GENERIC_MIN_RANDOMIZED_STRING_LENGTH(Integer.class, null),
	GENERIC_MAX_RANDOMIZED_STRING_LENGTH(Integer.class, null),

	PACKAGE_DICTIONARY(String.class, null),
	PACKAGE_MIN_RANDOMIZED_STRING_LENGTH(Integer.class, null),
	PACKAGE_MAX_RANDOMIZED_STRING_LENGTH(Integer.class, null),

	CLASS_DICTIONARY(String.class, null),
	CLASS_MIN_RANDOMIZED_STRING_LENGTH(Integer.class, null),
	CLASS_MAX_RANDOMIZED_STRING_LENGTH(Integer.class, null),

	METHOD_DICTIONARY(String.class, null),
	METHOD_MIN_RANDOMIZED_STRING_LENGTH(Integer.class, null),
	METHOD_MAX_RANDOMIZED_STRING_LENGTH(Integer.class, null),

	FIELD_DICTIONARY(String.class, null),
	FIELD_MIN_RANDOMIZED_STRING_LENGTH(Integer.class, null),
	FIELD_MAX_RANDOMIZED_STRING_LENGTH(Integer.class, null),

	// ============ transformers
	STRING_ENCRYPTION(Map.class, new StringEncryption()),
	FLOW_OBFUSCATION(Map.class, new FlowObfuscation()),
	REFERENCE_OBFUSCATION(Map.class, new ReferenceObfuscation()),
	STATIC_INITIALIZATION(Boolean.class, new StaticInitialization()),
	NUMBER_OBFUSCATION(Map.class, new NumberObfuscation()),
	ANTI_TAMPER(Boolean.class, new AntiTamper()),
	ANTI_DEBUG(Map.class, new AntiDebug()),
	INSTRUCTION_SET_REDUCER(Boolean.class, new InstructionSetReducer()),
	VIRTUALIZER(Boolean.class, new Virtualizer()),
	RESOURCE_RENAMER(Boolean.class, new ResourceRenamer()),
	PACKER(Boolean.class, new Packer()),
	HIDE_CODE(Map.class, new HideCode()),
	EXPIRATION(Map.class, new Expiration()),
	WATERMARK(Map.class, new Watermarker()),
	OPTIMIZER(Map.class, new Optimizer()),
	SHRINKER(Map.class, new Shrinker()),
	MEMBER_SHUFFLER(Map.class, new MemberShuffler()),
	EJECTOR(Map.class, new Ejector()),
	RENAMER(Map.class, new Renamer()),
	BAD_ANNOTATION(Boolean.class, new BadAnnotation()),
	BAD_ATTRIBUTES(Boolean.class, new BadAttributes()),
	BAD_SIGNATURE(Boolean.class, new BadSignature());

	public final Class<?> expectedType;
	public final Transformer transformer;

	ConfigurationSetting(final Class<?> expectedType, final Transformer transformer)
	{
		this.expectedType = expectedType;
		this.transformer = transformer;
	}

	/**
	 * Returns the name of this Enum constant in lowercase.
	 *
	 * @return the name of this Enum constant in lowercase.
	 */
	public String getConfigName()
	{
		return name().toLowerCase(Locale.ENGLISH);
	}

	@Override
	public String toString()
	{
		return getConfigName();
	}
}
