package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.network.PacketUpdateInventorySlot.UpdateInventorySlotMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketUpdateInventorySlot implements IMessageHandler<UpdateInventorySlotMessage, IMessage> {


    @Override
    public IMessage onMessage(UpdateInventorySlotMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (message.slotId < 0 || message.slotId >= player.inventory.getSizeInventory()) {
                return;
            }
            ItemStack stack = player.inventory.getStackInSlot(message.slotId);
            if (!stack.isEmpty() && stack.getItem() instanceof IModuleContainerItem) {
                player.inventory.setInventorySlotContents(message.slotId, stack);
                player.inventory.markDirty();
            }
        }, player);
        return null;
    }

    public static class UpdateInventorySlotMessage implements IMessage {

        private int slotId;
        private ItemStack stack;

        public UpdateInventorySlotMessage() {
        }

        public UpdateInventorySlotMessage(int slotId) {
            this.slotId = slotId;
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            slotId = dataStream.readInt();
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(slotId);
        }
    }
}
