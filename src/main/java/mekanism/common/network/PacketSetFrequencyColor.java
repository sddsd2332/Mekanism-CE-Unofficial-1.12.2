package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IColorableFrequency;
import mekanism.common.network.PacketSetFrequencyColor.SetFrequencyColorMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSetFrequencyColor implements IMessageHandler<SetFrequencyColorMessage, IMessage> {

    @Override
    public IMessage onMessage(SetFrequencyColorMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            Frequency frequency = message.frequencyType.getFrequency(message.identity, player.getUniqueID());
            if (frequency instanceof IColorableFrequency colorable && frequency.ownerMatches(player.getUniqueID())) {
                colorable.setColor(message.next ? getNext(colorable.getColor()) : getPrevious(colorable.getColor()));
            }
        }, player);
        return null;
    }

    private static EnumColor getNext(EnumColor color) {
        EnumColor[] colors = EnumColor.values();
        return colors[(color.ordinal() + 1) % colors.length];
    }

    private static EnumColor getPrevious(EnumColor color) {
        EnumColor[] colors = EnumColor.values();
        return colors[(color.ordinal() - 1 + colors.length) % colors.length];
    }

    public static class SetFrequencyColorMessage implements IMessage {

        public boolean next;
        public FrequencyType<?> frequencyType;
        public FrequencyIdentity identity;

        public SetFrequencyColorMessage() {
        }

        public SetFrequencyColorMessage(Frequency frequency, boolean next) {
            this.next = next;
            this.frequencyType = frequency.getType();
            this.identity = frequency.getIdentity();
        }

        public SetFrequencyColorMessage(FrequencyType<?> frequencyType, FrequencyIdentity identity, boolean next) {
            this.next = next;
            this.frequencyType = frequencyType;
            this.identity = identity;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(next);
            PacketHandler.writeString(buffer, frequencyType.getName());
            FrequencyType.writeIdentity(buffer, frequencyType, identity);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            next = buffer.readBoolean();
            frequencyType = FrequencyType.load(PacketHandler.readString(buffer));
            identity = FrequencyType.readIdentity(buffer, frequencyType);
        }
    }
}
