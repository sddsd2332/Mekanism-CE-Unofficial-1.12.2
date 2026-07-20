package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.custom.GuiQIOFilterList;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

/** In-place filter editor shared by QIO automation components. */
public class GuiQIOFilterListWindow extends GuiWindow {

    private final TileEntityQIOFilterHandler tile;

    public GuiQIOFilterListWindow(IGuiWrapper gui, TileEntityQIOFilterHandler tile) {
        super(gui, (gui.getWidth() - 168) / 2, 9, 168, 145, SelectedWindowData.UNSPECIFIED);
        this.tile = tile;
        interactionStrategy = InteractionStrategy.NONE;
        addChild(new MekanismButton(gui, relativeX + 7, relativeY + 19, 99, 18,
              new TextComponentTranslation("gui.qio.filter.add"), this::addHeldFilter, null));
        addChild(new MekanismButton(gui, relativeX + 108, relativeY + 19, 53, 18,
              new TextComponentTranslation("gui.qio.filter.clear"), () -> PacketQIOComponentConfig.clearFilter(tile), null));
        addChild(new GuiElementHolder(gui, relativeX + 6, relativeY + 40, 156, 98));
        addChild(new GuiQIOFilterList(gui, relativeX + 7, relativeY + 41, 154, tile));
    }

    private void addHeldFilter() {
        ItemStack held = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (held.isEmpty()) {
            held = tile.getFilterStack();
        }
        QIOFilter filter = QIOFilter.fromItemStack(held);
        if (filter != null) {
            PacketQIOComponentConfig.addFilter(tile, filter);
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation("gui.qio.filters"), 6);
    }
}
