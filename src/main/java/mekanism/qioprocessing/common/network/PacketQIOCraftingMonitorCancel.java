package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOCraftingMonitorPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOCraftingMonitorCancel 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOCraftingMonitorCancel implements
      IMessageHandler<PacketQIOCraftingMonitorCancel.Message, IMessage> {
    @Override public IMessage onMessage(Message m,MessageContext c){if(!m.valid)return null;EntityPlayer p=PacketHandler.getPlayer(c);if(p!=null)PacketHandler.handlePacket(()->handle(m,p),p);return null;}
    private static void handle(Message m,EntityPlayer p){if(!(p instanceof EntityPlayerMP mp)||!(p.openContainer instanceof QIOCraftingMonitorPageContainer x)||p.openContainer.windowId!=m.windowId||!p.openContainer.canInteractWith(p))return;QIOProcessingTerminalSession s=x.getTerminalSession();if(s==null||s.getFrequencyUUID()==null||s.getTerminalType()!=QIOProcessingTerminalType.CRAFTING_MONITOR||s.validate(m.nonce,p.getUniqueID(),s.getTargetKind(),QIOProcessingTerminalType.CRAFTING_MONITOR,m.terminalUUID,m.targetRevision,s.getFrequencyUUID(),s.getAccessRevision())!=QIOProcessingTerminalSession.Validation.ACCEPTED)return;QIOProcessingNetworkData n=QIOProcessingNetworkManager.INSTANCE.get(s.getFrequencyUUID());if(n==null)return;try{QIOCraftingMonitorService.CancelStatus status=QIOCraftingMonitorService.cancel(s,n,s.getAccessRevision(),m.jobId,m.runtimeRevision);if(status==QIOCraftingMonitorService.CancelStatus.ACCEPTED){try{QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(n);}catch(IOException|RuntimeException e){status=QIOCraftingMonitorService.CancelStatus.PERSISTENCE_ERROR;}}QIOProcessingPacketHandler.INSTANCE.sendTo(PacketQIOCraftingMonitorCancelResult.Message.create(m.windowId,s,m.requestId,m.jobId,status),mp);}catch(IllegalArgumentException|IllegalStateException|SecurityException ignored){}}
    public static final class Message implements IMessage {private int windowId;private UUID nonce,terminalUUID,requestId,jobId;private long targetRevision,runtimeRevision;private boolean valid;public Message(){}private Message(int w,UUID n,UUID t,long tr,UUID r,UUID j,long rr){windowId=w;nonce=n;terminalUUID=t;targetRevision=tr;requestId=r;jobId=j;runtimeRevision=rr;valid=shape();}public static Message create(int w,QIOProcessingTerminalContainerState s,UUID r,UUID j,long rr){return s==null||!s.isValid()?new Message():new Message(w,s.getSessionNonce(),s.getTerminalUUID(),s.getTargetRevision(),r,j,rr);}static Message create(int w,QIOProcessingTerminalSession s,UUID r,UUID j,long rr){return s==null?new Message():new Message(w,s.getSessionNonce(),s.getTerminalUUID(),s.getTargetRevision(),r,j,rr);}@Override public void toBytes(ByteBuf b){b.writeInt(windowId);uuid(b,nonce);uuid(b,terminalUUID);b.writeLong(targetRevision);uuid(b,requestId);uuid(b,jobId);b.writeLong(runtimeRevision);}@Override public void fromBytes(ByteBuf b){valid=false;try{windowId=b.readInt();nonce=uuid(b);terminalUUID=uuid(b);targetRevision=b.readLong();requestId=uuid(b);jobId=uuid(b);runtimeRevision=b.readLong();valid=shape();}catch(RuntimeException ignored){}}public boolean isValid(){return valid;}private boolean shape(){return windowId>=0&&nonce!=null&&terminalUUID!=null&&targetRevision>=0&&requestId!=null&&jobId!=null&&runtimeRevision>=0;}private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}}
}
