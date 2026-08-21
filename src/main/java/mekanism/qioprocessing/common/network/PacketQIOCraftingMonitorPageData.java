package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
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
 * QIO 处理模块中的 PacketQIOCraftingMonitorPageData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorPageData implements
      IMessageHandler<PacketQIOCraftingMonitorPageData.Message, IMessage> {
    public static final int MAX_WIRE_PAGE_SIZE=1024;
    @Override public IMessage onMessage(Message m, MessageContext c){EntityPlayer p=PacketHandler.getPlayer(c);
        if(p==null||!p.world.isRemote)return null; PacketHandler.handlePacket(()->{
            if(!m.valid||!(p.openContainer instanceof QIOCraftingMonitorPageContainer x)||p.openContainer.windowId!=m.windowId)return;
            QIOProcessingTerminalContainerState s=x.getTerminalState();
            if(s.matches(m.nonce,m.terminalUUID,m.targetRevision,m.frequencyUUID,m.accessRevision))
                x.getCraftingMonitorClientCache().applyPage(m.nonce,m.sourceRevision,m.offset,m.total,m.entries,m.cursor);
        },p);return null;}
    public static final class Message implements IMessage {
        private int windowId;private UUID nonce,terminalUUID,frequencyUUID;private long targetRevision,accessRevision,sourceRevision;
        private int offset,total;private List<QIOCraftingMonitorEntry> entries=Collections.emptyList();@Nullable private QIOPageCursor cursor;private boolean valid;
        public Message(){}
        private Message(int w,QIOProcessingTerminalSession s,QIOPage<QIOCraftingMonitorEntry> p){windowId=w;nonce=s.getSessionNonce();terminalUUID=s.getTerminalUUID();targetRevision=s.getTargetRevision();frequencyUUID=s.getFrequencyUUID();accessRevision=s.getAccessRevision();sourceRevision=p.getSourceRevision();offset=p.getOffset();total=p.getTotalSize();entries=Collections.unmodifiableList(new ArrayList<>(p.getEntries()));cursor=p.getNextCursor();valid=shape();}
        public static Message create(int w,@Nonnull QIOProcessingTerminalSession s,@Nonnull QIOPage<QIOCraftingMonitorEntry> p){return s.getFrequencyUUID()==null?new Message():new Message(w,s,p);}
        @Override public void toBytes(ByteBuf b){b.writeInt(windowId);uuid(b,nonce);uuid(b,terminalUUID);b.writeLong(targetRevision);uuid(b,frequencyUUID);b.writeLong(accessRevision);b.writeLong(sourceRevision);b.writeInt(offset);b.writeInt(total);b.writeBoolean(cursor!=null);if(cursor!=null)cursor.write(b);b.writeInt(entries.size());for(QIOCraftingMonitorEntry e:entries)PacketHandler.writeNBT(b,e.write());}
        @Override public void fromBytes(ByteBuf b){valid=false;try{windowId=b.readInt();nonce=uuid(b);terminalUUID=uuid(b);targetRevision=b.readLong();frequencyUUID=uuid(b);accessRevision=b.readLong();sourceRevision=b.readLong();offset=b.readInt();total=b.readInt();cursor=b.readBoolean()?QIOPageCursor.read(b):null;int n=b.readInt();if(n<0||n>MAX_WIRE_PAGE_SIZE||offset<0||total<0||offset>total||n>total-offset)return;List<QIOCraftingMonitorEntry>d=new ArrayList<>(n);for(int i=0;i<n;i++){NBTTagCompound tag=PacketHandler.readNBT(b);if(tag==null)return;d.add(QIOCraftingMonitorEntry.read(tag));}entries=Collections.unmodifiableList(d);valid=shape();}catch(QIOProcessingDataException|RuntimeException ignored){entries=Collections.emptyList();}}
        public boolean isValid(){return valid;} @Nonnull List<QIOCraftingMonitorEntry> getEntries(){return entries;}
        private boolean shape(){if(windowId<0||nonce==null||terminalUUID==null||frequencyUUID==null||targetRevision<0||accessRevision<0||sourceRevision<0||offset<0||total<0||offset>total||entries.size()>MAX_WIRE_PAGE_SIZE||entries.size()>total-offset)return false;return cursor==null?offset+entries.size()==total:nonce.equals(cursor.getSessionNonce())&&sourceRevision==cursor.getSourceRevision()&&cursor.getOffset()==offset+entries.size()&&cursor.getOffset()<total;}
        private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}
    }
}
