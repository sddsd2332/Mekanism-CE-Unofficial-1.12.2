package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.inventory.container.MekanismContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FrequencyListPropertyData extends PropertyData {

    private final FrequencyType<?> frequencyType;
    private final List<Frequency> value;

    public FrequencyListPropertyData(short property, FrequencyType<?> frequencyType, Collection<? extends Frequency> value) {
        super(PropertyType.FREQUENCY_LIST, property);
        this.frequencyType = frequencyType;
        this.value = new ArrayList<>(value);
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeInt(value.size());
        for (Frequency frequency : value) {
            frequency.write(buffer);
        }
    }
}
