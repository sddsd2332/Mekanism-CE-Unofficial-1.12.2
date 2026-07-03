package mekanism.common.inventory.container.sync;

import mekanism.api.gas.GasStack;
import mekanism.common.network.to_client.container.property.GasStackPropertyData;
import mekanism.common.network.to_client.container.property.IntPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableGasStack implements ISyncableData {

    public static SyncableGasStack create(Supplier<GasStack> getter, Consumer<GasStack> setter) {
        return new SyncableGasStack(getter, setter);
    }

    @Nullable
    private GasStack lastKnownValue;
    private final Supplier<GasStack> getter;
    private final Consumer<GasStack> setter;

    private SyncableGasStack(Supplier<GasStack> getter, Consumer<GasStack> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Nullable
    public GasStack get() {
        return getter.get();
    }

    public void set(@Nullable GasStack value) {
        setter.accept(value);
    }

    public void set(int amount) {
        GasStack stack = get();
        if (stack != null && stack.amount > 0) {
            set(stack.copy().withAmount(amount));
        }
    }

    @Override
    public DirtyType isDirty() {
        GasStack value = get();
        boolean bothEmpty = (value == null || value.amount <= 0) && (lastKnownValue == null || lastKnownValue.amount <= 0);
        if (bothEmpty) {
            return DirtyType.CLEAN;
        }
        boolean sameGas = value != null && lastKnownValue != null && value.isGasEqual(lastKnownValue);
        int amount = value == null ? 0 : value.amount;
        int lastAmount = lastKnownValue == null ? 0 : lastKnownValue.amount;
        if (!sameGas || amount != lastAmount) {
            lastKnownValue = value == null ? null : value.copy();
            return sameGas ? DirtyType.SIZE : DirtyType.DIRTY;
        }
        return DirtyType.CLEAN;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        if (dirtyType == DirtyType.SIZE) {
            GasStack stack = get();
            return new IntPropertyData(property, stack == null ? 0 : stack.amount);
        }
        return new GasStackPropertyData(property, get());
    }
}
