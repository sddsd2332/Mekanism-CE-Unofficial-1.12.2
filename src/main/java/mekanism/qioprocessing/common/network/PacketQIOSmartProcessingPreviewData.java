package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOSmartProcessingPreviewData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOSmartProcessingPreviewData implements IMessageHandler<PacketQIOSmartProcessingPreviewData.Message,IMessage>{
    @Override public IMessage onMessage(Message message,MessageContext context){EntityPlayer p=PacketHandler.getPlayer(context);if(p==null||!p.world.isRemote)return null;PacketHandler.handlePacket(()->{if(!message.valid||!(p.openContainer instanceof QIOSmartProcessingPageContainer c)||p.openContainer.windowId!=message.windowId)return;QIOProcessingTerminalContainerState s=c.getTerminalState();if(s.matches(message.sessionNonce,message.terminalUUID,message.targetRevision,message.frequencyUUID,message.accessRevision))c.getSmartProcessingClientCache().applyPreview(message.sessionNonce,message.requestId,message.actionStatus,message.snapshot);},p);return null;}
    public static final class Message implements IMessage{
        private int windowId;private UUID sessionNonce,terminalUUID,frequencyUUID,requestId,jobId;private long targetRevision,accessRevision;private String actionStatus="";@Nullable private QIOSmartProcessingPreviewSnapshot snapshot;private boolean valid;
        public Message(){}
        private Message(int w,QIOProcessingTerminalSession s,UUID request,String status,@Nullable QIOSmartProcessingPreviewSnapshot snapshot,@Nullable UUID jobId){windowId=w;sessionNonce=s.getSessionNonce();terminalUUID=s.getTerminalUUID();targetRevision=s.getTargetRevision();frequencyUUID=s.getFrequencyUUID();accessRevision=s.getAccessRevision();requestId=request;actionStatus=status==null?"":status;this.snapshot=snapshot;this.jobId=jobId;valid=shape();}
        public static Message create(int w,@Nonnull QIOProcessingTerminalSession s,@Nonnull UUID request,@Nonnull String status,@Nullable QIOSmartProcessingPreviewSnapshot snapshot,@Nullable UUID jobId){return s.getFrequencyUUID()==null?new Message():new Message(w,s,request,status,snapshot,jobId);}
        @Override public void toBytes(ByteBuf b){b.writeInt(windowId);uuid(b,sessionNonce);uuid(b,terminalUUID);b.writeLong(targetRevision);uuid(b,frequencyUUID);b.writeLong(accessRevision);uuid(b,requestId);PacketHandler.writeString(b,actionStatus);b.writeBoolean(snapshot!=null);if(snapshot!=null)PacketHandler.writeNBT(b,snapshot.write());b.writeBoolean(jobId!=null);if(jobId!=null)uuid(b,jobId);}
        @Override public void fromBytes(ByteBuf b){valid=false;try{windowId=b.readInt();sessionNonce=uuid(b);terminalUUID=uuid(b);targetRevision=b.readLong();frequencyUUID=uuid(b);accessRevision=b.readLong();requestId=uuid(b);actionStatus=PacketHandler.readString(b);if(actionStatus.length()>128)return;snapshot=b.readBoolean()?QIOSmartProcessingPreviewSnapshot.read(PacketHandler.readNBT(b)):null;jobId=b.readBoolean()?uuid(b):null;valid=shape();}catch(QIOProcessingDataException|RuntimeException ignored){}}
        public boolean isValid(){return valid;}
        private boolean shape(){return windowId>=0&&sessionNonce!=null&&terminalUUID!=null&&frequencyUUID!=null&&requestId!=null&&targetRevision>=0&&accessRevision>=0&&actionStatus!=null&&actionStatus.length()<=128;}
        private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}
    }
}
