package mekanism.common.inventory.container;

import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerDoubleElectricMachine<RECIPE extends DoubleMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityDoubleElectricMachine<RECIPE>> {

    public ContainerDoubleElectricMachine(InventoryPlayer inventory, TileEntityDoubleElectricMachine<RECIPE> tile) {
        super(tile, inventory);
    }

    private boolean isInputItem(ItemStack itemstack) {
        for (DoubleMachineInput input : tile.getRecipes().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.itemStack, itemstack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExtraItem(ItemStack itemstack) {
        for (DoubleMachineInput input : tile.getRecipes().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.extraStack, itemstack)) {
                return true;
            }
        }
        return false;
    }

}
