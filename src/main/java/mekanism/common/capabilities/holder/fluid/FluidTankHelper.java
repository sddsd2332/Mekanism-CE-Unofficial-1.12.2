package mekanism.common.capabilities.holder.fluid;

import mekanism.api.RelativeSide;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.component.TileComponentConfig;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class FluidTankHelper {

    private final IFluidTankHolder tankHolder;
    private boolean built;

    private FluidTankHelper(IFluidTankHolder tankHolder) {
        this.tankHolder = tankHolder;
    }

    public static FluidTankHelper forSide(Supplier<EnumFacing> facingSupplier) {
        return new FluidTankHelper(new FluidTankHolder(facingSupplier));
    }

    public static FluidTankHelper forSideWithConfig(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        return new FluidTankHelper(new ConfigFluidTankHolder(facingSupplier, configSupplier));
    }

    public static FluidTankHelper forSideWithConfig(ISideConfiguration sideConfiguration) {
        return new FluidTankHelper(new ConfigFluidTankHolder(sideConfiguration));
    }

    public <TANK extends IExtendedFluidTank> TANK addTank(@Nonnull TANK tank) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (tankHolder instanceof FluidTankHolder) {
            ((FluidTankHolder) tankHolder).addTank(tank);
        } else if (tankHolder instanceof ConfigFluidTankHolder) {
            ((ConfigFluidTankHolder) tankHolder).addTank(tank);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add tanks");
        }
        return tank;
    }

    public <TANK extends IExtendedFluidTank> TANK addTank(@Nonnull TANK tank, RelativeSide... sides) {
        if (built) {
            throw new IllegalStateException("Builder has already built.");
        }
        if (tankHolder instanceof FluidTankHolder) {
            ((FluidTankHolder) tankHolder).addTank(tank, sides);
        } else {
            throw new IllegalArgumentException("Holder does not know how to add tanks on specific sides");
        }
        return tank;
    }

    public IFluidTankHolder build() {
        built = true;
        return tankHolder;
    }
}
