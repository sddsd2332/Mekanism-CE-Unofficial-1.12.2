package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerChanceMachine;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.machines.SawmillRecipe;
import mekanism.common.tile.machine.TileEntityPrecisionSawmill;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiPrecisionSawmill extends GuiConfigurableTile<TileEntityPrecisionSawmill, ContainerChanceMachine<SawmillRecipe>> {

    public GuiPrecisionSawmill(InventoryPlayer inventory, TileEntityPrecisionSawmill tile) {
        super(tile, new ContainerChanceMachine<>(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiUpArrow(this, 60, 38));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiSlot(SlotType.OUTPUT_WIDE, this, 111, 30)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityPrecisionSawmill.NOT_ENOUGH_SPACE_SECONDARY_OUTPUT_ERROR)));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.BAR, this, 78, 38))
              .recipeViewerCategories(RecipeViewerRecipeType.SAWING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}