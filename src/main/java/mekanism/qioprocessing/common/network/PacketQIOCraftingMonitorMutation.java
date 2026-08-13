package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;
import java.util.UUID;

public final class PacketQIOCraftingMonitorMutation implements
      IMessageHandler<PacketQIOCraftingMonitorMutation.Message, IMessage> {

    public enum Action {
        PRIORITY
    }

    public enum Status {
        ACCEPTED,
        UNCHANGED,
        REVISION_CONFLICT,
        INVALID_STATE,
        NOT_FOUND,
        PERSISTENCE_ERROR
    }

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
        Status status;
        try {
            QIOCraftingMonitorService.MutationStatus updated =
                  QIOCraftingMonitorService.updatePriority(session, network,
                        session.getAccessRevision(), message.jobId,
                        message.expectedRuntimeRevision, message.value);
            status = switch (updated) {
                case ACCEPTED -> persistPriority(network, session, message.jobId);
                case UNCHANGED -> Status.UNCHANGED;
                case REVISION_CONFLICT -> Status.REVISION_CONFLICT;
                case INVALID_STATE -> Status.INVALID_STATE;
                case NOT_FOUND -> Status.NOT_FOUND;
            };
        } catch (IllegalStateException e) {
            status = Status.REVISION_CONFLICT;
        } catch (IllegalArgumentException | SecurityException e) {
            status = Status.INVALID_STATE;
        }
        QIOProcessingPacketHandler.INSTANCE.sendTo(
              PacketQIOCraftingMonitorMutationResult.Message.create(message.windowId, session,
                    message.requestId, message.jobId, status), playerMP);
    }

    private static Status persistPriority(QIOProcessingNetworkData network,
          QIOProcessingTerminalSession session, UUID jobId) {
        try {
            QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            QIOMaterialCommitment commitment = network.getCommitment(jobId);
            if (commitment != null && (commitment.getState() == QIOMaterialCommitment.State.ACTIVE ||
                  commitment.getState() == QIOMaterialCommitment.State.NEEDS_RECONCILIATION)) {
                QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
                QIOFrequencyReference reference = new QIOFrequencyReference(
                      network.getFrequencyUUID(), identity.getName(), identity.getOwnerUUID(),
                      identity.getSecurityMode(), session.getPlayerUUID());
                IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference,
                      session.getPlayerUUID());
                if (view != null) {
                    try {
                        QIOMaterialClaimCoordinator.refresh(network, jobId, view,
                              QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
                    } finally {
                        view.close();
                    }
                }
            }
            return Status.ACCEPTED;
        } catch (IOException | RuntimeException e) {
            return Status.PERSISTENCE_ERROR;
        }
    }

    public static final class Message implements IMessage {
        private int windowId;
        private UUID nonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID requestId;
        private UUID jobId;
        private long expectedRuntimeRevision;
        private Action action;
        private long value;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID nonce, UUID terminalUUID, long targetRevision,
              UUID requestId, UUID jobId, long expectedRuntimeRevision, Action action,
              long value) {
            this.windowId = windowId;
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.requestId = requestId;
            this.jobId = jobId;
            this.expectedRuntimeRevision = expectedRuntimeRevision;
            this.action = action;
            this.value = value;
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, UUID jobId, long expectedRuntimeRevision, Action action,
              long value) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  requestId, jobId, expectedRuntimeRevision, action, value);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID jobId, long expectedRuntimeRevision, Action action,
              long value) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestId, jobId, expectedRuntimeRevision,
                  action, value);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, requestId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, jobId);
            buffer.writeLong(expectedRuntimeRevision);
            buffer.writeByte(action.ordinal());
            buffer.writeLong(value);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                nonce = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                terminalUUID = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                targetRevision = buffer.readLong();
                requestId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                jobId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                expectedRuntimeRevision = buffer.readLong();
                int ordinal = buffer.readUnsignedByte();
                if (ordinal >= Action.values().length) return;
                action = Action.values()[ordinal];
                value = buffer.readLong();
                valid = shape();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            return windowId >= 0 && nonce != null && terminalUUID != null &&
                  targetRevision >= 0 && requestId != null && jobId != null &&
                  expectedRuntimeRevision >= 0 && action != null;
        }
    }
}
