package mekanism.common.capabilities.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.heat.HeatAPI;
import mekanism.api.functions.ConstantPredicates;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * High-version style energy container adapted to 1.12's double based energy values.
 */
public class BasicEnergyContainer implements IEnergyContainer, IContentsListener, INBTSerializable<NBTTagCompound> {

    public static final Predicate<AutomationType> internalOnly = automationType -> automationType == AutomationType.INTERNAL;
    public static final Predicate<AutomationType> manualOnly = automationType -> automationType == AutomationType.MANUAL;
    public static final Predicate<AutomationType> notExternal = automationType -> automationType != AutomationType.EXTERNAL;

    public static BasicEnergyContainer create(double maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysTrue(), listener);
    }

    public static BasicEnergyContainer input(double maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, notExternal, ConstantPredicates.alwaysTrue(), listener);
    }

    public static BasicEnergyContainer output(double maxEnergy, @Nullable IContentsListener listener) {
        return new BasicEnergyContainer(maxEnergy, ConstantPredicates.alwaysTrue(), internalOnly, listener);
    }

    public static BasicEnergyContainer create(double maxEnergy, Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new BasicEnergyContainer(maxEnergy, canExtract, canInsert, listener);
    }

    private double stored;
    protected final Predicate<AutomationType> canExtract;
    protected final Predicate<AutomationType> canInsert;
    private final double maxEnergy;
    @Nullable
    private final IContentsListener listener;

    protected BasicEnergyContainer(double maxEnergy, Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert,
          @Nullable IContentsListener listener) {
        if (!HeatAPI.isFinite(maxEnergy) || maxEnergy < 0) {
            throw new IllegalArgumentException("Max energy cannot be negative");
        }
        this.maxEnergy = Math.min(HeatAPI.MAX_HEAT, maxEnergy);
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.listener = listener;
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public double getEnergy() {
        return HeatAPI.isFinite(stored) && stored > 0 ? Math.min(HeatAPI.MAX_HEAT, stored) : 0;
    }

    protected double clampEnergy(double energy) {
        return HeatAPI.isFinite(energy) ? Math.max(0, Math.min(energy, getMaxEnergy())) : 0;
    }

    @Override
    public void setEnergy(double energy) {
        double previous = stored;
        setEnergyNoUpdate(energy);
        if (previous != stored) onContentsChanged();
    }

    /** Used only inside server-thread transactions; callers notify after the entire transaction succeeds. */
    public void setEnergyNoUpdate(double energy) {
        if (Double.isNaN(energy) || Double.isInfinite(energy)) {
            energy = 0;
        } else if (energy < 0) {
            throw new IllegalArgumentException("Energy cannot be negative");
        }
        energy = clampEnergy(energy);
        stored = energy;
    }

    protected double getInsertRate(@Nullable AutomationType automationType) {
        return Double.MAX_VALUE;
    }

    protected double getExtractRate(@Nullable AutomationType automationType) {
        return Double.MAX_VALUE;
    }

    @Override
    public double insert(double amount, Action action, AutomationType automationType) {
        if (!HeatAPI.isFinite(amount) || amount <= 0 || !canInsert.test(automationType)) {
            return amount;
        }
        double needed = Math.min(getInsertRate(automationType), getNeeded());
        if (needed <= 0) {
            return amount;
        }
        double toAdd = Math.min(amount, needed);
        if (action.execute()) {
            setEnergy(getEnergy() + toAdd);
        }
        return amount - toAdd;
    }

    @Override
    public double insert(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return insert(amount, action, automationType);
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        if (!HeatAPI.isFinite(amount) || amount <= 0 || !canExtract.test(automationType)) {
            return 0;
        }
        double ret = Math.min(Math.min(getExtractRate(automationType), getEnergy()), amount);
        if (ret > 0 && action.execute()) {
            setEnergy(getEnergy() - ret);
        }
        return ret;
    }

    @Override
    public double extract(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return extract(amount, action, automationType);
    }

    @Override
    public boolean isEmpty() {
        return stored <= 0;
    }

    @Override
    public double getMaxEnergy() {
        return maxEnergy;
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return amount - insert(amount, Action.get(!simulate), AutomationType.handler(side));
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return canInsert.test(AutomationType.handler(side)) && getNeeded() > 0;
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return extract(amount, Action.get(!simulate), AutomationType.handler(side));
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return canExtract.test(AutomationType.handler(side)) && !isEmpty();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!isEmpty()) {
            nbt.setDouble(NBTConstants.STORED, stored);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED)) {
            setEnergy(nbt.getDouble(NBTConstants.STORED));
        } else if (nbt.hasKey(NBTConstants.ENERGY_STORED)) {
            setEnergy(nbt.getDouble(NBTConstants.ENERGY_STORED));
        }
    }
}
