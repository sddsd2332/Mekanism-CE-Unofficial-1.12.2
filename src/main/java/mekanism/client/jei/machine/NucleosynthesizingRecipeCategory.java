package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.jei.machine.other.AntiprotonicNucleosynthesizerRecipeWrapper;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.lib.Color;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.tile.component.config.DataType;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

import java.util.Collections;

public class NucleosynthesizingRecipeCategory<WRAPPER extends AntiprotonicNucleosynthesizerRecipeWrapper<NucleosynthesizerRecipe>>
      extends BaseRecipeCategory<WRAPPER> {

    private final GuiSlot input;
    private final GuiSlot extra;
    private final GuiSlot output;
    private final GuiGauge<?> gasInput;

    public NucleosynthesizingRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
        input = addSlot(SlotType.INPUT, 26, 40);
        extra = addSlot(SlotType.EXTRA, 6, 69).with(SlotOverlay.PLUS);
        output = addSlot(SlotType.OUTPUT, 152, 40);
        addSlot(SlotType.POWER, 173, 69).with(SlotOverlay.POWER);
        addElement(new GuiInnerScreen(this, 45, 18, 104, 68));
        gasInput = addElement(dummyGasGauge(GuiGasGauge.Type.SMALL_MED, GuiGasGauge.GaugeColor.RED, 5, 18));
        addElement(new GuiEnergyGauge(new GuiEnergyGauge.IEnergyInfoHandler() {
            @Override
            public double getEnergy() {
                return 1;
            }

            @Override
            public double getMaxEnergy() {
                return 1;
            }
        }, GaugeType.SMALL_MED.with(DataType.ENERGY), this, 172, 18));
        addElement(new mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar(this, getBarProgressTimer(), 5, 88, 183,
              ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        NucleosynthesizerRecipe recipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, recipe.getInput().getSolid());
        initItem(itemStacks, 1, false, output, recipe.getOutput().output);
        initItem(itemStacks, 2, true, extra, MekanismJEI.GAS_STACK_HELPER.getStacksFor(recipe.getInput().getGas().getGas(), true));

        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput, Collections.singletonList(recipe.getInput().getGas()), true);
    }
}
