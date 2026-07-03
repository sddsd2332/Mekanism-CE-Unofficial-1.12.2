package mekanism.client.gui.element;

import mekanism.api.Coord4D;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketGuiInteract.GuiInteractMessage;
import mekanism.common.network.PacketGuiInteract.GuiInteraction;
import mekanism.common.tile.interfaces.IHasDumpButton;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.tileentity.TileEntity;

public class GuiDumpButton<TILE extends TileEntity & IHasDumpButton> extends GuiTexturedElement {

    private final TILE tile;

    public GuiDumpButton(IGuiWrapper gui, TILE tile, int x, int y) {
        super(MekanismUtils.getResource(ResourceType.GUI, "dump.png"), gui, x, y, 21, 10);
        this.tile = tile;
        this.playClickSound = true;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(getResource());
        GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, width, height);
        MekanismRenderer.resetColor();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        Mekanism.packetHandler.sendToServer(new GuiInteractMessage(GuiInteraction.DUMP_BUTTON, Coord4D.get(tile), 0));
    }
}
