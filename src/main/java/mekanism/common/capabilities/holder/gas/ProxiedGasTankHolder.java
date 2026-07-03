package mekanism.common.capabilities.holder.gas;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProxiedGasTankHolder extends ProxiedHolder implements IGasTankHolder {

    private final Function<EnumFacing, List<IExtendedGasTank>> tankFunction;
    private final Function<EnumFacing, List<IExtendedGasTank>> insertTankFunction;
    private final Function<EnumFacing, List<IExtendedGasTank>> extractTankFunction;

    public static ProxiedGasTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction) {
        return new ProxiedGasTankHolder(insertPredicate, extractPredicate, tankFunction,
              side -> insertPredicate.test(side) ? tankFunction.apply(side) : Collections.emptyList(),
              side -> extractPredicate.test(side) ? tankFunction.apply(side) : Collections.emptyList());
    }

    public static ProxiedGasTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction, Function<EnumFacing, List<IExtendedGasTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedGasTank>> extractTankFunction) {
        return new ProxiedGasTankHolder(insertPredicate, extractPredicate, tankFunction,
              side -> insertPredicate.test(side) ? insertTankFunction.apply(side) : Collections.emptyList(),
              side -> extractPredicate.test(side) ? extractTankFunction.apply(side) : Collections.emptyList());
    }

    protected ProxiedGasTankHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction, Function<EnumFacing, List<IExtendedGasTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedGasTank>> extractTankFunction) {
        super(insertPredicate, extractPredicate);
        this.tankFunction = tankFunction;
        this.insertTankFunction = insertTankFunction;
        this.extractTankFunction = extractTankFunction;
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanks(@Nullable EnumFacing side) {
        return tankFunction.apply(side);
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing side, @Nonnull IExtendedGasTank tank) {
        return getTanksForInsert(side).contains(tank);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side, @Nonnull IExtendedGasTank tank) {
        return getTanksForExtract(side).contains(tank);
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForInsert(@Nullable EnumFacing side) {
        return insertTankFunction.apply(side);
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForExtract(@Nullable EnumFacing side) {
        return extractTankFunction.apply(side);
    }
}
