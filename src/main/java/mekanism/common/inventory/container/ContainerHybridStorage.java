package mekanism.common.inventory.container;


import ic2.api.item.IElectricItem;
import mekanism.common.tile.TileEntityHybridStorage;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public class ContainerHybridStorage extends MekanismTileContainer<TileEntityHybridStorage> {

    public ContainerHybridStorage(InventoryPlayer inventory, TileEntityHybridStorage tile) {
        super(tile, inventory);
    }


    @Override
    protected int getInventoryYOffset() {
        return 201;
    }

    @Override
    protected int getInventoryXOffset() {
        return 62;
    }

    private boolean canTransfer(ItemStack slotStack) {
        return MekanismUtils.useIC2() && slotStack.getItem() instanceof IElectricItem;
    }
}
