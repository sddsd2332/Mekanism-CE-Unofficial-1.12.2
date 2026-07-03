package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyHandler;
import mekanism.common.network.PacketSetTileFrequency.SetTileFrequencyMessage;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSetTileFrequency implements IMessageHandler<SetTileFrequencyMessage, IMessage> {

    @Override
    public IMessage onMessage(SetTileFrequencyMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            TileEntity tile = message.coord.getTileEntity(player.world);
            if (tile instanceof IFrequencyHandler frequencyHandler && SecurityUtils.canAccess(player, tile)) {
                if (message.set) {
                    frequencyHandler.setFrequency(message.frequencyType, message.identity, player.getUniqueID());
                } else {
                    frequencyHandler.removeFrequency(message.frequencyType, message.identity, player.getUniqueID());
                }
            }
        }, player);
        return null;
    }

    public static class SetTileFrequencyMessage implements IMessage {

        public boolean set;
        public FrequencyType<?> frequencyType;
        public FrequencyIdentity identity;
        public Coord4D coord;

        public SetTileFrequencyMessage() {
        }

        public SetTileFrequencyMessage(boolean set, FrequencyType<?> frequencyType, FrequencyIdentity identity, TileEntity tile) {
            this.set = set;
            this.frequencyType = frequencyType;
            this.identity = identity;
            this.coord = Coord4D.get(tile);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(set);
            PacketHandler.writeString(buffer, frequencyType.getName());
            FrequencyType.writeIdentity(buffer, frequencyType, identity);
            coord.write(buffer);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            set = buffer.readBoolean();
            frequencyType = FrequencyType.load(PacketHandler.readString(buffer));
            identity = FrequencyType.readIdentity(buffer, frequencyType);
            coord = Coord4D.read(buffer);
        }
    }
}
