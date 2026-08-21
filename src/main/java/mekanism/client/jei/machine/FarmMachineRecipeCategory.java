package mekanism.client.jei.machine;

import mekanism.api.gas.GasStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.client.gui.element.bar.GuiEmptyBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mekanism.common.util.LangUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

import java.math.BigDecimal;
import java.util.List;

public class FarmMachineRecipeCategory<RECIPE extends FarmMachineRecipe<RECIPE>, WRAPPER extends FarmMachineRecipeWrapper<RECIPE>> extends BaseRecipeCategory<WRAPPER> {

    private static final int INPUT_ITEM_INDEX = 0;
    private static final int GUARANTEED_OUTPUT_INDEX = 1;
    private static final int FIRST_CHANCE_OUTPUT_INDEX = 2;
    private static final int FUEL_ITEM_INDEX = TileEntityFarmMachine.OUTPUT_SLOT_COUNT + 1;
    private static final int OUTPUT_GRID_X = 20;
    private static final int OUTPUT_COLUMNS = 8;
    private static final int RECIPE_WIDTH = OUTPUT_GRID_X + OUTPUT_COLUMNS * 18;
    private static final int RECIPE_HEIGHT = OUTPUT_COLUMNS * 18;

    private GuiSlot input;
    private GuiSlot extra;
    private GuiSlot[] outputSlots;
    private GuiEmptyBar gasInput;

    public FarmMachineRecipeCategory(IGuiHelper helper, String name, String unlocalized, ProgressType progress) {
        super(helper, DUMMY_GUI_TEXTURE, name, unlocalized, 0, 0, RECIPE_WIDTH, RECIPE_HEIGHT, progress);
    }

    @Override
    protected void addGuiElements() {
        // BaseRecipeCategory invokes this method from its constructor, before subclass fields initialize.
        outputSlots = new GuiSlot[TileEntityFarmMachine.OUTPUT_SLOT_COUNT];
        input = addElement(new GuiSlot(SlotType.INPUT, this, 0, 0).setRenderAboveSlots());
        extra = addElement(new GuiSlot(SlotType.EXTRA, this, 0, 36).setRenderAboveSlots());
        gasInput = addElement(new GuiEmptyBar(this, 4, 19, 8, 14));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 0, 60));
        for (int slot = 0; slot < outputSlots.length; slot++) {
            int x = OUTPUT_GRID_X + slot % OUTPUT_COLUMNS * 18;
            int y = slot / OUTPUT_COLUMNS * 18;
            outputSlots[slot] = addElement(new GuiSlot(SlotType.OUTPUT, this, x, y).setRenderAboveSlots());
        }
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        FarmMachineRecipe<?> tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, INPUT_ITEM_INDEX, true, input, tempRecipe.recipeInput.itemStack);
        FarmOutput output = tempRecipe.getOutput();
        initItem(itemStacks, GUARANTEED_OUTPUT_INDEX, false, outputSlots[0], output.getGuaranteedOutput());
        List<FarmChanceOutput> chanceOutputs = output.getChanceOutputs();
        for (int chanceIndex = 0; chanceIndex < chanceOutputs.size(); chanceIndex++) {
            initItem(itemStacks, FIRST_CHANCE_OUTPUT_INDEX + chanceIndex, false, outputSlots[chanceIndex + 1],
                  chanceOutputs.get(chanceIndex).getOutput());
        }
        itemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
            int chanceIndex = slotIndex - FIRST_CHANCE_OUTPUT_INDEX;
            if (!input && chanceIndex >= 0 && chanceIndex < chanceOutputs.size()) {
                double chance = chanceOutputs.get(chanceIndex).getChance();
                if (chance < 1) {
                    tooltip.add(LangUtils.localize("gui.probability2") + ": " + formatChance(chance));
                }
            }
        });
        int amount = recipeWrapper.getSecondaryInputAmount();
        if (tempRecipe.recipeInput.isGasInput()) {
            initItem(itemStacks, FUEL_ITEM_INDEX, false, extra, recipeWrapper.getFuelStacks(tempRecipe.recipeInput.gasInput.getGas()));
            IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
            initGas(gasStacks, 0, true, gasInput,
                  java.util.Collections.singletonList(tempRecipe.recipeInput.gasInput.copy().withAmount(amount)), false);
        } else if (tempRecipe.recipeInput.isFluidInput()) {
            IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
            initFluid(fluidStacks, 0, true, gasInput,
                  new net.minecraftforge.fluids.FluidStack(tempRecipe.recipeInput.fluidInput, amount));
        }
    }

    static String formatChance(double chance) {
        return BigDecimal.valueOf(chance).movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
