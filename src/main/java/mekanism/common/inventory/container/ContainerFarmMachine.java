package mekanism.common.inventory.container;

import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>> extends MekanismTileContainer<TileEntityFarmMachine<RECIPE>> {

    public ContainerFarmMachine(InventoryPlayer inventory, TileEntityFarmMachine<RECIPE> tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return 30;
    }

    @Override
    protected int getInventoryYOffset() {
        return 174;
    }

    private boolean isInputItem(ItemStack itemstack) {
        for (FarmInput input : tile.getRecipes().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.itemStack, itemstack)) {
                return true;
            }
        }
        return false;
    }


}
