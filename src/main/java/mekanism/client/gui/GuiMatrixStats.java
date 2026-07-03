package mekanism.client.gui;

import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiVerticalRateBar;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiMatrixTab;
import mekanism.client.gui.element.tab.GuiMatrixTab.MatrixTab;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.tile.multiblock.TileEntityInductionCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiMatrixStats extends GuiMekanismTile<TileEntityInductionCasing, ContainerNull> {

    public GuiMatrixStats(InventoryPlayer inventory, TileEntityInductionCasing tile) {
        super(tile, new ContainerNull(inventory.player, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiMatrixTab(this, tileEntity, MatrixTab.MAIN));
        addButton(new GuiEnergyGauge(this, tileEntity, GuiEnergyGauge.Type.STANDARD, 6, 13));
        addButton(new GuiVerticalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.receiving") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t");
            }

            @Override
            public double getLevel() {
                return getTransferLevel(tileEntity.getLastInput());
            }
        }, 30, 13));
        addButton(new GuiVerticalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.outputting") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t");
            }

            @Override
            public double getLevel() {
                return getTransferLevel(tileEntity.getLastOutput());
            }
        }, 38, 13));
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.matrixStats")), 5);
        drawString(new TextComponentString(LangUtils.localize("gui.input") + ":"), 45, 26, subheadingTextColor());
        drawString(new TextComponentString(MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + getTransferCapText()), 51, 35, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.output") + ":"), 45, 46, subheadingTextColor());
        drawString(new TextComponentString(MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + getTransferCapText()), 51, 55, titleTextColor());
        drawString(new TextComponentString(LangUtils.localize("gui.dimensions") + ":"), 8, 82, subheadingTextColor());
        if (tileEntity.structure != null) {
            drawString(new TextComponentString(tileEntity.structure.volWidth + " x " + tileEntity.structure.volHeight + " x " + tileEntity.structure.volLength), 14, 91, titleTextColor());
        }
        drawString(new TextComponentString(LangUtils.localize("gui.constituents") + ":"), 8, 102, subheadingTextColor());
        drawString(new TextComponentString(tileEntity.getCellCount() + " " + LangUtils.localize("gui.cells")), 14, 111, titleTextColor());
        drawString(new TextComponentString(tileEntity.getProviderCount() + " " + LangUtils.localize("gui.providers")), 14, 120, titleTextColor());
        super.drawForegroundText(mouseX, mouseY);
    }

    private double getTransferLevel(double transfer) {
        double transferCap = tileEntity.getTransferCap();
        if (tileEntity.structure == null || transferCap <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(transfer / transferCap, 1));
    }

    private String getTransferCapText() {
        double transferCap = tileEntity.getTransferCap();
        return tileEntity.structure == null || transferCap <= 0 ? "" : "/" + MekanismUtils.getEnergyDisplay(transferCap);
    }

    private List<ITextComponent> getEnergyTabText() {
        List<ITextComponent> info = new ArrayList<>();
        info.add(new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())));
        info.add(new TextComponentString(LangUtils.localize("gui.input") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t"));
        info.add(new TextComponentString(LangUtils.localize("gui.output") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t"));
        return info;
    }
}
