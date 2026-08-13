package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** Looks up the authoritative device-default row before opening an editor. */
public final class PacketQIOManagementPolicyLookupRequest implements
      IMessageHandler<PacketQIOManagementPolicyLookupRequest.Message, IMessage> {

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
            session.getFrequencyUUID() == null ||
            !container.tryRequestPolicyLookup(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) {
            return;
        }
        try {
            QIOPolicyEntrySnapshot entry = QIOManagementPolicyService.getDeviceDefault(
                  session, network, session.getAccessRevision(), message.deviceUUID);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOManagementPolicyLookupData.Message.create(message.windowId,
                        session, message.requestId, entry), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private UUID requestId;
        private UUID deviceUUID;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long expectedTargetRevision, UUID requestId, UUID deviceUUID) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.requestId = requestId;
            this.deviceUUID = deviceUUID;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              UUID deviceUUID) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), requestId, deviceUUID);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestId, deviceUUID);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            writeUUID(buffer, requestId);
            writeUUID(buffer, deviceUUID);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                requestId = readUUID(buffer);
                deviceUUID = readUUID(buffer);
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
                  expectedTargetRevision >= 0 && requestId != null && deviceUUID != null;
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
