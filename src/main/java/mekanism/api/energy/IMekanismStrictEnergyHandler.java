package mekanism.api.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public interface IMekanismStrictEnergyHandler extends ISidedStrictEnergyHandler, IContentsListener {

    default boolean canHandleEnergy() {
        return true;
    }

    @Nonnull
    List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side);

    @Nullable
    default IEnergyContainer getEnergyContainer(int container, @Nullable EnumFacing side) {
        List<IEnergyContainer> containers = getEnergyContainers(side);
        return container >= 0 && container < containers.size() ? containers.get(container) : null;
    }

    @Override
    default int getEnergyContainerCount(@Nullable EnumFacing side) {
        return getEnergyContainers(side).size();
    }

    @Override
    default double getEnergy(int container, @Nullable EnumFacing side) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        return energyContainer == null ? 0 : energyContainer.getEnergy();
    }

    @Override
    default void setEnergy(int container, double energy, @Nullable EnumFacing side) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        if (energyContainer != null) {
            energyContainer.setEnergy(Math.max(0, energy));
        }
    }

    @Override
    default double getMaxEnergy(int container, @Nullable EnumFacing side) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        return energyContainer == null ? 0 : energyContainer.getMaxEnergy();
    }

    @Override
    default double getNeededEnergy(int container, @Nullable EnumFacing side) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        return energyContainer == null ? 0 : energyContainer.getNeeded();
    }

    @Override
    default double insertEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        return energyContainer == null ? amount : energyContainer.insert(amount, side, action, AutomationType.handler(side));
    }

    @Override
    default double extractEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        IEnergyContainer energyContainer = getEnergyContainer(container, side);
        return energyContainer == null ? 0 : energyContainer.extract(amount, side, action, AutomationType.handler(side));
    }

    @Override
    default double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        if (amount <= 0) {
            return 0;
        }
        List<IEnergyContainer> energyContainers = getEnergyContainers(side);
        int containerCount = energyContainers.size();
        if (containerCount == 0) {
            return amount;
        } else if (containerCount == 1) {
            return energyContainers.get(0).insert(amount, side, action, AutomationType.handler(side));
        }
        double toInsert = amount;
        List<IEnergyContainer> emptyContainers = new ArrayList<>();
        AutomationType automationType = AutomationType.handler(side);
        for (IEnergyContainer energyContainer : energyContainers) {
            if (energyContainer.getEnergy() <= 0) {
                emptyContainers.add(energyContainer);
            } else {
                double remainder = energyContainer.insert(toInsert, side, action, automationType);
                if (remainder <= 0) {
                    return 0;
                }
                toInsert = remainder;
            }
        }
        for (IEnergyContainer energyContainer : emptyContainers) {
            double remainder = energyContainer.insert(toInsert, side, action, automationType);
            if (remainder <= 0) {
                return 0;
            }
            toInsert = remainder;
        }
        return toInsert;
    }

    @Override
    default double extractEnergy(double amount, @Nullable EnumFacing side, Action action) {
        if (amount <= 0) {
            return 0;
        }
        List<IEnergyContainer> energyContainers = getEnergyContainers(side);
        int containerCount = energyContainers.size();
        if (containerCount == 0) {
            return 0;
        } else if (containerCount == 1) {
            return energyContainers.get(0).extract(amount, side, action, AutomationType.handler(side));
        }
        double extracted = 0;
        double toExtract = amount;
        AutomationType automationType = AutomationType.handler(side);
        for (IEnergyContainer energyContainer : energyContainers) {
            double drained = energyContainer.extract(toExtract, side, action, automationType);
            if (drained > 0) {
                extracted += drained;
                toExtract -= drained;
                if (toExtract <= 0) {
                    break;
                }
            }
        }
        return extracted;
    }
}
