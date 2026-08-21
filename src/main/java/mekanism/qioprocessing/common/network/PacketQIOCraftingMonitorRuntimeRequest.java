package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOCraftingMonitorRuntimeRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorRuntimeRequest implements
      IMessageHandler<PacketQIOCraftingMonitorRuntimeRequest.Message, IMessage> {

    public static final int MAX_REQUESTED_NODES = 1_024;

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
        if (session == null || session.getFrequencyUUID() == null ||
            session.getTerminalType() != QIOProcessingTerminalType.CRAFTING_MONITOR ||
            session.validate(message.nonce, player.getUniqueID(), session.getTargetKind(),
                  QIOProcessingTerminalType.CRAFTING_MONITOR, message.terminalUUID,
                  message.targetRevision, session.getFrequencyUUID(),
                  session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED ||
            !container.tryRequestCraftingMonitorDetail(player.world.getTotalWorldTime())) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) return;
        try {
            QIOCraftingMonitorRuntimeSnapshot snapshot =
                  QIOCraftingMonitorService.getRuntimeSnapshot(session, network,
                        session.getAccessRevision(), message.jobId, message.planRevision,
                        message.knownRuntimeRevision, message.forceBaseline,
                        message.nodeOffset, message.knownNodeRevisions,
                        player.world.getTotalWorldTime());
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOCraftingMonitorRuntimeData.Message.create(message.windowId,
                        session, snapshot), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {
        private int windowId;
        private UUID nonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID jobId;
        private int planRevision;
        private long knownRuntimeRevision;
        private boolean forceBaseline;
        private int nodeOffset;
        private Map<Long, Long> knownNodeRevisions = Collections.emptyMap();
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID nonce, UUID terminalUUID, long targetRevision,
              UUID jobId, int planRevision, long knownRuntimeRevision, boolean forceBaseline,
              int nodeOffset, Map<Long, Long> knownNodeRevisions) {
            this.windowId = windowId;
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.jobId = jobId;
            this.planRevision = planRevision;
            this.knownRuntimeRevision = knownRuntimeRevision;
            this.forceBaseline = forceBaseline;
            this.nodeOffset = nodeOffset;
            this.knownNodeRevisions = Collections.unmodifiableMap(
                  new LinkedHashMap<>(knownNodeRevisions));
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID jobId, int planRevision, long knownRuntimeRevision,
              boolean forceBaseline, int nodeOffset, Map<Long, Long> knownNodeRevisions) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  jobId, planRevision, knownRuntimeRevision, forceBaseline,
                  nodeOffset, knownNodeRevisions);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID jobId, int planRevision, long knownRuntimeRevision,
              boolean forceBaseline, int nodeOffset, Map<Long, Long> knownNodeRevisions) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), jobId, planRevision, knownRuntimeRevision,
                  forceBaseline, nodeOffset, knownNodeRevisions);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, jobId);
            buffer.writeInt(planRevision);
            buffer.writeLong(knownRuntimeRevision);
            buffer.writeBoolean(forceBaseline);
            buffer.writeInt(nodeOffset);
            buffer.writeShort(knownNodeRevisions.size());
            for (Map.Entry<Long, Long> entry : knownNodeRevisions.entrySet()) {
                buffer.writeLong(entry.getKey());
                buffer.writeLong(entry.getValue());
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                nonce = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                terminalUUID = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                targetRevision = buffer.readLong();
                jobId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                planRevision = buffer.readInt();
                knownRuntimeRevision = buffer.readLong();
                forceBaseline = buffer.readBoolean();
                nodeOffset = buffer.readInt();
                int count = buffer.readUnsignedShort();
                if (count > MAX_REQUESTED_NODES) return;
                Map<Long, Long> decoded = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    if (decoded.put(buffer.readLong(), buffer.readLong()) != null) return;
                }
                knownNodeRevisions = Collections.unmodifiableMap(decoded);
                valid = shape();
            } catch (RuntimeException ignored) {
                knownNodeRevisions = Collections.emptyMap();
            }
        }

        public boolean isValid() { return valid; }
        Map<Long, Long> getKnownNodeRevisions() { return knownNodeRevisions; }

        private boolean shape() {
            if (windowId < 0 || nonce == null || terminalUUID == null || targetRevision < 0 ||
                jobId == null || planRevision <= 0 || knownRuntimeRevision < -1 ||
                nodeOffset < 0 ||
                knownNodeRevisions.size() > MAX_REQUESTED_NODES) return false;
            for (Map.Entry<Long, Long> entry : knownNodeRevisions.entrySet()) {
                if (entry.getKey() < 0 || entry.getValue() < -1) return false;
            }
            return true;
        }
    }
}
