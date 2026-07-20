package mekanism.common.integration.ic2;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IC2ItemManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        Loader loader = Loader.instance();
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(loader) == null) {
            namedMods.set(loader, Collections.emptyMap());
        }
        Bootstrap.register();
    }

    @Test
    void experimentalChargeSlotInfinityProbeAcceptsMekanismItems() {
        TestEnergyItem item = new TestEnergyItem(1_000, 0, 100);
        ItemStack stack = new ItemStack(item);

        double accepted = IC2ItemManager.INSTANCE.charge(stack, Double.POSITIVE_INFINITY, 0, true, true);

        assertTrue(Double.isFinite(accepted));
        assertTrue(accepted > 0);
        assertEquals(0, item.energyHandler.stored);
    }

    @Test
    void classicChargeSlotFiniteProbeAcceptsMekanismItems() {
        TestEnergyItem item = new TestEnergyItem(1_000, 0, 100);
        ItemStack stack = new ItemStack(item);

        double accepted = IC2ItemManager.INSTANCE.charge(stack, 1, 0, false, true);

        assertTrue(accepted > 0);
        assertEquals(0, item.energyHandler.stored);
    }

    @Test
    void lowerTierDischargeSlotAcceptsMekanismItems() {
        TestEnergyItem item = new TestEnergyItem(1_000, 500, 100);
        ItemStack stack = new ItemStack(item);
        assertTrue(IC2ItemManager.INSTANCE.getTier(stack) > 0);

        double accepted = IC2ItemManager.INSTANCE.discharge(stack, Double.POSITIVE_INFINITY, 0, true, true, true);

        assertTrue(Double.isFinite(accepted));
        assertTrue(accepted > 0);
        assertEquals(500, item.energyHandler.stored);
    }

    private static final class TestEnergyItem extends Item {

        private final double capacity;
        private final double stored;
        private final double transferLimit;
        private TestEnergyHandler energyHandler;

        private TestEnergyItem(double capacity, double stored, double transferLimit) {
            this.capacity = capacity;
            this.stored = stored;
            this.transferLimit = transferLimit;
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
            energyHandler = new TestEnergyHandler(capacity, stored, transferLimit);
            return new ICapabilityProvider() {
                @Override
                public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
                    return capability == Capabilities.STRICT_ENERGY_CAPABILITY;
                }

                @SuppressWarnings("unchecked")
                @Override
                public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
                    return capability == Capabilities.STRICT_ENERGY_CAPABILITY ? (T) energyHandler : null;
                }
            };
        }
    }

    private static final class TestEnergyHandler implements IStrictEnergyHandler {

        private final double capacity;
        private final double transferLimit;
        private double stored;

        private TestEnergyHandler(double capacity, double stored, double transferLimit) {
            this.capacity = capacity;
            this.stored = stored;
            this.transferLimit = transferLimit;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public double getEnergy(int container) {
            return stored;
        }

        @Override
        public void setEnergy(int container, double energy) {
            stored = Math.max(0, Math.min(capacity, energy));
        }

        @Override
        public double getMaxEnergy(int container) {
            return capacity;
        }

        @Override
        public double getNeededEnergy(int container) {
            return capacity - stored;
        }

        @Override
        public double insertEnergy(int container, double amount, Action action) {
            double accepted = Math.min(amount, Math.min(transferLimit, capacity - stored));
            if (action.execute()) {
                stored += accepted;
            }
            return amount - accepted;
        }

        @Override
        public double extractEnergy(int container, double amount, Action action) {
            double extracted = Math.min(amount, Math.min(transferLimit, stored));
            if (action.execute()) {
                stored -= extracted;
            }
            return extracted;
        }
    }
}
