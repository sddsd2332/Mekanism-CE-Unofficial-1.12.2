package mekanism.common.inventory.container;

import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.tile.prefab.TileEntityChanceMachine2;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChanceMachine2<RECIPE extends Chance2MachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityChanceMachine2<RECIPE>> {

    public ContainerChanceMachine2(InventoryPlayer inventory, TileEntityChanceMachine2<RECIPE> tile) {
        super(tile, inventory);
    }

}
