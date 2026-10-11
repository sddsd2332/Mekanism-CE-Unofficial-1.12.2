package mekanism.common.network;

import baubles.api.BaublesApi;
import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.item.interfaces.IModeItem.DisplayChange;
import mekanism.common.network.PacketBaublesModeChange.BaublesModeChangMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.items.IItemHandler;

public class PacketBaublesModeChange implements IMessageHandler<BaublesModeChangMessage, IMessage> {

    @Override
    public IMessage onMessage(BaublesModeChangMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player != null && Mekanism.hooks.Baubles) {
                IItemHandler baubles = BaublesApi.getBaublesHandler(player);
                if (message.slot < 0 || message.slot >= baubles.getSlots()) {
                    return;
                }
                ItemStack stack = baubles.getStackInSlot(message.slot);
                if (!stack.isEmpty() && stack.getItem() instanceof IModeItem modeItem) {
                    modeItem.changeMode(player, stack, message.shift, message.displayChangeMessage ? DisplayChange.OTHER : DisplayChange.NONE);
                }
            }
        }, player);
        return null;
    }

    public static class BaublesModeChangMessage implements IMessage {
        private int slot;
        private boolean displayChangeMessage;
        private int shift;

        public BaublesModeChangMessage() {
        }

        public BaublesModeChangMessage(int slot, boolean holdingShift) {
            this(slot, holdingShift ? -1 : 1, true);
        }

        public BaublesModeChangMessage(int slot, int shift, boolean displayChangeMessage) {
            this.slot = slot;
            this.shift = shift;
            this.displayChangeMessage = displayChangeMessage;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(slot);
            dataStream.writeInt(shift);
            dataStream.writeBoolean(displayChangeMessage);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            slot = dataStream.readInt();
            shift = dataStream.readInt();
            displayChangeMessage = dataStream.readBoolean();
        }
    }
}
