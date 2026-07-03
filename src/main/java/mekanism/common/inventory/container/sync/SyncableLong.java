package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.LongPropertyData;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public abstract class SyncableLong implements ISyncableData {

    private long lastKnownValue;

    public abstract long get();

    public abstract void set(long value);

    @Override
    public DirtyType isDirty() {
        long oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public LongPropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new LongPropertyData(property, get());
    }

    public static SyncableLong create(long[] longArray, int idx) {
        return create(() -> longArray[idx], value -> longArray[idx] = value);
    }

    public static SyncableLong create(LongSupplier getter, LongConsumer setter) {
        return new SyncableLong() {
            @Override
            public long get() {
                return getter.getAsLong();
            }

            @Override
            public void set(long value) {
                setter.accept(value);
            }
        };
    }
}
