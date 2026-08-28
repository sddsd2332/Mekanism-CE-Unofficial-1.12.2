package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationRequest.Operation;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyPreview;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosurePreview;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Client response for one workbench configuration request. */
/**
 * QIO 处理模块中的 PacketQIOWorkbenchConfigurationData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOWorkbenchConfigurationData implements
      IMessageHandler<PacketQIOWorkbenchConfigurationData.Message, IMessage> {

    public enum Status {
        OK,
        READY,
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        CATALOG_CHANGED,
        INVALID_TARGET,
        INVALID_PATTERN,
        READ_ONLY,
        LAST_CANDIDATE,
        ALREADY_IMPORTED,
        NOT_FOUND,
        ACCESS_DENIED,
        EXPIRED,
        SOURCE_CHANGED,
        TARGET_CHANGED,
        UNAVAILABLE,
        BUSY
    }

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) return null;
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOWorkbenchConfigurationContainer container) ||
                player.openContainer.windowId != message.windowId) return;
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (!state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID,
                  message.accessRevision)) return;
            if (message.snapshot != null) {
                container.getWorkbenchConfigurationClientCache().applyPage(message.requestId,
                      message.status, message.snapshot);
            } else if (message.operation == Operation.PRODUCTS ||
                      message.operation == Operation.RECIPES ||
                      message.operation == Operation.CANDIDATES) {
                container.getWorkbenchConfigurationClientCache().applyUnavailable(
                      message.operation, message.requestId, message.status);
            } else if (message.operation == Operation.MUTATE) {
                container.getWorkbenchConfigurationClientCache().applyMutation(
                      message.requestId, message.status);
            } else if (message.operation == Operation.CLOSURE_PREVIEW ||
                      message.operation == Operation.CLOSURE_CONFIRM) {
                container.getWorkbenchConfigurationClientCache().applyClosure(
                      message.requestId, message.status, message.closurePreview);
            } else {
                container.getWorkbenchConfigurationClientCache().applyCopy(message.requestId,
                      message.status, message.copyPreview);
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
        @Nullable private QIOWorkbenchConfigurationSnapshot snapshot;
        @Nullable private QIOWorkbenchCopyPreview copyPreview;
        @Nullable private QIOWorkbenchClosurePreview closurePreview;
        private boolean valid;

        public Message() {
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, Operation operation, Status status,
              @Nullable QIOWorkbenchConfigurationSnapshot snapshot,
              @Nullable QIOWorkbenchCopyPreview copyPreview) {
            return create(windowId, session, requestId, operation, status, snapshot,
                  copyPreview, null);
        }

        @Nonnull
        public static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, Operation operation, Status status,
              @Nullable QIOWorkbenchConfigurationSnapshot snapshot,
              @Nullable QIOWorkbenchCopyPreview copyPreview,
              @Nullable QIOWorkbenchClosurePreview closurePreview) {
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
                message.copyPreview = copyPreview;
                message.closurePreview = closurePreview;
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
            buffer.writeBoolean(copyPreview != null);
            if (copyPreview != null) PacketHandler.writeNBT(buffer, copyPreview.write());
            buffer.writeBoolean(closurePreview != null);
            if (closurePreview != null) PacketHandler.writeNBT(buffer,
                  closurePreview.write());
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
                NBTTagCompound storedSnapshot = buffer.readBoolean() ?
                      PacketHandler.readNBT(buffer) : null;
                snapshot = storedSnapshot == null ? null :
                      QIOWorkbenchConfigurationSnapshot.read(storedSnapshot);
                NBTTagCompound storedPreview = buffer.readBoolean() ?
                      PacketHandler.readNBT(buffer) : null;
                copyPreview = storedPreview == null ? null :
                      QIOWorkbenchCopyPreview.read(storedPreview);
                NBTTagCompound storedClosurePreview = buffer.readBoolean() ?
                      PacketHandler.readNBT(buffer) : null;
                closurePreview = storedClosurePreview == null ? null :
                      QIOWorkbenchClosurePreview.read(storedClosurePreview);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
                snapshot = null;
                copyPreview = null;
                closurePreview = null;
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                targetRevision < 0 || frequencyUUID == null || accessRevision < 0 ||
                requestId == null || operation == null || status == null) {
                return false;
            }
            return switch (operation) {
                case PRODUCTS, RECIPES, CANDIDATES -> copyPreview == null &&
                      closurePreview == null &&
                      (status == Status.OK) == (snapshot != null) &&
                      (snapshot == null || snapshot.getPageKind().name().equals(
                            operation.name()));
                case MUTATE -> snapshot == null && copyPreview == null &&
                      closurePreview == null &&
                      status != Status.OK && status != Status.READY;
                case COPY_PREVIEW -> snapshot == null &&
                      closurePreview == null &&
                      (status == Status.READY) == (copyPreview != null);
                case COPY_CONFIRM -> snapshot == null && copyPreview == null &&
                      closurePreview == null &&
                      status != Status.OK && status != Status.READY;
                case CLOSURE_PREVIEW -> snapshot == null && copyPreview == null &&
                      (status == Status.READY) == (closurePreview != null);
                case CLOSURE_CONFIRM -> snapshot == null && copyPreview == null &&
                      closurePreview == null && status != Status.OK &&
                      status != Status.READY;
            };
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
