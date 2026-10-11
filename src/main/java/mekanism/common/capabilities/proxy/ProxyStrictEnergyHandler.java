package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.energy.*;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;

public class ProxyStrictEnergyHandler extends ProxyHandler implements IStrictEnergyHandler, IStrictEnergyStorage, IStrictEnergyAcceptor, IStrictEnergyOutputter {

    private static final ClassValue<Boolean> DEFAULT_CONTAINER_READS = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getEnergyContainerCount", EnumFacing.class).getDeclaringClass() == IMekanismStrictEnergyHandler.class &&
                        type.getMethod("getEnergyContainer", int.class, EnumFacing.class).getDeclaringClass() == IMekanismStrictEnergyHandler.class &&
                        type.getMethod("getEnergy", int.class, EnumFacing.class).getDeclaringClass() == IMekanismStrictEnergyHandler.class &&
                        type.getMethod("getMaxEnergy", int.class, EnumFacing.class).getDeclaringClass() == IMekanismStrictEnergyHandler.class;
            } catch (ReflectiveOperationException | SecurityException unavailable) {
                return false;
            }
        }
    };

    private final ISidedStrictEnergyHandler energyHandler;
    @Nullable
    private final IMekanismStrictEnergyHandler directContainerReads;

    public ProxyStrictEnergyHandler(ISidedStrictEnergyHandler energyHandler, @Nullable EnumFacing side, @Nullable IHolder holder) {
        super(side, holder);
        this.energyHandler = energyHandler;
        directContainerReads = getClass() == ProxyStrictEnergyHandler.class &&
                energyHandler instanceof IMekanismStrictEnergyHandler nativeHandler && DEFAULT_CONTAINER_READS.get(energyHandler.getClass())
                ? nativeHandler : null;
    }

    @Override
    public int getEnergyContainerCount() {
        return energyHandler.getEnergyContainerCount(side);
    }

    @Override
    public double getEnergy(int container) {
        return energyHandler.getEnergy(container, side);
    }

    @Override
    public void setEnergy(int container, double energy) {
        if (!readOnly) {
            energyHandler.setEnergy(container, energy, side);
        }
    }

    @Override
    public double getMaxEnergy(int container) {
        return energyHandler.getMaxEnergy(container, side);
    }

    @Override
    public double getNeededEnergy(int container) {
        return energyHandler.getNeededEnergy(container, side);
    }

    @Override
    public double insertEnergy(int container, double amount, Action action) {
        return readOnlyInsert() ? amount : energyHandler.insertEnergy(container, amount, side, action);
    }

    @Override
    public double extractEnergy(int container, double amount, Action action) {
        return readOnlyExtract() ? 0 : energyHandler.extractEnergy(container, amount, side, action);
    }

    @Override
    public double insertEnergy(double amount, Action action) {
        return readOnlyInsert() ? amount : energyHandler.insertEnergy(amount, side, action);
    }

    @Override
    public double extractEnergy(double amount, Action action) {
        return readOnlyExtract() ? 0 : energyHandler.extractEnergy(amount, side, action);
    }

    @Override
    public double getEnergy() {
        double energy = 0;
        if (directContainerReads != null) {
            for (IEnergyContainer container : directContainerReads.getEnergyContainers(side))
                if (container != null) energy += container.getEnergy();
            return energy;
        }
        for (int container = 0; container < getEnergyContainerCount(); container++) {
            energy += getEnergy(container);
        }
        return energy;
    }

    @Override
    public void setEnergy(double energy) {
        if (readOnly) {
            return;
        }
        double remaining = Math.max(0, energy);
        for (int container = 0; container < getEnergyContainerCount(); container++) {
            double toSet = Math.min(remaining, getMaxEnergy(container));
            setEnergy(container, toSet);
            remaining -= toSet;
        }
    }

    @Override
    public double getMaxEnergy() {
        double maxEnergy = 0;
        if (directContainerReads != null) {
            for (IEnergyContainer container : directContainerReads.getEnergyContainers(side))
                if (container != null) maxEnergy += container.getMaxEnergy();
            return maxEnergy;
        }
        for (int container = 0; container < getEnergyContainerCount(); container++) {
            maxEnergy += getMaxEnergy(container);
        }
        return maxEnergy;
    }

    /**
     * Legacy aggregate bridge: reuse one live view, as modern bulk energy access does.
     * Keep max-then-stored summation and subtraction; summing per-container clamped needs
     * changes double rounding and the behavior of overfull/custom containers.
     */
    @Override
    public double getNeedEnergy() {
        if (directContainerReads == null) return getMaxEnergy() - getEnergy();
        List<IEnergyContainer> containers = directContainerReads.getEnergyContainers(side);
        double capacity = 0, stored = 0;
        for (IEnergyContainer container : containers) if (container != null) capacity += container.getMaxEnergy();
        for (IEnergyContainer container : containers) if (container != null) stored += container.getEnergy();
        return capacity - stored;
    }

    @Override
    public double acceptEnergy(EnumFacing ignored, double amount, boolean simulate) {
        if (readOnlyInsert()) {
            return 0;
        }
        return amount - insertEnergy(amount, Action.get(!simulate));
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing ignored) {
        return !readOnlyInsert() && getNeedEnergy() > 0;
    }

    @Override
    public double pullEnergy(EnumFacing ignored, double amount, boolean simulate) {
        return readOnlyExtract() ? 0 : extractEnergy(amount, Action.get(!simulate));
    }

    @Override
    public boolean canOutputEnergy(EnumFacing ignored) {
        return !readOnlyExtract() && getEnergy() > 0;
    }
}
