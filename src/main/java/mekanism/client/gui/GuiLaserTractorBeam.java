package mekanism.client.gui;

import mekanism.common.inventory.container.ContainerLaserTractorBeam;
import mekanism.common.tile.laser.TileEntityLaserTractorBeam;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiLaserTractorBeam extends GuiMekanismTile<TileEntityLaserTractorBeam, ContainerLaserTractorBeam> {

    public GuiLaserTractorBeam(InventoryPlayer inventory, TileEntityLaserTractorBeam tile) {
        super(tile, new ContainerLaserTractorBeam(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
