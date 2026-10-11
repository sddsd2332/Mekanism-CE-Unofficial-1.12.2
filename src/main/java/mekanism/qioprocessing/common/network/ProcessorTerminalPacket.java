package mekanism.qioprocessing.common.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Bounded NBT packet reused by the server-only Processor terminal protocol.
 */
public final class ProcessorTerminalPacket extends WorkbenchPacket {
    public ProcessorTerminalPacket() {
    }

    public ProcessorTerminalPacket(int window, long request, java.util.UUID token,
                                   net.minecraft.nbt.NBTTagCompound data) {
        super(window, request, token, data);
    }

    public static final class ServerHandler implements IMessageHandler<ProcessorTerminalPacket, IMessage> {
        @Override
        public IMessage onMessage(ProcessorTerminalPacket request, MessageContext context) {
            if (!request.valid) return null;
            net.minecraft.entity.player.EntityPlayerMP player = context.getServerHandler().player;
            mekanism.common.PacketHandler.handlePacket(
                    () -> ProcessorTerminalNetwork.handle(player, request), player);
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<ProcessorTerminalPacket, IMessage> {
        @Override
        public IMessage onMessage(ProcessorTerminalPacket reply, MessageContext context) {
            if (reply.valid) {
                net.minecraft.entity.player.EntityPlayer player = mekanism.common.PacketHandler.getPlayer(context);
                if (player != null) {
                    mekanism.common.PacketHandler.handlePacket(
                            () -> ProcessorTerminalClientSession.receiveActive(reply, player.openContainer.windowId), player);
                }
            }
            return null;
        }
    }
}

