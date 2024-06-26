package mekanism.client.jei.machine.chemical;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiProgress;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalInfuserRecipeCategory<WRAPPER extends ChemicalInfuserRecipeWrapper<ChemicalInfuserRecipe>> extends BaseRecipeCategory<WRAPPER> {

    public ChemicalInfuserRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.CHEMICAL_INFUSER.getJEICategory(),
                "tile.MachineBlock2.ChemicalInfuser.name", null, 4, 4, 169, 79);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(GuiGasGauge.getDummy(GuiGauge.Type.STANDARD, this, guiLocation, 25, 13).withColor(GuiGauge.TypeColor.RED));
        guiElements.add(GuiGasGauge.getDummy(GuiGauge.Type.STANDARD, this, guiLocation, 79, 4).withColor(GuiGauge.TypeColor.BLUE));
        guiElements.add(GuiGasGauge.getDummy(GuiGauge.Type.STANDARD, this, guiLocation, 133, 13).withColor(GuiGauge.TypeColor.ORANGE));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.POWER, this, guiLocation, 154, 4).with(GuiSlot.SlotOverlay.POWER));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.EXTRA, this, guiLocation, 154, 55).with(GuiSlot.SlotOverlay.MINUS));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.INPUT, this, guiLocation, 4, 55).with(GuiSlot.SlotOverlay.MINUS));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.OUTPUT, this, guiLocation, 79, 64).with(GuiSlot.SlotOverlay.PLUS));

        guiElements.add(new GuiProgress(new GuiProgress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (float) timer.getValue() / 20F;
            }
        }, GuiProgress.ProgressBar.SMALL_RIGHT, this, guiLocation, 45, 38));
        guiElements.add(new GuiProgress(new GuiProgress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (float) timer.getValue() / 20F;
            }
        }, GuiProgress.ProgressBar.SMALL_LEFT, this, guiLocation, 99, 38));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ChemicalInfuserRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, 26 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getInput().leftGas, true);
        initGas(gasStacks, 1, true, 134 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getInput().rightGas, true);
        initGas(gasStacks, 2, false, 80 - xOffset, 5 - yOffset, 16, 58, tempRecipe.getOutput().output, true);
    }
}
