package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Legacy viewer sync packet retained for older QIO GUI integrations. */
public class PacketQIOItemViewerGuiSync implements IMessageHandler<PacketQIOItemViewerGuiSync.Message, IMessage> {

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.openContainer instanceof QIOItemViewerContainer) {
                QIOItemViewerContainer container = (QIOItemViewerContainer) player.openContainer;
                if (!message.valid || container.windowId != message.windowId) {
                    return;
                }
                if (message.mode == Mode.KILL) {
                    container.applyKill();
                } else if (message.mode == Mode.BATCH) {
                    container.applyBatch(message.entries, message.countCapacity, message.typeCapacity);
                } else {
                    container.applyUpdate(message.entries, message.countCapacity, message.typeCapacity);
                }
            }
        }, player);
        return null;
    }

    public static void sendBatch(EntityPlayerMP player, @Nullable QIOFrequency frequency) {
        if (player == null) {
            return;
        }
        if (frequency == null) {
            PacketQIOViewerData.sendBatch(player, PacketQIOViewerData.getViewerWindowId(player), Collections.emptyList(), 0, 0);
        } else {
            PacketQIOViewerData.sendBatch(player, PacketQIOViewerData.getViewerWindowId(player), frequency.getResourceEntries(),
                  frequency.getTotalCountCapacity(), frequency.getTotalTypeCapacity());
        }
    }

    private enum Mode {
        BATCH,
        UPDATE,
        KILL
    }

    public static class Message implements IMessage {
        private Mode mode = Mode.KILL;
        private int windowId = -1;
        private List<QIOResourceEntry> entries = Collections.emptyList();
        private long countCapacity;
        private int typeCapacity;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, Mode mode, List<QIOResourceEntry> entries, long countCapacity, int typeCapacity) {
            this.windowId = windowId;
            this.mode = mode;
            this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
            this.countCapacity = Math.max(0, countCapacity);
            this.typeCapacity = Math.max(0, typeCapacity);
            valid = windowId >= 0 && mode != null;
        }

        public static Message batch(int windowId, List<QIOResourceEntry> entries, long countCapacity, int typeCapacity) {
            return new Message(windowId, Mode.BATCH, entries, countCapacity, typeCapacity);
        }

        public static Message update(int windowId, List<QIOResourceEntry> entries, long countCapacity, int typeCapacity) {
            return new Message(windowId, Mode.UPDATE, entries, countCapacity, typeCapacity);
        }

        public static Message kill(int windowId) {
            return new Message(windowId, Mode.KILL, Collections.emptyList(), 0, 0);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeByte(mode.ordinal());
            if (mode == Mode.KILL) {
                return;
            }
            buffer.writeLong(countCapacity);
            buffer.writeInt(typeCapacity);
            buffer.writeShort(Math.min(4096, entries.size()));
            for (int i = 0; i < entries.size() && i < 4096; i++) {
                entries.get(i).write(buffer);
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            windowId = -1;
            mode = Mode.KILL;
            entries = Collections.emptyList();
            try {
                int decodedWindowId = buffer.readInt();
                int ordinal = buffer.readUnsignedByte();
                if (decodedWindowId < 0 || ordinal < 0 || ordinal >= Mode.values().length) {
                    throw new IllegalArgumentException("Invalid legacy QIO viewer synchronization header");
                }
                windowId = decodedWindowId;
                mode = Mode.values()[ordinal];
                if (mode == Mode.KILL) {
                    valid = true;
                    return;
                }
                countCapacity = Math.max(0, buffer.readLong());
                typeCapacity = Math.max(0, buffer.readInt());
                int count = buffer.readUnsignedShort();
                if (count > PacketQIOViewerData.MAX_ENTRIES) {
                    throw new IllegalArgumentException("Legacy QIO viewer synchronization exceeds the entry limit");
                }
                List<QIOResourceEntry> decoded = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    QIOResourceEntry entry = QIOResourceEntry.read(buffer);
                    if (entry == null) {
                        throw new IllegalArgumentException("Invalid legacy QIO viewer resource entry");
                    }
                    decoded.add(entry);
                }
                entries = decoded;
                valid = true;
            } catch (RuntimeException ex) {
                windowId = -1;
                mode = Mode.KILL;
                entries = Collections.emptyList();
                countCapacity = 0;
                typeCapacity = 0;
            }
        }

        public int getWindowId() {
            return windowId;
        }

        public boolean isValid() {
            return valid;
        }
    }

    /** Source-compatible nested packet name. */
    public static class QIOItemViewerGuiSyncMessage extends Message {
        public QIOItemViewerGuiSyncMessage() {
            super();
        }
    }
}
