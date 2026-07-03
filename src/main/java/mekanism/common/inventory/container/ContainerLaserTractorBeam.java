package mekanism.common.inventory.container;

import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLaserTractorBeam extends MekanismTileContainer<TileEntityLaserTractorBeam> {

    public ContainerLaserTractorBeam(InventoryPlayer inventory, TileEntityLaserTractorBeam tile) {
        super(tile, inventory);
    }

}
