package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.IntPropertyData;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableEnum<ENUM extends Enum<ENUM>> implements ISyncableData {

    public static <ENUM extends Enum<ENUM>> SyncableEnum<ENUM> create(ENUM[] values, Supplier<ENUM> getter, Consumer<ENUM> setter) {
        return new SyncableEnum<>(values, getter, setter);
    }

    private final ENUM[] values;
    private final Supplier<ENUM> getter;
    private final Consumer<ENUM> setter;
    private ENUM lastKnownValue;

    private SyncableEnum(ENUM[] values, Supplier<ENUM> getter, Consumer<ENUM> setter) {
        this.values = values;
        this.getter = getter;
        this.setter = setter;
    }

    public ENUM get() {
        return getter.get();
    }

    public void set(int ordinal) {
        if (ordinal >= 0 && ordinal < values.length) {
            setter.accept(values[ordinal]);
        }
    }

    @Override
    public DirtyType isDirty() {
        ENUM oldValue = get();
        boolean dirty = oldValue != lastKnownValue;
        lastKnownValue = oldValue;
        return DirtyType.get(dirty);
    }

    @Override
    public IntPropertyData getPropertyData(short property, DirtyType dirtyType) {
        ENUM value = get();
        return new IntPropertyData(property, value == null ? -1 : value.ordinal());
    }
}
