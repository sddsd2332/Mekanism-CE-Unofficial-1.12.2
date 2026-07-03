package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.machines.AmbientGasRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class AmbientGasCategory<WRAPPER extends AmbientGasRecipeWrapper<AmbientGasRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> output;

    public AmbientGasCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", RecipeHandler.Recipe.AMBIENT_ACCUMULATOR.getJEICategory(),
                "tile.MachineBlock3.AmbientAccumulator.name", 6, 12, 139, 72);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiInnerScreen(this, 7, 13, 80, 65).clearFormat().padding(2).spacing(1).textScale(0.8F));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.ORANGE, 103,18));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        AmbientGasRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, false, output, tempRecipe.recipeOutput.output);
    }
}
