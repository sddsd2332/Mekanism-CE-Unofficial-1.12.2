package mekanism.generators.common.inventory.container;

import mekanism.generators.common.tile.TileEntityBioGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerBioGenerator extends ContainerFuelGenerator<TileEntityBioGenerator> {

    public ContainerBioGenerator(InventoryPlayer inventory, TileEntityBioGenerator generator) {
        super(inventory, generator);
    }
}
