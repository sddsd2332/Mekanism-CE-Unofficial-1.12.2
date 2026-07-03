package mekanism.common.inventory.container;

import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.tile.prefab.TileEntityChanceMachine;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChanceMachine<RECIPE extends ChanceMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityChanceMachine<RECIPE>> {

    public ContainerChanceMachine(InventoryPlayer inventory, TileEntityChanceMachine<RECIPE> tile) {
        super(tile, inventory);
    }

}
