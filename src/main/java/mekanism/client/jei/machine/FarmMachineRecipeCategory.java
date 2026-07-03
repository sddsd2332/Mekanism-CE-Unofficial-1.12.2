package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiEmptyBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class FarmMachineRecipeCategory<RECIPE extends FarmMachineRecipe<RECIPE>, WRAPPER extends FarmMachineRecipeWrapper<RECIPE>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot input;
    private GuiSlot extra;
    private GuiSlot output;
    private GuiEmptyBar gasInput;

    public FarmMachineRecipeCategory(IGuiHelper helper, String name, String unlocalized, ProgressType progress) {
        super(helper, "mekanism:gui/Null.png", name, unlocalized, 28, 16, 144, 54, progress);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(new GuiSlot(SlotType.INPUT, this, 55, 16).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 30, 34).with(SlotOverlay.POWER).setRenderAboveSlots());
        extra = addElement(new GuiSlot(SlotType.EXTRA, this, 55, 52).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT_WIDE, this, 111, 30).setRenderAboveSlots());
        guiElements.add(new GuiVerticalPowerBar(this, () -> 1F, 164, 15));
        gasInput = addElement(new GuiEmptyBar(this, 59, 35, 8, 14));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 77, 37));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        FarmMachineRecipe<?> tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.recipeInput.itemStack);
        ChanceOutput output = tempRecipe.getOutput();
        if (output.hasPrimary()) {
            initItem(itemStacks, 1, false, this.output.getRelativeX() + 4, this.output.getRelativeY() + 4, output.primaryOutput);
        }
        if (output.hasSecondary()) {
            initItem(itemStacks, 2, false, this.output.getRelativeX() + 20, this.output.getRelativeY() + 4, output.secondaryOutput);
        }
        initItem(itemStacks, 3, false, extra, recipeWrapper.getFuelStacks(tempRecipe.recipeInput.gasType));
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput,
              java.util.Collections.singletonList(new GasStack(tempRecipe.recipeInput.gasType,
                    TileEntityFarmMachine.BASE_TICKS_REQUIRED * TileEntityFarmMachine.BASE_GAS_PER_TICK)), false);
    }
}
