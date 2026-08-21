package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIODeviceCommandResult;
import mekanism.qioprocessing.common.terminal.QIODeviceCommandService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIODeviceCommand 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIODeviceCommand implements
      IMessageHandler<PacketQIODeviceCommand.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOManagementDevicePageContainer container) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) return;
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (session == null || session.getFrequencyUUID() == null ||
            session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT ||
            session.validate(message.nonce, player.getUniqueID(), session.getTargetKind(),
                  QIOProcessingTerminalType.MANAGEMENT, message.terminalUUID,
                  message.targetRevision, session.getFrequencyUUID(),
                  session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED ||
            !container.tryRequestDeviceCommand(player.world.getTotalWorldTime())) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              session.getFrequencyUUID());
        if (network == null) return;
        try {
            QIODeviceCommandResult result = QIODeviceCommandService.execute(session, network,
                  session.getAccessRevision(), message.requestId, message.deviceUUID,
                  message.directoryRevision, message.configurationRevision, message.command,
                  player.world.getTotalWorldTime());
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIODeviceCommandResult.Message.create(message.windowId, session, result),
                  playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    public static final class Message implements IMessage {
        private int windowId;
        private UUID nonce;
        private UUID terminalUUID;
        private long targetRevision;
        private UUID requestId;
        private UUID deviceUUID;
        private long directoryRevision;
        private long configurationRevision;
        private QIODeviceCommandService.Command command;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID nonce, UUID terminalUUID, long targetRevision,
              UUID requestId, UUID deviceUUID, long directoryRevision,
              long configurationRevision, QIODeviceCommandService.Command command) {
            this.windowId = windowId;
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.requestId = requestId;
            this.deviceUUID = deviceUUID;
            this.directoryRevision = directoryRevision;
            this.configurationRevision = configurationRevision;
            this.command = command;
            valid = shape();
        }

        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, UUID deviceUUID, long directoryRevision,
              long configurationRevision, QIODeviceCommandService.Command command) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  requestId, deviceUUID, directoryRevision, configurationRevision, command);
        }

        static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID, long directoryRevision,
              long configurationRevision, QIODeviceCommandService.Command command) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), requestId, deviceUUID, directoryRevision,
                  configurationRevision, command);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, requestId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, deviceUUID);
            buffer.writeLong(directoryRevision);
            buffer.writeLong(configurationRevision);
            buffer.writeByte(command.ordinal());
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
                deviceUUID = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                directoryRevision = buffer.readLong();
                configurationRevision = buffer.readLong();
                int ordinal = buffer.readUnsignedByte();
                if (ordinal >= QIODeviceCommandService.Command.values().length) return;
                command = QIODeviceCommandService.Command.values()[ordinal];
                valid = shape();
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            return windowId >= 0 && nonce != null && terminalUUID != null &&
                  targetRevision >= 0 && requestId != null && deviceUUID != null &&
                  directoryRevision >= 0 && configurationRevision >= 0 && command != null;
        }
    }
}
