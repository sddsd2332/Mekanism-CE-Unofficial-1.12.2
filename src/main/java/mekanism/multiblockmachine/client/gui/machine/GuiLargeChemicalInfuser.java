package mekanism.multiblockmachine.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.multiblockmachine.common.inventory.container.ContainerLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiLargeChemicalInfuser extends GuiMekanismTile<TileEntityLargeChemicalInfuser, ContainerLargeChemicalInfuser> {

    private GuiElement centerGauge;

    public GuiLargeChemicalInfuser(InventoryPlayer inventory, TileEntityLargeChemicalInfuser tile) {
        super(tile, new ContainerLargeChemicalInfuser(inventory, tile));
        inventoryLabelY += 2;
        titleLabelY = 5;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiHorizontalPowerBar(this, tileEntity.getMainEnergyContainer(), 115, 75))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE));
        addButton(new GuiEnergyTab(this, tileEntity.getMainEnergyContainer(), () -> tileEntity.clientEnergyUsed));
        addButton(new GuiGasGauge(this, tileEntity.leftTank, 25, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_LEFT_INPUT)));
        centerGauge = addButton(new GuiGasGauge(this, tileEntity.centerTank, 79, 4)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        addButton(new GuiGasGauge(this, tileEntity.rightTank, 133, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_RIGHT_INPUT)));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.SMALL_RIGHT, this, 47, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.CHEMICAL_INFUSING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.SMALL_LEFT, this, 101, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.CHEMICAL_INFUSING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), 1, titleLabelY, centerGauge.getRelativeX(), 4, TextAlignment.LEFT);
        renderInventoryText(centerGauge.getRelativeX());
        super.drawForegroundText(mouseX, mouseY);
    }
}
