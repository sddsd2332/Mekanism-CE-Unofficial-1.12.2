package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/** Compatibility packet for clients using the original QIO slot interaction API. */
public class PacketQIOItemViewerSlotInteract implements IMessageHandler<PacketQIOItemViewerSlotInteract.Message, IMessage> {

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
        PacketHandler.handlePacket(() -> {
            Container container = player.openContainer;
            if (container == null || container.windowId != message.windowId) {
                return;
            }
            if (!container.canInteractWith(player)) {
                return;
            }
            PacketQIOViewerAction.Message action;
            switch (message.type) {
                case TAKE:
                    action = PacketQIOViewerAction.Message.take(message.windowId, message.resource, message.amount);
                    break;
                case SHIFT_TAKE:
                    action = PacketQIOViewerAction.Message.shiftTake(message.windowId, message.resource, message.amount);
                    break;
                case PUT:
                    action = PacketQIOViewerAction.Message.put(message.windowId, message.amount);
                    break;
                default:
                    return;
            }
            PacketQIOViewerAction.handleMessage(action, player);
        }, player);
        return null;
    }

    public enum Type {
        TAKE,
        SHIFT_TAKE,
        PUT
    }

    public static class Message implements IMessage {
        private int windowId;
        private Type type = Type.PUT;
        @Nullable
        private UUID resource;
        private long amount;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, Type type, @Nullable UUID resource, long amount) {
            this.windowId = windowId;
            this.type = type;
            this.resource = resource;
            this.amount = amount;
            valid = isRequestValid(windowId, type, resource, amount);
        }

        public static Message take(int windowId, UUID resource, long amount) {
            return new Message(windowId, Type.TAKE, resource, amount);
        }

        public static Message shiftTake(int windowId, UUID resource, long amount) {
            return new Message(windowId, Type.SHIFT_TAKE, resource, amount);
        }

        public static Message put(int windowId, long amount) {
            return new Message(windowId, Type.PUT, null, amount);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeByte(type.ordinal());
            buffer.writeBoolean(resource != null);
            if (resource != null) {
                PacketHandler.writeUUID(buffer, resource);
            }
            buffer.writeLong(amount);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                int ordinal = buffer.readUnsignedByte();
                if (ordinal < 0 || ordinal >= Type.values().length) {
                    throw new IllegalArgumentException("Unknown QIO viewer slot interaction");
                }
                type = Type.values()[ordinal];
                resource = buffer.readBoolean() ? PacketHandler.readUUID(buffer) : null;
                amount = buffer.readLong();
                valid = isRequestValid(windowId, type, resource, amount);
            } catch (RuntimeException ex) {
                windowId = -1;
                type = Type.PUT;
                resource = null;
                amount = -1;
            }
        }

        private static boolean isRequestValid(int windowId, Type type, @Nullable UUID resource, long amount) {
            return windowId >= 0 && type != null && amount > 0
                  && (type == Type.PUT ? resource == null : resource != null);
        }

        public int getWindowId() {
            return windowId;
        }

        public Type getType() {
            return type;
        }

        @Nullable
        public UUID getResource() {
            return resource;
        }

        public long getAmount() {
            return amount;
        }

        public boolean isValid() {
            return valid;
        }
    }

    /** Source-compatible nested packet name. */
    public static class QIOItemViewerSlotInteractMessage extends Message {
        public QIOItemViewerSlotInteractMessage() {
            super();
        }
    }
}
