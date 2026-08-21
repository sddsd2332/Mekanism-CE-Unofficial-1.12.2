package mekanism.common.network.to_client.container;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.network.to_client.container.PacketUpdateContainer.UpdateContainerMessage;
import mekanism.common.network.to_client.container.property.PropertyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class PacketUpdateContainer implements IMessageHandler<UpdateContainerMessage, IMessage> {

    private static final int MAX_PROPERTY_COUNT = 4_096;

    @Override
    public IMessage onMessage(UpdateContainerMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.openContainer instanceof MekanismContainer container && container.windowId == message.windowId) {
                message.data.forEach(data -> data.handleWindowProperty(container));
            }
        }, player);
        return null;
    }

    public static class UpdateContainerMessage implements IMessage {

        private short windowId;
        private List<PropertyData> data = new ArrayList<>();

        public UpdateContainerMessage() {
        }

        public UpdateContainerMessage(short windowId, List<PropertyData> data) {
            this.windowId = windowId;
            this.data = data;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            windowId = buffer.readUnsignedByte();
            int size = buffer.readInt();
            if (size < 0 || size > MAX_PROPERTY_COUNT) {
                throw new IllegalArgumentException("Invalid container property count: " + size);
            }
            data = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                data.add(PropertyData.fromBuffer(buffer));
            }
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            if (data.size() > MAX_PROPERTY_COUNT) {
                throw new IllegalArgumentException("Too many container properties: " + data.size());
            }
            buffer.writeByte(windowId);
            buffer.writeInt(data.size());
            for (PropertyData propertyData : data) {
                propertyData.writeToPacket(buffer);
            }
        }
    }
}
