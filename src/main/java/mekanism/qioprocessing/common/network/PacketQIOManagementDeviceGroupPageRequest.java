package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupService;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
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

/**
 * QIO 处理模块中的 PacketQIOManagementDeviceGroupPageRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOManagementDeviceGroupPageRequest implements
      IMessageHandler<PacketQIOManagementDeviceGroupPageRequest.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOManagementDevicePageContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player) ||
            !container.tryRequestDeviceGroupPage(player.world.getTotalWorldTime())) return;
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (session == null || session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT ||
            session.getFrequencyUUID() == null ||
            session.validate(message.sessionNonce, player.getUniqueID(),
                  session.getTargetKind(), QIOProcessingTerminalType.MANAGEMENT,
                  message.terminalUUID, message.expectedTargetRevision,
                  session.getFrequencyUUID(), session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) return;
        try {
            QIOPage<QIOManagementDeviceGroupSnapshot> page =
                  QIOManagementDeviceGroupService.getPage(session, network,
                        session.getAccessRevision(), message.cursor,
                        message.requestedPageSize);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOManagementDeviceGroupPageData.Message.create(message.windowId,
                        session, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private int requestedPageSize;
        @Nullable private QIOPageCursor cursor;
        private boolean valid;

        public Message() {
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor) {
            Message message = new Message();
            if (state != null && state.isValid()) {
                message.windowId = windowId;
                message.sessionNonce = state.getSessionNonce();
                message.terminalUUID = state.getTerminalUUID();
                message.expectedTargetRevision = state.getTargetRevision();
                message.requestedPageSize = requestedPageSize;
                message.cursor = cursor;
                message.valid = message.shape();
            }
            return message;
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor) {
            Message message = new Message();
            if (session != null) {
                message.windowId = windowId;
                message.sessionNonce = session.getSessionNonce();
                message.terminalUUID = session.getTerminalUUID();
                message.expectedTargetRevision = session.getTargetRevision();
                message.requestedPageSize = requestedPageSize;
                message.cursor = cursor;
                message.valid = message.shape();
            }
            return message;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            buffer.writeInt(requestedPageSize);
            buffer.writeBoolean(cursor != null);
            if (cursor != null) cursor.write(buffer);
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
                valid = shape();
            } catch (RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 && requestedPageSize > 0 &&
                  requestedPageSize <= 1_024 &&
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
