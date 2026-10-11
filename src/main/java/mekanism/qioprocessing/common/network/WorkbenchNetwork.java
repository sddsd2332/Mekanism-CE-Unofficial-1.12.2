package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.workbench.WorkbenchRuntime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class WorkbenchNetwork {
    private WorkbenchNetwork() {
    }

    public static void request(WorkbenchPacket.Request request) {
        QIOProcessingPacketHandler.INSTANCE.sendToServer(request);
    }

    public static void reply(EntityPlayerMP player, WorkbenchPacket.Reply reply) {
        // Validate before asynchronous Netty encoding so oversized rows return a visible error.
        ByteBuf scratch = Unpooled.buffer();
        try {
            reply.toBytes(scratch);
        } catch (IllegalArgumentException error) {
            NBTTagCompound failure = new NBTTagCompound();
            failure.setString("status", "PAYLOAD_TOO_LARGE");
            reply = new WorkbenchPacket.Reply(reply.window, reply.request, reply.token, failure);
        } finally {
            scratch.release();
        }
        QIOProcessingPacketHandler.INSTANCE.sendTo(reply, player);
    }

    public static final class ServerHandler implements IMessageHandler<WorkbenchPacket.Request, IMessage> {
        @Override
        public IMessage onMessage(WorkbenchPacket.Request request, MessageContext context) {
            if (!request.valid) return null;
            EntityPlayerMP player = context.getServerHandler().player;
            PacketHandler.handlePacket(() -> WorkbenchRuntime.handle(player, request), player);
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<WorkbenchPacket.Reply, IMessage> {
        @Override
        public IMessage onMessage(WorkbenchPacket.Reply reply, MessageContext context) {
            if (!reply.valid) return null;
            EntityPlayer player = PacketHandler.getPlayer(context);
            if (player != null) PacketHandler.handlePacket(() -> {
                WorkbenchClientSession.receiveActive(reply, player.openContainer.windowId);
            }, player);
            return null;
        }
    }
}
