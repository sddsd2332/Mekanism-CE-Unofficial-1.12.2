package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import mekanism.api.gas.GasStack;
import mekanism.common.HashList;
import mekanism.common.PacketHandler;
import mekanism.common.content.filter.IFilter;
import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public enum PropertyType {
    BOOLEAN((property, buffer) -> new BooleanPropertyData(property, buffer.readBoolean())),
    BYTE((property, buffer) -> new BytePropertyData(property, buffer.readByte())),
    SHORT((property, buffer) -> new ShortPropertyData(property, buffer.readShort())),
    INT((property, buffer) -> new IntPropertyData(property, buffer.readInt())),
    LONG((property, buffer) -> new LongPropertyData(property, buffer.readLong())),
    FLOAT((property, buffer) -> new FloatPropertyData(property, buffer.readFloat())),
    DOUBLE((property, buffer) -> new DoublePropertyData(property, buffer.readDouble())),
    ITEM_STACK((property, buffer) -> new ItemStackPropertyData(property, PacketHandler.readStack(buffer))),
    FLUID_STACK((property, buffer) -> {
        NBTTagCompound tag = PacketHandler.readNBT(buffer);
        return new FluidStackPropertyData(property, tag == null || tag.isEmpty() ? null : FluidStack.loadFluidStackFromNBT(tag));
    }),
    GAS_STACK((property, buffer) -> {
        NBTTagCompound tag = PacketHandler.readNBT(buffer);
        return new GasStackPropertyData(property, tag == null || tag.isEmpty() ? null : GasStack.readFromNBT(tag));
    }),
    FILTER_LIST((property, buffer) -> {
        FilterListType filterListType = FilterListType.byIndex(buffer.readUnsignedByte());
        HashList<IFilter> filters = new HashList<>();
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            IFilter filter = filterListType.readFilter(buffer);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return new FilterListPropertyData<>(property, filterListType, filters);
    }),
    FREQUENCY((property, buffer) -> {
        Frequency frequency = buffer.readBoolean() ? FrequencyType.read(buffer) : null;
        return new FrequencyPropertyData(property, frequency == null ? FrequencyType.TELEPORTER : frequency.getType(), frequency);
    }),
    FREQUENCY_LIST((property, buffer) -> {
        int size = buffer.readInt();
        List<Frequency> frequencies = new ArrayList<>(size);
        FrequencyType<?> type = FrequencyType.TELEPORTER;
        for (int i = 0; i < size; i++) {
            Frequency frequency = FrequencyType.read(buffer);
            if (frequency != null) {
                type = frequency.getType();
                frequencies.add(frequency);
            }
        }
        return new FrequencyListPropertyData(property, type, frequencies);
    }),
    NBT((property, buffer) -> {
        NBTTagCompound value = PacketHandler.readNBT(buffer);
        return new NBTPropertyData(property, value == null ? new NBTTagCompound() : value);
    });

    private static final PropertyType[] VALUES = values();
    private final DataCreator dataCreator;

    PropertyType(DataCreator dataCreator) {
        this.dataCreator = dataCreator;
    }

    @Nullable
    public static PropertyType byIndex(int index) {
        return index < 0 || index >= VALUES.length ? null : VALUES[index];
    }

    public PropertyData createData(short property, ByteBuf buffer) {
        return dataCreator.create(property, buffer);
    }

    @FunctionalInterface
    private interface DataCreator {

        PropertyData create(short property, ByteBuf buffer);
    }
}
