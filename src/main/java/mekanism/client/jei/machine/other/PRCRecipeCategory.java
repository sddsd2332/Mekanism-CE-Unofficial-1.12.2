package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
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
import mekanism.common.recipe.machines.PressurizedRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class PRCRecipeCategory<WRAPPER extends PRCRecipeWrapper<PressurizedRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot solidInput;
    private GuiSlot itemOutput;
    private GuiGauge<?> fluidInput;
    private GuiGauge<?> gasInput;
    private GuiGauge<?> gasOutput;

    public PRCRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.PRESSURIZED_REACTION_CHAMBER.getJEICategory(),
                "tile.MachineBlock2.PressurizedReactionChamber.short.name", 3, 10, 170, 65, ProgressType.RIGHT);
    }

    @Override
    protected void addGuiElements() {
        solidInput = addElement(new GuiSlot(SlotType.INPUT, this, 53, 39).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 140, 21).with(SlotOverlay.POWER).setRenderAboveSlots());
        itemOutput = addElement(new GuiSlot(SlotType.OUTPUT, this, 115, 39).setRenderAboveSlots());
        fluidInput = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.YELLOW, 5, 15));
        gasInput = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 28, 15));
        gasOutput = addElement(dummyGasGauge(GuiGasGauge.Type.SMALL, GuiGasGauge.GaugeColor.BLUE, 140, 45));
        guiElements.add(new GuiVerticalPowerBar(this, () -> 1F, 163, 21));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 77, 43));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        PressurizedRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, solidInput, tempRecipe.recipeInput.getSolid());
        initItem(itemStacks, 1, false, itemOutput, tempRecipe.recipeOutput.getItemOutput());
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        initFluid(fluidStacks, 0, true, fluidInput, tempRecipe.recipeInput.getFluid());
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput, tempRecipe.recipeInput.getGas());
        initGas(gasStacks, 1, false, gasOutput, tempRecipe.recipeOutput.getGasOutput());
    }
}
