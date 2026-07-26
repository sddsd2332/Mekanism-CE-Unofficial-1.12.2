package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.network.PacketRadiationData.PacketRadiationDataMessage;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Objects;

public class PacketRadiationData implements IMessageHandler<PacketRadiationDataMessage, IMessage> {


    @Override
    public IMessage onMessage(PacketRadiationDataMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (message.type == RadiationPacketType.ENVIRONMENTAL) {
                RadiationManager.INSTANCE.setClientEnvironmentalRadiation(message.radiation, message.maxMagnitude);
            } else if (message.type == RadiationPacketType.PLAYER) {
                if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                    Objects.requireNonNull(player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)).set(message.radiation);
                }
            }
        }, player);

        return null;
    }


    public enum RadiationPacketType {
        ENVIRONMENTAL,
        PLAYER
    }


    public static void sync(EntityPlayerMP player) {
        RadiationManager.INSTANCE.syncPlayer(player);
    }

    public static PacketRadiationDataMessage createEnvironmental(double radiation) {
        return createEnvironmental(radiation, radiation);
    }

    public static PacketRadiationDataMessage createEnvironmental(double radiation, double maxMagnitude) {
        return new PacketRadiationDataMessage(RadiationPacketType.ENVIRONMENTAL, radiation, maxMagnitude);
    }

    public static PacketRadiationDataMessage createPlayer(double radiation) {
        return new PacketRadiationDataMessage(RadiationPacketType.PLAYER, radiation, radiation);
    }

    public static class PacketRadiationDataMessage implements IMessage {

        private RadiationPacketType type;
        private double radiation;
        private double maxMagnitude;

        public PacketRadiationDataMessage() {
        }

        PacketRadiationDataMessage(RadiationPacketType type, double radiation, double maxMagnitude) {
            this.type = type;
            this.radiation = radiation;
            this.maxMagnitude = maxMagnitude;
        }


        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(type.ordinal());
            dataStream.writeDouble(radiation);
            dataStream.writeDouble(maxMagnitude);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            type = MekanismUtils.getByIndex(RadiationPacketType.values(), dataStream.readInt(), RadiationPacketType.ENVIRONMENTAL);
            radiation = dataStream.readDouble();
            maxMagnitude = dataStream.readDouble();
        }

        RadiationPacketType getType() {
            return type;
        }

        double getRadiation() {
            return radiation;
        }

        double getMaxMagnitude() {
            return maxMagnitude;
        }


    }
}
