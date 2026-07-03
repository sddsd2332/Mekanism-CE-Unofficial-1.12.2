package mekanism.client.gui.element;

import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;

import javax.annotation.Nullable;

public class GuiRecipeViewerArea extends GuiElement implements IRecipeViewerRecipeArea<GuiRecipeViewerArea> {

    private IRecipeViewerRecipeType<?>[] recipeCategories;

    public GuiRecipeViewerArea(IGuiWrapper gui, int x, int y, int width, int height, IRecipeViewerRecipeType<?>... recipeCategories) {
        super(gui, x, y, width, height, new TextComponentGroup());
        this.recipeCategories = recipeCategories;
        visible = false;
    }

    @Override
    public GuiRecipeViewerArea recipeViewerCategories(IRecipeViewerRecipeType<?>... recipeCategories) {
        this.recipeCategories = recipeCategories;
        return this;
    }

    @Nullable
    @Override
    public IRecipeViewerRecipeType<?>[] getRecipeCategories() {
        return recipeCategories;
    }

    @Override
    public boolean isMouseOverRecipeViewerArea(double mouseX, double mouseY) {
        return active && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return RecipeViewerUtils.openRecipeViewerRecipes(this, mouseX, mouseY, button);
    }
}
