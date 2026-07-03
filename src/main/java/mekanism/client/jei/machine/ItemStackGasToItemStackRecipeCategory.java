package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.bar.GuiEmptyBar;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

import java.util.Collections;

public class ItemStackGasToItemStackRecipeCategory<RECIPE extends AdvancedMachineRecipe<RECIPE>, WRAPPER extends AdvancedMachineRecipeWrapper<RECIPE>>
      extends BaseRecipeCategory<WRAPPER> {

    private final GuiBar<?> gasInput;
    private final GuiSlot input;
    private final GuiSlot extra;
    private final GuiSlot output;

    public ItemStackGasToItemStackRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
        input = addSlot(SlotType.INPUT, 64, 17);
        extra = addSlot(SlotType.EXTRA, 64, 53);
        output = addSlot(SlotType.OUTPUT, 116, 35);
        addSlot(SlotType.POWER, 39, 35).with(SlotOverlay.POWER);
        addElement(new GuiVerticalPowerBar(this, FULL_BAR, 164, 16));
        gasInput = addElement(new GuiEmptyBar(this, 68, 36, 6, 12));
        addSimpleProgress(ProgressType.BAR, 86, 38);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        AdvancedMachineRecipe<?> recipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, recipe.getInput().itemStack);
        initItem(itemStacks, 1, false, output, recipe.getOutput().output);
        initItem(itemStacks, 2, true, extra, MekanismJEI.GAS_STACK_HELPER.getStacksFor(recipe.getInput().gasType, true));

        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        GasStack scaledGas = new GasStack(recipe.getInput().gasType, TileEntityAdvancedElectricMachine.BASE_TICKS_REQUIRED * TileEntityAdvancedElectricMachine.BASE_GAS_PER_TICK);
        initGas(gasStacks, 0, true, gasInput, Collections.singletonList(scaledGas), false);
    }
}
