package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyManager;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.container.item.FrequencyItemContainer;
import mekanism.common.network.PacketSetItemFrequency.SetItemFrequencyMessage;
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
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            ItemStack stack;
            if (player.openContainer == null || player.openContainer.windowId != message.windowId) {
                return;
            }
            if (player.openContainer instanceof FrequencyItemContainer) {
                FrequencyItemContainer<?> container = (FrequencyItemContainer<?>) player.openContainer;
                if (container.getHand() != message.currentHand || !container.canInteractWith(player)) {
                    return;
                }
                stack = container.getStack();
            } else if (player.openContainer instanceof PortableQIODashboardContainer) {
                PortableQIODashboardContainer container = (PortableQIODashboardContainer) player.openContainer;
                if (container.getHand() != message.currentHand || !container.canInteractWith(player)) {
                    return;
                }
                stack = container.getStack();
            } else {
                return;
            }
            if (stack.isEmpty() || !(stack.getItem() instanceof IFrequencyItem frequencyItem) || !SecurityUtils.canAccess(player, stack)) {
                return;
            }
            FrequencyType<?> frequencyType = frequencyItem.getFrequencyType();
            if (frequencyType != message.frequencyType) {
                return;
            }
            updateFrequency(player, stack, frequencyType, message);
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
        public int windowId = -1;
        private boolean valid;

        public SetItemFrequencyMessage() {
        }

        public SetItemFrequencyMessage(boolean set, FrequencyType<?> frequencyType, FrequencyIdentity identity, EnumHand currentHand) {
            this(-1, set, frequencyType, identity, currentHand);
        }

        public SetItemFrequencyMessage(int windowId, boolean set, FrequencyType<?> frequencyType, FrequencyIdentity identity, EnumHand currentHand) {
            this.windowId = windowId;
            this.set = set;
            this.frequencyType = frequencyType;
            this.identity = identity;
            this.currentHand = currentHand;
            valid = windowId >= 0 && frequencyType != null && identity != null && currentHand != null;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeBoolean(set);
            PacketHandler.writeString(buffer, frequencyType.getName());
            FrequencyType.writeIdentity(buffer, frequencyType, identity);
            buffer.writeInt(currentHand.ordinal());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                set = buffer.readBoolean();
                frequencyType = FrequencyType.load(PacketHandler.readString(buffer));
                if (frequencyType == null) {
                    return;
                }
                identity = FrequencyType.readIdentity(buffer, frequencyType);
                int handOrdinal = buffer.readInt();
                if (handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
                    return;
                }
                currentHand = EnumHand.values()[handOrdinal];
                valid = windowId >= 0 && identity != null;
            } catch (RuntimeException ex) {
                windowId = -1;
                frequencyType = null;
                identity = null;
                currentHand = EnumHand.MAIN_HAND;
            }
        }

        public boolean isValid() {
            return valid;
        }
    }
}
