package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.ShortPropertyData;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SyncableShort implements ISyncableData {

    private short lastKnownValue;

    public abstract short get();

    public abstract void set(short value);

    @Override
    public DirtyType isDirty() {
        short oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public ShortPropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new ShortPropertyData(property, get());
    }

    public static SyncableShort create(short[] shortArray, int idx) {
        return create(() -> shortArray[idx], value -> shortArray[idx] = value);
    }

    public static SyncableShort create(Supplier<Short> getter, Consumer<Short> setter) {
        return new SyncableShort() {
            @Override
            public short get() {
                return getter.get();
            }

            @Override
            public void set(short value) {
                setter.accept(value);
            }
        };
    }
}
