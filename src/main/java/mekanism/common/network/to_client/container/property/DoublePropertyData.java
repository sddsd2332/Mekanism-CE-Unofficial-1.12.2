package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.inventory.container.MekanismContainer;

public class DoublePropertyData extends PropertyData {

    private final double value;

    public DoublePropertyData(short property, double value) {
        super(PropertyType.DOUBLE, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeDouble(value);
    }
}
