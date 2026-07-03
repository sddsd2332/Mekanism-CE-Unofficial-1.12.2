package mekanism.common.inventory.container.sync;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.network.to_client.container.property.FluidStackPropertyData;
import mekanism.common.network.to_client.container.property.IntPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;
import mekanism.common.util.FluidContainerUtils;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableFluidStack implements ISyncableData {

    public static SyncableFluidStack create(IExtendedFluidTank handler) {
        return create(handler, false);
    }

    public static SyncableFluidStack create(IExtendedFluidTank handler, boolean isClient) {
        return create(handler::getFluid, isClient ? handler::setStackUnchecked : handler::setStack);
    }

    public static SyncableFluidStack create(Supplier<FluidStack> getter, Consumer<FluidStack> setter) {
        return new SyncableFluidStack(getter, setter);
    }

    @Nullable
    private FluidStack lastKnownValue;
    private final Supplier<FluidStack> getter;
    private final Consumer<FluidStack> setter;

    private SyncableFluidStack(Supplier<FluidStack> getter, Consumer<FluidStack> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Nullable
    public FluidStack get() {
        return getter.get();
    }

    public void set(@Nullable FluidStack value) {
        setter.accept(value);
    }

    public void set(int amount) {
        FluidStack fluid = get();
        if (fluid != null && fluid.amount > 0) {
            set(FluidContainerUtils.copyWithAmount(fluid, amount));
        }
    }

    @Override
    public DirtyType isDirty() {
        FluidStack value = get();
        boolean bothEmpty = (value == null || value.amount <= 0) && (lastKnownValue == null || lastKnownValue.amount <= 0);
        if (bothEmpty) {
            return DirtyType.CLEAN;
        }
        boolean sameFluid = value != null && lastKnownValue != null && value.isFluidEqual(lastKnownValue);
        int amount = value == null ? 0 : value.amount;
        int lastAmount = lastKnownValue == null ? 0 : lastKnownValue.amount;
        if (!sameFluid || amount != lastAmount) {
            lastKnownValue = value == null ? null : value.copy();
            return sameFluid ? DirtyType.SIZE : DirtyType.DIRTY;
        }
        return DirtyType.CLEAN;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        if (dirtyType == DirtyType.SIZE) {
            FluidStack stack = get();
            return new IntPropertyData(property, stack == null ? 0 : stack.amount);
        }
        return new FluidStackPropertyData(property, get());
    }
}
