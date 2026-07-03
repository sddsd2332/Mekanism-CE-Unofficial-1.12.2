package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.energy.*;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public class ProxyStrictEnergyHandler extends ProxyHandler implements IStrictEnergyHandler, IStrictEnergyStorage, IStrictEnergyAcceptor, IStrictEnergyOutputter {

    private final ISidedStrictEnergyHandler energyHandler;

    public ProxyStrictEnergyHandler(ISidedStrictEnergyHandler energyHandler, @Nullable EnumFacing side, @Nullable IHolder holder) {
        super(side, holder);
        this.energyHandler = energyHandler;
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
        for (int container = 0; container < getEnergyContainerCount(); container++) {
            maxEnergy += getMaxEnergy(container);
        }
        return maxEnergy;
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
