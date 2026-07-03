package mekanism.generators.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;

public abstract class ContainerPassiveGenerator<TILE extends TileEntityElectricBlock> extends MekanismTileContainer<TILE> {

    protected ContainerPassiveGenerator(InventoryPlayer inventory, TILE tile) {
        super(tile, inventory);
    }

    @Override
    protected void addInventorySlots(@Nonnull InventoryPlayer inv) {
        super.addInventorySlots(inv);
        addArmorSlots(inv, -20, 11, 0);
    }
}
