package mekanism.common.inventory.container;

import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.InfusionInput;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ContainerMetallurgicInfuser extends MekanismTileContainer<TileEntityMetallurgicInfuser> {

    public ContainerMetallurgicInfuser(InventoryPlayer inventory, TileEntityMetallurgicInfuser tile) {
        super(tile, inventory);
    }

    public boolean isInputItem(ItemStack itemStack) {
        if (tile.infuseStored.getType() != null) {
            return RecipeHandler.getMetallurgicInfuserRecipe(new InfusionInput(tile.infuseStored, itemStack)) != null;
        }
        for (InfusionInput input : Recipe.METALLURGIC_INFUSER.get().keySet()) {
            if (ItemHandlerHelper.canItemStacksStack(input.inputStack, itemStack)) {
                return true;
            }
        }
        return false;
    }

}
