package mekanism.client.gui;

import mekanism.common.inventory.container.ContainerPersonalChest;
import mekanism.common.tile.TileEntityPersonalChest;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiPersonalStorageTile extends GuiMekanismTile<TileEntityPersonalChest, ContainerPersonalChest> {

    public GuiPersonalStorageTile(InventoryPlayer inventory, TileEntityPersonalChest tile) {
        super(tile, new ContainerPersonalChest(inventory, tile));
        dynamicSlots = true;
        ySize += 56;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(LangUtils.localize("tile.MachineBlock.PersonalChest.name")), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
