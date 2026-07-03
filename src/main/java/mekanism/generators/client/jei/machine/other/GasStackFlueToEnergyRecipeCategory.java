package mekanism.generators.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.util.MekanismUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class GasStackFlueToEnergyRecipeCategory<WRAPPER extends GasStackFlueToEnergyRecipeWrapper<GasStackFuelToEnergyRecipe>> extends BaseRecipeCategory<WRAPPER> {

    public IGuiHelper helper;

    public GasStackFlueToEnergyRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", RecipeHandler.Recipe.GAS_FUEL_TO_ENERGY_RECIPE.getJEICategory(),
                "conversion.mekanism.gasflue.base", 20, 12, 132, 62);
        this.helper = helper;
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(dummyGasGauge(GuiGasGauge.Type.WIDE, GuiGasGauge.GaugeColor.RED, 55, 18));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        GasStackFuelToEnergyRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        //因为是每1MB发电,所以数量只设置成1
        GasStack stack = new GasStack(tempRecipe.getInput().ingredient.getGas());
        initGas(gasStacks, 0, true, 56 - xOffset, 19 - yOffset, 64, 48, stack, true);
    }


    @Override
    public IDrawable getIcon() {
        return createIcon(helper, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "gases.png"));
    }
}
