package mekanism.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.progress.GuiFlame;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.inventory.container.ContainerFuelwoodHeater;
import mekanism.common.tile.TileEntityFuelwoodHeater;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiFuelwoodHeater extends GuiMekanismTile<TileEntityFuelwoodHeater, ContainerFuelwoodHeater> {

    public GuiFuelwoodHeater(InventoryPlayer inventory, TileEntityFuelwoodHeater tile) {
        super(tile, new ContainerFuelwoodHeater(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 48, 23, 80, 28, this::getScreenText));
        addButton(new GuiFlame(new mekanism.client.gui.element.progress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return tileEntity.maxBurnTime == 0 ? 0 : tileEntity.burnTime / (double) tileEntity.maxBurnTime;
            }

            @Override
            public boolean isActive() {
                return tileEntity.burnTime > 0;
            }
        }, this, 144, 31));
        addButton(new GuiHeatTab(this, () -> {
            String temp = MekanismUtils.getTemperatureDisplay(tileEntity.getTemp(), TemperatureUnit.KELVIN);
            String transfer = MekanismUtils.getTemperatureDisplay(tileEntity.lastTransferLoss, TemperatureUnit.KELVIN, false);
            String environment = MekanismUtils.getTemperatureDisplay(tileEntity.lastEnvironmentLoss, TemperatureUnit.KELVIN, false);
            return Arrays.asList(
                  new TextComponentString(LangUtils.localize("gui.temp") + ": " + temp),
                  new TextComponentString(LangUtils.localize("gui.transferred") + ": " + transfer + "/t"),
                  new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t")
            );
        }));
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemp()),
              new TextComponentString(LangUtils.localize("gui.fuel") + ": " + tileEntity.burnTime)
        );
    }

    private String getTemp() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getTemp(), TemperatureUnit.KELVIN);
    }
}
