package mekanism.common.inventory.container;

import mekanism.common.tile.multiblock.TileEntityInductionCasing;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerInductionMatrix extends ContainerEnergyStorage<TileEntityInductionCasing> {

    public ContainerInductionMatrix(InventoryPlayer inventory, TileEntityInductionCasing tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addArmorSlots(inv, -20, 41, 0);
    }
}
