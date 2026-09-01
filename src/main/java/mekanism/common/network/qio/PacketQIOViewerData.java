package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOCapacitySummary;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIONetworkResourceLimits;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative server-to-client snapshot for the QIO resource viewer.
 * Resource templates are included for rendering only; interaction packets
 * identify a resource by UUID and are resolved again on the server.
 */
public class PacketQIOViewerData implements IMessageHandler<PacketQIOViewerData.Message, IMessage> {

    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_PACKET_BYTES = QIONetworkResourceLimits.MAX_PACKET_BYTES;
    private static final int PACKET_HEADER_BUDGET = 1_024;

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
                switch (message.mode) {
                    case BATCH:
                        container.applyBatchChunk(message.entries, message.capacitySummary, message.firstBatchChunk);
                        break;
                    case UPDATE:
                        container.applyUpdate(message.entries, message.capacitySummary);
                        break;
                    case KILL:
                        container.applyKill();
                        break;
                    default:
                        break;
                }
            }
        }, player);
        return null;
    }

    public static void sendBatch(EntityPlayerMP player, int windowId, List<QIOResourceEntry> entries, long totalCountCapacity,
          int totalTypeCapacity) {
        sendBatch(player, windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
              QIOAmount.of(totalTypeCapacity), 0, 0));
    }

    public static void sendBatch(EntityPlayerMP player, int windowId, List<QIOResourceEntry> entries,
          QIOCapacitySummary capacitySummary) {
        if (player == null || windowId < 0) {
            return;
        }
        for (Message message : createBatchMessages(windowId, entries, capacitySummary)) {
            Mekanism.packetHandler.sendTo(message, player);
        }
    }

    /** Builds bounded packets for tests and alternative transports. */
    public static List<Message> createBatchMessages(int windowId, List<QIOResourceEntry> entries, long totalCountCapacity,
          int totalTypeCapacity) {
        return createBatchMessages(windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
              QIOAmount.of(totalTypeCapacity), 0, 0));
    }

    public static List<Message> createBatchMessages(int windowId, List<QIOResourceEntry> entries,
          QIOCapacitySummary capacitySummary) {
        List<QIOResourceEntry> snapshot = entries == null ? Collections.emptyList() : entries;
        if (snapshot.isEmpty()) {
            return Collections.singletonList(Message.batch(windowId, Collections.emptyList(), capacitySummary, true));
        }
        List<List<QIOResourceEntry>> chunks = chunkEntries(snapshot);
        if (chunks.isEmpty()) {
            return Collections.singletonList(Message.batch(windowId, Collections.emptyList(),
                  capacitySummary, true));
        }
        List<Message> messages = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            messages.add(Message.batch(windowId, chunks.get(index), capacitySummary, index == 0));
        }
        return Collections.unmodifiableList(messages);
    }

    public static void sendUpdate(EntityPlayerMP player, int windowId, List<QIOResourceEntry> entries, long totalCountCapacity,
          int totalTypeCapacity) {
        sendUpdate(player, windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
              QIOAmount.of(totalTypeCapacity), 0, 0));
    }

    public static void sendUpdate(EntityPlayerMP player, int windowId, List<QIOResourceEntry> entries,
          QIOCapacitySummary capacitySummary) {
        if (player == null || windowId < 0) {
            return;
        }
        for (Message message : createUpdateMessages(windowId, entries, capacitySummary)) {
            Mekanism.packetHandler.sendTo(message, player);
        }
    }

    public static List<Message> createUpdateMessages(int windowId, List<QIOResourceEntry> entries, long totalCountCapacity,
          int totalTypeCapacity) {
        return createUpdateMessages(windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
              QIOAmount.of(totalTypeCapacity), 0, 0));
    }

    public static List<Message> createUpdateMessages(int windowId, List<QIOResourceEntry> entries,
          QIOCapacitySummary capacitySummary) {
        List<QIOResourceEntry> updates = entries == null ? Collections.emptyList() : entries;
        if (updates.isEmpty()) {
            return Collections.singletonList(Message.update(windowId, Collections.emptyList(), capacitySummary));
        }
        List<List<QIOResourceEntry>> chunks = chunkEntries(updates);
        if (chunks.isEmpty()) {
            return Collections.singletonList(Message.update(windowId, Collections.emptyList(), capacitySummary));
        }
        List<Message> messages = new ArrayList<>(chunks.size());
        for (List<QIOResourceEntry> chunk : chunks) {
            messages.add(Message.update(windowId, chunk, capacitySummary));
        }
        return Collections.unmodifiableList(messages);
    }

    private static List<List<QIOResourceEntry>> chunkEntries(List<QIOResourceEntry> entries) {
        List<List<QIOResourceEntry>> chunks = new ArrayList<>();
        List<QIOResourceEntry> current = new ArrayList<>();
        int currentBytes = PACKET_HEADER_BUDGET;
        for (QIOResourceEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            int entryBytes;
            try {
                entryBytes = entry.getNetworkEncodedSize();
            } catch (RuntimeException ignored) {
                continue;
            }
            if (entryBytes <= 0 || entryBytes > QIONetworkResourceLimits.MAX_ENTRY_BYTES) {
                continue;
            }
            if (!current.isEmpty() && (current.size() >= MAX_ENTRIES ||
                  currentBytes + entryBytes > MAX_PACKET_BYTES)) {
                chunks.add(current);
                current = new ArrayList<>();
                currentBytes = PACKET_HEADER_BUDGET;
            }
            current.add(entry);
            currentBytes += entryBytes;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    public static void sendKill(EntityPlayerMP player, int windowId) {
        if (player != null && windowId >= 0) {
            Mekanism.packetHandler.sendTo(Message.kill(windowId), player);
        }
    }

    /** Returns the current QIO viewer window or {@code -1} when the player is not viewing QIO. */
    public static int getViewerWindowId(@Nullable EntityPlayerMP player) {
        if (player == null) {
            return -1;
        }
        Container open = player.openContainer;
        return open instanceof QIOItemViewerContainer ? open.windowId : -1;
    }

    public enum Mode {
        BATCH,
        UPDATE,
        KILL;

        @Nullable
        private static Mode byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    public static class Message implements IMessage {

        private Mode mode = Mode.KILL;
        private int windowId = -1;
        private List<QIOResourceEntry> entries = Collections.emptyList();
        private QIOCapacitySummary capacitySummary = QIOCapacitySummary.EMPTY;
        private boolean firstBatchChunk;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, Mode mode, @Nonnull List<QIOResourceEntry> entries, QIOCapacitySummary capacitySummary,
              boolean firstBatchChunk) {
            this.windowId = windowId;
            this.mode = mode;
            this.entries = new ArrayList<>(entries.size());
            for (QIOResourceEntry entry : entries) {
                if (entry != null && this.entries.size() < MAX_ENTRIES) {
                    this.entries.add(entry);
                }
            }
            this.capacitySummary = capacitySummary == null ? QIOCapacitySummary.EMPTY : capacitySummary;
            this.firstBatchChunk = firstBatchChunk;
            valid = windowId >= 0 && mode != null;
        }

        public static Message batch(int windowId, List<QIOResourceEntry> entries, long totalCountCapacity, int totalTypeCapacity) {
            return batch(windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
                  QIOAmount.of(totalTypeCapacity), 0, 0), true);
        }

        private static Message batch(int windowId, List<QIOResourceEntry> entries, QIOCapacitySummary capacitySummary,
              boolean firstBatchChunk) {
            return new Message(windowId, Mode.BATCH, entries == null ? Collections.emptyList() : entries,
                  capacitySummary, firstBatchChunk);
        }

        public static Message update(int windowId, List<QIOResourceEntry> entries, long totalCountCapacity, int totalTypeCapacity) {
            return update(windowId, entries, new QIOCapacitySummary(QIOAmount.of(totalCountCapacity),
                  QIOAmount.of(totalTypeCapacity), 0, 0));
        }

        public static Message update(int windowId, List<QIOResourceEntry> entries, QIOCapacitySummary capacitySummary) {
            return new Message(windowId, Mode.UPDATE, entries == null ? Collections.emptyList() : entries,
                  capacitySummary, false);
        }

        public static Message kill(int windowId) {
            return new Message(windowId, Mode.KILL, Collections.emptyList(), QIOCapacitySummary.EMPTY, false);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            int messageStart = buffer.writerIndex();
            buffer.writeInt(windowId);
            buffer.writeByte(mode.ordinal());
            if (mode == Mode.KILL) {
                return;
            }
            if (mode == Mode.BATCH) {
                buffer.writeBoolean(firstBatchChunk);
            }
            capacitySummary.write(buffer);
            int countIndex = buffer.writerIndex();
            buffer.writeShort(0);
            int written = 0;
            for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
                int entryStart = buffer.writerIndex();
                try {
                    entries.get(i).write(buffer);
                } catch (RuntimeException ignored) {
                    buffer.writerIndex(entryStart);
                    continue;
                }
                if (buffer.writerIndex() - messageStart > MAX_PACKET_BYTES) {
                    buffer.writerIndex(entryStart);
                    break;
                }
                written++;
            }
            buffer.setShort(countIndex, written);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            entries = Collections.emptyList();
            try {
                if (buffer.readableBytes() > MAX_PACKET_BYTES) {
                    throw new IllegalArgumentException("QIO viewer synchronization exceeds the byte limit");
                }
                windowId = buffer.readInt();
                Mode decoded = Mode.byOrdinal(buffer.readUnsignedByte());
                if (windowId < 0 || decoded == null) {
                    throw new IllegalArgumentException("Invalid QIO viewer synchronization header");
                }
                mode = decoded;
                if (mode == Mode.KILL) {
                    valid = true;
                    return;
                }
                firstBatchChunk = mode == Mode.BATCH && buffer.readBoolean();
                capacitySummary = QIOCapacitySummary.read(buffer);
                int count = buffer.readUnsignedShort();
                if (count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("QIO viewer synchronization exceeds the entry limit");
                }
                List<QIOResourceEntry> decodedEntries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    QIOResourceEntry entry = QIOResourceEntry.read(buffer);
                    if (entry == null) {
                        throw new IllegalArgumentException("Invalid QIO viewer resource entry");
                    }
                    decodedEntries.add(entry);
                }
                entries = decodedEntries;
                valid = true;
            } catch (RuntimeException ex) {
                windowId = -1;
                mode = Mode.KILL;
                capacitySummary = QIOCapacitySummary.EMPTY;
                firstBatchChunk = false;
                entries = Collections.emptyList();
            }
        }

        public int getWindowId() {
            return windowId;
        }

        public boolean isValid() {
            return valid;
        }

        public Mode getMode() {
            return mode;
        }

        public List<QIOResourceEntry> getEntries() {
            return entries;
        }

        public long getTotalCountCapacity() {
            return capacitySummary.getCountCapacityClamped();
        }

        public int getTotalTypeCapacity() {
            return capacitySummary.getTypeCapacityClamped();
        }

        public QIOCapacitySummary getCapacitySummary() {
            return capacitySummary;
        }

        public boolean isFirstBatchChunk() {
            return firstBatchChunk;
        }
    }
}
