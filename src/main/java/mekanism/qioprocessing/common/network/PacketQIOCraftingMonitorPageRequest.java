package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
import mekanism.qioprocessing.common.terminal.QIOPage;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOCraftingMonitorPageRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorPageRequest implements
      IMessageHandler<PacketQIOCraftingMonitorPageRequest.Message, IMessage> {

    @Override public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOCraftingMonitorPageContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player)) return;
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session) ||
            !container.tryRequestCraftingMonitorPage(player.world.getTotalWorldTime())) return;
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) return;
        try {
            QIOPage<QIOCraftingMonitorEntry> page = QIOCraftingMonitorService.getPage(
                  session, network, session.getAccessRevision(), message.cursor,
                  message.pageSize);
            QIOProcessingPacketHandler.INSTANCE.sendTo(
                  PacketQIOCraftingMonitorPageData.Message.create(message.windowId,
                        session, page), playerMP);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
        }
    }

    private static boolean validSession(Message message, EntityPlayer player,
          QIOProcessingTerminalSession session) {
        return session != null && session.getFrequencyUUID() != null &&
              session.getTerminalType() == QIOProcessingTerminalType.CRAFTING_MONITOR &&
              session.validate(message.nonce, player.getUniqueID(), session.getTargetKind(),
                    QIOProcessingTerminalType.CRAFTING_MONITOR, message.terminalUUID,
                    message.targetRevision, session.getFrequencyUUID(),
                    session.getAccessRevision()) ==
                    QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    public static final class Message implements IMessage {
        private int windowId; private UUID nonce; private UUID terminalUUID;
        private long targetRevision; private int pageSize;
        @Nullable private QIOPageCursor cursor; private boolean valid;
        public Message() {}
        private Message(int windowId, UUID nonce, UUID terminalUUID, long targetRevision,
              int pageSize, @Nullable QIOPageCursor cursor) {
            this.windowId=windowId; this.nonce=nonce; this.terminalUUID=terminalUUID;
            this.targetRevision=targetRevision; this.pageSize=pageSize; this.cursor=cursor;
            valid=shape();
        }
        public static Message create(int windowId, QIOProcessingTerminalContainerState state,
              int pageSize, @Nullable QIOPageCursor cursor) {
            return state == null || !state.isValid() ? new Message() : new Message(windowId,
                  state.getSessionNonce(), state.getTerminalUUID(), state.getTargetRevision(),
                  pageSize, cursor);
        }
        static Message create(int windowId, QIOProcessingTerminalSession session,
              int pageSize, @Nullable QIOPageCursor cursor) {
            return session == null ? new Message() : new Message(windowId,
                  session.getSessionNonce(), session.getTerminalUUID(),
                  session.getTargetRevision(), pageSize, cursor);
        }
        @Override public void toBytes(ByteBuf b) { b.writeInt(windowId); uuid(b,nonce);
            uuid(b,terminalUUID); b.writeLong(targetRevision); b.writeInt(pageSize);
            b.writeBoolean(cursor!=null); if(cursor!=null)cursor.write(b); }
        @Override public void fromBytes(ByteBuf b) { valid=false; try { windowId=b.readInt();
            nonce=uuid(b); terminalUUID=uuid(b); targetRevision=b.readLong(); pageSize=b.readInt();
            cursor=b.readBoolean()?QIOPageCursor.read(b):null; valid=shape(); } catch(RuntimeException ignored){} }
        public boolean isValid(){return valid;}
        private boolean shape(){return windowId>=0&&nonce!=null&&terminalUUID!=null&&
              targetRevision>=0&&pageSize>0&&pageSize<=PacketQIOCraftingMonitorPageData.MAX_WIRE_PAGE_SIZE&&
              (cursor==null||nonce.equals(cursor.getSessionNonce()));}
        private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}
        private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}
    }
}
