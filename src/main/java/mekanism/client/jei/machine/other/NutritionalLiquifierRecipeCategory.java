package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.NutritionalRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class NutritionalLiquifierRecipeCategory<WRAPPER extends NutritionalLiquifierRecipeWrapper<NutritionalRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot input;
    private GuiGauge<?> output;

    public NutritionalLiquifierRecipeCategory(IGuiHelper helper) {
        super(helper, DUMMY_GUI_TEXTURE, Recipe.NUTRITIONAL_LIQUIFIER.getJEICategory(),
                "tile.MachineBlock3.NutritionalLiquifier.name", 20, 12, 132, 62, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.BLUE, 133, 13));
        input = addElement(new GuiSlot(SlotType.INPUT, this, 25, 35).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }
            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 54, 40));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        NutritionalRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.getInput().ingredient);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, false, output, tempRecipe.recipeOutput.output);
    }
}
