package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.element.GuiTurbineTab;
import mekanism.generators.client.gui.element.GuiTurbineTab.TurbineTab;
import mekanism.generators.common.content.turbine.SynchronizedTurbineData;
import mekanism.generators.common.content.turbine.TurbineUpdateProtocol;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;

public class GuiTurbineStats extends GuiMekanismTile<TileEntityTurbineCasing, ContainerNull> {

    public GuiTurbineStats(InventoryPlayer inventory, TileEntityTurbineCasing tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        xSize += 14;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiTurbineTab(this, tileEntity, TurbineTab.MAIN));
        addButton(new GuiEnergyTab(this, () -> {
            SynchronizedTurbineData data = tileEntity.structure;
            double storing = data != null && data.isFormed() ? tileEntity.getEnergy() : 0;
            double maxEnergy = data != null && data.isFormed() ? tileEntity.getMaxEnergy() : 0;
            double producing = data != null && data.isFormed() ? getProductionRate(data) : 0;
            return Arrays.asList(
                  new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(storing, maxEnergy)),
                  new TextComponentString(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(producing) + "/t")
            );
        }));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.turbineStats")), 6);
        SynchronizedTurbineData data = tileEntity.structure;
        if (data != null && data.isFormed()) {
            ITextComponent limiting = new TextComponentString(EnumColor.DARK_RED + " (" + LangUtils.localize("gui.limiting") + ")");
            int lowerVolume = data.lowerVolume;
            int dispersers = data.clientDispersers;
            int vents = data.vents;
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.tankVolume") + ": " + lowerVolume), 0, 26, TextAlignment.LEFT, titleTextColor(), 6, false);
            boolean dispersersLimiting = lowerVolume * dispersers * MekanismConfig.current().generators.turbineDisperserGasFlow.val()
                  < vents * MekanismConfig.current().generators.turbineVentGasFlow.val();
            boolean ventsLimiting = lowerVolume * dispersers * MekanismConfig.current().generators.turbineDisperserGasFlow.val()
                  > vents * MekanismConfig.current().generators.turbineVentGasFlow.val();
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.steamFlow")), 0, 40, TextAlignment.LEFT, subheadingTextColor(), 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.dispersers") + ": " + dispersers + (dispersersLimiting ? limiting.getFormattedText() : "")),
                  4, 49, TextAlignment.LEFT, titleTextColor(), xSize - 4, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.vents") + ": " + vents + (ventsLimiting ? limiting.getFormattedText() : "")),
                  4, 58, TextAlignment.LEFT, titleTextColor(), xSize - 4, 6, false);
            int coils = data.coils;
            int blades = data.blades;
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.production")), 0, 72, TextAlignment.LEFT, subheadingTextColor(), 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.blades") + ": " + blades + (coils * 4 > blades ? limiting.getFormattedText() : "")),
                  4, 81, TextAlignment.LEFT, titleTextColor(), xSize - 4, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.coils") + ": " + coils + (coils * 4 < blades ? limiting.getFormattedText() : "")),
                  4, 90, TextAlignment.LEFT, titleTextColor(), xSize - 4, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxProduction") + ": " +
                  MekanismUtils.getEnergyDisplay(getMaxFlowRate(data) * getEnergyMultiplier(data))), 0, 104, TextAlignment.LEFT, titleTextColor(), 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxWaterOutput") + ": " +
                  data.condensers * MekanismConfig.current().generators.condenserRate.val() + " mB/t"), 0, 113, TextAlignment.LEFT, titleTextColor(), 6, false);
        }
        super.drawForegroundText(mouseX, mouseY);
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
