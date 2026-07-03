package mekanism.client.jei.machine.chemical;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiInnerScreen;
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
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalCrystallizerRecipeCategory<WRAPPER extends ChemicalCrystallizerRecipeWrapper<CrystallizerRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> input;
    private GuiSlot output;

    public ChemicalCrystallizerRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.CHEMICAL_CRYSTALLIZER.getJEICategory(),
              "tile.MachineBlock2.ChemicalCrystallizer.name", 5, 3, 147, 79, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 5, 4));
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 5, 64).with(SlotOverlay.PLUS).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT, this, 130, 56).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.ORE, this, 128, 13).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 53, 61));
        guiElements.add(new GuiInnerScreen(this, 31, 13, 115, 42).clearFormat().padding(2).spacing(1));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        CrystallizerRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, false, output, tempRecipe.getOutput().output);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, input, tempRecipe.getInput().ingredient);
    }
}
