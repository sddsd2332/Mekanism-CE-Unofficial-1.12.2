package mekanism.common.tile.factory;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.tier.FactoryTier;

public class TileEntityBasicFactory extends TileEntityFactory {

    public TileEntityBasicFactory() {
        super(FactoryTier.BASIC, MachineType.BASIC_FACTORY);
    }
}
