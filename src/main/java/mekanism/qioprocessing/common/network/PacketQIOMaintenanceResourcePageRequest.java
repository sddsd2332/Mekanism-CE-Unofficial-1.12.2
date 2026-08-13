package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceResourcePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceResourceService;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

/** Server request for one bounded page of maintenance resources. */
public final class PacketQIOMaintenanceResourcePageRequest implements
      IMessageHandler<PacketQIOMaintenanceResourcePageRequest.Message, IMessage> {

    private static final int MAX_QUERY_LENGTH = 64;

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
              !(player.openContainer instanceof QIOMaintenanceResourcePageContainer container) ||
              !(player.openContainer instanceof mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
              player.openContainer.windowId != message.windowId ||
              !player.openContainer.canInteractWith(player) ||
              !container.tryRequestMaintenanceResourcePage(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session)) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) return;
        try {
            QIOPage<QIOSmartProcessingResourceEntry> page = QIOMaintenanceResourceService.getPage(
                  session, network, player.world, message.filter, message.query, message.offset,
                  message.requestedPageSize, message.expectedSourceRevision);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOMaintenanceResourcePageData.Message.create(message.windowId,
                        session, message.requestId, message.filter, message.query, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    private static boolean validSession(Message message, EntityPlayer player,
          QIOProcessingTerminalSession session) {
        return session != null && session.getFrequencyUUID() != null &&
              session.getTerminalType() == QIOProcessingTerminalType.MAINTENANCE &&
              session.validate(message.sessionNonce, player.getUniqueID(), session.getTargetKind(),
                    QIOProcessingTerminalType.MAINTENANCE, message.terminalUUID,
                    message.expectedTargetRevision, session.getFrequencyUUID(),
                    session.getAccessRevision()) == QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private UUID requestId;
        private QIOSmartProcessingResourceFilter filter;
        private String query = "";
        private int offset;
        private int requestedPageSize;
        private long expectedSourceRevision;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, UUID requestId,
              QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.requestId = requestId;
            this.filter = filter;
            this.query = query;
            this.offset = offset;
            this.requestedPageSize = requestedPageSize;
            this.expectedSourceRevision = expectedSourceRevision;
            valid = structurallyValid();
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  requestId, filter, normalizeQuery(query), offset, requestedPageSize,
                  expectedSourceRevision);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(), session.getTargetRevision(),
                  requestId, filter, normalizeQuery(query), offset, requestedPageSize,
                  expectedSourceRevision);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            writeUUID(buffer, requestId);
            buffer.writeByte(filter.ordinal());
            PacketHandler.writeString(buffer, query);
            buffer.writeInt(offset);
            buffer.writeInt(requestedPageSize);
            buffer.writeLong(expectedSourceRevision);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                requestId = readUUID(buffer);
                filter = QIOSmartProcessingResourceFilter.byId(buffer.readUnsignedByte());
                query = PacketHandler.readString(buffer);
                offset = buffer.readInt();
                requestedPageSize = buffer.readInt();
                expectedSourceRevision = buffer.readLong();
                valid = structurallyValid();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 && requestId != null && filter != null &&
                  query != null && query.length() <= MAX_QUERY_LENGTH && offset >= 0 &&
                  requestedPageSize > 0 && requestedPageSize <= 256 && expectedSourceRevision >= -1;
        }

        private static String normalizeQuery(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return normalized.length() <= MAX_QUERY_LENGTH ? normalized :
                  normalized.substring(0, MAX_QUERY_LENGTH);
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
