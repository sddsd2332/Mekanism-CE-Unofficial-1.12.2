package mekanism.client.gui;

import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiMatrixTab;
import mekanism.client.gui.element.tab.GuiMatrixTab.MatrixTab;
import mekanism.common.inventory.container.ContainerInductionMatrix;
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
public class GuiInductionMatrix extends GuiMekanismTile<TileEntityInductionCasing, ContainerInductionMatrix> {

    public GuiInductionMatrix(InventoryPlayer inventory, TileEntityInductionCasing tile) {
        super(tile, new ContainerInductionMatrix(inventory, tile));
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        addButton(new GuiSideHolder(this, -26, 36, 98, true, true));
        addButton(new GuiElementHolder(this, 141, 16, 26, 56));
        super.addGuiElements();
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 20));
        addButton(new GuiSlot(SlotType.INNER_HOLDER_SLOT, this, 145, 50));
        addButton(new GuiInnerScreen(this, 49, 21, 84, 46, this::getScreenText).spacing(1));
        addButton(new GuiMatrixTab(this, tileEntity, MatrixTab.STAT));
        addButton(new GuiEnergyGauge(this, tileEntity, GuiEnergyGauge.Type.MEDIUM, 7, 16));
        addButton(new GuiEnergyTab(this, this::getEnergyTabText));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> text = new ArrayList<>();
        text.add(new TextComponentString(LangUtils.localize("gui.energy") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy())));
        text.add(new TextComponentString(LangUtils.localize("gui.capacity") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxEnergy())));
        text.add(new TextComponentString(LangUtils.localize("gui.input") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t"));
        text.add(new TextComponentString(LangUtils.localize("gui.output") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t"));
        return text;
    }

    private List<ITextComponent> getEnergyTabText() {
        List<ITextComponent> info = new ArrayList<>();
        info.add(new TextComponentString(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy())));
        info.add(new TextComponentString(LangUtils.localize("gui.input") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t"));
        info.add(new TextComponentString(LangUtils.localize("gui.output") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t"));
        return info;
    }
}
