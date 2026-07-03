package mekanism.common.capabilities.holder.heat;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProxiedHeatCapacitorHolder extends ProxiedHolder implements IHeatCapacitorHolder {

    private final Function<EnumFacing, List<IHeatCapacitor>> capacitorFunction;

    public static ProxiedHeatCapacitorHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IHeatCapacitor>> capacitorFunction) {
        return new ProxiedHeatCapacitorHolder(insertPredicate, extractPredicate, capacitorFunction);
    }

    private ProxiedHeatCapacitorHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IHeatCapacitor>> capacitorFunction) {
        super(insertPredicate, extractPredicate);
        this.capacitorFunction = capacitorFunction;
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        return capacitorFunction.apply(side);
    }
}
