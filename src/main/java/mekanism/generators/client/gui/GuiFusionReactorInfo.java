package mekanism.generators.client.gui;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.common.Mekanism;
import mekanism.common.base.IGuiProvider;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketSimpleGui;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class GuiFusionReactorInfo extends GuiMekanismTile<TileEntityReactorController, ContainerNull> {

    protected GuiFusionReactorInfo(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        xSize += 10;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new MekanismImageButton(this, 6, 6, 14, getButtonLocation("back"), this::sendBack,
              getOnHover(() -> new TextComponentString(LangUtils.localize("gui.back")))));
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
        addButton(new GuiHeatTab(this, this::getHeatTabText));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), 18, 5, getXSize());
        super.drawForegroundText(mouseX, mouseY);
    }

    protected List<ITextComponent> getEnergyTabText() {
        if (!tileEntity.isFormed()) {
            return new ArrayList<>();
        }
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())),
              new TextComponentString(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getReactor().getPassiveGeneration(false, true)) + "/t")
        );
    }

    protected List<ITextComponent> getHeatTabText() {
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

    protected void sendBack() {
        List<IGuiProvider> handlers = PacketSimpleGui.handlers;
        int handler = handlers.indexOf(MekanismGenerators.proxy);
        Mekanism.packetHandler.sendToServer(new SimpleGuiMessage(Coord4D.get(tileEntity), handler, 10));
    }
}
