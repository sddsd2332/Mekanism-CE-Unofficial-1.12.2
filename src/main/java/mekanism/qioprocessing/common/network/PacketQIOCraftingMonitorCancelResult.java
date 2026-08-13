package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService.CancelStatus;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public final class PacketQIOCraftingMonitorCancelResult implements IMessageHandler<PacketQIOCraftingMonitorCancelResult.Message,IMessage>{
    @Override public IMessage onMessage(Message m,MessageContext c){EntityPlayer p=PacketHandler.getPlayer(c);if(p==null||!p.world.isRemote)return null;PacketHandler.handlePacket(()->{if(!m.valid||!(p.openContainer instanceof QIOCraftingMonitorPageContainer x)||p.openContainer.windowId!=m.windowId)return;QIOProcessingTerminalContainerState s=x.getTerminalState();if(s.matches(m.nonce,m.terminalUUID,m.targetRevision,m.frequencyUUID,m.accessRevision))x.getCraftingMonitorClientCache().applyCancel(m.nonce,m.requestId,m.jobId,m.status);},p);return null;}
    public static final class Message implements IMessage{private int windowId;private UUID nonce,terminalUUID,frequencyUUID,requestId,jobId;private long targetRevision,accessRevision;private CancelStatus status;private boolean valid;public Message(){}private Message(int w,QIOProcessingTerminalSession s,UUID r,UUID j,CancelStatus st){windowId=w;nonce=s.getSessionNonce();terminalUUID=s.getTerminalUUID();targetRevision=s.getTargetRevision();frequencyUUID=s.getFrequencyUUID();accessRevision=s.getAccessRevision();requestId=r;jobId=j;status=st;valid=shape();}public static Message create(int w,QIOProcessingTerminalSession s,UUID r,UUID j,CancelStatus st){return s==null||s.getFrequencyUUID()==null?new Message():new Message(w,s,r,j,st);}@Override public void toBytes(ByteBuf b){b.writeInt(windowId);uuid(b,nonce);uuid(b,terminalUUID);b.writeLong(targetRevision);uuid(b,frequencyUUID);b.writeLong(accessRevision);uuid(b,requestId);uuid(b,jobId);b.writeByte(status.ordinal());}@Override public void fromBytes(ByteBuf b){valid=false;try{windowId=b.readInt();nonce=uuid(b);terminalUUID=uuid(b);targetRevision=b.readLong();frequencyUUID=uuid(b);accessRevision=b.readLong();requestId=uuid(b);jobId=uuid(b);int o=b.readUnsignedByte();if(o>=CancelStatus.values().length)return;status=CancelStatus.values()[o];valid=shape();}catch(RuntimeException ignored){}}public boolean isValid(){return valid;}private boolean shape(){return windowId>=0&&nonce!=null&&terminalUUID!=null&&targetRevision>=0&&frequencyUUID!=null&&accessRevision>=0&&requestId!=null&&jobId!=null&&status!=null;}private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}}
}
