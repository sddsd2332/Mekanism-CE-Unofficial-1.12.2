package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PacketQIOSmartProcessingResourcePageData implements
      IMessageHandler<PacketQIOSmartProcessingResourcePageData.Message, IMessage> {

    public static final int MAX_WIRE_PAGE_SIZE = 256;
    private static final int MAX_QUERY_LENGTH = 64;

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) return null;
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                  !(player.openContainer instanceof QIOSmartProcessingPageContainer container) ||
                  player.openContainer.windowId != message.windowId) return;
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID, message.accessRevision)) {
                container.getSmartProcessingClientCache().applyPage(message.sessionNonce,
                      message.requestId, message.filter, message.query, message.sourceRevision,
                      message.offset, message.totalSize, message.entries);
            }
        }, player);
        return null;
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private UUID frequencyUUID;
        private UUID requestId;
        private long targetRevision;
        private long accessRevision;
        private long sourceRevision;
        private int offset;
        private int totalSize;
        private QIOSmartProcessingResourceFilter filter;
        private String query = "";
        private List<QIOSmartProcessingResourceEntry> entries = Collections.emptyList();
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session, UUID requestId,
              QIOSmartProcessingResourceFilter filter, String query,
              QIOPage<QIOSmartProcessingResourceEntry> page) {
            this.windowId = windowId;
            sessionNonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            this.requestId = requestId;
            this.filter = filter;
            this.query = query;
            sourceRevision = page.getSourceRevision();
            offset = page.getOffset();
            totalSize = page.getTotalSize();
            entries = Collections.unmodifiableList(new ArrayList<>(page.getEntries()));
            valid = structurallyValid();
        }

        @Nonnull
        public static Message create(int windowId, @Nonnull QIOProcessingTerminalSession session,
              @Nonnull UUID requestId, @Nonnull QIOSmartProcessingResourceFilter filter,
              @Nonnull String query, @Nonnull QIOPage<QIOSmartProcessingResourceEntry> page) {
            return session.getFrequencyUUID() == null ? new Message() :
                  new Message(windowId, session, requestId, filter, query, page);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            writeUUID(buffer, requestId);
            buffer.writeByte(filter.ordinal());
            PacketHandler.writeString(buffer, query);
            buffer.writeLong(sourceRevision);
            buffer.writeInt(offset);
            buffer.writeInt(totalSize);
            buffer.writeInt(entries.size());
            for (QIOSmartProcessingResourceEntry entry : entries) {
                PacketHandler.writeNBT(buffer, entry.write());
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                targetRevision = buffer.readLong();
                frequencyUUID = readUUID(buffer);
                accessRevision = buffer.readLong();
                requestId = readUUID(buffer);
                filter = QIOSmartProcessingResourceFilter.byId(buffer.readUnsignedByte());
                query = PacketHandler.readString(buffer);
                sourceRevision = buffer.readLong();
                offset = buffer.readInt();
                totalSize = buffer.readInt();
                int size = buffer.readInt();
                if (size < 0 || size > MAX_WIRE_PAGE_SIZE || totalSize < 0 || offset < 0 ||
                      offset > totalSize || size > totalSize - offset) return;
                List<QIOSmartProcessingResourceEntry> decoded = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) return;
                    decoded.add(QIOSmartProcessingResourceEntry.read(data));
                }
                entries = Collections.unmodifiableList(decoded);
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                entries = Collections.emptyList();
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  frequencyUUID != null && requestId != null && targetRevision >= 0 &&
                  accessRevision >= 0 && sourceRevision >= 0 && filter != null && query != null &&
                  query.length() <= MAX_QUERY_LENGTH && offset >= 0 && totalSize >= 0 &&
                  offset <= totalSize && entries.size() <= MAX_WIRE_PAGE_SIZE &&
                  entries.size() <= totalSize - offset;
        }

        private static void writeUUID(ByteBuf buffer, UUID value) {
            buffer.writeLong(value.getMostSignificantBits());
            buffer.writeLong(value.getLeastSignificantBits());
        }

        private static UUID readUUID(ByteBuf buffer) {
            return new UUID(buffer.readLong(), buffer.readLong());
        }
    }
}
