package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceRulePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService;
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
 * QIO 处理模块中的 PacketQIOMaintenanceRulePageRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOMaintenanceRulePageRequest implements
      IMessageHandler<PacketQIOMaintenanceRulePageRequest.Message, IMessage> {

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
            !(player.openContainer instanceof QIOMaintenanceRulePageContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session) ||
            !container.tryRequestMaintenanceRulePage(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) {
            return;
        }
        try {
            QIOPage<QIOMaintenanceRule> page = QIOMaintenanceRuleService.getPage(
                  session, network, session.getAccessRevision(), message.cursor,
                  message.requestedPageSize);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOMaintenanceRulePageData.Message.create(message.windowId,
                        session, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    private static boolean validSession(Message message, EntityPlayer player,
          QIOProcessingTerminalSession session) {
        return session != null && session.getFrequencyUUID() != null &&
              session.getTerminalType() == QIOProcessingTerminalType.MAINTENANCE &&
              session.validate(message.sessionNonce, player.getUniqueID(),
                    session.getTargetKind(), QIOProcessingTerminalType.MAINTENANCE,
                    message.terminalUUID, message.expectedTargetRevision,
                    session.getFrequencyUUID(), session.getAccessRevision()) ==
                    QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private int requestedPageSize;
        @Nullable
        private QIOPageCursor cursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, int requestedPageSize,
              @Nullable QIOPageCursor cursor) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.requestedPageSize = requestedPageSize;
            this.cursor = cursor;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, int requestedPageSize,
              @Nullable QIOPageCursor cursor) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestedPageSize, cursor);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              int requestedPageSize, @Nullable QIOPageCursor cursor) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestedPageSize, cursor);
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
                valid = structurallyValid();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 && requestedPageSize > 0 &&
                  requestedPageSize <= PacketQIOMaintenanceRulePageData.MAX_WIRE_PAGE_SIZE &&
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
