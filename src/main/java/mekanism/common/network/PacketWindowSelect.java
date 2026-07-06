package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.network.PacketWindowSelect.WindowSelectMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;

public class PacketWindowSelect implements IMessageHandler<WindowSelectMessage, IMessage> {

    @Override
    public IMessage onMessage(WindowSelectMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.openContainer instanceof MekanismContainer container) {
                container.setSelectedWindow(player.getUniqueID(), message.selectedWindow);
            }
        }, player);
        return null;
    }

    public static class WindowSelectMessage implements IMessage {

        @Nullable
        public SelectedWindowData selectedWindow;

        public WindowSelectMessage() {
        }

        public WindowSelectMessage(@Nullable SelectedWindowData selectedWindow) {
            this.selectedWindow = selectedWindow;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            if (selectedWindow == null) {
                dataStream.writeByte(-1);
            } else {
                dataStream.writeByte(selectedWindow.extraData);
                PacketHandler.writeString(dataStream, selectedWindow.type.getRegistryNameString());
            }
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            byte extraData = dataStream.readByte();
            if (extraData == -1) {
                selectedWindow = null;
                return;
            }
            WindowType windowType = WindowType.byName(PacketHandler.readString(dataStream));
            if (windowType == null) {
                windowType = WindowType.UNSPECIFIED;
            }
            selectedWindow = windowType == WindowType.UNSPECIFIED ? SelectedWindowData.UNSPECIFIED : new SelectedWindowData(windowType, extraData);
        }
    }
}
