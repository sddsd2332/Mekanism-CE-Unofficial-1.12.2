package mekanism.generators.common.inventory.container;

import mekanism.generators.common.tile.TileEntityHeatGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerHeatGenerator extends ContainerFuelGenerator<TileEntityHeatGenerator> {

    public ContainerHeatGenerator(InventoryPlayer inventory, TileEntityHeatGenerator generator) {
        super(inventory, generator);
    }
}
