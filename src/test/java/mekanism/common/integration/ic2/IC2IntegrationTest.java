package mekanism.common.integration.ic2;

import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItemManager;
import mekanism.common.Mekanism;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IC2IntegrationTest {

    private IElectricItemManager originalItemManager;
    private boolean originalIC2CLoaded;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @BeforeEach
    void rememberItemManager() {
        originalItemManager = ElectricItem.manager;
        originalIC2CLoaded = Mekanism.hooks.IC2CLoaded;
    }

    @AfterEach
    void restoreItemManager() {
        ElectricItem.manager = originalItemManager;
        Mekanism.hooks.IC2CLoaded = originalIC2CLoaded;
    }

    @Test
    void sinkTierPacketizesWithoutThrottlingTheTickTransferAmount() {
        TestSink sink = new TestSink(1, 100, 0);

        double accepted = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 100, false);

        assertEquals(100, accepted);
        assertEquals(100, sink.injectedAmount);
        assertEquals(32, sink.maxInjectedAmount);
        assertEquals(32, sink.injectedVoltage);
        assertEquals(4, sink.injectCalls);
    }

    @Test
    void transferUsesEachTargetsOwnVoltage() {
        TestSink tierOne = new TestSink(1, 100, 0);
        TestSink tierTwo = new TestSink(2, 100, 0);

        IC2Integration.transferToSink(tierOne, EnumFacing.NORTH, 100, false);
        IC2Integration.transferToSink(tierTwo, EnumFacing.NORTH, 100, false);

        assertEquals(32, tierOne.injectedVoltage);
        assertEquals(128, tierTwo.injectedVoltage);
    }

    @Test
    void classicStyleSinkReceivesPacketsWithinItsInputLimit() {
        Mekanism.hooks.IC2CLoaded = true;
        ClassicStyleSink sink = new ClassicStyleSink(1, 100);

        double accepted = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 100, false);

        assertEquals(100, accepted);
        assertEquals(100, sink.stored);
        assertEquals(32, sink.maxInjectedAmount);
        assertEquals(32, sink.injectedVoltage);
        assertEquals(4, sink.injectCalls);
    }

    @Test
    void classicStyleSinkLeavesFractionalEuInMekanism() {
        Mekanism.hooks.IC2CLoaded = true;
        ClassicStyleSink sink = new ClassicStyleSink(1, 100);

        double simulated = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 10.75, true);
        double accepted = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 10.75, false);

        assertEquals(10, simulated);
        assertEquals(10, accepted);
        assertEquals(10, sink.stored);
    }

    @Test
    void simulationUsesDemandWithoutInjecting() {
        TestSink sink = new TestSink(1, 40, 0);

        double accepted = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 100, true);

        assertEquals(40, accepted);
        assertEquals(0, sink.injectCalls);
    }

    @Test
    void rejectedEnergyIsNotReportedAsAccepted() {
        TestSink sink = new TestSink(2, 100, 25);

        double accepted = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 100, false);

        assertEquals(75, accepted);
    }

    @Test
    void sourceTierCanCarryTheFixedEuOutputAmount() {
        double euOutput = 100;

        int tier = IC2Integration.getTierFromPower(euOutput);

        assertTrue(IC2Integration.getPowerFromTier(tier) >= euOutput);
        assertTrue(tier == 0 || IC2Integration.getPowerFromTier(tier - 1) < euOutput);
    }

    @Test
    void itemChargingUsesTheTargetItemsTier() {
        TestItemManager manager = new TestItemManager(2);
        ElectricItem.manager = manager;
        ItemStack stack = new ItemStack(new Item());

        double charged = IC2Integration.chargeItem(stack, 100, true, false);

        assertEquals(100, charged);
        assertEquals(2, manager.lastTier);
        assertEquals(1, manager.chargeCalls);
    }

    @Test
    void itemDischargeUsesTheTargetsTier() {
        TestItemManager manager = new TestItemManager(1);
        ElectricItem.manager = manager;
        ItemStack stack = new ItemStack(new Item());

        double discharged = IC2Integration.dischargeItem(stack, 100, 3, true, false);

        assertEquals(100, discharged);
        assertEquals(3, manager.lastTier);
        assertEquals(1, manager.dischargeCalls);
    }

    private static final class TestSink implements IEnergySink {

        private final int tier;
        private final double demand;
        private final double rejected;
        private double injectedAmount;
        private double maxInjectedAmount;
        private double injectedVoltage;
        private int injectCalls;

        private TestSink(int tier, double demand, double rejected) {
            this.tier = tier;
            this.demand = demand;
            this.rejected = rejected;
        }

        @Override
        public double getDemandedEnergy() {
            return demand;
        }

        @Override
        public int getSinkTier() {
            return tier;
        }

        @Override
        public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
            injectedAmount += amount;
            maxInjectedAmount = Math.max(maxInjectedAmount, amount);
            injectedVoltage = voltage;
            injectCalls++;
            return rejected;
        }

        @Override
        public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
            return true;
        }
    }

    /** Mirrors IC2 Classic's behavior of silently dropping an oversized direct injection. */
    private static final class ClassicStyleSink implements IEnergySink {

        private final int tier;
        private final double capacity;
        private double stored;
        private double maxInjectedAmount;
        private double injectedVoltage;
        private int injectCalls;

        private ClassicStyleSink(int tier, double capacity) {
            this.tier = tier;
            this.capacity = capacity;
        }

        @Override
        public double getDemandedEnergy() {
            return capacity - stored;
        }

        @Override
        public int getSinkTier() {
            return tier;
        }

        @Override
        public double injectEnergy(EnumFacing directionFrom, double amount, double voltage) {
            injectCalls++;
            maxInjectedAmount = Math.max(maxInjectedAmount, amount);
            injectedVoltage = voltage;
            if (amount > IC2Integration.getPowerFromTier(tier)) {
                return 0;
            }
            double accepted = Math.min(amount, capacity - stored);
            stored += accepted;
            return amount - accepted;
        }

        @Override
        public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
            return true;
        }
    }

    private static final class TestItemManager implements IElectricItemManager {

        private final int itemTier;
        private int lastTier;
        private int chargeCalls;
        private int dischargeCalls;

        private TestItemManager(int itemTier) {
            this.itemTier = itemTier;
        }

        @Override
        public double charge(ItemStack itemStack, double amount, int tier, boolean ignoreTransferLimit, boolean simulate) {
            lastTier = tier;
            chargeCalls++;
            return amount;
        }

        @Override
        public double discharge(ItemStack itemStack, double amount, int tier, boolean ignoreTransferLimit, boolean external,
                                boolean simulate) {
            lastTier = tier;
            dischargeCalls++;
            return amount;
        }

        @Override
        public double getCharge(ItemStack itemStack) {
            return 0;
        }

        @Override
        public double getMaxCharge(ItemStack itemStack) {
            return 1_000;
        }

        @Override
        public boolean canUse(ItemStack itemStack, double amount) {
            return false;
        }

        @Override
        public boolean use(ItemStack itemStack, double amount, EntityLivingBase entity) {
            return false;
        }

        @Override
        public void chargeFromArmor(ItemStack itemStack, EntityLivingBase entity) {
        }

        @Override
        public String getToolTip(ItemStack itemStack) {
            return null;
        }

        @Override
        public int getTier(ItemStack itemStack) {
            return itemTier;
        }
    }
}
