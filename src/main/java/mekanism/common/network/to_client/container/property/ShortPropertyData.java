package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.inventory.container.MekanismContainer;

public class ShortPropertyData extends PropertyData {

    private final short value;

    public ShortPropertyData(short property, short value) {
        super(PropertyType.SHORT, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeShort(value);
    }
}
