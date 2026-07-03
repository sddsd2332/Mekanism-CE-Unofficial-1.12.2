package mekanism.generators.client.gui;

import mekanism.api.IHeatTransfer;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.common.inventory.container.ContainerHeatGenerator;
import mekanism.generators.common.tile.TileEntityHeatGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;

public class GuiHeatGenerator extends GuiGenerator<TileEntityHeatGenerator, ContainerHeatGenerator> {

    public GuiHeatGenerator(InventoryPlayer inventory, TileEntityHeatGenerator tile) {
        super(tile, new ContainerHeatGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.producingEnergy)));
        addButton(new GuiFluidGauge(this, tileEntity.lavaTank, GuiFluidGauge.Type.WIDE, 55, 18));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
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
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }
}
