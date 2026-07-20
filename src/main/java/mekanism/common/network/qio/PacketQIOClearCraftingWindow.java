package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

/** Server-authoritative clear operation for a selected QIO crafting window. */
public class PacketQIOClearCraftingWindow implements IMessageHandler<PacketQIOClearCraftingWindow.Message, IMessage> {

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.isValid()) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        Container open = player.openContainer;
        if (open == null || open.windowId != message.windowId || !(open instanceof IQIOItemViewerContainer)) {
            return;
        }
        IQIOItemViewerContainer container = (IQIOItemViewerContainer) open;
        if (open instanceof net.minecraft.inventory.Container && !((net.minecraft.inventory.Container) open).canInteractWith(player)) {
            return;
        }
        byte selected = container.getSelectedCraftingGrid(player.getUniqueID());
        if (selected != message.window || message.window < 0 || message.window >= IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS) {
            return;
        }
        QIOCraftingWindow window = container.getCraftingWindow(message.window);
        if (window == null) {
            return;
        }
        window.emptyTo(message.toPlayerInventory, message.toPlayerInventory ? player : null);
        container.sendViewerSync(player);
    }

    public static class Message implements IMessage {

        private int windowId;
        private byte window;
        private boolean toPlayerInventory;
        private boolean valid;

        public Message() {
        }

        public Message(int windowId, byte window, boolean toPlayerInventory) {
            this.windowId = windowId;
            this.window = window;
            this.toPlayerInventory = toPlayerInventory;
            valid = isRequestValid(windowId, window);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeByte(window);
            buffer.writeBoolean(toPlayerInventory);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                window = buffer.readByte();
                toPlayerInventory = buffer.readBoolean();
                valid = isRequestValid(windowId, window);
            } catch (RuntimeException ex) {
                windowId = -1;
                window = -1;
                toPlayerInventory = false;
            }
        }

        private static boolean isRequestValid(int windowId, byte window) {
            return windowId >= 0 && window >= 0 && window < IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS;
        }

        public int getWindowId() {
            return windowId;
        }

        public byte getWindow() {
            return window;
        }

        public boolean isToPlayerInventory() {
            return toPlayerInventory;
        }

        public boolean isValid() {
            return valid;
        }
    }

    /** Source-compatible name used by the original 1.12 QIO port. */
    public static class QIOClearCraftingWindowMessage extends Message {
        public QIOClearCraftingWindowMessage() {
            super();
        }

        public QIOClearCraftingWindowMessage(int windowId, byte window, boolean toPlayerInventory) {
            super(windowId, window, toPlayerInventory);
        }
    }
}
