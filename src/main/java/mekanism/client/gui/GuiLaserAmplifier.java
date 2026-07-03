package mekanism.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.tab.GuiAmplifierTab;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerLaserAmplifier;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.laser.TileEntityLaserAmplifier;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiLaserAmplifier extends GuiMekanismTile<TileEntityLaserAmplifier, ContainerLaserAmplifier> {

    private GuiTextField minField;
    private GuiTextField maxField;
    private GuiTextField timerField;
    private GuiEnergyGauge energyGauge;

    public GuiLaserAmplifier(InventoryPlayer inventory, TileEntityLaserAmplifier tile) {
        super(tile, new ContainerLaserAmplifier(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        energyGauge = addButton(new GuiEnergyGauge(this, tileEntity.getEnergyContainer(), GuiEnergyGauge.Type.STANDARD, 6, 10));
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
        addButton(new GuiAmplifierTab(this, tileEntity));
        timerField = addButton(new GuiTextField(this, 0, 96, 28, 36, 11)
              .setMaxLength(4)
              .setInputValidator(this::isTimerInput)
              .setEnterHandler(this::setTime));
        minField = addButton(new GuiTextField(this, 1, 96, 43, 72, 11)
              .setMaxLength(10)
              .setInputValidator(this::isEnergyInput)
              .setEnterHandler(this::setMinThreshold));
        maxField = addButton(new GuiTextField(this, 2, 96, 58, 72, 11)
              .setMaxLength(10)
              .setInputValidator(this::isEnergyInput)
              .setEnterHandler(this::setMaxThreshold));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        int start = energyGauge == null ? 26 : energyGauge.getRelativeX() + energyGauge.getWidth();
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), start, 4, getXSize());
        renderInventoryText();
        ITextComponent delay = new TextComponentString(tileEntity.getDelay() > 0 ? LangUtils.localize("gui.delay") + ": " + tileEntity.getDelay() + "t" : LangUtils.localize("gui.noDelay"));
        drawScaledScrollingString(delay, start, 30, TextAlignment.LEFT, titleTextColor(), timerField.getRelativeX() - start, 2, false, 1, GuiElement.getMillis());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.min") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMinThreshold())),
              start, 45, TextAlignment.LEFT, titleTextColor(), minField.getRelativeX() - start, 2, false, 1, GuiElement.getMillis());
        drawScaledScrollingString(new TextComponentString(LangUtils.localize("gui.max") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxThreshold())),
              start, 60, TextAlignment.LEFT, titleTextColor(), maxField.getRelativeX() - start, 2, false, 1, GuiElement.getMillis());
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getEnergyTabText() {
        return Collections.singletonList(new TextComponentString(LangUtils.localize("gui.storing") + ": " +
              MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())));
    }

    private boolean isTimerInput(char c, int keyCode) {
        return Character.isDigit(c) || GuiMekanism.isTextboxKey(c, keyCode);
    }

    private boolean isEnergyInput(char c, int keyCode) {
        return Character.isDigit(c) || c == '.' || c == 'E' || GuiMekanism.isTextboxKey(c, keyCode);
    }

    private void setMinThreshold() {
        if (!minField.isEmpty()) {
            double toUse;
            try {
                toUse = Math.max(0, Double.parseDouble(minField.getText()));
            } catch (NumberFormatException e) {
                minField.clear();
                return;
            }
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0, toUse)));
            minField.clear();
        }
    }

    private void setMaxThreshold() {
        if (!maxField.isEmpty()) {
            double toUse;
            try {
                toUse = Math.max(0, Double.parseDouble(maxField.getText()));
            } catch (NumberFormatException e) {
                maxField.clear();
                return;
            }
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(1, toUse)));
            maxField.clear();
        }
    }

    private void setTime() {
        if (!timerField.isEmpty()) {
            int toUse;
            try {
                toUse = Math.max(0, Integer.parseInt(timerField.getText()));
            } catch (NumberFormatException e) {
                timerField.clear();
                return;
            }
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(2, toUse)));
            timerField.clear();
        }
    }
}
