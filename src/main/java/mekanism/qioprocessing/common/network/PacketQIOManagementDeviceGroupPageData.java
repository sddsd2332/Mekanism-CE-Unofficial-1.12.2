package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PacketQIOManagementDeviceGroupPageData implements
      IMessageHandler<PacketQIOManagementDeviceGroupPageData.Message, IMessage> {

    private static final int MAX_PAGE_SIZE = 1_024;

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || !player.world.isRemote) return null;
        PacketHandler.handlePacket(() -> {
            if (!message.valid ||
                !(player.openContainer instanceof QIOManagementDevicePageContainer container) ||
                player.openContainer.windowId != message.windowId) return;
            QIOProcessingTerminalContainerState state = container.getTerminalState();
            if (!state.matches(message.sessionNonce, message.terminalUUID,
                  message.targetRevision, message.frequencyUUID,
                  message.accessRevision)) return;
            container.getDeviceGroupClientCache().apply(message.sessionNonce,
                  message.sourceRevision, message.offset, message.totalSize,
                  message.groups, message.nextCursor);
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
        private List<QIOManagementDeviceGroupSnapshot> groups = Collections.emptyList();
        @Nullable private QIOPageCursor nextCursor;
        private boolean valid;

        public Message() {
        }

        public static Message create(int windowId, QIOProcessingTerminalSession session,
              QIOPage<QIOManagementDeviceGroupSnapshot> page) {
            Message message = new Message();
            if (session != null && session.getFrequencyUUID() != null) {
                message.windowId = windowId;
                message.sessionNonce = session.getSessionNonce();
                message.terminalUUID = session.getTerminalUUID();
                message.targetRevision = session.getTargetRevision();
                message.frequencyUUID = session.getFrequencyUUID();
                message.accessRevision = session.getAccessRevision();
                message.sourceRevision = page.getSourceRevision();
                message.offset = page.getOffset();
                message.totalSize = page.getTotalSize();
                message.groups = Collections.unmodifiableList(
                      new ArrayList<>(page.getEntries()));
                message.nextCursor = page.getNextCursor();
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
            buffer.writeLong(sourceRevision);
            buffer.writeInt(offset);
            buffer.writeInt(totalSize);
            buffer.writeBoolean(nextCursor != null);
            if (nextCursor != null) nextCursor.write(buffer);
            buffer.writeInt(groups.size());
            for (QIOManagementDeviceGroupSnapshot group : groups) {
                PacketHandler.writeNBT(buffer, group.write());
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
                if (count < 0 || count > MAX_PAGE_SIZE) return;
                List<QIOManagementDeviceGroupSnapshot> decoded = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) return;
                    decoded.add(QIOManagementDeviceGroupSnapshot.read(data));
                }
                groups = Collections.unmodifiableList(decoded);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
                groups = Collections.emptyList();
            }
        }

        public boolean isValid() { return valid; }

        List<QIOManagementDeviceGroupSnapshot> getGroups() { return groups; }

        private boolean shape() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                targetRevision < 0 || frequencyUUID == null || accessRevision < 0 ||
                sourceRevision < 0 || offset < 0 || totalSize < 0 ||
                offset > totalSize || groups.size() > MAX_PAGE_SIZE ||
                groups.size() > totalSize - offset) return false;
            if (nextCursor == null) return offset + groups.size() == totalSize;
            return sessionNonce.equals(nextCursor.getSessionNonce()) &&
                  sourceRevision == nextCursor.getSourceRevision() &&
                  nextCursor.getOffset() == offset + groups.size() &&
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
