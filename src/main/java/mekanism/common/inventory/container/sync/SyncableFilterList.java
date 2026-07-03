package mekanism.common.inventory.container.sync;

import mekanism.api.TileNetworkList;
import mekanism.common.HashList;
import mekanism.common.content.filter.IFilter;
import mekanism.common.network.to_client.container.property.FilterListPropertyData;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import mekanism.common.network.to_client.container.property.PropertyData;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SyncableFilterList<FILTER extends IFilter> implements ISyncableData {

    private int lastKnownHash;

    public abstract HashList<FILTER> get();

    public abstract void set(HashList<FILTER> value);

    public abstract FilterListType getType();

    public abstract BiConsumer<FILTER, TileNetworkList> getWriter();

    @Override
    public DirtyType isDirty() {
        int oldHash = Objects.hashCode(get());
        boolean dirty = oldHash != lastKnownHash;
        lastKnownHash = oldHash;
        return DirtyType.get(dirty);
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new FilterListPropertyData<>(property, getType(), get(), getWriter());
    }

    public static <FILTER extends IFilter> SyncableFilterList<FILTER> create(Supplier<HashList<FILTER>> getter, Consumer<HashList<FILTER>> setter,
          FilterListType type, BiConsumer<FILTER, TileNetworkList> writer) {
        return new SyncableFilterList<>() {
            @Override
            public HashList<FILTER> get() {
                return getter.get();
            }

            @Override
            public void set(HashList<FILTER> value) {
                setter.accept(value);
            }

            @Override
            public FilterListType getType() {
                return type;
            }

            @Override
            public BiConsumer<FILTER, TileNetworkList> getWriter() {
                return writer;
            }
        };
    }
}
