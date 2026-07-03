package mekanism.client.jei.machine.chemical;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge.GaugeColor;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.OxidationRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalOxidizerRecipeCategory<WRAPPER extends ChemicalOxidizerRecipeWrapper<OxidationRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot input;
    private GuiGauge<?> output;

    public ChemicalOxidizerRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.CHEMICAL_OXIDIZER.getJEICategory(),
              "tile.MachineBlock2.ChemicalOxidizer.name", 20, 12, 132, 62, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GaugeColor.BLUE, 131, 13));
        input = addElement(new GuiSlot(SlotType.INPUT, this, 25, 35).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 64, 40));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        OxidationRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.getInput().ingredient);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, false, output, tempRecipe.recipeOutput.output);
    }
}
