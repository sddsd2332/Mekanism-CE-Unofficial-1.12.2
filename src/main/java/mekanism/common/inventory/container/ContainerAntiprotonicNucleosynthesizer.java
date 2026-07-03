package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerAntiprotonicNucleosynthesizer extends MekanismTileContainer<TileEntityAntiprotonicNucleosynthesizer> {

    public ContainerAntiprotonicNucleosynthesizer(InventoryPlayer inventory, TileEntityAntiprotonicNucleosynthesizer tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 10;
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 27;
    }
}
