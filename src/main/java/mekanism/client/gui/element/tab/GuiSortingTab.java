package mekanism.client.gui.element.tab;

import mekanism.api.TileNetworkList;
import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.text.TextComponentString;

public class GuiSortingTab extends GuiInsetElement<TileEntityFactory> {

    public GuiSortingTab(IGuiWrapper gui, TileEntityFactory tile) {
        super(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "sorting.png"), gui, tile, -26, 62, 35, 18, true);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_FACTORY_SORT.argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentString(LangUtils.localize("gui.factory.autoSort")), mouseX, mouseY);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        drawScaledScrollingString(new TextComponentString(LangUtils.transOnOff(dataSource.isSorting())), 0, 24, TextAlignment.CENTER, titleTextColor(), width, 3,
              false, 1, getMillis());
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(dataSource, TileNetworkList.withContents(0)));
    }
}
