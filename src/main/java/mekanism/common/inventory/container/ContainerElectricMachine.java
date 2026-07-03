package mekanism.common.inventory.container;

import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerElectricMachine<RECIPE extends BasicMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityElectricMachine<RECIPE>> {

    public ContainerElectricMachine(InventoryPlayer inventory, TileEntityElectricMachine<RECIPE> tile) {
        super(tile, inventory);
    }

}
