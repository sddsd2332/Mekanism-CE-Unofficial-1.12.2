package mekanism.client.gui.filter;

import mekanism.api.Coord4D;
import mekanism.client.gui.button.GuiColorButton;
import mekanism.client.gui.button.GuiDisableableButton;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiPlayerSlot;
import mekanism.client.gui.element.GuiSlot;
import mekanism.common.Mekanism;
import mekanism.common.content.transporter.TMaterialFilter;
import mekanism.common.network.PacketLogisticalSorterGui.LogisticalSorterGuiMessage;
import mekanism.common.network.PacketLogisticalSorterGui.SorterGuiPacket;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public class GuiTMaterialFilter extends GuiMaterialFilter<TMaterialFilter, TileEntityLogisticalSorter> {

    public GuiTMaterialFilter(EntityPlayer player, TileEntityLogisticalSorter tile, int index) {
        super(player, tile);
        origFilter = (TMaterialFilter) tileEntity.filters.get(index);
        filter = ((TMaterialFilter) tileEntity.filters.get(index)).clone();
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 11, 18));
        addGuiElement(new GuiInnerScreen(this, getGuiLocation(), 33, 18, 111, 43));
        addGuiElement(new GuiPlayerSlot(this, getGuiLocation()));
    }

    public GuiTMaterialFilter(EntityPlayer player, TileEntityLogisticalSorter tile) {
        super(player, tile);
        isNew = true;
        filter = new TMaterialFilter();
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 11, 18));
        addGuiElement(new GuiInnerScreen(this, getGuiLocation(), 33, 18, 111, 43));
        addGuiElement(new GuiPlayerSlot(this, getGuiLocation()));
    }

    @Override
    protected void addButtons() {
        buttonList.add(saveButton = new GuiDisableableButton(0, guiLeft + 47, guiTop + 62, 60, 20, LangUtils.localize("gui.save")));
        buttonList.add(deleteButton = new GuiDisableableButton(1, guiLeft + 109, guiTop + 62, 60, 20, LangUtils.localize("gui.delete")));
        buttonList.add(backButton = new GuiDisableableButton(2, guiLeft + 5, guiTop + 5, 11, 11).with(GuiDisableableButton.ImageOverlay.SMALL_BACK));
        buttonList.add(defaultButton = new GuiDisableableButton(3, guiLeft + 11, guiTop + 64, 11, 11).with(GuiDisableableButton.ImageOverlay.DEFAULT));
        buttonList.add(colorButton = new GuiColorButton(4, guiLeft + 12, guiTop + 44, () -> filter.color));
    }

    @Override
    protected void sendPacketToServer(int guiID) {
        Mekanism.packetHandler.sendToServer(new LogisticalSorterGuiMessage(SorterGuiPacket.SERVER, Coord4D.get(tileEntity), guiID, 0, 0));
    }

    @Override
    protected void drawForegroundLayer(int mouseX, int mouseY) {
        if (!filter.getMaterialItem().isEmpty()) {
            renderScaledText(filter.getMaterialItem().getDisplayName(), 35, 41, 0x00CD00, 107);
        }
        drawTransporterForegroundLayer(mouseX, mouseY, filter.getMaterialItem());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && overTypeInput(mouseX - guiLeft, mouseY - guiTop)) {
            materialMouseClicked();
        } else {
            transporterMouseClicked(button, filter);
        }
    }

}
