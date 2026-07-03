package mekanism.common.capabilities.holder.chemical;

import mekanism.api.RelativeSide;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Compatibility wrapper for older gas holder chemical naming.
 *
 * @deprecated Use {@link mekanism.common.capabilities.holder.gas.GasTankHolder}.
 */
@Deprecated
public class GasTankHolder extends mekanism.common.capabilities.holder.gas.GasTankHolder implements IGasTankHolder {

    GasTankHolder(Supplier<EnumFacing> facingSupplier, @Nullable Predicate<RelativeSide> insertPredicate, @Nullable Predicate<RelativeSide> extractPredicate) {
        super(facingSupplier, insertPredicate, extractPredicate);
    }
}
