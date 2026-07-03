package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSetItemFrequency implements IMessageHandler<SetItemFrequencyMessage, IMessage> {

    @Override
    public IMessage onMessage(SetItemFrequencyMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            ItemStack stack = player.getHeldItem(message.currentHand);
            if (stack.isEmpty() || !(stack.getItem() instanceof IFrequencyItem frequencyItem) || !SecurityUtils.canAccess(player, stack)) {
                return;
            }
            updateFrequency(player, stack, frequencyItem.getFrequencyType(), message);
        }, player);
        return null;
    }

    private <FREQ extends Frequency> void updateFrequency(EntityPlayer player, ItemStack stack, FrequencyType<FREQ> frequencyType, SetItemFrequencyMessage message) {
        if (message.identity == null) {
            return;
        }
        IFrequencyItem frequencyItem = (IFrequencyItem) stack.getItem();
        if (message.set) {
            frequencyItem.setFrequencyAware(stack, FrequencyAware.create(frequencyType, message.identity, player.getUniqueID()));
        } else {
            FrequencyManager<FREQ> manager = frequencyType.getManager(message.identity, message.identity.ownerUUID() == null ? player.getUniqueID() : message.identity.ownerUUID());
            if (manager != null && manager.remove(message.identity.key(), player.getUniqueID())) {
                FrequencyAware<?> stored = frequencyItem.getFrequencyAware(stack);
                if (stored.hasIdentity(message.identity)) {
                    frequencyItem.setFrequencyAware(stack, null);
                }
            }
        }
    }

    public static class SetItemFrequencyMessage implements IMessage {

        public boolean set;
        public FrequencyType<?> frequencyType;
        public FrequencyIdentity identity;
        public EnumHand currentHand;

        public SetItemFrequencyMessage() {
        }

        public SetItemFrequencyMessage(boolean set, FrequencyType<?> frequencyType, FrequencyIdentity identity, EnumHand currentHand) {
            this.set = set;
            this.frequencyType = frequencyType;
            this.identity = identity;
            this.currentHand = currentHand;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(set);
            PacketHandler.writeString(buffer, frequencyType.getName());
            FrequencyType.writeIdentity(buffer, frequencyType, identity);
            buffer.writeInt(currentHand.ordinal());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            set = buffer.readBoolean();
            frequencyType = FrequencyType.load(PacketHandler.readString(buffer));
            identity = FrequencyType.readIdentity(buffer, frequencyType);
            currentHand = MekanismUtils.getByIndex(EnumHand.values(), buffer.readInt(), EnumHand.MAIN_HAND);
        }
    }
}
