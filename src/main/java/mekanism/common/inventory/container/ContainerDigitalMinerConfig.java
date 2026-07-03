package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerDigitalMinerConfig extends MekanismTileContainer<TileEntityDigitalMiner> {

    public ContainerDigitalMinerConfig(InventoryPlayer inv, TileEntityDigitalMiner tile) {
        super(tile, inv);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 50;
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 88;
    }

    @Override
    protected void addSlots() {
        // Digital miner config does not expose the machine inventory or upgrade slots.
    }

    @Override
    protected void addContainerTrackers() {
        if (tile != null) {
            tile.addConfigContainerTrackers(this);
        }
    }
}
