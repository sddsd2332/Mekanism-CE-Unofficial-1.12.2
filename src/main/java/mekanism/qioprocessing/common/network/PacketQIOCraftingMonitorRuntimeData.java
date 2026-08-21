package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOCraftingMonitorRuntimeData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorRuntimeData implements
      IMessageHandler<PacketQIOCraftingMonitorRuntimeData.Message, IMessage> {

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
                container.getCraftingMonitorClientCache().applyRuntime(message.nonce,
                      message.snapshot);
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
        private QIOCraftingMonitorRuntimeSnapshot snapshot;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session,
              QIOCraftingMonitorRuntimeSnapshot snapshot) {
            this.windowId = windowId;
            nonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            this.snapshot = snapshot;
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalSession session,
              QIOCraftingMonitorRuntimeSnapshot snapshot) {
            return session == null || session.getFrequencyUUID() == null ? new Message() :
                  new Message(windowId, session, snapshot);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            PacketHandler.writeNBT(buffer, snapshot.write());
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
                NBTTagCompound data = PacketHandler.readNBT(buffer);
                if (data == null) return;
                snapshot = QIOCraftingMonitorRuntimeSnapshot.read(data);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                snapshot = null;
            }
        }

        public boolean isValid() { return valid; }
        QIOCraftingMonitorRuntimeSnapshot getSnapshot() { return snapshot; }

        private boolean shape() {
            return windowId >= 0 && nonce != null && terminalUUID != null &&
                  targetRevision >= 0 && frequencyUUID != null && accessRevision >= 0 &&
                  snapshot != null;
        }
    }
}
