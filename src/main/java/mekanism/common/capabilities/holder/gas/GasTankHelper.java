package mekanism.common.capabilities.holder.gas;

import mekanism.api.RelativeSide;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class GasTankHelper {

    private final IGasTankHolder tankHolder;
    private boolean built;

    protected GasTankHelper(IGasTankHolder tankHolder) {
        this.tankHolder = tankHolder;
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

    public <TANK extends IExtendedGasTank> TANK addTank(@Nonnull TANK tank) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (tankHolder instanceof GasTankHolder) {
            ((GasTankHolder) tankHolder).addTank(tank);
        } else if (tankHolder instanceof ConfigGasTankHolder) {
            ((ConfigGasTankHolder) tankHolder).addTank(tank);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add tanks");
        }
        return tank;
    }

    public <TANK extends IExtendedGasTank> TANK addTank(@Nonnull TANK tank, RelativeSide... sides) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (tankHolder instanceof GasTankHolder) {
            ((GasTankHolder) tankHolder).addTank(tank, sides);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add tanks on specific sides");
        }
        return tank;
    }

    public IGasTankHolder build() {
        built = true;
        return tankHolder;
    }
}
