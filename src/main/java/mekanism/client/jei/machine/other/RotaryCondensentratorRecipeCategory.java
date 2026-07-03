package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;

public class RotaryCondensentratorRecipeCategory extends BaseRecipeCategory<RotaryCondensentratorRecipeWrapper> {

    private final boolean condensentrating;
    private GuiGauge<?> gasGauge;
    private GuiGauge<?> fluidGauge;

    public RotaryCondensentratorRecipeCategory(IGuiHelper helper, boolean condensentrating) {
        super(helper, "mekanism:gui/Null.png",
                condensentrating ? "mekanism.rotary_condensentrator_condensentrating" : "mekanism.rotary_condensentrator_decondensentrating",
                condensentrating ? "gui.condensentrating" : "gui.decondensentrating", 3, 12, 170, 64);
        this.condensentrating = condensentrating;
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiDownArrow(this, 159, 44));
        gasGauge = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 25, 13));
        fluidGauge = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.RED, 133, 13));
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 4, 24).with(SlotOverlay.PLUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 4, 55).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 154, 24).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 154, 55).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new IProgressInfoHandler.IBooleanProgressInfoHandler() {
            @Override
            public boolean fillProgressBar() {
                return true;
            }
            @Override
            public boolean isActive() {
                return condensentrating;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }

        }, ProgressType.LARGE_RIGHT , this, 64, 39));
        guiElements.add(new GuiProgress(new IProgressInfoHandler.IBooleanProgressInfoHandler() {
            @Override
            public boolean fillProgressBar() {
                return true;
            }
            @Override
            public boolean isActive() {
                return !condensentrating;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, ProgressType.LARGE_LEFT , this, 64, 39));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, RotaryCondensentratorRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        if (condensentrating) {
            initGas(gasStacks, 0, true, gasGauge, new GasStack(recipeWrapper.getGasType(), RotaryCondensentratorRecipeWrapper.GAS_AMOUNT));
            initFluid(fluidStacks, 0, false, fluidGauge, ingredients.getOutputs(VanillaTypes.FLUID).get(0), true);
        } else {
            initGas(gasStacks, 0, false, gasGauge, new GasStack(recipeWrapper.getGasType(), RotaryCondensentratorRecipeWrapper.GAS_AMOUNT));
            initFluid(fluidStacks, 0, true, fluidGauge, ingredients.getInputs(VanillaTypes.FLUID).get(0), true);
        }
    }
}
