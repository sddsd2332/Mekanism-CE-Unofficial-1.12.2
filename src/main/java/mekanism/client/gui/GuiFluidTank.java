package mekanism.client.gui;

import mekanism.client.gui.element.GuiContainerEditMode;
import mekanism.client.gui.element.GuiPlayerArmmorSlot;
import mekanism.client.gui.element.GuiPlayerSlot;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.gui.element.GuiSlot.SlotOverlay;
import mekanism.client.gui.element.GuiSlot.SlotType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.common.inventory.container.ContainerFluidTank;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiFluidTank extends GuiMekanismTile<TileEntityFluidTank> {

    public GuiFluidTank(InventoryPlayer inventory, TileEntityFluidTank tile) {
        super(tile, new ContainerFluidTank(inventory, tile));
        ResourceLocation resource = getGuiLocation();
        addGuiElement(new GuiContainerEditMode(this, tileEntity, resource));
        addGuiElement(new GuiSecurityTab(this, tileEntity, resource));
        addGuiElement(new GuiFluidGauge(() -> tileEntity.fluidTank, GuiFluidGauge.Type.WIDE, this, resource, 48, 18));
        addGuiElement(new GuiSlot(SlotType.NORMAL, this, resource, 145, 18).with(SlotOverlay.INPUT));
        addGuiElement(new GuiSlot(SlotType.NORMAL, this, resource, 145, 50).with(SlotOverlay.OUTPUT));
        addGuiElement(new GuiPlayerSlot(this, resource));
        addGuiElement(new GuiPlayerArmmorSlot(this, resource, -26, 62, true));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tileEntity.getName(), (xSize / 2) - (fontRenderer.getStringWidth(tileEntity.getName()) / 2), 4, 0x404040);
        fontRenderer.drawString(LangUtils.localize("container.inventory"), 8, ySize - 96 + 2, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

}
