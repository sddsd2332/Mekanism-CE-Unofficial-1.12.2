package mekanism.generators.common.inventory.container;

import mekanism.generators.common.tile.TileEntitySolarGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSolarGenerator extends ContainerPassiveGenerator<TileEntitySolarGenerator> {

    public ContainerSolarGenerator(InventoryPlayer inventory, TileEntitySolarGenerator generator) {
        super(inventory, generator);
    }

}
