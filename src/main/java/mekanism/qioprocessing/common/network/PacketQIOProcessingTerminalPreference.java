package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOBlockTerminalContainer;
import mekanism.qioprocessing.common.inventory.container.QIOPortableTerminalContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalSessionContainer;
import mekanism.qioprocessing.common.item.ItemPortableQIOProcessingTerminal;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.tile.TileEntityQIOSmartProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** Session-bound mutation of a terminal-local interaction preference. */
/**
 * QIO 处理模块中的 PacketQIOProcessingTerminalPreference 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOProcessingTerminalPreference implements
      IMessageHandler<PacketQIOProcessingTerminalPreference.Message, IMessage> {

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
        if (!(player.openContainer instanceof QIOProcessingTerminalSessionContainer holder) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) {
            return;
        }
        QIOProcessingTerminalSession session = holder.getTerminalSession();
        if (session == null || session.validate(message.sessionNonce,
              player.getUniqueID(), message.targetKind, message.terminalType,
              message.terminalUUID, message.expectedTargetRevision,
              session.getFrequencyUUID(), session.getAccessRevision()) !=
              QIOProcessingTerminalSession.Validation.ACCEPTED ||
            message.terminalType != QIOProcessingTerminalType.SMART_PROCESSING) {
            return;
        }
        if (message.targetKind == QIOProcessingTerminalSession.TargetKind.BLOCK &&
            player.openContainer instanceof QIOBlockTerminalContainer container &&
            container.getTerminalTile() instanceof TileEntityQIOSmartProcessingTerminal terminal &&
            terminal.getConfigurationRevision() == message.expectedTargetRevision) {
            terminal.toggleShiftClickIntoFrequency();
            container.acceptAuthorizedBindingChange(message.expectedTargetRevision,
                  terminal.getConfigurationRevision());
        } else if (message.targetKind ==
              QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM &&
              player.openContainer instanceof QIOPortableTerminalContainer container) {
            mutatePortable(message, player, container);
        }
    }

    private static void mutatePortable(Message message, EntityPlayer player,
          QIOPortableTerminalContainer container) {
        ItemStack stack = container.getPortableStack();
        if (stack.isEmpty() ||
            !(stack.getItem() instanceof ItemPortableQIOProcessingTerminal item) ||
            item.getTerminalType() != QIOProcessingTerminalType.SMART_PROCESSING) {
            return;
        }
        try {
            PortableQIOProcessingTerminalData data =
                  PortableQIOProcessingTerminalData.read(stack);
            if (data == null || data.getGeneration() != message.expectedTargetRevision ||
                !data.getTerminalUUID().equals(message.terminalUUID)) {
                return;
            }
            PortableQIOProcessingTerminalData updated =
                  data.withShiftClickIntoFrequency(!data.shiftClickIntoFrequency());
            updated.writeTo(stack);
            player.inventory.markDirty();
            container.acceptAuthorizedMutation(message.expectedTargetRevision,
                  updated.getGeneration());
        } catch (QIOProcessingDataException ignored) {
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private QIOProcessingTerminalSession.TargetKind targetKind;
        private QIOProcessingTerminalType terminalType;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce,
              QIOProcessingTerminalSession.TargetKind targetKind,
              QIOProcessingTerminalType terminalType, UUID terminalUUID,
              long expectedTargetRevision) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.targetKind = targetKind;
            this.terminalType = terminalType;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              QIOProcessingTerminalContainerState state) {
            return state == null || !state.isValid() ? new Message() :
                  new Message(windowId, state.getSessionNonce(), state.getTargetKind(),
                        state.getTerminalType(), state.getTerminalUUID(),
                        state.getTargetRevision());
        }

        static Message create(int windowId, QIOProcessingTerminalSession session) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTargetKind(),
                  session.getTerminalType(), session.getTerminalUUID(),
                  session.getTargetRevision());
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            buffer.writeByte(targetKind.ordinal());
            buffer.writeByte(terminalType.ordinal());
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                int kindOrdinal = buffer.readUnsignedByte();
                int typeOrdinal = buffer.readUnsignedByte();
                if (kindOrdinal >= QIOProcessingTerminalSession.TargetKind.values().length ||
                    typeOrdinal >= QIOProcessingTerminalType.values().length) {
                    return;
                }
                targetKind = QIOProcessingTerminalSession.TargetKind.values()[kindOrdinal];
                terminalType = QIOProcessingTerminalType.values()[typeOrdinal];
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                valid = structurallyValid();
            } catch (RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean structurallyValid() {
            return windowId >= 0 && sessionNonce != null && targetKind != null &&
                  terminalType == QIOProcessingTerminalType.SMART_PROCESSING &&
                  terminalUUID != null && expectedTargetRevision >= 0;
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
