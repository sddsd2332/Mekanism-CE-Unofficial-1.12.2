package mekanism.client.jei.machine.other;

import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiEmptyBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class MetallurgicInfuserRecipeCategory<WRAPPER extends MetallurgicInfuserRecipeWrapper<MetallurgicInfuserRecipe>> extends BaseRecipeCategory<WRAPPER> {

    private GuiSlot infusionInput;
    private GuiSlot itemInput;
    private GuiSlot output;

    public MetallurgicInfuserRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", Recipe.METALLURGIC_INFUSER.getJEICategory(),
                "tile.MachineBlock.MetallurgicInfuser.name", 5, 16, 166, 54, ProgressType.RIGHT);
    }

    public static List<ItemStack> getInfuseStacks(InfuseType type) {
        return InfuseRegistry.getObjectMap().entrySet().stream().filter(obj -> obj.getValue().type == type).map(Entry::getKey).collect(Collectors.toList());
    }

    @Override
    protected void addGuiElements() {
        infusionInput = addElement(new GuiSlot(SlotType.EXTRA, this, 16, 34).setRenderAboveSlots());
        itemInput = addElement(new GuiSlot(SlotType.INPUT, this, 50, 42).setRenderAboveSlots());
        guiElements.add(new GuiSlot(SlotType.POWER, this, 142, 34).with(SlotOverlay.POWER).setRenderAboveSlots());
        output = addElement(new GuiSlot(SlotType.OUTPUT, this, 108, 42).setRenderAboveSlots());
        guiElements.add(new GuiVerticalPowerBar(this, new IBarInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        }, 164, 15));
        guiElements.add(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return (double) timer.getValue() / 20F;
            }

            @Override
            public boolean isGuiInJei() {
                return true;
            }
        }, progressType, this, 72, 47));
        guiElements.add(new GuiEmptyBar(this, 7, 15, 4, 52));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        MetallurgicInfuserRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        initItem(itemStacks, 0, true, itemInput, tempRecipe.getInput().inputStack);
        initItem(itemStacks, 1, false, output, tempRecipe.getOutput().output);
        initItem(itemStacks, 2, true, infusionInput, getInfuseStacks(tempRecipe.getInput().infuse.getType()));
    }
}
