package mekanism.client.gui.robit;

import mekanism.client.gui.element.GuiProgress;
import mekanism.client.gui.element.GuiSlot;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitCrafting;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRobitCrafting extends GuiRobit {

    public GuiRobitCrafting(InventoryPlayer inventory, EntityRobit entity) {
        super(entity, new ContainerRobitCrafting(inventory, entity));
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL, this, getGuiLocation(), 29 + x * 18, 16 + y * 18));
            }
        }
        addGuiElement(new GuiSlot(GuiSlot.SlotType.NORMAL_LARGE, this, getGuiLocation(), 119, 30));
        addGuiElement(new GuiProgress(new GuiProgress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return 0F;
            }
        }, GuiProgress.ProgressBar.TALL_RIGHT, this, getGuiLocation(), 90, 35));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(LangUtils.localize("gui.robit.crafting"), 8, 6, 0x404040);
        fontRenderer.drawString(LangUtils.localize("container.inventory"), 8, ySize - 93, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int id) {
        return id != 1;
    }
}
