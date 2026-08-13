package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.DirectoryPage;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.PageMode;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.WorkbenchFilter;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Requests one revision-consistent page of central management policies. */
public final class PacketQIOManagementPolicyPageRequest implements
      IMessageHandler<PacketQIOManagementPolicyPageRequest.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) {
            PacketHandler.handlePacket(() -> handle(message, player), player);
        }
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOManagementPolicyPageContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (session == null || session.getTerminalType() !=
            QIOProcessingTerminalType.MANAGEMENT ||
            session.validate(message.sessionNonce, player.getUniqueID(),
                  session.getTargetKind(), QIOProcessingTerminalType.MANAGEMENT,
                  message.terminalUUID, message.expectedTargetRevision,
                  session.getFrequencyUUID(), session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED ||
            session.getFrequencyUUID() == null ||
            !container.tryRequestPolicyPage(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) {
            return;
        }
        try {
            DirectoryPage directory = QIOManagementPolicyService.getDirectoryPage(
                  session, network, session.getAccessRevision(), message.cursor,
                  message.requestedPageSize, message.pageMode, message.workbenchFilter,
                  message.query, message.expectedRecipeCatalogRevision);
            QIOPage<QIOPolicyEntrySnapshot> page = directory.getPage();
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOManagementPolicyPageData.Message.create(message.windowId,
                        session, message.requestId, message.pageMode,
                        directory.getRecipeCatalogRevision(), page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private int requestedPageSize;
        private UUID requestId;
        private PageMode pageMode;
        private WorkbenchFilter workbenchFilter;
        private String query;
        private long expectedRecipeCatalogRevision;
        @Nullable
        private QIOPageCursor cursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, int requestedPageSize,
              @Nullable QIOPageCursor cursor, UUID requestId, PageMode pageMode,
              WorkbenchFilter workbenchFilter, String query,
              long expectedRecipeCatalogRevision) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.requestedPageSize = requestedPageSize;
            this.cursor = cursor;
            this.requestId = Objects.requireNonNull(requestId, "requestId");
            this.pageMode = Objects.requireNonNull(pageMode, "pageMode");
            this.workbenchFilter = Objects.requireNonNull(workbenchFilter,
                  "workbenchFilter");
            this.query = Objects.requireNonNull(query, "query");
            this.expectedRecipeCatalogRevision = expectedRecipeCatalogRevision;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestedPageSize, cursor,
                        UUID.randomUUID(), PageMode.CONFIGURED, WorkbenchFilter.ALL,
                        "", -1);
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor, UUID requestId, PageMode pageMode,
              WorkbenchFilter workbenchFilter, String query,
              long expectedRecipeCatalogRevision) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestedPageSize, cursor, requestId,
                        pageMode, workbenchFilter, query,
                        expectedRecipeCatalogRevision);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestedPageSize, cursor,
                  UUID.randomUUID(), PageMode.CONFIGURED, WorkbenchFilter.ALL, "", -1);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor, UUID requestId,
              PageMode pageMode, WorkbenchFilter workbenchFilter, String query,
              long expectedRecipeCatalogRevision) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestedPageSize, cursor, requestId,
                  pageMode, workbenchFilter, query, expectedRecipeCatalogRevision);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            buffer.writeInt(requestedPageSize);
            writeUUID(buffer, requestId);
            buffer.writeByte(pageMode.ordinal());
            buffer.writeByte(workbenchFilter.ordinal());
            ByteBufUtils.writeUTF8String(buffer, query);
            buffer.writeLong(expectedRecipeCatalogRevision);
            buffer.writeBoolean(cursor != null);
            if (cursor != null) {
                cursor.write(buffer);
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                requestedPageSize = buffer.readInt();
                requestId = readUUID(buffer);
                pageMode = readEnum(buffer, PageMode.values());
                workbenchFilter = readEnum(buffer, WorkbenchFilter.values());
                query = ByteBufUtils.readUTF8String(buffer);
                expectedRecipeCatalogRevision = buffer.readLong();
                cursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                valid = structurallyValid();
            } catch (RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  requestId != null && pageMode != null && workbenchFilter != null &&
                  query != null && query.length() <= QIOManagementPolicyService.MAX_QUERY_LENGTH &&
                  expectedTargetRevision >= 0 && requestedPageSize > 0 &&
                  expectedRecipeCatalogRevision >= -1 &&
                  (pageMode != PageMode.CONFIGURED ||
                        expectedRecipeCatalogRevision == -1) &&
                  (pageMode != PageMode.WORKBENCH || cursor == null ||
                        expectedRecipeCatalogRevision >= 0) &&
                  requestedPageSize <= PacketQIOManagementPolicyPageData.MAX_WIRE_PAGE_SIZE &&
                  (cursor == null || sessionNonce.equals(cursor.getSessionNonce()));
        }

        private static <T> T readEnum(ByteBuf buffer, T[] values) {
            int ordinal = buffer.readUnsignedByte();
            if (ordinal >= values.length) {
                throw new IllegalArgumentException("Invalid QIO policy page enum");
            }
            return values[ordinal];
        }

        private static void writeUUID(ByteBuf buffer, UUID uuid) {
            buffer.writeLong(uuid.getMostSignificantBits());
            buffer.writeLong(uuid.getLeastSignificantBits());
        }

        private static UUID readUUID(ByteBuf buffer) {
            return new UUID(buffer.readLong(), buffer.readLong());
        }
    }
}
