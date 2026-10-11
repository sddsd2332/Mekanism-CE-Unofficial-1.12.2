package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.network.PacketResetPlayerClient.ResetPlayerClientMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class PacketResetPlayerClient implements IMessageHandler<ResetPlayerClientMessage, IMessage> {

    @Override
    public IMessage onMessage(ResetPlayerClientMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            Mekanism.playerState.clearPlayer(message.uuid, true);
        }, player);
        return null;
    }


    public static class ResetPlayerClientMessage implements IMessage {

        private UUID uuid;

        public ResetPlayerClientMessage() {
        }

        public ResetPlayerClientMessage(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            PacketHandler.writeUUID(dataStream, uuid);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            uuid = PacketHandler.readUUID(dataStream);
        }


    }
}
