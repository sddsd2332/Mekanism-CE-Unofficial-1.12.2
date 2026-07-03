package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.inventory.container.MekanismContainer;

public class LongPropertyData extends PropertyData {

    private final long value;

    public LongPropertyData(short property, long value) {
        super(PropertyType.LONG, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeLong(value);
    }
}
