package mekanism.common.capabilities.holder.fluid;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProxiedFluidTankHolder extends ProxiedHolder implements IFluidTankHolder {

    private final Function<EnumFacing, List<IExtendedFluidTank>> tankFunction;
    private final Function<EnumFacing, List<IExtendedFluidTank>> insertTankFunction;
    private final Function<EnumFacing, List<IExtendedFluidTank>> extractTankFunction;

    public static ProxiedFluidTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedFluidTank>> tankFunction) {
        return new ProxiedFluidTankHolder(insertPredicate, extractPredicate, tankFunction,
              side -> insertPredicate.test(side) ? tankFunction.apply(side) : Collections.emptyList(),
              side -> extractPredicate.test(side) ? tankFunction.apply(side) : Collections.emptyList());
    }

    public static ProxiedFluidTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedFluidTank>> tankFunction, Function<EnumFacing, List<IExtendedFluidTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedFluidTank>> extractTankFunction) {
        return new ProxiedFluidTankHolder(insertPredicate, extractPredicate, tankFunction,
              side -> insertPredicate.test(side) ? insertTankFunction.apply(side) : Collections.emptyList(),
              side -> extractPredicate.test(side) ? extractTankFunction.apply(side) : Collections.emptyList());
    }

    private ProxiedFluidTankHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedFluidTank>> tankFunction, Function<EnumFacing, List<IExtendedFluidTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedFluidTank>> extractTankFunction) {
        super(insertPredicate, extractPredicate);
        this.tankFunction = tankFunction;
        this.insertTankFunction = insertTankFunction;
        this.extractTankFunction = extractTankFunction;
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanks(@Nullable EnumFacing side) {
        return tankFunction.apply(side);
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing side, @Nonnull IExtendedFluidTank tank) {
        return getTanksForInsert(side).contains(tank);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side, @Nonnull IExtendedFluidTank tank) {
        return getTanksForExtract(side).contains(tank);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForInsert(@Nullable EnumFacing side) {
        return insertTankFunction.apply(side);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForExtract(@Nullable EnumFacing side) {
        return extractTankFunction.apply(side);
    }
}
