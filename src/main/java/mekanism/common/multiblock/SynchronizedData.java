package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.IHeatTransfer;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public abstract class SynchronizedData<T extends SynchronizedData<T>> implements IMekanismInventory {

    public Set<Coord4D> locations = new ObjectOpenHashSet<>();

    public int volLength;

    public int volWidth;

    public int volHeight;

    public int volume;

    public String inventoryID;

    public boolean didTick;

    public boolean hasRenderer;

    @Nullable//may be null if structure has not been fully sent
    public Coord4D renderLocation;

    public Coord4D minLocation;
    public Coord4D maxLocation;

    public boolean destroyed;
    private boolean formed;

    public Set<Coord4D> internalLocations = new ObjectOpenHashSet<>();

    protected final List<IInventorySlot> inventorySlots = new ArrayList<>();
    protected final List<IExtendedFluidTank> fluidTanks = new ArrayList<>();
    protected final List<IExtendedGasTank> gasTanks = new ArrayList<>();
    protected final List<IEnergyContainer> energyContainers = new ArrayList<>();
    protected final List<IHeatTransfer> heatTransfers = new ArrayList<>();

    private final BiPredicate<Object, AutomationType> formedBiPred = (stack, automationType) -> isFormed();
    private final BiPredicate<Object, AutomationType> notExternalFormedBiPred = (stack, automationType) -> automationType != AutomationType.EXTERNAL && isFormed();

    @SuppressWarnings("unchecked")
    public <V> BiPredicate<V, AutomationType> formedBiPred() {
        return (BiPredicate<V, AutomationType>) formedBiPred;
    }

    @SuppressWarnings("unchecked")
    public <V> BiPredicate<V, AutomationType> notExternalFormedBiPred() {
        return (BiPredicate<V, AutomationType>) notExternalFormedBiPred;
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return isFormed() ? inventorySlots : Collections.emptyList();
    }

    @Nonnull
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return isFormed() ? fluidTanks : Collections.emptyList();
    }

    @Nonnull
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return isFormed() ? gasTanks : Collections.emptyList();
    }

    @Nonnull
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return isFormed() ? energyContainers : Collections.emptyList();
    }

    @Nonnull
    public List<IHeatTransfer> getHeatTransfers(@Nullable EnumFacing side) {
        return isFormed() ? heatTransfers : Collections.emptyList();
    }

    @Override
    public boolean hasInventory() {
        return isFormed() && !inventorySlots.isEmpty();
    }

    @Override
    public void onContentsChanged() {
    }

    public List<IInventorySlot> getInternalInventorySlots() {
        return Collections.unmodifiableList(inventorySlots);
    }

    public boolean isFormed() {
        return formed && !destroyed;
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + locations.hashCode();
        code = 31 * code + volLength;
        code = 31 * code + volWidth;
        code = 31 * code + volHeight;
        code = 31 * code + volume;
        return code;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        SynchronizedData<T> data = (SynchronizedData<T>) obj;
        if (!data.locations.equals(locations)) {
            return false;
        }
        if (data.volLength != volLength || data.volWidth != volWidth || data.volHeight != volHeight) {
            return false;
        }
        return data.volume == volume;
    }
}
