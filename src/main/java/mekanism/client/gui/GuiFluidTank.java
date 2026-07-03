package mekanism.client.gui;

import mekanism.client.gui.element.GuiSideHolder;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiContainerEditModeTab;
import mekanism.common.inventory.container.ContainerFluidTank;
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiFluidTank extends GuiMekanismTile<TileEntityFluidTank, ContainerFluidTank> {

    public GuiFluidTank(InventoryPlayer inventory, TileEntityFluidTank tile) {
        super(tile, new ContainerFluidTank(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        addButton(GuiSideHolder.armorHolder(this));
        super.addGuiElements();
        addButton(new GuiContainerEditModeTab<>(this, tileEntity));
        addButton(new GuiFluidGauge(this, tileEntity.fluidTank, GuiFluidGauge.Type.WIDE, 48, 18));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
