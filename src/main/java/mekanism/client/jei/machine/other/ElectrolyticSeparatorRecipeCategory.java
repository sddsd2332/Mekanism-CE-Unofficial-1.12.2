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
import mekanism.common.recipe.machines.SeparatorRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;

public class ElectrolyticSeparatorRecipeCategory<WRAPPER extends ElectrolyticSeparatorRecipeWrapper<SeparatorRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> fluidInput;
    private GuiGauge<?> leftOutput;
    private GuiGauge<?> rightOutput;

    public ElectrolyticSeparatorRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.ELECTROLYTIC_SEPARATOR.getJEICategory(),
                "tile.MachineBlock2.ElectrolyticSeparator.name", 4, 9, 167, 62, ProgressType.BI);
    }

    @Override
    protected void addGuiElements() {
        fluidInput = addElement(dummyFluidGauge(GuiFluidGauge.Type.STANDARD, GuiFluidGauge.GaugeColor.RED, 5, 10));
        leftOutput = addElement(dummyGasGauge(GuiGasGauge.Type.SMALL, GuiGasGauge.GaugeColor.BLUE, 58, 18));
        rightOutput = addElement(dummyGasGauge(GuiGasGauge.Type.SMALL, GuiGasGauge.GaugeColor.AQUA, 100, 18));
        guiElements.add(new GuiVerticalPowerBar(this, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        }, 164, 15));
        guiElements.add(new GuiSlot(SlotType.INPUT, this, 25, 34).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 58, 51).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT_2, this, 100, 51).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 142, 34).with(SlotOverlay.POWER).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return 1;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 80, 30));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        SeparatorRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        initFluid(fluidStacks, 0, true, fluidInput, ingredients.getInputs(VanillaTypes.FLUID).get(0), true);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, false, leftOutput, tempRecipe.recipeOutput.leftGas);
        initGas(gasStacks, 1, false, rightOutput, tempRecipe.recipeOutput.rightGas);
    }
}
