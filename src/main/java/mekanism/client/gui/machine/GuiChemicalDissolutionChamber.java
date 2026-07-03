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
import mekanism.common.inventory.container.ContainerChemicalDissolutionChamber;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiChemicalDissolutionChamber extends GuiConfigurableTile<TileEntityChemicalDissolutionChamber, ContainerChemicalDissolutionChamber> {

    private GuiElement inputGauge;

    public GuiChemicalDissolutionChamber(InventoryPlayer inventory, TileEntityChemicalDissolutionChamber tile) {
        super(tile, new ContainerChemicalDissolutionChamber(inventory, tile));
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiHorizontalPowerBar(this, tileEntity.getEnergyContainer(), 115, 75))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        inputGauge = addButton(new GuiGasGauge(this, tileEntity.injectTank, 7, 4)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT)));
        addButton(new GuiGasGauge(this, tileEntity.outputTank, 131, 13)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.LARGE_RIGHT, this, 64, 40))
              .recipeViewerCategories(RecipeViewerRecipeType.DISSOLUTION)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), inputGauge.getRelativeRight(), titleLabelY, getXSize());
        super.drawForegroundText(mouseX, mouseY);
    }
}