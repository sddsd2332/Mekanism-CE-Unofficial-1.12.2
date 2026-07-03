package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerQuantumEntangloporter extends MekanismTileContainer<TileEntityQuantumEntangloporter> {

    public ContainerQuantumEntangloporter(InventoryPlayer inventory, TileEntityQuantumEntangloporter tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 74;
    }
}
