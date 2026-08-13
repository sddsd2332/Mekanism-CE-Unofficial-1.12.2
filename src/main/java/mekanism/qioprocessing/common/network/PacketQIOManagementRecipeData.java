package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOManagementRecipeRequest.Operation;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Response to one management-terminal remote recipe request. */
public final class PacketQIOManagementRecipeData implements
      IMessageHandler<PacketQIOManagementRecipeData.Message, IMessage> {

    public enum Status {
        OK,
        APPLIED,
        UNCHANGED,
        UNAVAILABLE,
        REVISION_CONFLICT,
        INVALID_TARGET,
        READ_ONLY
    }

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) return null;
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOManagementRecipeContainer container) ||
                player.openContainer.windowId != message.windowId) return;
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (!state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID,
                  message.accessRevision)) return;
            if (message.operation == Operation.MUTATE) {
                container.getManagementRecipeClientCache().applyMutation(message.requestId,
                      message.status);
            } else if (message.snapshot != null) {
                container.getManagementRecipeClientCache().applyPage(message.requestId,
                      message.status, message.snapshot);
            } else {
                container.getManagementRecipeClientCache().applyUnavailable(
                      message.operation == Operation.PRODUCTS ? PageKind.PRODUCTS :
                            PageKind.ROUTES,
                      message.requestId, message.status);
            }
        }, player);
        return null;
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID frequencyUUID;
        private long accessRevision;
        private UUID requestId;
        private Operation operation = Operation.PRODUCTS;
        private Status status = Status.UNAVAILABLE;
        @Nullable private QIOManagementRecipeSnapshot snapshot;
        private boolean valid;

        public Message() {
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, Operation operation, Status status,
              @Nullable QIOManagementRecipeSnapshot snapshot) {
            Message message = new Message();
            if (session != null && session.getFrequencyUUID() != null) {
                message.windowId = windowId;
                message.sessionNonce = session.getSessionNonce();
                message.terminalUUID = session.getTerminalUUID();
                message.targetRevision = session.getTargetRevision();
                message.frequencyUUID = session.getFrequencyUUID();
                message.accessRevision = session.getAccessRevision();
                message.requestId = requestId;
                message.operation = operation;
                message.status = status;
                message.snapshot = snapshot;
                message.valid = message.shape();
            }
            return message;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            writeUUID(buffer, requestId);
            buffer.writeByte(operation.ordinal());
            buffer.writeByte(status.ordinal());
            buffer.writeBoolean(snapshot != null);
            if (snapshot != null) PacketHandler.writeNBT(buffer, snapshot.write());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                targetRevision = buffer.readLong();
                frequencyUUID = readUUID(buffer);
                accessRevision = buffer.readLong();
                requestId = readUUID(buffer);
                int operationIndex = buffer.readUnsignedByte();
                int statusIndex = buffer.readUnsignedByte();
                if (operationIndex >= Operation.values().length ||
                    statusIndex >= Status.values().length) return;
                operation = Operation.values()[operationIndex];
                status = Status.values()[statusIndex];
                NBTTagCompound payload = buffer.readBoolean() ? PacketHandler.readNBT(buffer) : null;
                snapshot = payload == null ? null : QIOManagementRecipeSnapshot.read(payload);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
                snapshot = null;
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                targetRevision < 0 || frequencyUUID == null || accessRevision < 0 ||
                requestId == null || operation == null || status == null) return false;
            if (operation == Operation.MUTATE) {
                return snapshot == null && status != Status.OK;
            }
            if (snapshot == null) {
                return status != Status.OK;
            }
            PageKind expected = operation == Operation.PRODUCTS ? PageKind.PRODUCTS :
                  PageKind.ROUTES;
            return status == Status.OK && snapshot.getPageKind() == expected;
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
