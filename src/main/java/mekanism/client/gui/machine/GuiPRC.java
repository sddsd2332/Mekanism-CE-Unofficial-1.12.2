package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerPRC;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityPRC;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiPRC extends GuiConfigurableTile<TileEntityPRC, ContainerPRC> {

    public GuiPRC(InventoryPlayer inventory, TileEntityPRC tile) {
        super(tile, new ContainerPRC(inventory, tile));
        dynamicSlots = true;
        ySize += 5;
        inventoryLabelY += 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiFluidGauge(this, tileEntity.inputFluidTank, GuiFluidGauge.Type.STANDARD, 5, 15)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(TileEntityPRC.NOT_ENOUGH_FLUID_INPUT_ERROR)));
        addButton(new GuiGasGauge(this, tileEntity.inputGasTank, 28, 15)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(TileEntityPRC.NOT_ENOUGH_GAS_INPUT_ERROR)));
        addButton(new GuiGasGauge(this, tileEntity.outputGasTank, GuiGasGauge.Type.SMALL, 140, 45)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityPRC.NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 163, 21))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.RIGHT, this, 77, 43))
              .recipeViewerCategories(RecipeViewerRecipeType.REACTION)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}