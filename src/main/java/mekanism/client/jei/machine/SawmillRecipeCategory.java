package mekanism.client.jei.machine;

import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class SawmillRecipeCategory<RECIPE extends ChanceMachineRecipe<RECIPE>, WRAPPER extends ChanceMachineRecipeWrapper<RECIPE>>
      extends BaseRecipeCategory<WRAPPER> {

    private final GuiSlot input;
    private final GuiSlot output;

    public SawmillRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
        addElement(new GuiUpArrow(this, 60, 38));
        input = addSlot(SlotType.INPUT, 56, 17);
        addSlot(SlotType.POWER, 56, 53).with(SlotOverlay.POWER);
        output = addSlot(SlotType.OUTPUT_WIDE, 112, 31);
        addElement(new GuiVerticalPowerBar(this, FULL_BAR, 164, 15));
        addSimpleProgress(ProgressType.BAR, 78, 38);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ChanceMachineRecipe<?> recipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, recipe.getInput().ingredient);
        ChanceOutput outputDefinition = recipe.getOutput();
        if (outputDefinition.hasPrimary()) {
            initItem(itemStacks, 1, false, output.getRelativeX() + 4, output.getRelativeY() + 4, outputDefinition.primaryOutput);
        }
        if (outputDefinition.hasSecondary()) {
            initItem(itemStacks, 2, false, output.getRelativeX() + 20, output.getRelativeY() + 4, outputDefinition.secondaryOutput);
        }
    }
}
