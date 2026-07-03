package mekanism.multiblockmachine.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.multiblockmachine.common.inventory.container.ContainerLargeSolarNeutronActivator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiLargeSolarNeutronActivator extends GuiMekanismTile<TileEntityLargeSolarNeutronActivator, ContainerLargeSolarNeutronActivator>
{

    public GuiLargeSolarNeutronActivator(InventoryPlayer inventory, TileEntityLargeSolarNeutronActivator tile) {
        super(tile, new ContainerLargeSolarNeutronActivator(inventory, tile));
        inventoryLabelY += 2;
        titleLabelY = 4;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiGasGauge(this, tileEntity.inputTank, 25, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity::hasWarningNoMatchingRecipe));
        addButton(new GuiGasGauge(this, tileEntity.outputTank, 133, 13)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity::hasWarningNoSpaceInOutput));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.LARGE_RIGHT, this, 64, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.ACTIVATING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity::hasWarningInputDoesntProduceOutput);
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
