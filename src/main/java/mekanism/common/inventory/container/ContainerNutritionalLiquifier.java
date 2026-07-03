package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityNutritionalLiquifier;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerNutritionalLiquifier extends MekanismTileContainer<TileEntityNutritionalLiquifier> {

    public ContainerNutritionalLiquifier(InventoryPlayer inventory, TileEntityNutritionalLiquifier tile) {
        super(tile, inventory);
    }

}
