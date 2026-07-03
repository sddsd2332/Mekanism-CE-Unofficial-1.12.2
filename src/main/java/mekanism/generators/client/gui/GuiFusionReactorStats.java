package mekanism.generators.client.gui;

import mekanism.api.EnumColor;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.client.gui.element.GuiFusionReactorTab;
import mekanism.generators.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;

import java.text.NumberFormat;

public class GuiFusionReactorStats extends GuiFusionReactorInfo {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    public GuiFusionReactorStats(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(inventory, tile);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.HEAT));
        addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.FUEL));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        if (tileEntity.isFormed()) {
            int indentation = 4;
            int textArea = xSize - indentation;
            drawScrollingString(new TextComponentString(EnumColor.DARK_GREEN + LangUtils.localize("gui.passive")), 0, 26, TextAlignment.LEFT, titleTextColor(), 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.minInject") + ": " + tileEntity.getReactor().getMinInjectionRate(false)),
                  indentation, 36, TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.ignition") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getIgnitionTemperature(false), TemperatureUnit.KELVIN)), indentation, 46,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxPlasma") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getMaxPlasmaTemperature(false), TemperatureUnit.KELVIN)), indentation, 56,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxCasing") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getMaxCasingTemperature(false), TemperatureUnit.KELVIN)), indentation, 66,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.passiveGeneration") + ": " +
                  MekanismUtils.getEnergyDisplay(tileEntity.getReactor().getPassiveGeneration(false, false)) + "/t"), indentation, 76, TextAlignment.LEFT,
                  titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(EnumColor.DARK_BLUE + LangUtils.localize("gui.active")), 0, 92, TextAlignment.LEFT, titleTextColor(), 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.minInject") + ": " + tileEntity.getReactor().getMinInjectionRate(true)),
                  indentation, 102, TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.ignition") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getIgnitionTemperature(true), TemperatureUnit.KELVIN)), indentation, 112,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxPlasma") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getMaxPlasmaTemperature(true), TemperatureUnit.KELVIN)), indentation, 122,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.maxCasing") + ": " +
                  MekanismUtils.getTemperatureDisplay(tileEntity.getReactor().getMaxCasingTemperature(true), TemperatureUnit.KELVIN)), indentation, 132,
                  TextAlignment.LEFT, titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.passiveGeneration") + ": " +
                  MekanismUtils.getEnergyDisplay(tileEntity.getReactor().getPassiveGeneration(true, false)) + "/t"), indentation, 142, TextAlignment.LEFT,
                  titleTextColor(), textArea, 6, false);
            drawScrollingString(new TextComponentString(LangUtils.localize("gui.steamProduction") + ": " +
                  NUMBER_FORMAT.format(tileEntity.getReactor().getSteamPerTick(false)) + "mB/t"), indentation, 152, TextAlignment.LEFT, titleTextColor(), textArea,
                  6, false);
        }
        super.drawForegroundText(mouseX, mouseY);
    }
}
