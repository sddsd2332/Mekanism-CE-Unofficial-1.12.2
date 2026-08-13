package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IdentitySerializer;
import mekanism.qioprocessing.common.inventory.container.QIOPortableTerminalContainer;
import mekanism.qioprocessing.common.inventory.container.QIOBlockTerminalContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalSessionContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOPortableTerminalBindingService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalBindingService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/** Exact frequency bind/unbind command shared by block and portable terminal sessions. */
public final class PacketQIOProcessingTerminalBinding implements
      IMessageHandler<PacketQIOProcessingTerminalBinding.Message, IMessage> {

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
              QIOProcessingTerminalSession.Validation.ACCEPTED) {
            return;
        }
        QIOFrequency frequency = message.bind ? resolve(message.identity,
              message.frequencyUUID) : null;
        if (message.bind && frequency == null) {
            return;
        }
        if (message.targetKind == QIOProcessingTerminalSession.TargetKind.BLOCK &&
            player.openContainer instanceof QIOBlockTerminalContainer container) {
            QIOProcessingTerminal terminal = container.getTerminalTile();
            if (terminal == null || terminal.getConfigurationRevision() !=
                message.expectedTargetRevision) {
                return;
            }
            boolean changed = message.bind ? QIOProcessingTerminalBindingService.bind(
                  terminal, frequency, player) :
                  QIOProcessingTerminalBindingService.unbind(terminal, player);
            if (changed) {
                container.acceptAuthorizedBindingChange(message.expectedTargetRevision,
                      terminal.getConfigurationRevision());
            }
        } else if (message.targetKind ==
              QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM &&
              player.openContainer instanceof QIOPortableTerminalContainer container) {
            boolean changed = message.bind ? QIOPortableTerminalBindingService.bind(
                  container, frequency, player) :
                  QIOPortableTerminalBindingService.unbind(container, player);
            if (changed) {
                try {
                    mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData data =
                          mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData.read(
                                container.getPortableStack());
                    if (data != null) {
                        container.acceptAuthorizedMutation(message.expectedTargetRevision,
                              data.getGeneration());
                    }
                } catch (mekanism.qioprocessing.common.content.QIOProcessingDataException ignored) {
                }
            }
        }
    }

    @Nullable
    private static QIOFrequency resolve(@Nullable FrequencyIdentity identity,
          @Nullable UUID expectedFrequencyUUID) {
        if (identity == null || expectedFrequencyUUID == null) {
            return null;
        }
        FrequencyManager<QIOFrequency> manager = FrequencyType.QIO.getManager(
              identity.ownerUUID(), identity.securityMode());
        QIOFrequency frequency = manager == null ? null :
              manager.getFrequency(identity.key());
        return frequency != null && expectedFrequencyUUID.equals(
              frequency.getFrequencyUUID()) ? frequency : null;
    }

    public static final class Message implements IMessage {

        private boolean bind;
        private int windowId;
        private UUID sessionNonce;
        private QIOProcessingTerminalSession.TargetKind targetKind;
        private QIOProcessingTerminalType terminalType;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        @Nullable
        private FrequencyIdentity identity;
        @Nullable
        private UUID frequencyUUID;
        private boolean valid;

        public Message() {
        }

        private Message(boolean bind, int windowId, UUID sessionNonce,
              QIOProcessingTerminalSession.TargetKind targetKind,
              QIOProcessingTerminalType terminalType, UUID terminalUUID,
              long expectedTargetRevision, @Nullable FrequencyIdentity identity,
              @Nullable UUID frequencyUUID) {
            this.bind = bind;
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.targetKind = targetKind;
            this.terminalType = terminalType;
            this.terminalUUID = terminalUUID;
            this.expectedTargetRevision = expectedTargetRevision;
            this.identity = identity;
            this.frequencyUUID = frequencyUUID;
            valid = isStructurallyValid();
        }

        public static Message bind(int windowId,
              QIOProcessingTerminalSession session, FrequencyIdentity identity,
              UUID frequencyUUID) {
            return new Message(true, windowId, session.getSessionNonce(),
                  session.getTargetKind(), session.getTerminalType(),
                  session.getTerminalUUID(), session.getTargetRevision(), identity,
                  frequencyUUID);
        }

        public static Message unbind(int windowId,
              QIOProcessingTerminalSession session) {
            return new Message(false, windowId, session.getSessionNonce(),
                  session.getTargetKind(), session.getTerminalType(),
                  session.getTerminalUUID(), session.getTargetRevision(), null, null);
        }

        public static Message bind(int windowId,
              QIOProcessingTerminalContainerState state, FrequencyIdentity identity,
              UUID frequencyUUID) {
            if (state == null || !state.isValid()) {
                return new Message();
            }
            return new Message(true, windowId, state.getSessionNonce(),
                  state.getTargetKind(), state.getTerminalType(),
                  state.getTerminalUUID(), state.getTargetRevision(), identity,
                  frequencyUUID);
        }

        public static Message unbind(int windowId,
              QIOProcessingTerminalContainerState state) {
            if (state == null || !state.isValid()) {
                return new Message();
            }
            return new Message(false, windowId, state.getSessionNonce(),
                  state.getTargetKind(), state.getTerminalType(),
                  state.getTerminalUUID(), state.getTargetRevision(), null, null);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(bind);
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            buffer.writeByte(targetKind.ordinal());
            buffer.writeByte(terminalType.ordinal());
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            if (bind) {
                IdentitySerializer.NAME.write(buffer, identity);
                writeUUID(buffer, frequencyUUID);
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                bind = buffer.readBoolean();
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                int targetOrdinal = buffer.readUnsignedByte();
                int typeOrdinal = buffer.readUnsignedByte();
                if (targetOrdinal >= QIOProcessingTerminalSession.TargetKind.values().length ||
                    typeOrdinal >= QIOProcessingTerminalType.values().length) {
                    return;
                }
                targetKind = QIOProcessingTerminalSession.TargetKind.values()[targetOrdinal];
                terminalType = QIOProcessingTerminalType.values()[typeOrdinal];
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                if (bind) {
                    identity = IdentitySerializer.NAME.read(buffer);
                    frequencyUUID = readUUID(buffer);
                } else {
                    identity = null;
                    frequencyUUID = null;
                }
                valid = isStructurallyValid();
            } catch (RuntimeException e) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }

        private boolean isStructurallyValid() {
            return windowId >= 0 && sessionNonce != null && targetKind != null &&
                  terminalType != null && terminalUUID != null &&
                  expectedTargetRevision >= 0 &&
                  (!bind || identity != null && frequencyUUID != null);
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
