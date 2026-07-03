package mekanism.common.capabilities.holder.chemical;

import mekanism.api.RelativeSide;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Compatibility wrapper for older gas holder chemical naming.
 *
 * @deprecated Use {@link mekanism.common.capabilities.holder.gas.GasTankHelper}.
 */
@Deprecated
public class GasTankHelper extends mekanism.common.capabilities.holder.gas.GasTankHelper {

    private GasTankHelper(mekanism.common.capabilities.holder.gas.IGasTankHolder tankHolder) {
        super(tankHolder);
    }

    public static GasTankHelper forSide(Supplier<EnumFacing> facingSupplier) {
        return forSide(facingSupplier, null, null);
    }

    public static GasTankHelper forSide(Supplier<EnumFacing> facingSupplier, @Nullable Predicate<RelativeSide> insertPredicate,
          @Nullable Predicate<RelativeSide> extractPredicate) {
        return new GasTankHelper(new GasTankHolder(facingSupplier, insertPredicate, extractPredicate));
    }

    public static GasTankHelper forSideWithConfig(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        return new GasTankHelper(new ConfigGasTankHolder(facingSupplier, configSupplier));
    }

    public static GasTankHelper forSideWithConfig(ISideConfiguration sideConfiguration) {
        return new GasTankHelper(new ConfigGasTankHolder(sideConfiguration));
    }

    @Nonnull
    @Override
    public IGasTankHolder build() {
        return (IGasTankHolder) super.build();
    }
}
