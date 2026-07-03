package mekanism.multiblockmachine.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerLargeElectrolyticSeparator extends MekanismTileContainer<TileEntityLargeElectrolyticSeparator> {

    public ContainerLargeElectrolyticSeparator(InventoryPlayer inventory, TileEntityLargeElectrolyticSeparator tile) {
        super(tile, inventory);
    }

    public boolean isCorrectFluid(ItemStack itemStack) {
        return FluidInventorySlot.fillInsertCheck(tile.fluidTank, itemStack);
    }

}
