package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.MekanismContainer;

import javax.annotation.Nullable;

public class FrequencyPropertyData extends PropertyData {

    @Nullable
    private final Frequency value;

    public FrequencyPropertyData(short property, FrequencyType<?> frequencyType, @Nullable Frequency value) {
        super(PropertyType.FREQUENCY, property);
        this.value = value;
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeBoolean(value != null);
        if (value != null) {
            value.write(buffer);
        }
    }
}
