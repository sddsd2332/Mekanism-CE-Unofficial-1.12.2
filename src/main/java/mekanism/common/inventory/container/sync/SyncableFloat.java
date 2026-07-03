package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.FloatPropertyData;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SyncableFloat implements ISyncableData {

    private float lastKnownValue;

    public abstract float get();

    public abstract void set(float value);

    @Override
    public DirtyType isDirty() {
        float oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public FloatPropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new FloatPropertyData(property, get());
    }

    public static SyncableFloat create(float[] floatArray, int idx) {
        return create(() -> floatArray[idx], value -> floatArray[idx] = value);
    }

    public static SyncableFloat create(Supplier<Float> getter, Consumer<Float> setter) {
        return new SyncableFloat() {
            @Override
            public float get() {
                return getter.get();
            }

            @Override
            public void set(float value) {
                setter.accept(value);
            }
        };
    }
}
