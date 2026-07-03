package mekanism.client.jei.machine.other;

import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiNumberGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.recipe.ItemStackToEnergyRecipe;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.util.MekanismUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class ItemStackToEnergyRecipeCategory<WRAPPER extends ItemStackToEnergyRecipeWrapper<ItemStackToEnergyRecipe>> extends BaseRecipeCategory<WRAPPER> {

    public IGuiHelper helper;
    private GuiSlot input;

    public ItemStackToEnergyRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", RecipeHandler.Recipe.ENERGY_RECIPE.getJEICategory(),
                "conversion.mekanism.energy", 20, 12, 132, 62, ProgressType.LARGE_RIGHT);
        this.helper = helper;
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiNumberGauge(new GuiNumberGauge.INumberInfoHandler() {
            @Override
            public TextureAtlasSprite getIcon() {
                return MekanismRenderer.energyIcon;
            }

            @Override
            public double getLevel() {
                return 1D;
            }

            @Override
            public double getScaledLevel() {
                return 1D;
            }

            @Override
            public String getText() {
                return "";
            }
        }, GaugeType.STANDARD, this, 133, 13));
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
        }, progressType, this, 64, 40));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ItemStackToEnergyRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, input, tempRecipe.getInput().ingredient);
    }

    @Override
    public IDrawable getIcon() {
        return createIcon(helper, MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "energy.png"));
    }
}
