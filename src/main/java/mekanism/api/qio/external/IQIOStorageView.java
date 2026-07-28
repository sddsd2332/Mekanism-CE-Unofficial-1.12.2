package mekanism.api.qio.external;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Authorized, lifecycle-bound access to one QIO frequency. */
public interface IQIOStorageView {

    @Nonnull
    UUID getFrequencyUUID();

    @Nonnull
    String getFrequencyName();

    long getContentsRevision();

    long getCapacityRevision();

    long getAccessRevision();

    @Nonnull
    QIOStorageSnapshot getSnapshot();

    @Nullable
    QIOStorageEntry getResource(UUID resourceUUID);

    long insert(ItemStack stack, long amount, Action action);

    long insert(FluidStack stack, long amount, Action action);

    long insert(GasStack stack, long amount, Action action);

    long extract(ItemStack stack, long amount, Action action);

    long extract(FluidStack stack, long amount, Action action);

    long extract(GasStack stack, long amount, Action action);

    boolean addListener(IQIOStorageListener listener);

    boolean removeListener(IQIOStorageListener listener);

    boolean isValid();

    void close();
}
