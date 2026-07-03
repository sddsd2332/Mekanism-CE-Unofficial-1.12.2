package mekanism.common.capabilities.merged;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.fluid.FluidTankWrapper;

import java.util.Objects;

public class MergedTank {

    public static MergedTank create(IExtendedFluidTank fluidTank, IExtendedGasTank gasTank) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return new MergedTank(fluidTank, gasTank);
    }

    private final IExtendedFluidTank fluidTank;
    private final IExtendedGasTank gasTank;

    private MergedTank(IExtendedFluidTank fluidTank, IExtendedGasTank gasTank) {
        this.fluidTank = new FluidTankWrapper(this, fluidTank, gasTank::isEmpty);
        this.gasTank = new GasTankWrapper(this, gasTank, this.fluidTank::isEmpty);
    }

    public CurrentType getCurrentType() {
        if (fluidTank.getFluidAmount() > 0) {
            return CurrentType.FLUID;
        } else if (gasTank.getGasAmount() > 0) {
            return CurrentType.GAS;
        }
        return CurrentType.EMPTY;
    }

    public final IExtendedFluidTank getFluidTank() {
        return fluidTank;
    }

    public final IExtendedGasTank getGasTank() {
        return gasTank;
    }

    /**
     * Compatibility alias for older merged chemical naming.
     *
     * @deprecated Use {@link #getGasTank()}.
     */
    @Deprecated
    public final IExtendedGasTank getChemicalTank() {
        return getGasTank();
    }

    public enum CurrentType {
        EMPTY,
        FLUID,
        GAS,
        /**
         * @deprecated Use {@link #GAS}.
         */
        @Deprecated
        CHEMICAL;

        public boolean isGas() {
            return this == GAS || this == CHEMICAL;
        }
    }
}
