package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceDirectoryService;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/** Requests one bounded device-directory page from a live management session. */
public final class PacketQIOManagementDevicePageRequest implements
      IMessageHandler<PacketQIOManagementDevicePageRequest.Message, IMessage> {

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
            !(player.openContainer instanceof QIOManagementDevicePageContainer container) ||
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
            !container.tryRequestDevicePage(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) {
            return;
        }
        try {
            QIOPage<mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot> page =
                  message.typeKey.isEmpty() ?
                        QIOManagementDeviceDirectoryService.getPage(session, network,
                              session.getAccessRevision(), message.cursor,
                              message.requestedPageSize) :
                        QIOManagementDeviceDirectoryService.getPageForType(session, network,
                              session.getAccessRevision(), message.typeKey, message.cursor,
                              message.requestedPageSize);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOManagementDevicePageData.Message.create(message.windowId,
                        session, page, message.typeKey), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private int requestedPageSize;
        @Nullable
        private QIOPageCursor cursor;
        private String typeKey = "";
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, int requestedPageSize,
              @Nullable QIOPageCursor cursor, String typeKey) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.requestedPageSize = requestedPageSize;
            this.cursor = cursor;
            this.typeKey = typeKey == null ? "" : typeKey;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestedPageSize, cursor, "");
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor, String typeKey) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestedPageSize, cursor, typeKey);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor) {
            return create(windowId, session, requestedPageSize, cursor, "");
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor, String typeKey) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestedPageSize, cursor, typeKey);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            buffer.writeInt(requestedPageSize);
            buffer.writeBoolean(cursor != null);
            if (cursor != null) {
                cursor.write(buffer);
            }
            PacketHandler.writeString(buffer, typeKey);
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
                cursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                typeKey = PacketHandler.readString(buffer);
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
                  expectedTargetRevision >= 0 && requestedPageSize > 0 &&
                  requestedPageSize <= 1_024 &&
                  typeKey != null && typeKey.length() <= 2_048 &&
                  (cursor == null || sessionNonce.equals(cursor.getSessionNonce()));
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
