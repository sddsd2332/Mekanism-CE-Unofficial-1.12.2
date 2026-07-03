package mekanism.common.network.to_client.container.property;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.TileNetworkList;
import mekanism.common.HashList;
import mekanism.common.PacketHandler;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.tile.machine.TileEntityOredictionificator.OredictionificatorFilter;

import java.util.function.BiConsumer;

public class FilterListPropertyData<FILTER extends IFilter> extends PropertyData {

    private final FilterListType filterListType;
    private final HashList<FILTER> value;
    private final byte[] encodedFilterData;

    public FilterListPropertyData(short property, FilterListType filterListType, HashList<FILTER> value, BiConsumer<FILTER, TileNetworkList> writer) {
        super(PropertyType.FILTER_LIST, property);
        this.filterListType = filterListType;
        this.value = value.clone();
        this.encodedFilterData = encodeFilters(this.value, writer);
    }

    public FilterListPropertyData(short property, FilterListType filterListType, HashList<FILTER> value) {
        super(PropertyType.FILTER_LIST, property);
        this.filterListType = filterListType;
        this.value = value.clone();
        this.encodedFilterData = null;
    }

    private static <FILTER extends IFilter> byte[] encodeFilters(HashList<FILTER> value, BiConsumer<FILTER, TileNetworkList> writer) {
        TileNetworkList data = new TileNetworkList();
        for (FILTER filter : value) {
            writer.accept(filter, data);
        }
        ByteBuf buffer = Unpooled.buffer();
        try {
            PacketHandler.encode(data.toArray(), buffer);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    @Override
    public void handleWindowProperty(MekanismContainer container) {
        container.handleWindowProperty(getProperty(), value);
    }

    @Override
    public void writeToPacket(ByteBuf buffer) {
        super.writeToPacket(buffer);
        buffer.writeByte(filterListType.ordinal());
        buffer.writeInt(value.size());
        if (encodedFilterData != null) {
            buffer.writeBytes(encodedFilterData);
        }
    }

    public enum FilterListType {
        TRANSPORTER {
            @Override
            public IFilter readFilter(ByteBuf buffer) {
                return TransporterFilter.readFromPacket(buffer);
            }
        },
        MINER {
            @Override
            public IFilter readFilter(ByteBuf buffer) {
                return MinerFilter.readFromPacket(buffer);
            }
        },
        OREDICTIONIFICATOR {
            @Override
            public IFilter readFilter(ByteBuf buffer) {
                return OredictionificatorFilter.readFromPacket(buffer);
            }
        };

        private static final FilterListType[] VALUES = values();

        public abstract IFilter readFilter(ByteBuf buffer);

        public static FilterListType byIndex(int index) {
            return index < 0 || index >= VALUES.length ? MINER : VALUES[index];
        }
    }
}
