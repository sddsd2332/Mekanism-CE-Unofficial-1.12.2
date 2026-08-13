package mekanism.client.gui.element.window;

import mekanism.client.MekanismClient;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.custom.GuiFrequencySelector;
import mekanism.client.gui.element.custom.GuiFrequencySelector.IGuiFrequencySelector;
import mekanism.client.gui.element.custom.GuiFrequencySelector.IGuiColorFrequencySelector;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Shared in-place frequency window for every QIO component. */
public class GuiQIOFrequencySelectWindow extends GuiWindow {

    public static final int WIDTH = 176;
    /** Matches the 26.2 QIO frequency screen's 176x155 image bounds. */
    public static final int HEIGHT = 155;

    private final ITextComponent title;

    public GuiQIOFrequencySelectWindow(IGuiWrapper gui, IGuiFrequencySelector<QIOFrequency> selector,
          SelectedWindowData windowData) {
        this(gui, selector, MekanismLang.QIO_FREQUENCY_SELECT.translate(), windowData);
    }

    public GuiQIOFrequencySelectWindow(IGuiWrapper gui, IGuiFrequencySelector<QIOFrequency> selector,
          ITextComponent title, SelectedWindowData windowData) {
        super(gui, (gui.getWidth() - WIDTH) / 2, (gui.getHeight() - HEIGHT) / 2, WIDTH, HEIGHT, windowData);
        this.title = title;
        // Frequency selection is an independent QIO child window. Allow
        // clicks to reach other open child windows so they can be focused
        // and brought to the front without closing this selector.
        interactionStrategy = InteractionStrategy.ALL;
        // Keep the selector's coordinates identical to the 26.2 standalone
        // screen so this window can be reused without a second layout.
        addChild(new GuiFrequencySelector<>(gui, selector, relativeX + 27, relativeY + 20));
    }

    public static GuiQIOFrequencySelectWindow forTile(IGuiWrapper gui, TileEntityQIOComponent tile,
          SelectedWindowData windowData) {
        return forTile(gui, tile, windowData, MekanismLang.QIO_FREQUENCY_SELECT.translate());
    }

    /**
     * Creates the standard tile selector with a module-specific window title.
     * The selector and packet behavior remain the native QIO component behavior.
     */
    public static GuiQIOFrequencySelectWindow forTile(IGuiWrapper gui, TileEntityQIOComponent tile,
          SelectedWindowData windowData, ITextComponent title) {
        return new GuiQIOFrequencySelectWindow(gui, new TileSelector(tile), title, windowData);
    }

    public static GuiQIOFrequencySelectWindow forItem(IGuiWrapper gui, PortableQIODashboardContainer container,
          SelectedWindowData windowData) {
        return new GuiQIOFrequencySelectWindow(gui, new ItemSelector(container), windowData);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(title, 6);
    }

    private static class TileSelector implements IGuiColorFrequencySelector<QIOFrequency> {

        private final TileEntityQIOComponent tile;

        private TileSelector(TileEntityQIOComponent tile) {
            this.tile = tile;
        }

        @Override
        public void sendSetFrequency(FrequencyIdentity identity) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(true, FrequencyType.QIO, identity, tile));
        }

        @Override
        public void sendRemoveFrequency(FrequencyIdentity identity) {
            Mekanism.packetHandler.sendToServer(new SetTileFrequencyMessage(false, FrequencyType.QIO, identity, tile));
        }

        @Nullable
        @Override
        public QIOFrequency getFrequency() {
            return tile.getQIOFrequency();
        }

        @Override
        public List<QIOFrequency> getPublicFrequencies() {
            return tile.getPublicCache(FrequencyType.QIO);
        }

        @Override
        public List<QIOFrequency> getTrustedFrequencies() {
            return tile.getTrustedCache(FrequencyType.QIO);
        }

        @Override
        public List<QIOFrequency> getPrivateFrequencies() {
            return tile.getPrivateCache(FrequencyType.QIO);
        }

        @Nullable
        @Override
        public UUID getOwnerUUID() {
            return tile.getSecurity().getOwnerUUID();
        }

        @Override
        public String getSelfOwnerName() {
            String owner = tile.getSecurity().getClientOwner();
            return owner == null ? "" : owner;
        }
    }

    private static class ItemSelector implements IGuiColorFrequencySelector<QIOFrequency> {

        private final PortableQIODashboardContainer container;

        private ItemSelector(PortableQIODashboardContainer container) {
            this.container = container;
        }

        @Override
        public void sendSetFrequency(FrequencyIdentity identity) {
            Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(container.windowId, true, FrequencyType.QIO, identity, container.getHand()));
        }

        @Override
        public void sendRemoveFrequency(FrequencyIdentity identity) {
            Mekanism.packetHandler.sendToServer(new SetItemFrequencyMessage(container.windowId, false, FrequencyType.QIO, identity, container.getHand()));
        }

        @Nullable
        @Override
        public QIOFrequency getFrequency() {
            return container.getFrequency();
        }

        @Override
        public List<QIOFrequency> getPublicFrequencies() {
            return container.getPublicFrequencyCache();
        }

        @Override
        public List<QIOFrequency> getTrustedFrequencies() {
            return container.getTrustedFrequencyCache();
        }

        @Override
        public List<QIOFrequency> getPrivateFrequencies() {
            return container.getPrivateFrequencyCache();
        }

        @Nullable
        @Override
        public UUID getOwnerUUID() {
            ItemStack stack = container.getStack();
            return stack.isEmpty() || !(stack.getItem() instanceof ItemPortableQIODashboard) ? null :
                  ((ItemPortableQIODashboard) stack.getItem()).getOwnerUUID(stack);
        }

        @Override
        public String getSelfOwnerName() {
            UUID owner = getOwnerUUID();
            if (owner == null) {
                return "";
            }
            String name = MekanismClient.clientUUIDMap.get(owner);
            if (name == null && GuiFrequencySelector.minecraft.player != null && owner.equals(GuiFrequencySelector.minecraft.player.getUniqueID())) {
                name = GuiFrequencySelector.minecraft.player.getName();
            }
            return name == null ? "" : name;
        }

        @Override
        public boolean isPortable() {
            return true;
        }
    }
}
