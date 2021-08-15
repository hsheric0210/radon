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

package me.itzsomebody.radon.transformers.obfuscators.flow;

import java.util.Locale;

public enum FlowObfuscationSetting
{
	INSERT_BOGUS_SWITCH_JUMPS(new BogusSwitchJumpInserter()),
	INSERT_BOGUS_JUMPS(new BogusJumpInserter()),
	MUTILATE_NULL_CHECK(new NullCheckMutilator()),
	MUTILATE_INSTANCEOF_CHECK(new InstanceofCheckMutilator()),
	SPLIT_BLOCKS(new BlockSplitter()),
	FAKE_CATCH_BLOCKS(new FakeCatchBlocks()),
	INSERT_BAD_POPS(new BadPopInserter()),
	REPLACE_GOTO(new GotoReplacer()),
	MUTILATE_RETURN(new ReturnMutilator()),
	PACK_LOCAL_VARIABLES(new LocalVariablePacker()),
	MUTILATE_LOCAL_VARIABLES(new LocalVariableMutilator());

	private final FlowObfuscation flowObfuscation;

	FlowObfuscationSetting(final FlowObfuscation flowObfuscation)
	{
		this.flowObfuscation = flowObfuscation;
	}

	public FlowObfuscation getFlowObfuscation()
	{
		return flowObfuscation;
	}

	public String getName()
	{
		return name().toLowerCase(Locale.ENGLISH);
	}
}
