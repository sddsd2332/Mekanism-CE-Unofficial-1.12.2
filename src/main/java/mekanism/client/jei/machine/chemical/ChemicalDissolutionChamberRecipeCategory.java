package mekanism.client.jei.machine.chemical;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.MekanismFluids;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class ChemicalDissolutionChamberRecipeCategory<WRAPPER extends ChemicalDissolutionChamberRecipeWrapper<DissolutionRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiGauge<?> sulfuricAcid;
    private GuiGauge<?> output;
    private GuiSlot input;

    public ChemicalDissolutionChamberRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png",
              Recipe.CHEMICAL_DISSOLUTION_CHAMBER.getJEICategory(), "gui.chemicalDissolutionChamber.short", 4, 4, 169, 78, ProgressType.LARGE_RIGHT);
    }

    @Override
    protected void addGuiElements() {
        sulfuricAcid = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 7, 4));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.BLUE, 131, 13));
        guiElements.add(new GuiSlot(SlotType.POWER, this, 151, 13).with(SlotOverlay.POWER).setRenderAboveSlots());
        input = addElement(new GuiSlot(SlotType.INPUT, this, 27, 35).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.OUTPUT, this, 151, 54).with(SlotOverlay.PLUS).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.EXTRA, this, 7, 64).with(SlotOverlay.MINUS).setRenderAboveSlots());
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 64, 40));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        DissolutionRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.getInput().ingredient);
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, sulfuricAcid,
                new GasStack(MekanismFluids.SulfuricAcid, TileEntityChemicalDissolutionChamber.BASE_INJECT_USAGE * 100),
                true);
        initGas(gasStacks, 1, false, output, tempRecipe.getOutput().output);
    }
}
