package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiEmptyBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

import java.util.Collections;

public class AdvancedMachineRecipeCategory<RECIPE extends AdvancedMachineRecipe<RECIPE>, WRAPPER extends AdvancedMachineRecipeWrapper<RECIPE>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot input;
    private GuiSlot extra;
    private GuiSlot output;
    private GuiEmptyBar gasInput;

    public AdvancedMachineRecipeCategory(IGuiHelper helper, String name, String unlocalized, ProgressType progress) {
        super(helper, DUMMY_GUI_TEXTURE, name, unlocalized, 28, 16, 144, 54, progress);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(new GuiSlot(SlotType.INPUT, this, 63, 16).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 38, 34).setRenderAboveSlots());
        extra = addElement(new GuiSlot(SlotType.EXTRA, this, 63, 52).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT, this, 115, 34).setRenderAboveSlots());
        guiElements.add(new GuiVerticalPowerBar(this, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        }, 164, 16));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }
            @Override
            public boolean isGuiInJei(){
                return true;
            }
        }, progressType, this, 86, 38));
        gasInput = addElement(new GuiEmptyBar(this, 68, 36, 8, 14));
    }


    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        AdvancedMachineRecipe<?> tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.recipeInput.itemStack);
        initItem(itemStacks, 1, false, output, tempRecipe.recipeOutput.output);
        initItem(itemStacks, 2, false, extra, recipeWrapper.getFuelStacks(tempRecipe.recipeInput.gasType));
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput,
              Collections.singletonList(new GasStack(tempRecipe.recipeInput.gasType,
                    TileEntityAdvancedElectricMachine.BASE_TICKS_REQUIRED * TileEntityAdvancedElectricMachine.BASE_GAS_PER_TICK)), false);
    }
}
