package mekanism.generators.common.inventory.container;

import mekanism.generators.common.tile.TileEntityWindGenerator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerWindGenerator extends ContainerPassiveGenerator<TileEntityWindGenerator> {

    public ContainerWindGenerator(InventoryPlayer inventory, TileEntityWindGenerator generator) {
        super(inventory, generator);
    }

}
