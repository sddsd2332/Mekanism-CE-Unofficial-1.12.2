package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleMutation;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceRulePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOMaintenanceRuleMutation 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOMaintenanceRuleMutation implements
      IMessageHandler<PacketQIOMaintenanceRuleMutation.Message, IMessage> {

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
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (session == null || session.getFrequencyUUID() == null ||
            session.getTerminalType() != QIOProcessingTerminalType.MAINTENANCE ||
            session.validate(message.sessionNonce, player.getUniqueID(),
                  session.getTargetKind(), QIOProcessingTerminalType.MAINTENANCE,
                  message.terminalUUID, message.expectedTargetRevision,
                  session.getFrequencyUUID(), session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) {
            return;
        }
        try {
            QIOMaintenanceRuleService.MutationResult result =
                  QIOMaintenanceRuleService.mutate(session, network,
                        session.getAccessRevision(), message.expectedRulesRevision,
                        message.mutation, player.getUniqueID(),
                        player.world.getTotalWorldTime());
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOMaintenanceRuleMutationResult.Message.create(
                        message.windowId, session, message.requestId, result), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private long expectedRulesRevision;
        private UUID requestId;
        private QIOMaintenanceRuleMutation mutation;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, long expectedRulesRevision, UUID requestId,
              QIOMaintenanceRuleMutation mutation) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.expectedRulesRevision = expectedRulesRevision;
            this.requestId = requestId;
            this.mutation = mutation;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, long expectedRulesRevision,
              UUID requestId, QIOMaintenanceRuleMutation mutation) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), expectedRulesRevision, requestId,
                        mutation);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              long expectedRulesRevision, UUID requestId,
              QIOMaintenanceRuleMutation mutation) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), expectedRulesRevision, requestId,
                  mutation);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            buffer.writeLong(expectedRulesRevision);
            writeUUID(buffer, requestId);
            PacketHandler.writeNBT(buffer, mutation.write());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                expectedRulesRevision = buffer.readLong();
                requestId = readUUID(buffer);
                NBTTagCompound data = PacketHandler.readNBT(buffer);
                if (data != null) {
                    mutation = QIOMaintenanceRuleMutation.read(data);
                    valid = structurallyValid();
                }
            } catch (QIOProcessingDataException | RuntimeException ignored) {
            }
        }

        public boolean isValid() {
            return valid;
        }

        QIOMaintenanceRuleMutation getMutation() {
            return mutation;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 && expectedRulesRevision >= 0 &&
                  requestId != null && mutation != null;
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
