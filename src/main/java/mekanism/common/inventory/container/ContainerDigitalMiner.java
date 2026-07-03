package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityDigitalMiner;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerDigitalMiner extends MekanismTileContainer<TileEntityDigitalMiner> {

    public ContainerDigitalMiner(InventoryPlayer inventory, TileEntityDigitalMiner tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 160;
    }
}
