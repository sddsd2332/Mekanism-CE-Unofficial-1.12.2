package mekanism.client.jei.machine;

import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.machines.RecyclerRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class RecyclerRecipeCategory extends Chance2MachineRecipeCategory<RecyclerRecipe, RecyclerRecipeWrapper> {

    public RecyclerRecipeCategory(IGuiHelper helper) {
        super(helper, RecipeHandler.Recipe.RECYCLER.getJEICategory(), "tile.MachineBlock4.Recycler.name", ProgressType.BAR);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, RecyclerRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 27, 0);
        itemStacks.init(1, false, 83, 14);
        itemStacks.set(ingredients);
    }
}
