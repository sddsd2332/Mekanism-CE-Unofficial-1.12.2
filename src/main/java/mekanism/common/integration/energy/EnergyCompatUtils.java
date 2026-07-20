package mekanism.common.integration.energy;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.ElectricItem;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.util.MekanismUtils;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public final class EnergyCompatUtils {

    private EnergyCompatUtils() {
    }

    public static boolean hasStrictEnergyHandler(ItemStack stack) {
        return getStrictEnergyHandler(stack) != null;
    }

    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
            IStrictEnergyHandler energyHandler = stack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
            if (energyHandler != null && energyHandler.getEnergyContainerCount() > 0) {
                return energyHandler;
            }
        }
        if (MekanismUtils.useForge() && stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
            IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
            if (storage != null && storage.getMaxEnergyStored() > 0) {
                return new ForgeStrictEnergyHandler(storage);
            }
        }
        if (MekanismUtils.useTesla() && stack.hasCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null)) {
            ITeslaHolder holder = stack.getCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null);
            if (holder != null && holder.getCapacity() > 0) {
                ITeslaConsumer consumer = stack.hasCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null) ? stack.getCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null) : null;
                ITeslaProducer producer = stack.hasCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null) ? stack.getCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null) : null;
                if (consumer != null || producer != null) {
                    return new TeslaStrictEnergyHandler(holder, consumer, producer);
                }
            }
        }
        if (MekanismUtils.useRF() && stack.getItem() instanceof IEnergyContainerItem item && item.getMaxEnergyStored(stack) > 0) {
            return new RFStrictEnergyHandler(stack, item);
        }
        if (MekanismUtils.useIC2() && ElectricItem.manager.getMaxCharge(stack) > 0) {
            return new IC2StrictEnergyHandler(stack);
        }
        return null;
    }

    private static class ForgeStrictEnergyHandler implements IStrictEnergyHandler {

        private final IEnergyStorage storage;

        private ForgeStrictEnergyHandler(IEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public double getEnergy(int container) {
            return container == 0 ? ForgeEnergyIntegration.fromForge(storage.getEnergyStored()) : 0;
        }

        @Override
        public void setEnergy(int container, double energy) {
        }

        @Override
        public double getMaxEnergy(int container) {
            return container == 0 ? ForgeEnergyIntegration.fromForge(storage.getMaxEnergyStored()) : 0;
        }

        @Override
        public double getNeededEnergy(int container) {
            return container == 0 ? ForgeEnergyIntegration.fromForge(Math.max(0, storage.getMaxEnergyStored() - storage.getEnergyStored())) : 0;
        }

        @Override
        public double insertEnergy(int container, double amount, Action action) {
            if (container != 0 || !storage.canReceive() || amount <= 0) {
                return amount;
            }
            int toInsert = ForgeEnergyIntegration.toForge(amount);
            if (toInsert <= 0) {
                return amount;
            }
            int inserted = storage.receiveEnergy(toInsert, action.simulate());
            return Math.max(0, amount - ForgeEnergyIntegration.fromForge(inserted));
        }

        @Override
        public double extractEnergy(int container, double amount, Action action) {
            if (container != 0 || !storage.canExtract() || amount <= 0) {
                return 0;
            }
            int toExtract = ForgeEnergyIntegration.toForge(amount);
            if (toExtract <= 0) {
                return 0;
            }
            return ForgeEnergyIntegration.fromForge(storage.extractEnergy(toExtract, action.simulate()));
        }
    }

    private static class TeslaStrictEnergyHandler implements IStrictEnergyHandler {

        private final ITeslaHolder holder;
        @Nullable
        private final ITeslaConsumer consumer;
        @Nullable
        private final ITeslaProducer producer;

        private TeslaStrictEnergyHandler(ITeslaHolder holder, @Nullable ITeslaConsumer consumer, @Nullable ITeslaProducer producer) {
            this.holder = holder;
            this.consumer = consumer;
            this.producer = producer;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public double getEnergy(int container) {
            return container == 0 ? TeslaIntegration.fromTesla(holder.getStoredPower()) : 0;
        }

        @Override
        public void setEnergy(int container, double energy) {
        }

        @Override
        public double getMaxEnergy(int container) {
            return container == 0 ? TeslaIntegration.fromTesla(holder.getCapacity()) : 0;
        }

        @Override
        public double getNeededEnergy(int container) {
            return container == 0 ? TeslaIntegration.fromTesla(Math.max(0, holder.getCapacity() - holder.getStoredPower())) : 0;
        }

        @Override
        public double insertEnergy(int container, double amount, Action action) {
            if (container != 0 || consumer == null || amount <= 0) {
                return amount;
            }
            long toInsert = TeslaIntegration.toTesla(amount);
            if (toInsert <= 0) {
                return amount;
            }
            long inserted = consumer.givePower(toInsert, action.simulate());
            return Math.max(0, amount - TeslaIntegration.fromTesla(inserted));
        }

        @Override
        public double extractEnergy(int container, double amount, Action action) {
            if (container != 0 || producer == null || amount <= 0) {
                return 0;
            }
            long toExtract = TeslaIntegration.toTesla(amount);
            if (toExtract <= 0) {
                return 0;
            }
            return TeslaIntegration.fromTesla(producer.takePower(toExtract, action.simulate()));
        }
    }

    private static class RFStrictEnergyHandler implements IStrictEnergyHandler {

        private final ItemStack stack;
        private final IEnergyContainerItem item;

        private RFStrictEnergyHandler(ItemStack stack, IEnergyContainerItem item) {
            this.stack = stack;
            this.item = item;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public double getEnergy(int container) {
            return container == 0 ? RFIntegration.fromRF(item.getEnergyStored(stack)) : 0;
        }

        @Override
        public void setEnergy(int container, double energy) {
        }

        @Override
        public double getMaxEnergy(int container) {
            return container == 0 ? RFIntegration.fromRF(item.getMaxEnergyStored(stack)) : 0;
        }

        @Override
        public double getNeededEnergy(int container) {
            return container == 0 ? RFIntegration.fromRF(Math.max(0, item.getMaxEnergyStored(stack) - item.getEnergyStored(stack))) : 0;
        }

        @Override
        public double insertEnergy(int container, double amount, Action action) {
            if (container != 0 || amount <= 0) {
                return amount;
            }
            int toInsert = RFIntegration.toRF(amount);
            if (toInsert <= 0) {
                return amount;
            }
            int inserted = item.receiveEnergy(stack, toInsert, action.simulate());
            return Math.max(0, amount - RFIntegration.fromRF(inserted));
        }

        @Override
        public double extractEnergy(int container, double amount, Action action) {
            if (container != 0 || amount <= 0) {
                return 0;
            }
            int toExtract = RFIntegration.toRF(amount);
            if (toExtract <= 0) {
                return 0;
            }
            return RFIntegration.fromRF(item.extractEnergy(stack, toExtract, action.simulate()));
        }
    }

    private static class IC2StrictEnergyHandler implements IStrictEnergyHandler {

        private final ItemStack stack;

        private IC2StrictEnergyHandler(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public double getEnergy(int container) {
            return container == 0 ? IC2Integration.fromEU(ElectricItem.manager.getCharge(stack)) : 0;
        }

        @Override
        public void setEnergy(int container, double energy) {
        }

        @Override
        public double getMaxEnergy(int container) {
            return container == 0 ? IC2Integration.fromEU(ElectricItem.manager.getMaxCharge(stack)) : 0;
        }

        @Override
        public double getNeededEnergy(int container) {
            return container == 0 ? Math.max(0, getMaxEnergy(container) - getEnergy(container)) : 0;
        }

        @Override
        public double insertEnergy(int container, double amount, Action action) {
            if (container != 0 || amount <= 0) {
                return amount;
            }
            double toInsert = IC2Integration.toEU(amount);
            if (toInsert <= 0) {
                return amount;
            }
            double inserted = IC2Integration.chargeItem(stack, toInsert, true, action.simulate());
            return Math.max(0, amount - IC2Integration.fromEU(inserted));
        }

        @Override
        public double extractEnergy(int container, double amount, Action action) {
            if (container != 0 || amount <= 0) {
                return 0;
            }
            double toExtract = IC2Integration.toEU(amount);
            if (toExtract <= 0) {
                return 0;
            }
            return IC2Integration.fromEU(IC2Integration.dischargeItemToMekanism(stack, toExtract, true, action.simulate()));
        }
    }
}
