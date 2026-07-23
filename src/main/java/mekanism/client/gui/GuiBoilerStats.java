package mekanism.client.gui;

import mekanism.client.gui.element.GuiGraph;
import mekanism.client.gui.element.tab.GuiBoilerTab;
import mekanism.client.gui.element.tab.GuiBoilerTab.BoilerTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;

@SideOnly(Side.CLIENT)
public class GuiBoilerStats extends GuiMekanismTile<TileEntityBoilerCasing, ContainerNull> {

    private GuiGraph boilGraph;
    private GuiGraph maxGraph;

    public GuiBoilerStats(InventoryPlayer inventory, TileEntityBoilerCasing tile) {
        super(tile, new ContainerNull(inventory.player, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiBoilerTab(this, tileEntity, BoilerTab.MAIN));
        addButton(new GuiHeatTab(this, () -> Collections.singletonList(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " +
              getEnvironmentLoss() + "/t"))));
        boilGraph = addButton(new GuiGraph(this, 7, 82, 162, 38, data -> LangUtils.localize("gui.boilRate") + ": " + data + " mB/t"));
        maxGraph = addButton(new GuiGraph(this, 7, 121, 162, 38, data -> LangUtils.localize("gui.maxBoil") + ": " + data + " mB/t"));
        maxGraph.enableFixedScale(getBoilCapacity());
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, 109, false));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (boilGraph != null) {
            boilGraph.addData(tileEntity.getLastBoilRate());
        }
        if (maxGraph != null) {
            maxGraph.addData(tileEntity.getLastMaxBoil());
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.boilerStats")), 4);
        drawString(new TextComponentString(LangUtils.localize("gui.maxWater") + ": " + tileEntity.clientWaterCapacity + " mB"), 8, 26, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.maxSteam") + ": " + tileEntity.clientSteamCapacity + " mB"), 8, 35, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.heatTransfer")), 8, 49, subheadingTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.superheaters") + ": " + tileEntity.getSuperheatingElements()), 14, 58, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.boilCapacity") + ": " + getBoilCapacity() + " mB/t"), 8, 72, titleTextColor());
        super.drawForegroundText(mouseX, mouseY);
    }

    private String getEnvironmentLoss() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getLastEnvironmentLoss(), TemperatureUnit.KELVIN, false);
    }

    private int getBoilCapacity() {
        return tileEntity.getBoilCapacity();
    }
}
