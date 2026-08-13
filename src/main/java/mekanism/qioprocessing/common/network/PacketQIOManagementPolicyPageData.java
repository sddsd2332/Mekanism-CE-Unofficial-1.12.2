package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.inventory.container.QIOManagementPolicyPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.PageMode;
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

/** One bounded server-authoritative management policy page. */
public final class PacketQIOManagementPolicyPageData implements
      IMessageHandler<PacketQIOManagementPolicyPageData.Message, IMessage> {

    public static final int MAX_WIRE_PAGE_SIZE = 1_024;

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
            container.getPolicyClientCache().applyPage(message.sessionNonce,
                  message.requestId, message.pageMode,
                  message.recipeCatalogRevision,
                  message.sourceRevision, message.offset, message.totalSize,
                  message.entries, message.nextCursor);
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
        private PageMode pageMode;
        private long recipeCatalogRevision;
        private long sourceRevision;
        private int offset;
        private int totalSize;
        private List<QIOPolicyEntrySnapshot> entries = Collections.emptyList();
        @Nullable
        private QIOPageCursor nextCursor;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, UUID sessionNonce, UUID terminalUUID,
              long targetRevision, UUID frequencyUUID, long accessRevision,
              UUID requestId, PageMode pageMode, long recipeCatalogRevision,
              long sourceRevision,
              int offset, int totalSize, List<QIOPolicyEntrySnapshot> entries,
              @Nullable QIOPageCursor nextCursor) {
            this.windowId = windowId;
            this.sessionNonce = sessionNonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequencyUUID = frequencyUUID;
            this.accessRevision = accessRevision;
            this.requestId = requestId;
            this.pageMode = pageMode;
            this.recipeCatalogRevision = recipeCatalogRevision;
            this.sourceRevision = sourceRevision;
            this.offset = offset;
            this.totalSize = totalSize;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.nextCursor = nextCursor;
            valid = structurallyValid();
        }

        public static Message create(int windowId,
              @Nonnull mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession session,
              @Nonnull QIOPage<QIOPolicyEntrySnapshot> page) {
            return create(windowId, session, UUID.randomUUID(), PageMode.CONFIGURED,
                  -1, page);
        }

        public static Message create(int windowId,
              @Nonnull mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession session,
              @Nonnull UUID requestId, @Nonnull PageMode pageMode,
              long recipeCatalogRevision,
              @Nonnull QIOPage<QIOPolicyEntrySnapshot> page) {
            if (session.getFrequencyUUID() == null) {
                return new Message();
            }
            return new Message(windowId, session.getSessionNonce(),
                  session.getTerminalUUID(), session.getTargetRevision(),
                  session.getFrequencyUUID(), session.getAccessRevision(),
                  requestId, pageMode, recipeCatalogRevision,
                  page.getSourceRevision(),
                  page.getOffset(), page.getTotalSize(), page.getEntries(),
                  page.getNextCursor());
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
            buffer.writeByte(pageMode.ordinal());
            buffer.writeLong(recipeCatalogRevision);
            buffer.writeLong(sourceRevision);
            buffer.writeInt(offset);
            buffer.writeInt(totalSize);
            buffer.writeBoolean(nextCursor != null);
            if (nextCursor != null) {
                nextCursor.write(buffer);
            }
            buffer.writeInt(entries.size());
            for (QIOPolicyEntrySnapshot entry : entries) {
                PacketHandler.writeNBT(buffer, entry.write());
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
                int modeOrdinal = buffer.readUnsignedByte();
                if (modeOrdinal >= PageMode.values().length) {
                    return;
                }
                pageMode = PageMode.values()[modeOrdinal];
                recipeCatalogRevision = buffer.readLong();
                sourceRevision = buffer.readLong();
                offset = buffer.readInt();
                totalSize = buffer.readInt();
                nextCursor = buffer.readBoolean() ? QIOPageCursor.read(buffer) : null;
                int count = buffer.readInt();
                if (count < 0 || count > MAX_WIRE_PAGE_SIZE || totalSize < 0 ||
                    offset < 0 || offset > totalSize || count > totalSize - offset) {
                    return;
                }
                List<QIOPolicyEntrySnapshot> decoded = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    NBTTagCompound data = PacketHandler.readNBT(buffer);
                    if (data == null) {
                        return;
                    }
                    decoded.add(QIOPolicyEntrySnapshot.read(data));
                }
                entries = Collections.unmodifiableList(decoded);
                valid = structurallyValid();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
                entries = Collections.emptyList();
            }
        }

        public boolean isValid() {
            return valid;
        }

        @Nonnull
        List<QIOPolicyEntrySnapshot> getEntries() {
            return entries;
        }

        private boolean structurallyValid() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                targetRevision < 0 || frequencyUUID == null || accessRevision < 0 ||
                requestId == null || pageMode == null || recipeCatalogRevision < -1 ||
                (pageMode == PageMode.CONFIGURED ? recipeCatalogRevision != -1 :
                      recipeCatalogRevision < 0) ||
                sourceRevision < 0 ||
                offset < 0 || totalSize < 0 || offset > totalSize ||
                entries.size() > MAX_WIRE_PAGE_SIZE ||
                entries.size() > totalSize - offset || entries.stream().anyMatch(
                      entry -> entry.getPolicyRevision() != sourceRevision)) {
                return false;
            }
            if (nextCursor == null) {
                return offset + entries.size() == totalSize;
            }
            return sessionNonce.equals(nextCursor.getSessionNonce()) &&
                  sourceRevision == nextCursor.getSourceRevision() &&
                  nextCursor.getOffset() == offset + entries.size() &&
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
