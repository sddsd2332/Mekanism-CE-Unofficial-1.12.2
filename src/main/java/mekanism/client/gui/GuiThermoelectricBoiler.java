package mekanism.client.gui;

import mekanism.api.EnumColor;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalRateBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiBoilerTab;
import mekanism.client.gui.element.tab.GuiBoilerTab.BoilerTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerThermoelectricBoiler;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiThermoelectricBoiler extends GuiMekanismTile<TileEntityBoilerCasing, ContainerThermoelectricBoiler> {

    public GuiThermoelectricBoiler(InventoryPlayer inventory, TileEntityBoilerCasing tile) {
        super(tile, new ContainerThermoelectricBoiler(inventory, tile));
        dynamicSlots = true;
        xSize += 42;
        inventoryLabelX += 21;
        inventoryLabelY += 2;
        titleLabelY = 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 54, 23, 110, 40, this::getScreenText).padding(3).spacing(1));
        addButton(new GuiBoilerTab(this, tileEntity, BoilerTab.STAT));
        addButton(new GuiVerticalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.boilRate") + ": " + tileEntity.getLastBoilRate() + " mB/t");
            }

            @Override
            public double getLevel() {
                int maxBoil = tileEntity.getLastMaxBoil();
                return maxBoil <= 0 ? 0 : Math.min(1, tileEntity.getLastBoilRate() / (double) maxBoil);
            }
        }, 44, 13));
        addButton(new GuiVerticalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.maxBoil") + ": " + tileEntity.getLastMaxBoil() + " mB/t");
            }

            @Override
            public double getLevel() {
                int capacity = tileEntity.getBoilCapacity();
                if (capacity <= 0) {
                    return 0;
                }
                return Math.max(0, Math.min(1, tileEntity.getLastMaxBoil() / (double) capacity));
            }
        }, 166, 13));
        addButton(new GuiGasGauge(() -> tileEntity.getInputGasTank(), () -> tileEntity.getGasTanks(null), GaugeType.STANDARD, this, 6, 13)
              .setLabel(MekanismLang.BOILER_HEATED_COOLANT_TANK.translateColored(EnumColor.ORANGE)));
        addButton(new GuiFluidGauge(() -> tileEntity.getWaterTank(), () -> tileEntity.getFluidTanks(null), GaugeType.STANDARD, this, 26, 13)
              .setLabel(MekanismLang.BOILER_WATER_TANK.translateColored(EnumColor.INDIGO)));
        addButton(new GuiFluidGauge(() -> tileEntity.getSteamTank(), () -> tileEntity.getFluidTanks(null), GaugeType.STANDARD, this, 174, 13)
              .setLabel(MekanismLang.BOILER_STEAM_TANK.translateColored(EnumColor.GREY)));
        addButton(new GuiGasGauge(() -> tileEntity.getOutputGasTank(), () -> tileEntity.getGasTanks(null), GaugeType.STANDARD, this, 194, 13)
              .setLabel(MekanismLang.BOILER_COOLANT_TANK.translateColored(EnumColor.AQUA)));
        addButton(new GuiHeatTab(this, () -> Collections.singletonList(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " +
              getEnvironmentLoss() + "/t"))));
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, 109, false));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>();
        text.add(new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemperature()));
        text.add(new TextComponentString(LangUtils.localize("gui.boilRate") + ": " + tileEntity.getLastBoilRate()));
        text.add(new TextComponentString(LangUtils.localize("gui.maxBoil") + ": " + tileEntity.getLastMaxBoil()));
        return text;
    }

    private String getTemperature() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getTemperature(), TemperatureUnit.KELVIN);
    }

    private String getEnvironmentLoss() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getLastEnvironmentLoss(), TemperatureUnit.KELVIN, false);
    }

}
