package mekanism.common.tile.component.config.slot;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public interface IProxiedSlotInfo extends ISlotInfo {

    class EnergyProxy extends EnergySlotInfo implements IProxiedSlotInfo {

        private final Supplier<List<IEnergyContainer>> containerSupplier;

        public EnergyProxy(boolean canInput, boolean canOutput, Supplier<List<IEnergyContainer>> containerSupplier) {
            super(canInput, canOutput, Collections.emptyList());
            this.containerSupplier = containerSupplier;
        }

        @Override
        public List<IEnergyContainer> getContainers() {
            return containerSupplier.get();
        }
    }

    class FluidProxy extends FluidSlotInfo implements IProxiedSlotInfo {

        private final Supplier<List<IExtendedFluidTank>> tankSupplier;

        public FluidProxy(boolean canInput, boolean canOutput, Supplier<List<IExtendedFluidTank>> tankSupplier) {
            super(canInput, canOutput, Collections.emptyList());
            this.tankSupplier = tankSupplier;
        }

        @Override
        public List<IExtendedFluidTank> getTanks() {
            return tankSupplier.get();
        }

        @Override
        public List<IExtendedFluidTank> getInputTanks() {
            return canInput() ? tankSupplier.get() : Collections.emptyList();
        }

        @Override
        public List<IExtendedFluidTank> getOutputTanks() {
            return canOutput() ? tankSupplier.get() : Collections.emptyList();
        }
    }

    class GasProxy extends GasSlotInfo implements IProxiedSlotInfo {

        private final Supplier<List<IExtendedGasTank>> tankSupplier;

        public GasProxy(boolean canInput, boolean canOutput, Supplier<List<IExtendedGasTank>> tankSupplier) {
            super(canInput, canOutput, Collections.emptyList());
            this.tankSupplier = tankSupplier;
        }

        @Override
        public List<IExtendedGasTank> getTanks() {
            return tankSupplier.get();
        }

        @Override
        public List<IExtendedGasTank> getInputTanks() {
            return canInput() ? tankSupplier.get() : Collections.emptyList();
        }

        @Override
        public List<IExtendedGasTank> getOutputTanks() {
            return canOutput() ? tankSupplier.get() : Collections.emptyList();
        }
    }

    class InventoryProxy extends InventorySlotInfo implements IProxiedSlotInfo {

        private final Supplier<List<IInventorySlot>> slotSupplier;

        public InventoryProxy(boolean canInput, boolean canOutput, Supplier<List<IInventorySlot>> slotSupplier) {
            super(canInput, canOutput, Collections.emptyList());
            this.slotSupplier = slotSupplier;
        }

        @Override
        public List<IInventorySlot> getSlots() {
            return slotSupplier.get();
        }

        @Override
        public List<IInventorySlot> getInputSlots() {
            return canInput() ? slotSupplier.get() : Collections.emptyList();
        }

        @Override
        public List<IInventorySlot> getOutputSlots() {
            return canOutput() ? slotSupplier.get() : Collections.emptyList();
        }
    }

    class HeatProxy extends HeatSlotInfo implements IProxiedSlotInfo {

        private final Supplier<List<IHeatCapacitor>> heatCapacitorSupplier;

        public HeatProxy(boolean canInput, boolean canOutput, Supplier<List<IHeatCapacitor>> heatCapacitorSupplier) {
            super(canInput, canOutput, Collections.emptyList());
            this.heatCapacitorSupplier = heatCapacitorSupplier;
        }

        @Override
        public List<IHeatCapacitor> getHeatCapacitors() {
            return heatCapacitorSupplier.get();
        }
    }

    @FunctionalInterface
    interface ProxySlotInfoCreator<T> {

        IProxiedSlotInfo create(boolean canInput, boolean canOutput, Supplier<List<T>> supplier);
    }
}
