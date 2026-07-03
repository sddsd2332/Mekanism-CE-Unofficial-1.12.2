package mekanism.multiblockmachine.common.inventory.container;

import mekanism.generators.common.inventory.container.ContainerFuelGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLargeGasGenerator extends ContainerFuelGenerator<TileEntityLargeGasGenerator> {

    public ContainerLargeGasGenerator(InventoryPlayer inventory, TileEntityLargeGasGenerator generator) {
        super(inventory, generator);
    }
}
