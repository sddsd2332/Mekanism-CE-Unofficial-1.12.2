package mekanism.client.gui.robit;

import mekanism.client.gui.element.GuiSlot;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitInventory;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRobitInventory extends GuiRobit {

    public GuiRobitInventory(InventoryPlayer inventory, EntityRobit entity) {
        super(entity, new ContainerRobitInventory(inventory, entity));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 7 + x * 18, 17 + y * 18));
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(LangUtils.localize("gui.robit.inventory"), 8, 6, 0x404040);
        fontRenderer.drawString(LangUtils.localize("container.inventory"), 8, ySize - 93, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int id) {
        return id != 2;
    }
}
