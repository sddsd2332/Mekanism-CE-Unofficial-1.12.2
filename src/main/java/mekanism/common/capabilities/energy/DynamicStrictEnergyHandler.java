package mekanism.common.capabilities.energy;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.capabilities.DynamicHandler;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class DynamicStrictEnergyHandler extends DynamicHandler<IEnergyContainer> implements IMekanismStrictEnergyHandler {

    public DynamicStrictEnergyHandler(Function<EnumFacing, List<IEnergyContainer>> containerSupplier, Predicate<EnumFacing> canExtract,
          Predicate<EnumFacing> canInsert, @Nullable IContentsListener listener) {
        super(containerSupplier, canExtract, canInsert, listener);
    }

    public DynamicStrictEnergyHandler(Function<EnumFacing, List<IEnergyContainer>> containerSupplier, InteractPredicate canExtract, InteractPredicate canInsert,
          @Nullable IContentsListener listener) {
        super(containerSupplier, canExtract, canInsert, listener);
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return containerSupplier.apply(side);
    }

    @Override
    public double insertEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        return canInsert.test(container, side) ? IMekanismStrictEnergyHandler.super.insertEnergy(container, amount, side, action) : amount;
    }

    @Override
    public double extractEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        return canExtract.test(container, side) ? IMekanismStrictEnergyHandler.super.extractEnergy(container, amount, side, action) : 0;
    }

    @Override
    public double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        List<IEnergyContainer> containers = getEnergyContainers(side);
        for (int container = 0; container < containers.size(); container++) {
            if (canInsert.test(container, side)) {
                return IMekanismStrictEnergyHandler.super.insertEnergy(amount, side, action);
            }
        }
        return amount;
    }

    @Override
    public double extractEnergy(double amount, @Nullable EnumFacing side, Action action) {
        List<IEnergyContainer> containers = getEnergyContainers(side);
        for (int container = 0; container < containers.size(); container++) {
            if (canExtract.test(container, side)) {
                return IMekanismStrictEnergyHandler.super.extractEnergy(amount, side, action);
            }
        }
        return 0;
    }
}
