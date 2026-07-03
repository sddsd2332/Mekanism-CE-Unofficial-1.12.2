package mekanism.generators.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.bar.GuiVerticalRateBar;
import mekanism.client.gui.element.button.GuiGasMode;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.element.GuiTurbineTab;
import mekanism.generators.client.gui.element.GuiTurbineTab.TurbineTab;
import mekanism.generators.common.content.turbine.SynchronizedTurbineData;
import mekanism.generators.common.content.turbine.TurbineFluidTank;
import mekanism.generators.common.content.turbine.TurbineUpdateProtocol;
import mekanism.generators.common.inventory.container.ContainerIndustrialTurbine;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuiIndustrialTurbine extends GuiMekanismTile<TileEntityTurbineCasing, ContainerIndustrialTurbine> {

    private final TurbineFluidTank steamTank;

    public GuiIndustrialTurbine(InventoryPlayer inventory, TileEntityTurbineCasing tile) {
        super(tile, new ContainerIndustrialTurbine(inventory, tile));
        steamTank = new TurbineFluidTank(tile);
        xSize += 14;
        inventoryLabelX += 7;
        inventoryLabelY += 2;
        titleLabelY = 5;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 50, 18, 126, 50, this::getScreenText));
        addButton(new GuiTurbineTab(this, tileEntity, TurbineTab.STAT));
        addButton(new GuiVerticalPowerBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                SynchronizedTurbineData data = tileEntity.structure;
                if (data != null && data.isFormed()) {
                    return new TextComponentString(MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy()));
                }
                return new TextComponentString(MekanismUtils.getEnergyDisplay(0));
            }

            @Override
            public double getLevel() {
                SynchronizedTurbineData data = tileEntity.structure;
                if (data == null || !data.isFormed()) {
                    return 1;
                }
                double maxEnergy = tileEntity.getMaxEnergy();
                return maxEnergy == 0 ? 1 : Math.min(1, tileEntity.getEnergy() / maxEnergy);
            }
        }, 178, 16));
        addButton(new GuiVerticalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.steamInput") + ": " + (tileEntity.structure == null ? 0 : tileEntity.structure.lastSteamInput) + " mB/t");
            }

            @Override
            public double getLevel() {
                SynchronizedTurbineData data = tileEntity.structure;
                if (data == null || !data.isFormed()) {
                    return 0;
                }
                double rate = getMaxFlowRate(data);
                return rate == 0 ? 0 : Math.min(1, data.lastSteamInput / rate);
            }
        }, 40, 13));
        addButton(new GuiFluidGauge(() -> steamTank, () -> Collections.singletonList(steamTank), GaugeType.MEDIUM, this, 6, 13));
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
        addButton(new GuiGasMode(this, 173, 72, true, this::getDumpMode, this::sendDumpModePacket, this::getDumpModeTooltip));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText(99);
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        SynchronizedTurbineData data = tileEntity.structure;
        if (data != null && data.isFormed()) {
            list.add(new TextComponentString(LangUtils.localize("gui.production") + ": " + MekanismUtils.getEnergyDisplay(getProductionRate(data))));
            list.add(new TextComponentString(LangUtils.localize("gui.flowRate") + ": " + data.clientFlow + " mB/t"));
            list.add(new TextComponentString(LangUtils.localize("gui.capacity") + ": " + data.getFluidCapacity() + " mB"));
            list.add(new TextComponentString(LangUtils.localize("gui.maxFlow") + ": " + getMaxFlowRate(data) + " mB/t"));
        }
        return list;
    }

    private List<ITextComponent> getEnergyTabText() {
        SynchronizedTurbineData data = tileEntity.structure;
        double storing = data != null && data.isFormed() ? tileEntity.getEnergy() : 0;
        double maxEnergy = data != null && data.isFormed() ? tileEntity.getMaxEnergy() : 0;
        double producing = data != null && data.isFormed() ? getProductionRate(data) : 0;
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(storing, maxEnergy)),
              new TextComponentString(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(producing) + "/t")
        );
    }

    private List<ITextComponent> getDumpModeTooltip() {
        GasMode dumpMode = getDumpMode();
        if (dumpMode == GasMode.IDLE) {
            return Collections.emptyList();
        }
        String warning = " " + LangUtils.localize("fluid.steam");
        return Collections.singletonList(new TextComponentString(LangUtils.localize(dumpMode.getLangKey()) + warning));
    }

    private GasMode getDumpMode() {
        return tileEntity.structure == null ? GasMode.IDLE : tileEntity.structure.dumpMode;
    }

    private void sendDumpModePacket() {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0)));
        SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
    }

    private double getProductionRate(SynchronizedTurbineData data) {
        return data.clientFlow * getEnergyMultiplier(data);
    }

    private double getEnergyMultiplier(SynchronizedTurbineData data) {
        return (MekanismConfig.current().general.maxEnergyPerSteam.val() / TurbineUpdateProtocol.MAX_BLADES) *
              Math.min(data.blades, data.coils * MekanismConfig.current().generators.turbineBladesPerCoil.val());
    }

    private double getMaxFlowRate(SynchronizedTurbineData data) {
        return Math.min(data.lowerVolume * data.clientDispersers * MekanismConfig.current().generators.turbineDisperserGasFlow.val(),
              data.vents * MekanismConfig.current().generators.turbineVentGasFlow.val());
    }
}
