package mekanism.multiblockmachine.client.gui.machine;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.multiblockmachine.common.inventory.container.ContainerLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiLargeChemicalWasher extends GuiMekanismTile<TileEntityLargeChemicalWasher, ContainerLargeChemicalWasher> {

    public GuiLargeChemicalWasher(InventoryPlayer inventory, TileEntityLargeChemicalWasher tile) {
        super(tile, new ContainerLargeChemicalWasher(inventory, tile));
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        addButton(GuiSideHolder.create(this, xSize, 66, 57, false, true, SpecialColors.TAB_CHEMICAL_WASHER));
        super.addGuiElements();
        addButton(new GuiDownArrow(this, xSize + 8, 91));
        addButton(new GuiHorizontalPowerBar(this, tileEntity.getMainEnergyContainer(), 115, 75))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE));
        addButton(new GuiEnergyTab(this, tileEntity.getMainEnergyContainer(), () -> tileEntity.clientEnergyUsed));
        addButton(new GuiFluidGauge(() -> tileEntity.fluidTank, () -> tileEntity.getFluidTanks(null), GaugeType.STANDARD, this, 7, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)));
        addButton(new GuiGasGauge(() -> tileEntity.inputTank, () -> tileEntity.getGasTanks(null), GaugeType.STANDARD, this, 28, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        addButton(new GuiGasGauge(() -> tileEntity.outputTank, () -> tileEntity.getGasTanks(null), GaugeType.STANDARD, this, 131, 13)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.LARGE_RIGHT, this, 64, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.WASHING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        super.drawForegroundText(mouseX, mouseY);
    }
}
