package mekanism.client.jei.machine.chemical;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiFluidGauge.GaugeColor;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.WasherRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalWasherRecipeCategory<WRAPPER extends ChemicalWasherRecipeWrapper<WasherRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> fluidInput;
    private GuiGauge<?> gasInput;
    private GuiGauge<?> output;

    public ChemicalWasherRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.CHEMICAL_WASHER.getJEICategory(),
              "tile.MachineBlock2.ChemicalWasher.name", 4, 4, 169, 69, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        fluidInput = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GaugeColor.NORMAL, 7, 13));
        gasInput = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 28, 13));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.BLUE, 133, 13));
        guiElements.add(new GuiSlot(SlotType.POWER, this, 154, 13).with(SlotOverlay.POWER).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 154, 55).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 64, 39));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        WasherRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        initFluid(fluidStacks, 0, true, fluidInput, tempRecipe.recipeInput.ingredientFluid);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput, tempRecipe.getInput().ingredientGas);
        initGas(gasStacks, 1, false, output, tempRecipe.getOutput().output);
    }
}
