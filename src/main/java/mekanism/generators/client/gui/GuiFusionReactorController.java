package mekanism.generators.client.gui;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.client.gui.element.GuiFusionReactorTab;
import mekanism.generators.client.gui.element.GuiFusionReactorTab.FusionReactorTab;
import mekanism.generators.common.inventory.container.ContainerReactorController;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuiFusionReactorController extends GuiMekanismTile<TileEntityReactorController, ContainerReactorController> {

    public GuiFusionReactorController(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(tile, new ContainerReactorController(inventory, tile));
        dynamicSlots = true;
        xSize += 10;
        inventoryLabelX += 5;
        inventoryLabelY += 2;
        titleLabelY = 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        if (tileEntity.isFormed()) {
            addButton(new GuiEnergyTab(this, this::getEnergyTabText));
            addButton(new GuiHeatTab(this, this::getHeatTabText));
            addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.HEAT));
            addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.FUEL));
            addButton(new GuiFusionReactorTab(this, tileEntity, FusionReactorTab.STAT));
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        renderInventoryText();
        drawScrollingString(new TextComponentString(LangUtils.localize(tileEntity.isFormed() ? "gui.formed" : "gui.incomplete")), 0, 16, TextAlignment.LEFT,
              titleTextColor(), 13, false);
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getEnergyTabText() {
        if (!tileEntity.isFormed()) {
            return new ArrayList<>();
        }
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())),
              new TextComponentString(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getReactor().getPassiveGeneration(false, true)) + "/t")
        );
    }

    private List<ITextComponent> getHeatTabText() {
        if (!tileEntity.isFormed()) {
            return new ArrayList<>();
        }
        TemperatureUnit unit = TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
        String transfer = UnitDisplayUtils.getDisplayShort(tileEntity.getReactor().lastTransferLoss * unit.intervalSize, false, unit);
        String environment = UnitDisplayUtils.getDisplayShort(tileEntity.getReactor().lastEnvironmentLoss * unit.intervalSize, false, unit);
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.transferred") + ": " + transfer + "/t"),
              new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t")
        );
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }
}
