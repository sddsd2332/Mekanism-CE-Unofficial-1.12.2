package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import java.util.UUID;

/** Authoritative device-default row returned to a management editor. */
/**
 * QIO 处理模块中的 PacketQIOManagementPolicyLookupData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOManagementPolicyLookupData implements
      IMessageHandler<PacketQIOManagementPolicyLookupData.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOManagementPolicyPageContainer container) ||
                player.openContainer.windowId != message.windowId) {
                return;
            }
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (!state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID,
                  message.accessRevision)) {
                return;
            }
            container.getPolicyClientCache().applyLookup(message.sessionNonce,
                  message.requestId, message.authoritativeEntry);
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
        private QIOPolicyEntrySnapshot authoritativeEntry;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long targetRevision, UUID frequencyUUID, long accessRevision,
              UUID requestId, QIOPolicyEntrySnapshot authoritativeEntry) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequencyUUID = frequencyUUID;
            this.accessRevision = accessRevision;
            this.requestId = requestId;
            this.authoritativeEntry = authoritativeEntry;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              @Nonnull QIOProcessingTerminalSession session,
              @Nonnull UUID requestId,
              @Nonnull QIOPolicyEntrySnapshot authoritativeEntry) {
            if (session.getFrequencyUUID() == null) {
                return new Message();
            }
            return new Message(windowId, session.getSessionNonce(),
                  session.getTerminalUUID(), session.getTargetRevision(),
                  session.getFrequencyUUID(), session.getAccessRevision(), requestId,
                  authoritativeEntry);
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
            PacketHandler.writeNBT(buffer, authoritativeEntry.write());
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
                NBTTagCompound data = PacketHandler.readNBT(buffer);
                if (data == null) {
                    return;
                }
                authoritativeEntry = QIOPolicyEntrySnapshot.read(data);
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }

        QIOPolicyEntrySnapshot getAuthoritativeEntry() {
            return authoritativeEntry;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  targetRevision >= 0 && frequencyUUID != null && accessRevision >= 0 &&
                  requestId != null && authoritativeEntry != null &&
                  authoritativeEntry.getKind() ==
                        QIOPolicyEntrySnapshot.Kind.DEVICE_DEFAULT &&
                  authoritativeEntry.getDeviceUUID() != null;
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
