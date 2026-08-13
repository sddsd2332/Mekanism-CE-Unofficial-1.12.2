package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class PacketQIOAutomationTracking implements IMessageHandler<PacketQIOAutomationTracking.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player.openContainer instanceof MekanismTileContainer<?> container) ||
            container.windowId != message.windowId || !container.canInteractWith(player)) {
            return;
        }
        TileEntity tile = message.position.getTileEntity(player.world);
        if (tile == null || tile != container.getTileEntity() ||
            !PacketHandler.canAccessTile(player, tile, true)) {
            return;
        }
        QIOAutomationContainerState state = QIOAutomationContainerState.get(container);
        if (state == null) {
            return;
        }
        if (message.start) {
            container.stopTracking(QIOAutomationContainerState.TRACKING_KEY);
            container.startTrackingServer(QIOAutomationContainerState.TRACKING_KEY, state);
        } else {
            container.stopTracking(QIOAutomationContainerState.TRACKING_KEY);
        }
    }

    public static final class Message implements IMessage {

        private boolean start;
        private int windowId;
        private Coord4D position;
        private boolean valid;

        public Message() {
        }

        public Message(boolean start, int windowId, TileEntity tile) {
            this.start = start;
            this.windowId = windowId;
            position = Coord4D.get(tile);
            valid = windowId >= 0;
        }

        Message(boolean start, int windowId, Coord4D position) {
            this.start = start;
            this.windowId = windowId;
            this.position = position;
            valid = windowId >= 0 && position != null;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(start);
            buffer.writeInt(windowId);
            position.write(buffer);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                start = buffer.readBoolean();
                windowId = buffer.readInt();
                position = Coord4D.read(buffer);
                valid = windowId >= 0 && position != null;
            } catch (RuntimeException e) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }
    }
}
