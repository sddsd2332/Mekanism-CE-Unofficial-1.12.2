package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiPowerBar;
import mekanism.client.gui.element.GuiPowerBar.IPowerInfoHandler;
import mekanism.client.gui.element.GuiProgress;
import mekanism.client.gui.element.GuiProgress.IProgressInfoHandler;
import mekanism.client.gui.element.GuiProgress.ProgressBar;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.gui.element.GuiSlot.SlotOverlay;
import mekanism.client.gui.element.GuiSlot.SlotType;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.client.Minecraft;

public class AdvancedMachineRecipeCategory<RECIPE extends AdvancedMachineRecipe<RECIPE>, WRAPPER extends AdvancedMachineRecipeWrapper<RECIPE>> extends BaseRecipeCategory<WRAPPER> {

    public AdvancedMachineRecipeCategory(IGuiHelper helper, String name, String unlocalized, ProgressBar progress) {
        super(helper, "mekanism:gui/Null.png", name, unlocalized, progress, 28, 16, 144, 54);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiSlot(SlotType.INPUT, this, guiLocation, 55, 16));
        guiElements.add(new GuiSlot(SlotType.POWER, this, guiLocation, 30, 34).with(SlotOverlay.POWER));
        guiElements.add(new GuiSlot(SlotType.EXTRA, this, guiLocation, 55, 52));
        guiElements.add(new GuiSlot(SlotType.OUTPUT_LARGE, this, guiLocation, 111, 30));
        guiElements.add(new GuiPowerBar(this, new IPowerInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        }, guiLocation, 164, 15));
        guiElements.add(new GuiProgress(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }
        }, progressBar, this, guiLocation, 77, 37));
        guiElements.add(new GuiBar(this, guiLocation, 60, 36, 8, 14));
    }

    @Override
    public void drawExtras(Minecraft minecraft) {
        super.drawExtras(minecraft);
        drawTexturedRect(61 - xOffset, 37 - yOffset, 0, 167, 6, 12);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        AdvancedMachineRecipe<?> tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 27, 0);
        itemStacks.init(1, false, 87, 18);
        itemStacks.init(2, false, 27, 36);
        itemStacks.set(0, tempRecipe.recipeInput.itemStack);
        itemStacks.set(1, tempRecipe.recipeOutput.output);
        itemStacks.set(2, recipeWrapper.getFuelStacks(tempRecipe.recipeInput.gasType));
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, 33, 21, 6, 12, new GasStack(tempRecipe.recipeInput.gasType, TileEntityAdvancedElectricMachine.BASE_TICKS_REQUIRED
                * TileEntityAdvancedElectricMachine.BASE_GAS_PER_TICK), false);
    }
}