package mekanism.client.gui.filter;

import mekanism.client.gui.button.GuiDisableableButton;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.LangUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public abstract class GuiFilterSelect<TILE extends TileEntityContainerBlock> extends GuiFilter<TILE> {

    protected GuiDisableableButton itemStackButton;
    protected GuiDisableableButton oredictButton;
    protected GuiDisableableButton materialButton;
    protected GuiDisableableButton modIDButton;
    protected GuiDisableableButton backButton;

    protected GuiFilterSelect(EntityPlayer player, TILE tile) {
        super(tile, new ContainerNull(player, tile));
    }

    @Override
    protected void addButtons() {
        buttonList.add(itemStackButton = new GuiDisableableButton(0, guiLeft + 24, guiTop + 32, 128, 20, LangUtils.localize("gui.itemstack")));
        buttonList.add(oredictButton = new GuiDisableableButton(1, guiLeft + 24, guiTop + 52, 128, 20, LangUtils.localize("gui.oredict")));
        buttonList.add(materialButton = new GuiDisableableButton(2, guiLeft + 24, guiTop + 72, 128, 20, LangUtils.localize("gui.material")));
        buttonList.add(modIDButton = new GuiDisableableButton(3, guiLeft + 24, guiTop + 92, 128, 20, LangUtils.localize("gui.modID")));
        buttonList.add(backButton = new GuiDisableableButton(4, guiLeft + 5, guiTop + 5, 11, 11).with(GuiDisableableButton.ImageOverlay.SMALL_BACK));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(LangUtils.localize("gui.filterSelect.title"), 43, 6, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) throws IOException {
        super.actionPerformed(guibutton);
        if (guibutton.id == itemStackButton.id) {
            sendPacketToServer(1);
        } else if (guibutton.id == oredictButton.id) {
            sendPacketToServer(2);
        } else if (guibutton.id == materialButton.id) {
            sendPacketToServer(3);
        } else if (guibutton.id == backButton.id) {
            sendPacketToServer(0);
        }
    }
}
