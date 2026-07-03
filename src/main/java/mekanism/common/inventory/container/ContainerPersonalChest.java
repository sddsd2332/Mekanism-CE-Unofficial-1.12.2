package mekanism.common.inventory.container;

import invtweaks.api.container.ChestContainer;
import mekanism.common.tile.TileEntityPersonalChest;
import net.minecraft.entity.player.InventoryPlayer;

@ChestContainer(isLargeChest = true)
public class ContainerPersonalChest extends MekanismTileContainer<TileEntityPersonalChest> {

    public ContainerPersonalChest(InventoryPlayer inventory, TileEntityPersonalChest tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 56;
    }
}
