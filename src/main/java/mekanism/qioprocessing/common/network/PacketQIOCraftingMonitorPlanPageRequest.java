package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
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
 * QIO 处理模块中的 PacketQIOCraftingMonitorPlanPageRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorPlanPageRequest implements
      IMessageHandler<PacketQIOCraftingMonitorPlanPageRequest.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOCraftingMonitorPageContainer container) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) return;
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session) ||
            !container.tryRequestCraftingMonitorDetail(player.world.getTotalWorldTime())) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) return;
        try {
            QIOPage<QIOCraftingMonitorPlanEntry> page = QIOCraftingMonitorService.getPlanPage(
                  session, network, session.getAccessRevision(), message.jobId,
                  message.planRevision, message.cursor, message.pageSize);
            String signature = network.getJob(message.jobId).getActivePlan()
                  .getStructuralSignature();
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOCraftingMonitorPlanPageData.Message.create(message.windowId,
                        session, message.jobId, message.planRevision, signature, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    private static boolean validSession(Message message, EntityPlayer player,
          @Nullable QIOProcessingTerminalSession session) {
        return session != null && session.getFrequencyUUID() != null &&
              session.getTerminalType() == QIOProcessingTerminalType.CRAFTING_MONITOR &&
              session.validate(message.nonce, player.getUniqueID(), session.getTargetKind(),
                    QIOProcessingTerminalType.CRAFTING_MONITOR, message.terminalUUID,
                    message.targetRevision, session.getFrequencyUUID(),
                    session.getAccessRevision()) ==
                    QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    public static final class Message implements IMessage {
        private int windowId;
        private UUID nonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID jobId;
        private int planRevision;
        private int pageSize;
        @Nullable private QIOPageCursor cursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID nonce, UUID terminalUUID, long targetRevision,
              UUID jobId, int planRevision, int pageSize, @Nullable QIOPageCursor cursor) {
            this.windowId = windowId;
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.jobId = jobId;
            this.planRevision = planRevision;
            this.pageSize = pageSize;
            this.cursor = cursor;
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID jobId, int planRevision, int pageSize, @Nullable QIOPageCursor cursor) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  jobId, planRevision, pageSize, cursor);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID jobId, int planRevision, int pageSize, @Nullable QIOPageCursor cursor) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), jobId, planRevision, pageSize, cursor);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, nonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            writeUUID(buffer, jobId);
            buffer.writeInt(planRevision);
            buffer.writeInt(pageSize);
            buffer.writeBoolean(cursor != null);
            if (cursor != null) cursor.write(buffer);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                nonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                targetRevision = buffer.readLong();
                jobId = readUUID(buffer);
                planRevision = buffer.readInt();
                pageSize = buffer.readInt();
                cursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                valid = shape();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            return windowId >= 0 && nonce != null && terminalUUID != null &&
                  targetRevision >= 0 && jobId != null && planRevision > 0 &&
                  pageSize > 0 && pageSize <= PacketQIOCraftingMonitorPlanPageData.MAX_PAGE_SIZE &&
                  (cursor == null || nonce.equals(cursor.getSessionNonce()));
        }
    }

    static void writeUUID(ByteBuf buffer, UUID value) {
        buffer.writeLong(value.getMostSignificantBits());
        buffer.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUUID(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }
}
