package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceRulePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationResult;
import mekanism.qioprocessing.common.terminal.QIOMaintenanceRuleService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOMaintenanceRuleMutationResult 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOMaintenanceRuleMutationResult implements
      IMessageHandler<PacketQIOMaintenanceRuleMutationResult.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOMaintenanceRulePageContainer container) ||
                player.openContainer.windowId != message.windowId) {
                return;
            }
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID,
                  message.accessRevision)) {
                container.getMaintenanceRuleClientCache().applyMutation(
                      message.sessionNonce, message.requestId, message.status,
                      message.rulesRevision, message.authoritativeRule);
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
        private MutationStatus status;
        private long rulesRevision;
        @Nullable
        private QIOMaintenanceRule authoritativeRule;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, MutationResult result) {
            this.windowId = windowId;
            sessionNonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            this.requestId = requestId;
            status = result.getStatus();
            rulesRevision = result.getRulesRevision();
            authoritativeRule = result.getAuthoritativeRule();
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              @Nonnull QIOProcessingTerminalSession session,
              @Nonnull UUID requestId, @Nonnull MutationResult result) {
            return session.getFrequencyUUID() == null ? new Message() :
                  new Message(windowId, session, requestId, result);
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
            buffer.writeByte(status.ordinal());
            buffer.writeLong(rulesRevision);
            buffer.writeBoolean(authoritativeRule != null);
            if (authoritativeRule != null) {
                PacketHandler.writeNBT(buffer, authoritativeRule.write());
            }
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
                int ordinal = buffer.readUnsignedByte();
                if (ordinal >= MutationStatus.values().length) {
                    return;
                }
                status = MutationStatus.values()[ordinal];
                rulesRevision = buffer.readLong();
                if (buffer.readBoolean()) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) {
                        return;
                    }
                    authoritativeRule = QIOMaintenanceRule.read(data);
                }
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
            }
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        QIOMaintenanceRule getAuthoritativeRule() {
            return authoritativeRule;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && terminalUUID != null &&
                  targetRevision >= 0 && frequencyUUID != null && accessRevision >= 0 &&
                  requestId != null && status != null && rulesRevision >= 0;
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
