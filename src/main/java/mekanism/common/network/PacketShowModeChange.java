package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.common.PacketHandler;
import mekanism.common.network.PacketShowModeChange.ShowModeChangeMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketShowModeChange implements IMessageHandler<ShowModeChangeMessage, IMessage> {


    @Override
    public IMessage onMessage(ShowModeChangeMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(MekanismStatusOverlay.INSTANCE::setTimer, player);
        return null;
    }

    public static class ShowModeChangeMessage implements IMessage {


        public static final ShowModeChangeMessage INSTANCE = new ShowModeChangeMessage();

        @Override
        public void fromBytes(ByteBuf buf) {

        }

        @Override
        public void toBytes(ByteBuf buf) {

        }
    }

}
