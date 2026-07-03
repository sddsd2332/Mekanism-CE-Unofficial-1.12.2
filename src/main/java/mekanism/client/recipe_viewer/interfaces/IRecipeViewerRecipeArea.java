package mekanism.client.recipe_viewer.interfaces;

import mekanism.client.gui.element.GuiElement;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.cache.IRecipeLookupHandler;

import javax.annotation.Nullable;

public interface IRecipeViewerRecipeArea<ELEMENT extends GuiElement> {

    @Nullable
    IRecipeViewerRecipeType<?>[] getRecipeCategories();

    default boolean isRecipeViewerAreaActive() {
        return true;
    }

    ELEMENT recipeViewerCategories(IRecipeViewerRecipeType<?>... recipeCategories);

    default ELEMENT recipeViewerCategory(IRecipeLookupHandler<?> recipeLookup) {
        IRecipeViewerRecipeType<?> recipeType = recipeLookup.recipeViewerType();
        if (recipeType != null) {
            return recipeViewerCategories(recipeType);
        }
        return (ELEMENT) this;
    }

    default ELEMENT recipeViewerCrafting() {
        return recipeViewerCategories(RecipeViewerRecipeType.VANILLA_CRAFTING);
    }

    default boolean isMouseOverRecipeViewerArea(double mouseX, double mouseY) {
        return ((GuiElement) this).isMouseOver(mouseX, mouseY);
    }
}
