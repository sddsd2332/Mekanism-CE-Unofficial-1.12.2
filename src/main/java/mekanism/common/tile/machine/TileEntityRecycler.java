package mekanism.common.tile.machine;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.RecyclerRecipe;
import mekanism.common.tile.prefab.TileEntityChanceMachine2;
import net.minecraft.item.ItemStack;

import java.util.Map;


public class TileEntityRecycler extends TileEntityChanceMachine2<RecyclerRecipe> {

    public TileEntityRecycler() {
        super("Recycler", MachineType.RECYCLER, 200);
    }

    @Override
    public Map<ItemStackInput, RecyclerRecipe> getRecipes() {
        return Recipe.RECYCLER.get();
    }

    @Override
    public RecyclerRecipe getRecipe() {
        ItemStack stack = inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
        if (stack.isEmpty()) {
            return null;
        }
        RecyclerRecipe registered = super.getRecipe();
        return registered == null ? RecyclerRecipe.temporary(stack.copy()) : registered;
    }

    @Override
    protected RecyclerRecipe getRecipeForInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        RecyclerRecipe registered = super.getRecipeForInput(stack);
        return registered == null ? RecyclerRecipe.temporary(stack.copy()) : registered;
    }
}
