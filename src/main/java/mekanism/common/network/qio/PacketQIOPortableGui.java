package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.QIOGuiConstants;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.container.QIOItemFrequencySelectContainer;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

/** Opens another GUI for the portable dashboard without trusting a slot id. */
public class PacketQIOPortableGui implements IMessageHandler<PacketQIOPortableGui.Message, IMessage> {

    public static final int ACTION_TOGGLE_TARGET = -2;

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.isValid()) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!Message.isSupportedGui(message.guiId)) {
                return;
            }
            ItemStack stack;
            int itemSlot;
            if (player.openContainer instanceof PortableQIODashboardContainer) {
                PortableQIODashboardContainer container = (PortableQIODashboardContainer) player.openContainer;
                if (container.windowId != message.windowId || container.getHand() != message.hand || !container.canInteractWith(player)) {
                    return;
                }
                stack = container.getStack();
                itemSlot = container.getItemSlot();
            } else if (player.openContainer instanceof QIOItemFrequencySelectContainer) {
                QIOItemFrequencySelectContainer container = (QIOItemFrequencySelectContainer) player.openContainer;
                if (container.windowId != message.windowId || container.getHand() != message.hand || !container.canInteractWith(player)) {
                    return;
                }
                stack = container.getStack();
                itemSlot = container.getItemSlot();
            } else {
                return;
            }
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemPortableQIODashboard) || !SecurityUtils.canAccess(player, stack)) {
                return;
            }
            if (message.guiId == ACTION_TOGGLE_TARGET) {
                boolean current = !ItemDataUtils.hasData(stack, "qioInsertIntoFrequency") || ItemDataUtils.getBoolean(stack, "qioInsertIntoFrequency");
                ItemDataUtils.setBoolean(stack, "qioInsertIntoFrequency", !current);
                player.inventory.markDirty();
                return;
            }
            MekanismUtils.openItemGui(player, message.hand, itemSlot, message.guiId);
        }, player);
        return null;
    }

    public static class Message implements IMessage {
        private EnumHand hand = EnumHand.MAIN_HAND;
        private int guiId = QIOGuiConstants.PORTABLE_DASHBOARD;
        private int windowId = -1;
        private boolean valid;

        public Message() {
        }

        public Message(int windowId, EnumHand hand, int guiId) {
            this.windowId = windowId;
            this.hand = hand;
            this.guiId = guiId;
            valid = windowId >= 0 && hand != null && isSupportedGui(guiId);
        }

        @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(windowId); buffer.writeByte(hand.ordinal()); buffer.writeInt(guiId); }
        @Override public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                int ordinal = buffer.readUnsignedByte();
                if (ordinal < 0 || ordinal >= EnumHand.values().length) {
                    throw new IllegalArgumentException("Unknown hand for portable QIO GUI");
                }
                hand = EnumHand.values()[ordinal];
                guiId = buffer.readInt();
                valid = windowId >= 0 && isSupportedGui(guiId);
            } catch (RuntimeException ex) {
                windowId = -1;
                hand = EnumHand.MAIN_HAND;
                guiId = -1;
            }
        }

        private static boolean isSupportedGui(int guiId) {
            return guiId == QIOGuiConstants.PORTABLE_DASHBOARD || guiId == QIOGuiConstants.PORTABLE_FREQUENCY || guiId == ACTION_TOGGLE_TARGET;
        }

        public EnumHand getHand() {
            return hand;
        }

        public int getGuiId() {
            return guiId;
        }

        public int getWindowId() {
            return windowId;
        }

        public boolean isValid() {
            return valid;
        }
    }

}
