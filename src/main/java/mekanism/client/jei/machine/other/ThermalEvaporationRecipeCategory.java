package mekanism.client.jei.machine.other;

import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.ThermalEvaporationRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ThermalEvaporationRecipeCategory<WRAPPER extends ThermalEvaporationRecipeWrapper<ThermalEvaporationRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> input;
    private GuiGauge<?> output;

    public ThermalEvaporationRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png",
                Recipe.THERMAL_EVAPORATION_PLANT.getJEICategory(), "gui.thermalEvaporationController.short", 3, 12, 190, 62);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.NORMAL, 6, 13));
        output = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.NORMAL, 172, 13));
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 27, 19).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 27, 50).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 151, 19).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 151, 50).setRenderAboveSlots());
        guiElements.add(new GuiInnerScreen(this, 48, 19, 100, 40).spacing(1));
        guiElements.add(new GuiDownArrow(this, 32, 39));
        guiElements.add(new GuiDownArrow(this, 156, 39));
        guiElements.add(new GuiHorizontalRateBar(this, () -> 1F, 58, 62));
    }


    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ThermalEvaporationRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        initFluid(fluidStacks, 0, true, input, tempRecipe.recipeInput.ingredient);
        initFluid(fluidStacks, 1, false, output, tempRecipe.recipeOutput.output);
    }
}
