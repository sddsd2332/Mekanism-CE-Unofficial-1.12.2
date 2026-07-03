package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.inventory.container.MekanismContainer;

public abstract class PropertyData {

    private final PropertyType type;
    private final short property;

    protected PropertyData(PropertyType type, short property) {
        this.type = type;
        this.property = property;
    }

    public PropertyType getType() {
        return type;
    }

    public short getProperty() {
        return property;
    }

    public abstract void handleWindowProperty(MekanismContainer container);

    public void writeToPacket(ByteBuf buffer) {
        buffer.writeByte(type.ordinal());
        buffer.writeShort(property);
    }

    public static PropertyData fromBuffer(ByteBuf buffer) {
        PropertyType type = PropertyType.byIndex(buffer.readUnsignedByte());
        if (type == null) {
            throw new IllegalArgumentException("Unknown container property type.");
        }
        short property = buffer.readShort();
        return type.createData(property, buffer);
    }
}
