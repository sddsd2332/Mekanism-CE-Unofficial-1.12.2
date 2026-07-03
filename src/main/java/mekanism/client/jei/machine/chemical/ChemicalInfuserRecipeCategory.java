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
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalInfuserRecipeCategory<WRAPPER extends ChemicalInfuserRecipeWrapper<ChemicalInfuserRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> leftInput;
    private GuiGauge<?> output;
    private GuiGauge<?> rightInput;

    public ChemicalInfuserRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.CHEMICAL_INFUSER.getJEICategory(),
                "tile.MachineBlock2.ChemicalInfuser.name", 4, 4, 169, 79);
    }

    @Override
    protected void addGuiElements() {
        leftInput = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GaugeColor.RED, 25, 13));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GaugeColor.BLUE, 79, 4));
        rightInput = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GaugeColor.ORANGE, 133, 13));
        guiElements.add(new GuiSlot(SlotType.POWER, this, 154, 4).with(SlotOverlay.POWER).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.EXTRA, this, 154, 55).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 4, 55).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 79, 64).with(SlotOverlay.PLUS).setRenderAboveSlots());

        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, ProgressType.SMALL_RIGHT, this, 47, 39));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, ProgressType.SMALL_LEFT, this, 101, 39));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ChemicalInfuserRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, leftInput, tempRecipe.getInput().leftGas);
        initGas(gasStacks, 1, true, rightInput, tempRecipe.getInput().rightGas);
        initGas(gasStacks, 2, false, output, tempRecipe.getOutput().output);
    }
}
