package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerEnergyCube extends ContainerEnergyStorage<TileEntityEnergyCube> {

    public ContainerEnergyCube(InventoryPlayer inventory, TileEntityEnergyCube tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addArmorSlots(inv, 180, 41, 0);
    }
}
