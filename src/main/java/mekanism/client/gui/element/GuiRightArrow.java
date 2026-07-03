package mekanism.client.gui.element;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public class GuiRightArrow extends GuiTextureOnlyElement implements IRecipeViewerRecipeArea<GuiRightArrow> {

    private static final ResourceLocation ARROW = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "right_arrow.png");
    private IRecipeViewerRecipeType<?>[] recipeCategories;

    public GuiRightArrow(IGuiWrapper gui, int x, int y) {
        super(ARROW, gui, x, y, 22, 15);
    }

    @Override
    public GuiRightArrow recipeViewerCategories(IRecipeViewerRecipeType<?>... recipeCategories) {
        this.recipeCategories = recipeCategories;
        return this;
    }

    @Nullable
    @Override
    public IRecipeViewerRecipeType<?>[] getRecipeCategories() {
        return recipeCategories;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return RecipeViewerUtils.openRecipeViewerRecipes(this, mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
}
