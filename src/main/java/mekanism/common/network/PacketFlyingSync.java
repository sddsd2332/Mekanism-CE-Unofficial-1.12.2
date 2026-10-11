package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.network.PacketFlyingSync.FlyingSyncMessage;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketFlyingSync implements IMessageHandler<FlyingSyncMessage, IMessage> {


    @Override
    public IMessage onMessage(FlyingSyncMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player instanceof EntityPlayerSP p) {
                p.capabilities.allowFlying = message.allowFlying;
                p.capabilities.isFlying = message.isFlying;
            }
        }, player);
        return null;
    }

    public static class FlyingSyncMessage implements IMessage {

        private boolean allowFlying;
        private boolean isFlying;

        public FlyingSyncMessage() {
        }

        public FlyingSyncMessage(boolean allowFlying, boolean isFlying) {
            this.allowFlying = allowFlying;
            this.isFlying = isFlying;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeBoolean(allowFlying);
            dataStream.writeBoolean(isFlying);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            allowFlying = dataStream.readBoolean();
            isFlying = dataStream.readBoolean();
        }


    }
}
