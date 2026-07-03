package mekanism.common.tile.factory;

import mekanism.common.block.states.BlockStateMachine;
import mekanism.common.tier.FactoryTier;

public class TileEntityUltimateFactory extends TileEntityFactory {

    public TileEntityUltimateFactory() {
        super(FactoryTier.ULTIMATE, BlockStateMachine.MachineType.ULTIMATE_FACTORY);
    }
}
