package mekanism.common.tile.factory;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.tier.FactoryTier;

public class TileEntityEliteFactory extends TileEntityFactory {

    public TileEntityEliteFactory() {
        super(FactoryTier.ELITE, MachineType.ELITE_FACTORY);
    }
}
