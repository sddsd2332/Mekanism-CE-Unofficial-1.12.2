package mekanism.client.jei.machine;

import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.common.recipe.machines.Chance2MachineRecipe;
import mekanism.common.recipe.outputs.ChanceOutput2;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;

public class Chance2MachineRecipeCategory<RECIPE extends Chance2MachineRecipe<RECIPE>, WRAPPER extends Chance2MachineRecipeWrapper<RECIPE>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot input;
    private GuiSlot output;

    public Chance2MachineRecipeCategory(IGuiHelper helper, String name, String unlocalized, ProgressType progress) {
        super(helper, DUMMY_GUI_TEXTURE, name, unlocalized, 28, 16, 144, 54, progress);
    }

    @Override
    protected void addGuiElements() {
        input = addElement(new GuiSlot(SlotType.INPUT, this, 55, 16).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 55, 52).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT, this, 111, 30).setRenderAboveSlots());
        guiElements.add(new GuiVerticalPowerBar(this, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        }, 164, 15));
        guiElements.add(new GuiUpArrow(this, 60, 38));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 78, 38));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        Chance2MachineRecipe<?> tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.recipeInput.ingredient);
        ChanceOutput2 outputs = tempRecipe.getOutput();
        if (outputs.hasPrimary()) {
            initItem(itemStacks, 1, false, output, outputs.primaryOutput);
        }
    }
}
