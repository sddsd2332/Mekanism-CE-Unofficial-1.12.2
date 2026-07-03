package mekanism.common.inventory.container.sync;

import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.network.to_client.container.property.FrequencyListPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableFrequencyList<FREQ extends Frequency> implements ISyncableData {

    public static <FREQ extends Frequency> SyncableFrequencyList<FREQ> create(FrequencyType<FREQ> type, Supplier<? extends Collection<FREQ>> getter,
          Consumer<List<FREQ>> setter) {
        return new SyncableFrequencyList<>(type, getter, setter);
    }

    private final FrequencyType<FREQ> type;
    private final Supplier<? extends Collection<FREQ>> getter;
    private final Consumer<List<FREQ>> setter;
    private int lastKnownHash;

    private SyncableFrequencyList(FrequencyType<FREQ> type, Supplier<? extends Collection<FREQ>> getter, Consumer<List<FREQ>> setter) {
        this.type = type;
        this.getter = getter;
        this.setter = setter;
    }

    public Collection<FREQ> getRaw() {
        Collection<FREQ> value = getter.get();
        return value == null ? java.util.Collections.emptyList() : value;
    }

    public List<FREQ> get() {
        Collection<FREQ> value = getRaw();
        if (value instanceof List) {
            return (List<FREQ>) value;
        }
        return new ArrayList<>(value);
    }

    @SuppressWarnings("unchecked")
    public void set(List<? extends Frequency> value) {
        setter.accept((List<FREQ>) value);
    }

    @Override
    public DirtyType isDirty() {
        int hash = 1;
        for (FREQ frequency : getRaw()) {
            hash = 31 * hash + frequency.getSyncHash();
        }
        if (hash == lastKnownHash) {
            return DirtyType.CLEAN;
        }
        lastKnownHash = hash;
        return DirtyType.DIRTY;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new FrequencyListPropertyData(property, type, getRaw());
    }
}
