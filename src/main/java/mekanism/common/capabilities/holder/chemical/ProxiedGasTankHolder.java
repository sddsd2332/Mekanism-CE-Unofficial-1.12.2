package mekanism.common.capabilities.holder.chemical;

import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.util.EnumFacing;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Compatibility wrapper for older gas holder chemical naming.
 *
 * @deprecated Use {@link mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder}.
 */
@Deprecated
public class ProxiedGasTankHolder extends mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder implements IGasTankHolder {

    public static ProxiedGasTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction) {
        return new ProxiedGasTankHolder(insertPredicate, extractPredicate, tankFunction,
              tankFunction, tankFunction);
    }

    public static ProxiedGasTankHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction, Function<EnumFacing, List<IExtendedGasTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedGasTank>> extractTankFunction) {
        return new ProxiedGasTankHolder(insertPredicate, extractPredicate, tankFunction, insertTankFunction, extractTankFunction);
    }

    private ProxiedGasTankHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IExtendedGasTank>> tankFunction, Function<EnumFacing, List<IExtendedGasTank>> insertTankFunction,
          Function<EnumFacing, List<IExtendedGasTank>> extractTankFunction) {
        super(insertPredicate, extractPredicate, tankFunction, insertTankFunction, extractTankFunction);
    }
}
