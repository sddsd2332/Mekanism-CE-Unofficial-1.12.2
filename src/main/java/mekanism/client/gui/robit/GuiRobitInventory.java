package mekanism.client.gui.robit;

import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitInventory;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRobitInventory extends GuiRobit<ContainerRobitInventory> {

    public GuiRobitInventory(InventoryPlayer inventory, EntityRobit entity) {
        super(new ContainerRobitInventory(inventory, entity), entity);
        inventoryLabelY = ySize - 93;
        dynamicSlots = true;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.robit.inventory")), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int guiId) {
        return guiId != GUI_INVENTORY;
    }
}
