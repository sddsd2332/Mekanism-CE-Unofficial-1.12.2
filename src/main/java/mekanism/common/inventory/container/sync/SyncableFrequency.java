package mekanism.common.inventory.container.sync;

import mekanism.common.frequency.Frequency;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.network.to_client.container.property.FrequencyPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableFrequency<FREQ extends Frequency> implements ISyncableData {

    public static <FREQ extends Frequency> SyncableFrequency<FREQ> create(FrequencyType<FREQ> type, Supplier<FREQ> getter, Consumer<FREQ> setter) {
        return new SyncableFrequency<>(type, getter, setter);
    }

    private final FrequencyType<FREQ> type;
    private final Supplier<FREQ> getter;
    private final Consumer<FREQ> setter;
    private int lastKnownHash;

    private SyncableFrequency(FrequencyType<FREQ> type, Supplier<FREQ> getter, Consumer<FREQ> setter) {
        this.type = type;
        this.getter = getter;
        this.setter = setter;
    }

    public FREQ get() {
        return getter.get();
    }

    public void set(Frequency frequency) {
        setter.accept(frequency == null ? null : (FREQ) frequency);
    }

    @Override
    public DirtyType isDirty() {
        FREQ value = get();
        int hash = value == null ? 0 : value.getSyncHash();
        if (hash == lastKnownHash) {
            return DirtyType.CLEAN;
        }
        lastKnownHash = hash;
        return DirtyType.DIRTY;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new FrequencyPropertyData(property, type, get());
    }
}
