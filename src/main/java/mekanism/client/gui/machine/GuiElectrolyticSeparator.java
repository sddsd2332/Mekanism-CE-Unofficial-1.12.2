package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.GuiGasMode;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerElectrolyticSeparator;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiElectrolyticSeparator extends GuiConfigurableTile<TileEntityElectrolyticSeparator, ContainerElectrolyticSeparator> {

    private GuiElement fluidGauge;

    public GuiElectrolyticSeparator(InventoryPlayer inventory, TileEntityElectrolyticSeparator tile) {
        super(tile, new ContainerElectrolyticSeparator(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getEnergyUsed));
        fluidGauge = addButton(new GuiFluidGauge(this, tileEntity.fluidTank, GuiFluidGauge.Type.STANDARD, 5, 10)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        addButton(new GuiGasGauge(this, tileEntity.leftTank, GuiGasGauge.Type.SMALL, 58, 18)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityElectrolyticSeparator.NOT_ENOUGH_SPACE_LEFT_OUTPUT_ERROR)));
        addButton(new GuiGasGauge(this, tileEntity.rightTank, GuiGasGauge.Type.SMALL, 100, 18)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityElectrolyticSeparator.NOT_ENOUGH_SPACE_RIGHT_OUTPUT_ERROR)));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE));
        addButton(new GuiProgress(tileEntity::getActive, ProgressType.BI, this, 80, 30))
              .recipeViewerCategories(RecipeViewerRecipeType.SEPARATING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiGasMode(this, 7, 72, false, () -> tileEntity.dumpLeft, () -> sendModePacket((byte) 0)));
        addButton(new GuiGasMode(this, 159, 72, true, () -> tileEntity.dumpRight, () -> sendModePacket((byte) 1)));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), fluidGauge.getRelativeRight(), 4, getXSize());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void sendModePacket(byte tank) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(tank)));
        SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
    }

}