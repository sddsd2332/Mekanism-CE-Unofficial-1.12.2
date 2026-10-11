package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.network.PacketPlayerData.PlayerDataMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class PacketPlayerData implements IMessageHandler<PlayerDataMessage, IMessage> {

    @Override
    public IMessage onMessage(PlayerDataMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            try {
                Mekanism.playerState.setFlamethrowerState(message.uuid, message.activeFlamethrower, false);
                Mekanism.playerState.setJetpackState(message.uuid, message.activeJetpack, false);
                Mekanism.playerState.setScubaMaskState(message.uuid, message.activeScubaMask, false);
                Mekanism.playerState.setGravitationalModulationState(message.uuid, message.activeModulator, false);
            } catch (Exception e) {
                Mekanism.logger.error("FIXME: Packet handling error", e);
            }
        }, player);
        return null;
    }

    public static class PlayerDataMessage implements IMessage {

        private UUID uuid;
        private boolean activeFlamethrower;
        private boolean activeJetpack;
        private boolean activeScubaMask;
        private boolean activeModulator;

        public PlayerDataMessage() {
        }

        public PlayerDataMessage(UUID uuid) {
            this.uuid = uuid;
            this.activeFlamethrower = Mekanism.playerState.getActiveFlamethrowers().contains(uuid);
            this.activeJetpack = Mekanism.playerState.getActiveJetpacks().contains(uuid);
            this.activeScubaMask = Mekanism.playerState.getActiveScubaMask().contains(uuid);
            this.activeModulator = Mekanism.playerState.getActiveGravitationalModulators().contains(uuid);
        }


        @Override
        public void toBytes(ByteBuf dataStream) {
            PacketHandler.writeUUID(dataStream, uuid);
            dataStream.writeBoolean(activeFlamethrower);
            dataStream.writeBoolean(activeJetpack);
            dataStream.writeBoolean(activeScubaMask);
            dataStream.writeBoolean(activeModulator);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            uuid = PacketHandler.readUUID(dataStream);
            activeFlamethrower = dataStream.readBoolean();
            activeJetpack = dataStream.readBoolean();
            activeScubaMask = dataStream.readBoolean();
            activeModulator = dataStream.readBoolean();
        }


    }
}
