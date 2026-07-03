package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerIsotopicCentrifuge;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiIsotopicCentrifuge extends GuiConfigurableTile<TileEntityIsotopicCentrifuge, ContainerIsotopicCentrifuge> {

    private GuiElement energyBar;

    public GuiIsotopicCentrifuge(InventoryPlayer inventory, TileEntityIsotopicCentrifuge tile) {
        super(tile, new ContainerIsotopicCentrifuge(inventory, tile));
        inventoryLabelY += 2;
        titleLabelY = 4;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        energyBar = addButton(new GuiHorizontalPowerBar(this, tileEntity.getEnergyContainer(), 115, 75)
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE)));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getEnergyUsed));
        addButton(new GuiGasGauge(this, tileEntity.inputTank, 25, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        addButton(new GuiGasGauge(this, tileEntity.outputTank, 133, 13)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.LARGE_RIGHT, this, 64, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.CENTRIFUGING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText(energyBar.getRelativeX());
        super.drawForegroundText(mouseX, mouseY);
    }
}