package mekanism.common.inventory.container;

import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityFarmMachine<RECIPE>> {

    public ContainerFarmMachine(InventoryPlayer inventory, TileEntityFarmMachine<RECIPE> tile) {
        super(tile, inventory);
    }

    private boolean isInputItem(ItemStack itemstack) {
        for (AdvancedMachineInput input : tile.getRecipes().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.itemStack, itemstack)) {
                return true;
            }
        }
        return false;
    }


}
