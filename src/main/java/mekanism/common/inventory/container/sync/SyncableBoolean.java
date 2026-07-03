package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.BooleanPropertyData;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class SyncableBoolean implements ISyncableData {

    private boolean lastKnownValue;

    public abstract boolean get();

    public abstract void set(boolean value);

    @Override
    public DirtyType isDirty() {
        boolean oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public BooleanPropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new BooleanPropertyData(property, get());
    }

    public static SyncableBoolean create(boolean[] booleanArray, int idx) {
        return create(() -> booleanArray[idx], value -> booleanArray[idx] = value);
    }

    public static SyncableBoolean create(boolean[][] booleanArray, int idx1, int idx2) {
        return create(() -> booleanArray[idx1][idx2], value -> booleanArray[idx1][idx2] = value);
    }

    public static SyncableBoolean create(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new SyncableBoolean() {
            @Override
            public boolean get() {
                return getter.getAsBoolean();
            }

            @Override
            public void set(boolean value) {
                setter.accept(value);
            }
        };
    }
}
