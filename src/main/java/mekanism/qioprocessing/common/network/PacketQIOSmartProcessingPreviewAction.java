package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingPageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingRequestLedger;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.order.QIOOrderService;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOSmartProcessingPreviewAction 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOSmartProcessingPreviewAction implements IMessageHandler<PacketQIOSmartProcessingPreviewAction.Message, IMessage> {
    public enum Action { REQUEST, POLL, CONFIRM, CANCEL }
    @Override public IMessage onMessage(Message message, MessageContext context){if(!message.valid)return null;EntityPlayer player=PacketHandler.getPlayer(context);if(player!=null)PacketHandler.handlePacket(()->handle(message,player),player);return null;}
    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP mp) ||
              !(player.openContainer instanceof QIOSmartProcessingPageContainer container) ||
              player.openContainer.windowId != message.windowId ||
              !player.openContainer.canInteractWith(player)) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (!validSession(message, player, session)) {
            return;
        }
        QIOFrequency frequency = container.getTerminalFrequency();
        if (frequency == null || !frequency.getFrequencyUUID().equals(session.getFrequencyUUID())) {
            return;
        }
        PortableResourceDescriptor target = null;
        if (message.action == Action.REQUEST || message.action == Action.CONFIRM) {
            try {
                target = PortableResourceDescriptor.read(message.target);
            } catch (RuntimeException e) {
                return;
            }
        }
        boolean mutating = message.action != Action.POLL;
        String fingerprint = fingerprint(message, target);
        QIOSmartProcessingRequestLedger ledger = container.getSmartProcessingRequestLedger();
        if (mutating) {
            QIOSmartProcessingRequestLedger.Lookup lookup = ledger.begin(message.requestId,
                  fingerprint);
            if (lookup.getStatus() == QIOSmartProcessingRequestLedger.Status.REPLAY) {
                send(message, session, mp, lookup.getResponse());
                return;
            }
            if (lookup.getStatus() != QIOSmartProcessingRequestLedger.Status.NEW) {
                String status = switch (lookup.getStatus()) {
                    case PENDING -> "REQUEST_PENDING";
                    case CONFLICT -> "DUPLICATE_REQUEST";
                    case FULL -> "REQUEST_LEDGER_FULL";
                    default -> "INVALID_REQUEST";
                };
                send(message, session, mp,
                      new QIOSmartProcessingRequestLedger.Response(status, null, null));
                return;
            }
        }
        // Polling is read-only and uses its own bucket so a catalog request in the same server
        // tick cannot suppress the only completion path for an asynchronous planning task.
        boolean allowed = message.action == Action.POLL ?
              container.tryRequestSmartProcessingPoll(player.world.getTotalWorldTime()) :
              container.tryRequestSmartProcessing(player.world.getTotalWorldTime());
        if (!allowed) {
            if (mutating) {
                ledger.abort(message.requestId, fingerprint);
            }
            return;
        }

        QIOSmartProcessingRequestLedger.Response response;
        try {
            QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
                  frequency, player.getUniqueID());
            String status;
            QIOSmartProcessingPreviewSnapshot snapshot = null;
            UUID jobId = null;
            if (message.action == Action.REQUEST) {
                QIOOrderService.PreviewRequestResult result =
                      QIOOrderService.INSTANCE.requestPreview(player.world, reference,
                            player.getUniqueID(), target, message.amount, message.priority,
                            message.mergeOrder, player.world.getTotalWorldTime());
                status = result.getStatus().name();
                if (result.getPreview() != null) {
                    snapshot = QIOSmartProcessingPreviewSnapshot.fromPreview(
                          result.getPreview(), null);
                }
            } else {
                QIOOrderPreview preview = QIOOrderService.INSTANCE.getPreview(message.previewId,
                      player.getUniqueID());
                if (message.action == Action.POLL) {
                    status = preview == null ? "NOT_FOUND" : "OK";
                    if (preview != null) {
                        snapshot = QIOSmartProcessingPreviewSnapshot.fromPreview(preview, null);
                    }
                } else if (message.action == Action.CANCEL) {
                    status = QIOOrderService.INSTANCE.cancelPreview(message.previewId,
                          player.getUniqueID()) ? "ACCEPTED" : "NOT_FOUND";
                } else if (preview == null) {
                    status = "NOT_FOUND";
                } else if (!preview.getTarget().equals(target) ||
                      preview.getAmount() != message.amount) {
                    status = "PREVIEW_MISMATCH";
                    snapshot = QIOSmartProcessingPreviewSnapshot.fromPreview(preview, null);
                } else {
                    QIOOrderService.ConfirmResult result = QIOOrderService.INSTANCE.confirm(
                          message.previewId, player.getUniqueID(),
                          player.world.getTotalWorldTime());
                    status = result.getStatus().name();
                    if (result.getJob() != null) {
                        jobId = result.getJob().getJobId();
                        snapshot = QIOSmartProcessingPreviewSnapshot.fromPreview(preview, jobId);
                    }
                }
            }
            response = new QIOSmartProcessingRequestLedger.Response(status, snapshot, jobId);
        } catch (RuntimeException e) {
            response = new QIOSmartProcessingRequestLedger.Response("INTERNAL_ERROR", null, null);
        }
        if (mutating) {
            ledger.complete(message.requestId, fingerprint, response);
        }
        send(message, session, mp, response);
    }

    private static String fingerprint(Message message, @Nullable PortableResourceDescriptor target) {
        return message.action.name() + '|' + String.valueOf(message.previewId) + '|' +
              message.amount + '|' + message.priority + '|' + message.mergeOrder + '|' +
              (target == null ? "" : target.write().toString());
    }

    private static void send(Message message, QIOProcessingTerminalSession session,
          EntityPlayerMP player, QIOSmartProcessingRequestLedger.Response response) {
        if (response == null) {
            return;
        }
        QIOProcessingPacketHandler.INSTANCE.sendTo(
              PacketQIOSmartProcessingPreviewData.Message.create(message.windowId, session,
                    message.requestId, response.getActionStatus(), response.getSnapshot(),
                    response.getJobId()), player);
    }
    private static boolean validSession(Message m,EntityPlayer player,QIOProcessingTerminalSession session){return session!=null&&session.getFrequencyUUID()!=null&&session.getTerminalType()==QIOProcessingTerminalType.SMART_PROCESSING&&session.validate(m.sessionNonce,player.getUniqueID(),session.getTargetKind(),QIOProcessingTerminalType.SMART_PROCESSING,m.terminalUUID,m.targetRevision,session.getFrequencyUUID(),session.getAccessRevision())==QIOProcessingTerminalSession.Validation.ACCEPTED;}
    public static final class Message implements IMessage{
        private int windowId;private UUID sessionNonce,terminalUUID,requestId,previewId;private long targetRevision,amount,priority;private Action action;@Nullable private NBTTagCompound target;private boolean mergeOrder,valid;
        public Message(){}
        private Message(int w,QIOProcessingTerminalSession s,UUID request,Action action,@Nullable UUID preview,@Nullable NBTTagCompound target,long amount,long priority,boolean mergeOrder){windowId=w;sessionNonce=s.getSessionNonce();terminalUUID=s.getTerminalUUID();targetRevision=s.getTargetRevision();requestId=request;this.action=action;previewId=preview;this.target=target;this.amount=amount;this.priority=priority;this.mergeOrder=mergeOrder;valid=shape();}
        public static Message request(int w,QIOProcessingTerminalContainerState state,UUID request,NBTTagCompound target,long amount,long priority){return request(w,state,request,target,amount,priority,false);}
        public static Message request(int w,QIOProcessingTerminalContainerState state,UUID request,NBTTagCompound target,long amount,long priority,boolean mergeOrder){return state==null||!state.isValid()?new Message():new Message(w,state.getSessionNonce(),state.getTerminalUUID(),state.getTargetRevision(),request,Action.REQUEST,null,target,amount,priority,mergeOrder);}
        public static Message confirm(int w,QIOProcessingTerminalContainerState state,UUID request,UUID preview,NBTTagCompound target,long amount){return state==null||!state.isValid()?new Message():new Message(w,state.getSessionNonce(),state.getTerminalUUID(),state.getTargetRevision(),request,Action.CONFIRM,preview,target,amount,0,false);}
        public static Message action(int w,QIOProcessingTerminalContainerState state,UUID request,Action action,UUID preview){return state==null||!state.isValid()?new Message():new Message(w,state.getSessionNonce(),state.getTerminalUUID(),state.getTargetRevision(),request,action,preview,null,0,0,false);}
        public static Message request(int w,QIOProcessingTerminalSession session,UUID request,NBTTagCompound target,long amount,long priority){return request(w,session,request,target,amount,priority,false);}
        public static Message request(int w,QIOProcessingTerminalSession session,UUID request,NBTTagCompound target,long amount,long priority,boolean mergeOrder){return session==null?new Message():new Message(w,session,request,Action.REQUEST,null,target,amount,priority,mergeOrder);}
        public static Message confirm(int w,QIOProcessingTerminalSession session,UUID request,UUID preview,NBTTagCompound target,long amount){return session==null?new Message():new Message(w,session,request,Action.CONFIRM,preview,target,amount,0,false);}
        public static Message action(int w,QIOProcessingTerminalSession session,UUID request,Action action,UUID preview){return session==null?new Message():new Message(w,session,request,action,preview,null,0,0,false);}
        private Message(int w,UUID nonce,UUID terminal,long revision,UUID request,Action action,UUID preview,NBTTagCompound target,long amount,long priority,boolean mergeOrder){windowId=w;sessionNonce=nonce;terminalUUID=terminal;targetRevision=revision;requestId=request;this.action=action;previewId=preview;this.target=target;this.amount=amount;this.priority=priority;this.mergeOrder=mergeOrder;valid=shape();}
        @Override public void toBytes(ByteBuf b){b.writeInt(windowId);uuid(b,sessionNonce);uuid(b,terminalUUID);b.writeLong(targetRevision);uuid(b,requestId);b.writeByte(action==null?-1:action.ordinal());b.writeBoolean(previewId!=null);if(previewId!=null)uuid(b,previewId);b.writeBoolean(target!=null);if(target!=null)PacketHandler.writeNBT(b,target);b.writeLong(amount);b.writeLong(priority);b.writeBoolean(mergeOrder);}
        @Override public void fromBytes(ByteBuf b){valid=false;try{windowId=b.readInt();sessionNonce=uuid(b);terminalUUID=uuid(b);targetRevision=b.readLong();requestId=uuid(b);int a=b.readByte();action=a<0||a>=Action.values().length?null:Action.values()[a];previewId=b.readBoolean()?uuid(b):null;target=b.readBoolean()?PacketHandler.readNBT(b):null;amount=b.readLong();priority=b.readLong();mergeOrder=b.readBoolean();valid=shape();}catch(RuntimeException ignored){}}
        public boolean isValid(){return valid;}
        private boolean shape(){if(windowId<0||sessionNonce==null||terminalUUID==null||requestId==null||targetRevision<0||action==null)return false;if(action==Action.REQUEST)return target!=null&&amount>0&&previewId==null;if(mergeOrder)return false;if(action==Action.CONFIRM)return target!=null&&amount>0&&previewId!=null;return target==null&&previewId!=null&&amount==0;}
        private static void uuid(ByteBuf b,UUID u){b.writeLong(u.getMostSignificantBits());b.writeLong(u.getLeastSignificantBits());}private static UUID uuid(ByteBuf b){return new UUID(b.readLong(),b.readLong());}
    }
}
