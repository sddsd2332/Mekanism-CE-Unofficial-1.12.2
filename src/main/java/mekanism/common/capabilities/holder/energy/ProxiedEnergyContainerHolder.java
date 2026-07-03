package mekanism.common.capabilities.holder.energy;

import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProxiedEnergyContainerHolder extends ProxiedHolder implements IEnergyContainerHolder {

    private final Function<EnumFacing, List<IEnergyContainer>> containerFunction;

    public static ProxiedEnergyContainerHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IEnergyContainer>> containerFunction) {
        return new ProxiedEnergyContainerHolder(insertPredicate, extractPredicate, containerFunction);
    }

    private ProxiedEnergyContainerHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IEnergyContainer>> containerFunction) {
        super(insertPredicate, extractPredicate);
        this.containerFunction = containerFunction;
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return containerFunction.apply(side);
    }
}
