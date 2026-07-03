package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mezz.jei.api.IGuiHelper;

public class GasToGasRecipeCategory<WRAPPER extends mezz.jei.api.recipe.IRecipeWrapper> extends mekanism.client.jei.BaseRecipeCategory<WRAPPER> {

    private final mekanism.client.jei.BaseRecipeCategory<WRAPPER> delegate;

    public GasToGasRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType, mekanism.client.jei.BaseRecipeCategory<WRAPPER> delegate) {
        super(helper, recipeType);
        this.delegate = delegate;
    }

    @Override
    public String getUid() {
        return delegate.getUid();
    }

    @Override
    public String getTitle() {
        return delegate.getTitle();
    }

    @Override
    public mezz.jei.api.gui.IDrawable getBackground() {
        return delegate.getBackground();
    }

    @Override
    public mezz.jei.api.gui.IDrawable getIcon() {
        return delegate.getIcon();
    }

    @Override
    public void drawExtras(net.minecraft.client.Minecraft minecraft) {
        delegate.drawExtras(minecraft);
    }

    @Override
    public void setRecipe(mezz.jei.api.gui.IRecipeLayout recipeLayout, WRAPPER recipeWrapper, mezz.jei.api.ingredients.IIngredients ingredients) {
        delegate.setRecipe(recipeLayout, recipeWrapper, ingredients);
    }
}
