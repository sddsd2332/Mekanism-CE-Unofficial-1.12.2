package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOSmartProcessingResourcePageRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOSmartProcessingResourcePageRequest implements
      IMessageHandler<PacketQIOSmartProcessingResourcePageRequest.Message, IMessage> {

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
              !(player.openContainer instanceof QIOSmartProcessingPageContainer container) ||
              player.openContainer.windowId != message.windowId ||
              !player.openContainer.canInteractWith(player) ||
              !container.tryRequestSmartProcessing(player.world.getTotalWorldTime())) return;
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session)) return;
        QIOFrequency frequency = container.getTerminalFrequency();
        if (frequency == null || !frequency.getFrequencyUUID().equals(session.getFrequencyUUID())) return;
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, player.getUniqueID());
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference,
              player.getUniqueID());
        if (view == null) return;
        try {
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
                  reference.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(reference.getFrequencyName(),
                        reference.getOwnerUUID(), reference.getSecurityMode()));
            QIOPage<QIOSmartProcessingResourceEntry> page = QIOSmartProcessingService.getResourcePage(
                  session, network, view.getSnapshot(), player.world, player.getUniqueID(),
                  message.filter, message.query, message.offset, message.requestedPageSize,
                  message.expectedSourceRevision);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOSmartProcessingResourcePageData.Message.create(message.windowId,
                        session, message.requestId, message.filter, message.query, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        } finally {
            view.close();
        }
    }

    private static boolean validSession(Message message, EntityPlayer player,
          QIOProcessingTerminalSession session) {
        return session != null && session.getFrequencyUUID() != null &&
              session.getTerminalType() == QIOProcessingTerminalType.SMART_PROCESSING &&
              session.validate(message.sessionNonce, player.getUniqueID(), session.getTargetKind(),
                    QIOProcessingTerminalType.SMART_PROCESSING, message.terminalUUID,
                    message.targetRevision, session.getFrequencyUUID(), session.getAccessRevision()) ==
                    QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private UUID requestId;
        private long targetRevision;
        private long expectedSourceRevision;
        private int requestedPageSize;
        private int offset;
        private QIOSmartProcessingResourceFilter filter;
        private String query = "";
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID, long targetRevision,
              UUID requestId, QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.requestId = requestId;
            this.filter = filter;
            this.query = normalizeQuery(query);
            this.offset = offset;
            this.requestedPageSize = requestedPageSize;
            this.expectedSourceRevision = expectedSourceRevision;
            valid = structurallyValid();
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestId, filter, query, offset,
                        requestedPageSize, expectedSourceRevision);
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, QIOSmartProcessingResourceFilter filter, String query, int offset,
              int requestedPageSize, long expectedSourceRevision) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(), session.getTargetRevision(),
                  requestId, filter, query, offset, requestedPageSize, expectedSourceRevision);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
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
                targetRevision = buffer.readLong();
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
                  requestId != null && targetRevision >= 0 && filter != null && query != null &&
                  query.length() <= MAX_QUERY_LENGTH && offset >= 0 && requestedPageSize > 0 &&
                  requestedPageSize <= PacketQIOSmartProcessingResourcePageData.MAX_WIRE_PAGE_SIZE &&
                  expectedSourceRevision >= -1;
        }

        private static String normalizeQuery(String query) {
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
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
