package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
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
import mekanism.common.recipe.machines.SolarNeutronRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class SolarNeutronRecipeCategory<WRAPPER extends SolarNeutronRecipeWrapper<SolarNeutronRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> input;
    private GuiGauge<?> output;

    public SolarNeutronRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.SOLAR_NEUTRON_ACTIVATOR.getJEICategory(),
                "tile.MachineBlock3.SolarNeutronActivator.name", 3, 12, 170, 62, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 25, 13));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.BLUE, 133, 13));
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 4, 55).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 154, 55).with(SlotOverlay.PLUS).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (float) timer.getValue() / 20F;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 64, 39));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        SolarNeutronRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, input, tempRecipe.recipeInput.ingredient);
        initGas(gasStacks, 1, false, output, tempRecipe.recipeOutput.output);
    }
}
