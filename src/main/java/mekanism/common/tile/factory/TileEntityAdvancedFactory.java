package mekanism.common.tile.factory;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.tier.FactoryTier;

public class TileEntityAdvancedFactory extends TileEntityFactory {

    public TileEntityAdvancedFactory() {
        super(FactoryTier.ADVANCED, MachineType.ADVANCED_FACTORY);
    }
}
