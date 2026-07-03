package mekanism.client.gui.robit;

import mekanism.client.gui.element.GuiRightArrow;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.robit.ContainerRobitCrafting;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRobitCrafting extends GuiRobit<ContainerRobitCrafting> {

    public GuiRobitCrafting(InventoryPlayer inventory, EntityRobit entity) {
        super(new ContainerRobitCrafting(inventory, entity), entity);
        inventoryLabelY += 1;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiRightArrow(this, 90, 35).recipeViewerCrafting());
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("gui.robit.crafting")), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    protected boolean shouldOpenGui(int guiId) {
        return guiId != GUI_CRAFTING;
    }
}
