package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.policy.QIOPolicyMutation;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** Compare-and-set central policy mutation from a live management session. */
public final class PacketQIOManagementPolicyMutation implements
      IMessageHandler<PacketQIOManagementPolicyMutation.Message, IMessage> {

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
            session.getFrequencyUUID() == null) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) {
            return;
        }
        try {
            QIOManagementPolicyService.MutationResult result =
                  QIOManagementPolicyService.mutate(session, network,
                        session.getAccessRevision(), message.expectedPolicyRevision,
                        message.mutation);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOManagementPolicyMutationResult.Message.create(
                        message.windowId, session, message.requestId, result), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private long expectedPolicyRevision;
        private UUID requestId;
        private QIOPolicyMutation mutation;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, long expectedPolicyRevision,
              UUID requestId, QIOPolicyMutation mutation) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.expectedPolicyRevision = expectedPolicyRevision;
            this.requestId = requestId;
            this.mutation = mutation;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, long expectedPolicyRevision,
              UUID requestId, QIOPolicyMutation mutation) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), expectedPolicyRevision, requestId,
                        mutation);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              long expectedPolicyRevision, UUID requestId,
              QIOPolicyMutation mutation) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), expectedPolicyRevision, requestId,
                  mutation);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            buffer.writeLong(expectedPolicyRevision);
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
                expectedPolicyRevision = buffer.readLong();
                requestId = readUUID(buffer);
                NBTTagCompound data = PacketHandler.readNBT(buffer);
                if (data == null) {
                    return;
                }
                mutation = QIOPolicyMutation.read(data);
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }

        QIOPolicyMutation getMutation() {
            return mutation;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 && expectedPolicyRevision >= 0 &&
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
