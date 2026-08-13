package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PacketQIOCraftingMonitorPlanPageData implements
      IMessageHandler<PacketQIOCraftingMonitorPlanPageData.Message, IMessage> {

    public static final int MAX_PAGE_SIZE = 1_024;

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
                container.getCraftingMonitorClientCache().applyPlanPage(message.nonce,
                      message.jobId, message.planRevision, message.structuralSignature,
                      message.sourceRevision, message.offset, message.total, message.entries,
                      message.cursor);
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
        private UUID jobId;
        private int planRevision;
        private String structuralSignature = "";
        private long sourceRevision;
        private int offset;
        private int total;
        private List<QIOCraftingMonitorPlanEntry> entries = Collections.emptyList();
        @Nullable private QIOPageCursor cursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalSession session, UUID jobId,
              int planRevision, String structuralSignature,
              QIOPage<QIOCraftingMonitorPlanEntry> page) {
            this.windowId = windowId;
            nonce = session.getSessionNonce();
            terminalUUID = session.getTerminalUUID();
            targetRevision = session.getTargetRevision();
            frequencyUUID = session.getFrequencyUUID();
            accessRevision = session.getAccessRevision();
            this.jobId = jobId;
            this.planRevision = planRevision;
            this.structuralSignature = structuralSignature;
            sourceRevision = page.getSourceRevision();
            offset = page.getOffset();
            total = page.getTotalSize();
            entries = Collections.unmodifiableList(new ArrayList<>(page.getEntries()));
            cursor = page.getNextCursor();
            valid = shape();
        }

        public static Message create(int windowId, @Nonnull QIOProcessingTerminalSession session,
              @Nonnull UUID jobId, int planRevision, @Nonnull String structuralSignature,
              @Nonnull QIOPage<QIOCraftingMonitorPlanEntry> page) {
            return session.getFrequencyUUID() == null ? new Message() : new Message(windowId,
                  session, jobId, planRevision, structuralSignature, page);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, nonce);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, terminalUUID);
            buffer.writeLong(targetRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, frequencyUUID);
            buffer.writeLong(accessRevision);
            PacketQIOCraftingMonitorPlanPageRequest.writeUUID(buffer, jobId);
            buffer.writeInt(planRevision);
            writeString(buffer, structuralSignature);
            buffer.writeLong(sourceRevision);
            buffer.writeInt(offset);
            buffer.writeInt(total);
            buffer.writeBoolean(cursor != null);
            if (cursor != null) cursor.write(buffer);
            buffer.writeInt(entries.size());
            for (QIOCraftingMonitorPlanEntry entry : entries) {
                PacketHandler.writeNBT(buffer, entry.write());
            }
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
                jobId = PacketQIOCraftingMonitorPlanPageRequest.readUUID(buffer);
                planRevision = buffer.readInt();
                structuralSignature = readString(buffer, 128);
                sourceRevision = buffer.readLong();
                offset = buffer.readInt();
                total = buffer.readInt();
                cursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                int count = buffer.readInt();
                if (count < 0 || count > MAX_PAGE_SIZE || offset < 0 || total < 0 ||
                    offset > total || count > total - offset) return;
                List<QIOCraftingMonitorPlanEntry> decoded = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) return;
                    decoded.add(QIOCraftingMonitorPlanEntry.read(data));
                }
                entries = Collections.unmodifiableList(decoded);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                entries = Collections.emptyList();
            }
        }

        public boolean isValid() { return valid; }
        @Nonnull List<QIOCraftingMonitorPlanEntry> getEntries() { return entries; }

        private boolean shape() {
            if (windowId < 0 || nonce == null || terminalUUID == null || frequencyUUID == null ||
                targetRevision < 0 || accessRevision < 0 || jobId == null || planRevision <= 0 ||
                structuralSignature.isEmpty() || structuralSignature.length() > 128 ||
                sourceRevision < 0 || offset < 0 || total < 0 || offset > total ||
                entries.size() > MAX_PAGE_SIZE || entries.size() > total - offset) return false;
            return cursor == null ? offset + entries.size() == total :
                  nonce.equals(cursor.getSessionNonce()) &&
                        sourceRevision == cursor.getSourceRevision() &&
                        cursor.getOffset() == offset + entries.size() &&
                        cursor.getOffset() < total;
        }

        private static void writeString(ByteBuf buffer, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.writeShort(bytes.length);
            buffer.writeBytes(bytes);
        }

        private static String readString(ByteBuf buffer, int maximum) {
            int length = buffer.readUnsignedShort();
            if (length > maximum * 4 || length > buffer.readableBytes()) {
                throw new IllegalArgumentException("QIO monitor string is too large");
            }
            String value = buffer.readCharSequence(length, StandardCharsets.UTF_8).toString();
            if (value.length() > maximum) throw new IllegalArgumentException("QIO monitor string is too long");
            return value;
        }
    }
}
