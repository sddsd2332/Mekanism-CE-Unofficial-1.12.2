package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public final class PacketQIOCraftingMonitorMutationResult implements
      IMessageHandler<PacketQIOCraftingMonitorMutationResult.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) return null;
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOCraftingMonitorPageContainer container) ||
                player.openContainer.windowId != message.windowId) return;
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (state.matches(message.nonce, message.terminalUUID, message.targetRevision,
                  message.frequencyUUID, message.accessRevision)) {
                container.getCraftingMonitorClientCache().applyMutation(message.nonce,
                      message.requestId, message.jobId, message.status.name());
            }
        }, player);
        return null;
    }

    public static final class Message implements IMessage {
        private int windowId;
        private UUID nonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID frequencyUUID;
        private long accessRevision;
        private UUID requestId;
        private UUID jobId;
        private PacketQIOCraftingMonitorMutation.Status status;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session, UUID requestId,
              UUID jobId, PacketQIOCraftingMonitorMutation.Status status) {
            this.windowId = windowId;
            nonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            this.requestId = requestId;
            this.jobId = jobId;
            this.status = status;
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID jobId,
              PacketQIOCraftingMonitorMutation.Status status) {
            return session == null || session.getFrequencyUUID() == null ? new Message() :
                  new Message(windowId, session, requestId, jobId, status);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, requestId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, jobId);
            buffer.writeByte(status.ordinal());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                nonce = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                terminalUUID = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                targetRevision = buffer.readLong();
                frequencyUUID = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                accessRevision = buffer.readLong();
                requestId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                jobId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                int ordinal = buffer.readUnsignedByte();
                if (ordinal >= PacketQIOCraftingMonitorMutation.Status.values().length) return;
                status = PacketQIOCraftingMonitorMutation.Status.values()[ordinal];
                valid = shape();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            return windowId >= 0 && nonce != null && terminalUUID != null &&
                  targetRevision >= 0 && frequencyUUID != null && accessRevision >= 0 &&
                  requestId != null && jobId != null && status != null;
        }
    }
}
