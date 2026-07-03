package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.BytePropertyData;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SyncableByte implements ISyncableData {

    private byte lastKnownValue;

    public abstract byte get();

    public abstract void set(byte value);

    @Override
    public DirtyType isDirty() {
        byte oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public BytePropertyData getPropertyData(short property, DirtyType dirtyType) {
        return new BytePropertyData(property, get());
    }

    public static SyncableByte create(byte[] byteArray, int idx) {
        return create(() -> byteArray[idx], value -> byteArray[idx] = value);
    }

    public static SyncableByte create(Supplier<Byte> getter, Consumer<Byte> setter) {
        return new SyncableByte() {
            @Override
            public byte get() {
                return getter.get();
            }

            @Override
            public void set(byte value) {
                setter.accept(value);
            }
        };
    }
}
