package mekanism.common.inventory.container;

import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerAdvancedElectricMachine<RECIPE extends AdvancedMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityAdvancedElectricMachine<RECIPE>> {

    public ContainerAdvancedElectricMachine(InventoryPlayer inventory, TileEntityAdvancedElectricMachine<RECIPE> tile) {
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
