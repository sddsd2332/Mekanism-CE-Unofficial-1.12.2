package mekanism.common.inventory.container;

import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerElectrolyticSeparator extends MekanismTileContainer<TileEntityElectrolyticSeparator> {

    public ContainerElectrolyticSeparator(InventoryPlayer inventory, TileEntityElectrolyticSeparator tile) {
        super(tile, inventory);
    }

    public boolean isCorrectFluid(ItemStack itemStack) {
        return tile != null && FluidInventorySlot.fillInsertCheck(tile.fluidTank, itemStack);
    }

}
