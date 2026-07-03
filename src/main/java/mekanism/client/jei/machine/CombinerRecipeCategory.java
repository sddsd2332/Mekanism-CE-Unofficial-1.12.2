package mekanism.client.jei.machine;

import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class CombinerRecipeCategory<RECIPE extends DoubleMachineRecipe<RECIPE>, WRAPPER extends DoubleMachineRecipeWrapper<RECIPE>>
      extends BaseRecipeCategory<WRAPPER> {

    private final GuiSlot input;
    private final GuiSlot extra;
    private final GuiSlot output;

    public CombinerRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
        addElement(new GuiUpArrow(this, 68, 38));
        input = addSlot(SlotType.INPUT, 64, 17);
        extra = addSlot(SlotType.EXTRA, 64, 53);
        output = addSlot(SlotType.OUTPUT, 116, 35);
        addSlot(SlotType.POWER, 39, 35).with(SlotOverlay.POWER);
        addElement(new GuiVerticalPowerBar(this, FULL_BAR, 164, 16));
        addSimpleProgress(ProgressType.BAR, 86, 38);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        DoubleMachineRecipe<?> recipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, recipe.getInput().itemStack);
        initItem(itemStacks, 1, false, output, recipe.getOutput().output);
        initItem(itemStacks, 2, true, extra, recipe.getInput().extraStack);
    }
}
