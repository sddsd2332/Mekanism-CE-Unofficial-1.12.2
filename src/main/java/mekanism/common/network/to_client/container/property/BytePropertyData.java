package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.inventory.container.MekanismContainer;

public class BytePropertyData extends PropertyData {

    private final byte value;

    public BytePropertyData(short property, byte value) {
        super(PropertyType.BYTE, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeByte(value);
    }
}
