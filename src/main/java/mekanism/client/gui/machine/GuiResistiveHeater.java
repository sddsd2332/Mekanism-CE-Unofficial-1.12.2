package mekanism.client.gui.machine;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.api.IHeatTransfer;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerResistiveHeater;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.TileEntityResistiveHeater;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiResistiveHeater extends GuiMekanismTile<TileEntityResistiveHeater, ContainerResistiveHeater> {

    private GuiTextField energyUsageField;

    public GuiResistiveHeater(InventoryPlayer inventory, TileEntityResistiveHeater tile) {
        super(tile, new ContainerResistiveHeater(inventory, tile));
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 48, 23, 80, 42, this::getScreenText).clearFormat());
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> {
                  MachineEnergyContainer energyContainer = tileEntity.getEnergyContainer();
                  return energyContainer.isEmpty() && energyContainer.getEnergyPerTick() > 0;
              }).warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, () -> {
                  MachineEnergyContainer energyContainer = tileEntity.getEnergyContainer();
                  return energyContainer.getEnergyPerTick() > energyContainer.getEnergy();
              });
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getEnergyUsed));
        addButton(new GuiHeatTab(this, () -> {
            TemperatureUnit unit = TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
            String temp = UnitDisplayUtils.getDisplayShort(tileEntity.getTemp() + IHeatTransfer.AMBIENT_TEMP, unit);
            String transfer = UnitDisplayUtils.getDisplayShort(tileEntity.lastTransferLoss * unit.intervalSize, false, unit);
            String environment = UnitDisplayUtils.getDisplayShort(tileEntity.lastEnvironmentLoss * unit.intervalSize, false, unit);
            return Arrays.asList(
                  new TextComponentString(LangUtils.localize("gui.temp") + ": " + temp),
                  new TextComponentString(LangUtils.localize("gui.transferred") + ": " + transfer + "/t"),
                  new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t")
            );
        }));
        energyUsageField = addButton(new GuiTextField(this, 0, 50, 51, 76, 12)
              .setMaxLength(7)
              .setInputValidator(this::isDigitOrTextKey)
              .configureDigitalInput(this::setEnergyUsage));
        energyUsageField.setFocused(true);
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private boolean isDigitOrTextKey(char c, int keyCode) {
        return Character.isDigit(c) || GuiMekanism.isTextboxKey(c, keyCode);
    }

    private void setEnergyUsage() {
        if (!energyUsageField.isEmpty()) {
            int toUse = Integer.parseInt(energyUsageField.getText());
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(toUse)));
            energyUsageField.clear();
        }
    }

    private List<ITextComponent> getScreenText() {
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemp()),
              new TextComponentString(LangUtils.localize("gui.usage") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.energyUsage) + "/t")
        );
    }

    private String getTemp() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getTemp(), TemperatureUnit.AMBIENT);
    }
}