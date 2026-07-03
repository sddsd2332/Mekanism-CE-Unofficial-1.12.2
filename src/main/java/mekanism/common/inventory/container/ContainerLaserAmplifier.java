package mekanism.common.inventory.container;

import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLaserAmplifier extends MekanismTileContainer<TileEntityLaserAmplifier> {

    public ContainerLaserAmplifier(InventoryPlayer inventory, TileEntityLaserAmplifier tile) {
        super(tile, inventory);
    }
}
