package mekanism.generators.client.jei.machine.other;

import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.FusionCoolingRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class FusionCoolingRecipeCategory<WRAPPER extends FusionCoolingRecipeWrapper<FusionCoolingRecipe>> extends BaseRecipeCategory<WRAPPER> {

    public FusionCoolingRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png",
                Recipe.FUSION_COOLING.getJEICategory(), "gui.FusionCooling", 24, 12, 130, 63, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.RED, 25, 13));
        guiElements.add(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.BLUE, 133, 13));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (float) timer.getValue() / 20F;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 62, 38));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        FusionCoolingRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        fluidStacks.init(0, true, 26 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getInput().ingredient.amount, false, fluidOverlayLarge);
        fluidStacks.init(1, false, 134 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getOutput().output.amount, false, fluidOverlayLarge);
        fluidStacks.set(0, tempRecipe.recipeInput.ingredient);
        fluidStacks.set(1, tempRecipe.recipeOutput.output);
    }
}
