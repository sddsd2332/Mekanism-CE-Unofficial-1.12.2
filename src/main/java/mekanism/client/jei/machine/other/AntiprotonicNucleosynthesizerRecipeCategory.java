package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.lib.Color;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.util.LangUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class AntiprotonicNucleosynthesizerRecipeCategory<WRAPPER extends AntiprotonicNucleosynthesizerRecipeWrapper<NucleosynthesizerRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> gasInput;
    private GuiSlot input;
    private GuiSlot extra;
    private GuiSlot output;

    public AntiprotonicNucleosynthesizerRecipeCategory(IGuiHelper helper) {
        super(helper, DUMMY_GUI_TEXTURE, Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.getJEICategory(),
                "tile.MachineBlock3.antiprotonicnucleosynthesizer.name", 6, 18, 182, 80);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiInnerScreen(this, 45, 18, 104, 68));
        gasInput = addElement(dummyGasGauge(GuiGasGauge.Type.SMALL_MED, GuiGasGauge.GaugeColor.RED, 5, 18));
        input = addElement(new GuiSlot(SlotType.INPUT, this, 25, 39).setRenderAboveSlots());
        extra = addElement(new GuiSlot(SlotType.EXTRA, this, 5, 68).with(SlotOverlay.PLUS).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT, this, 151, 39).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 172, 68).with(SlotOverlay.POWER).setRenderAboveSlots());
        guiElements.add(new GuiEnergyGauge(new GuiEnergyGauge.IEnergyInfoHandler() {
            @Override
            public double getEnergy() {
                return 1;
            }

            @Override
            public double getMaxEnergy() {
                return 1;
            }
        }, GuiEnergyGauge.Type.SMALL_MED.asGaugeType(), this, 172, 18));
        guiElements.add(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.progress") + ": " + (int) (timer.getValue() / 20F * 100) + "%");
            }

            @Override
            public double getLevel() {
                return timer.getValue() / 20F;
            }
        }, 5, 88, 183, ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        NucleosynthesizerRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.recipeInput.getSolid());
        initItem(itemStacks, 1, false, output, tempRecipe.recipeOutput.output);
        initItem(itemStacks, 2, true, extra, MekanismJEI.GAS_STACK_HELPER.getStacksFor(tempRecipe.recipeInput.getGas().getGas(), true));
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, gasInput, tempRecipe.recipeInput.getGas());
    }
}
