package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityFormulaicAssemblicator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFormulaicAssemblicator extends MekanismTileContainer<TileEntityFormulaicAssemblicator> {

    public ContainerFormulaicAssemblicator(InventoryPlayer inventory, TileEntityFormulaicAssemblicator tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 148;
    }
}
