package mekanism.common.util;

import mekanism.api.gas.IExtendedGasTank;
import net.minecraftforge.fluids.IFluidTank;

public interface TankProvider {

    int getTankCapacity();

    int getTankAmount();

    class Fluid implements TankProvider {

        private final IFluidTank handler;

        public Fluid(final IFluidTank handler) {
            this.handler = handler;
        }

        @Override
        public int getTankCapacity() {
            return handler.getCapacity();
        }

        @Override
        public int getTankAmount() {
            return handler.getFluidAmount();
        }
    }

    class Gas implements TankProvider {
        
        private final IExtendedGasTank handler;

        public Gas(final IExtendedGasTank handler) {
            this.handler = handler;
        }

        @Override
        public int getTankCapacity() {
            return handler.getMaxGas();
        }

        @Override
        public int getTankAmount() {
            return handler.getGasAmount();
        }

    }

}
