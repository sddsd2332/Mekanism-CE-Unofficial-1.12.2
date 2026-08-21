package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.inventory.container.QIOMaintenanceRulePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOMaintenanceRulePageData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOMaintenanceRulePageData implements
      IMessageHandler<PacketQIOMaintenanceRulePageData.Message, IMessage> {

    public static final int MAX_WIRE_PAGE_SIZE = 1_024;

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
                container.getMaintenanceRuleClientCache().applyPage(message.sessionNonce,
                      message.sourceRevision, message.offset, message.totalSize,
                      message.rules, message.nextCursor);
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
        private long sourceRevision;
        private int offset;
        private int totalSize;
        private List<QIOMaintenanceRule> rules = Collections.emptyList();
        @Nullable
        private QIOPageCursor nextCursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session,
              QIOPage<QIOMaintenanceRule> page) {
            this.windowId = windowId;
            sessionNonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            sourceRevision = page.getSourceRevision();
            offset = page.getOffset();
            totalSize = page.getTotalSize();
            rules = Collections.unmodifiableList(new ArrayList<>(page.getEntries()));
            nextCursor = page.getNextCursor();
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              @Nonnull QIOProcessingTerminalSession session,
              @Nonnull QIOPage<QIOMaintenanceRule> page) {
            return session.getFrequencyUUID() == null ? new Message() :
                  new Message(windowId, session, page);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            buffer.writeLong(sourceRevision);
            buffer.writeInt(offset);
            buffer.writeInt(totalSize);
            buffer.writeBoolean(nextCursor != null);
            if (nextCursor != null) {
                nextCursor.write(buffer);
            }
            buffer.writeInt(rules.size());
            for (QIOMaintenanceRule rule : rules) {
                PacketHandler.writeNBT(buffer, rule.write());
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
                sourceRevision = buffer.readLong();
                offset = buffer.readInt();
                totalSize = buffer.readInt();
                nextCursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                int count = buffer.readInt();
                if (count < 0 || count > MAX_WIRE_PAGE_SIZE || totalSize < 0 ||
                    offset < 0 || offset > totalSize || count > totalSize - offset) {
                    return;
                }
                List<QIOMaintenanceRule> decoded = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) {
                        return;
                    }
                    decoded.add(QIOMaintenanceRule.read(data));
                }
                rules = Collections.unmodifiableList(decoded);
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                rules = Collections.emptyList();
            }
        }

        public boolean isValid() {
            return valid;
        }

        @Nonnull
        List<QIOMaintenanceRule> getRules() {
            return rules;
        }

        private boolean structurallyValid() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                targetRevision < 0 || frequencyUUID == null || accessRevision < 0 ||
                sourceRevision < 0 || offset < 0 || totalSize < 0 ||
                offset > totalSize || rules.size() > MAX_WIRE_PAGE_SIZE ||
                rules.size() > totalSize - offset) {
                return false;
            }
            if (nextCursor == null) {
                return offset + rules.size() == totalSize;
            }
            return sessionNonce.equals(nextCursor.getSessionNonce()) &&
                  sourceRevision == nextCursor.getSourceRevision() &&
                  nextCursor.getOffset() == offset + rules.size() &&
                  nextCursor.getOffset() < totalSize;
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
