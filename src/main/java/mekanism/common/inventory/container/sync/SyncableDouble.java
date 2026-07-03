package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.DoublePropertyData;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public abstract class SyncableDouble implements ISyncableData {

    private double lastKnownValue;

    public abstract double get();

    public abstract void set(double value);

    @Override
    public DirtyType isDirty() {
        double oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public DoublePropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new DoublePropertyData(property, get());
    }

    public static SyncableDouble create(double[] doubleArray, int idx) {
        return create(() -> doubleArray[idx], value -> doubleArray[idx] = value);
    }

    public static SyncableDouble create(DoubleSupplier getter, DoubleConsumer setter) {
        return new SyncableDouble() {
            @Override
            public double get() {
                return getter.getAsDouble();
            }

            @Override
            public void set(double value) {
                setter.accept(value);
            }
        };
    }
}
