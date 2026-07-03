package mekanism.generators.common.inventory.container;

import mekanism.generators.common.tile.TileEntityGasGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerGasGenerator extends ContainerFuelGenerator<TileEntityGasGenerator> {

    public ContainerGasGenerator(InventoryPlayer inventory, TileEntityGasGenerator generator) {
        super(inventory, generator);
    }
}
