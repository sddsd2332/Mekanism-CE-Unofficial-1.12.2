package mekanism.common.tile.component;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.TestBootstrap;
import mekanism.common.Upgrade;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.tile.factory.TileEntityBasicFactory;
import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.upgrade.ExternalUpgradeSupportRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeRecalculationBatchTest {

    private static final ResourceLocation CHANGE_UPGRADE_ID = new ResourceLocation("mekanism", "test_batch_change");
    private static final ResourceLocation EXTERNAL_SUPPORT_ID = new ResourceLocation("mekanism", "test_batch_external_support");
    private static final AtomicInteger CHANGE_CALLBACKS = new AtomicInteger();
    private static final AtomicInteger PREVIOUS_AMOUNT = new AtomicInteger();
    private static final AtomicInteger CURRENT_AMOUNT = new AtomicInteger();
    private static Upgrade changeUpgrade;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        changeUpgrade = Upgrade.byName(CHANGE_UPGRADE_ID);
        if (changeUpgrade == null) {
            changeUpgrade = Upgrade.builder(CHANGE_UPGRADE_ID)
                  .maxInstalled(8)
                  .onChanged((upgrade, tile, previousAmount, amount) -> {
                      CHANGE_CALLBACKS.incrementAndGet();
                      PREVIOUS_AMOUNT.set(previousAmount);
                      CURRENT_AMOUNT.set(amount);
                  })
                  .register();
        }
    }

    @BeforeEach
    void resetChangeTracking() {
        CHANGE_CALLBACKS.set(0);
        PREVIOUS_AMOUNT.set(-1);
        CURRENT_AMOUNT.set(-1);
        ExternalUpgradeSupportRegistry.unregister(EXTERNAL_SUPPORT_ID);
    }

    @Test
    void batchPreservesOrderFiltersNullsAndCallsCompletionOnce() {
        TrackingTile tile = new TrackingTile();

        tile.recalculateAllUpgradables(Arrays.asList(
              Upgrade.GAS, Upgrade.SPEED, Upgrade.GAS, null, Upgrade.ENERGY));

        assertEquals(Arrays.asList(Upgrade.GAS, Upgrade.SPEED, Upgrade.ENERGY), tile.callbacks);
        assertEquals(Arrays.asList(true, true, true), tile.batchStates);
        assertEquals(1, tile.completedBatches);
        assertEquals(Arrays.asList(Upgrade.GAS, Upgrade.SPEED, Upgrade.ENERGY), tile.completedUpgrades);
        assertFalse(tile.batchStateAtCompletion);
        assertFalse(tile.isBatchActive());
    }

    @Test
    void failedCallbackRestoresBatchStateAndSkipsCompletion() {
        TrackingTile tile = new TrackingTile();
        tile.failOn = Upgrade.SPEED;

        assertThrows(IllegalStateException.class,
              () -> tile.recalculateAllUpgradables(Arrays.asList(Upgrade.GAS, Upgrade.SPEED, Upgrade.ENERGY)));

        assertEquals(Arrays.asList(Upgrade.GAS, Upgrade.SPEED), tile.callbacks);
        assertEquals(0, tile.completedBatches);
        assertFalse(tile.isBatchActive());

        tile.clearObservations();
        tile.failOn = null;
        tile.recalculateAllUpgradables(Arrays.asList(Upgrade.ENERGY));
        assertEquals(Arrays.asList(Upgrade.ENERGY), tile.callbacks);
        assertEquals(1, tile.completedBatches);
    }

    @Test
    void singleUpgradeChangeRemainsImmediateAndInvokesChangeHandler() {
        TrackingTile tile = new TrackingTile();
        TileComponentUpgrade component = new TileComponentUpgrade(tile);
        component.setSupported(changeUpgrade);

        assertEquals(2, component.setUpgrades(changeUpgrade, 2));

        assertEquals(Arrays.asList(changeUpgrade), tile.callbacks);
        assertEquals(Arrays.asList(false), tile.batchStates);
        assertEquals(0, tile.completedBatches);
        assertEquals(1, CHANGE_CALLBACKS.get());
        assertEquals(0, PREVIOUS_AMOUNT.get());
        assertEquals(2, CURRENT_AMOUNT.get());
    }

    @Test
    void nbtAndNetworkReadsUseBatchRecalculation() {
        TrackingTile tile = new TrackingTile();
        TileComponentUpgrade component = new TileComponentUpgrade(tile);
        NBTTagCompound nbt = new NBTTagCompound();
        component.write(nbt);

        component.read(nbt);
        assertStandardComponentBatch(tile);

        tile.clearObservations();
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(0);
            buffer.writeInt(17);
            component.read(buffer);
        } finally {
            buffer.release();
        }
        assertEquals(17, component.upgradeTicks);
        assertStandardComponentBatch(tile);
    }

    @Test
    void nbtReadCanDelayRecalculationUntilStateRestorationFinishes() {
        TrackingTile tile = new TrackingTile();
        TileComponentUpgrade component = new TileComponentUpgrade(tile);

        component.read(new NBTTagCompound(), false);
        assertTrue(tile.callbacks.isEmpty());
        assertEquals(0, tile.completedBatches);

        tile.recalculateAllUpgradables(component.getSupportedTypes());
        assertStandardComponentBatch(tile);
    }

    @Test
    void externalUpgradeSupportIsIncludedInReadBatchSnapshot() {
        TrackingTile tile = new TrackingTile();
        TileComponentUpgrade component = new TileComponentUpgrade(tile);
        try {
            ExternalUpgradeSupportRegistry.register(EXTERNAL_SUPPORT_ID, candidate -> candidate == tile,
                  Upgrade.STONE_GENERATOR);

            component.read(new NBTTagCompound());

            assertEquals(Arrays.asList(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.STONE_GENERATOR), tile.callbacks);
            assertEquals(Arrays.asList(Upgrade.SPEED, Upgrade.ENERGY, Upgrade.STONE_GENERATOR), tile.completedUpgrades);
            assertEquals(1, tile.completedBatches);
        } finally {
            ExternalUpgradeSupportRegistry.unregister(EXTERNAL_SUPPORT_ID);
        }
    }

    @Test
    void electricPumpBatchMatchesImmediateResultsAndClampsEnergyOnce() {
        int speedUpgrades = Math.min(2, Upgrade.SPEED.getMaxInstalled());
        int energyUpgrades = Math.min(3, Upgrade.ENERGY.getMaxInstalled());
        assertTrue(speedUpgrades > 0);
        assertTrue(energyUpgrades > 0);

        CountingElectricPump immediate = new CountingElectricPump();
        double initialEnergy = immediate.BASE_MAX_ENERGY / 2;
        immediate.setEnergy(initialEnergy);
        immediate.resetSetEnergyCalls();
        immediate.getComponent().setUpgrades(Upgrade.SPEED, speedUpgrades);
        immediate.getComponent().setUpgrades(Upgrade.ENERGY, energyUpgrades);
        assertEquals(2, immediate.setEnergyCalls);

        CountingElectricPump batched = new CountingElectricPump();
        batched.setEnergy(initialEnergy);
        Map<Upgrade, Integer> installed = new LinkedHashMap<>();
        installed.put(Upgrade.SPEED, speedUpgrades);
        installed.put(Upgrade.ENERGY, energyUpgrades);
        NBTTagCompound componentData = new NBTTagCompound();
        Upgrade.saveComponentMap(installed, componentData);
        batched.getComponent().read(componentData, false);
        batched.resetSetEnergyCalls();

        batched.recalculateAllUpgradables(batched.getComponent().getSupportedTypes());

        assertEquals(1, batched.setEnergyCalls);
        assertEquals(immediate.ticksRequired, batched.ticksRequired);
        assertEquals(immediate.energyPerTick, batched.energyPerTick);
        assertEquals(immediate.getMaxEnergy(), batched.getMaxEnergy());
        assertEquals(immediate.getEnergy(), batched.getEnergy());
    }

    @Test
    void dissolutionBatchPreservesEnergyAndGasRecalculationPrecedence() {
        int speedUpgrades = Math.min(2, Upgrade.SPEED.getMaxInstalled());
        int energyUpgrades = Math.min(3, Upgrade.ENERGY.getMaxInstalled());
        int gasUpgrades = Math.min(2, Upgrade.GAS.getMaxInstalled());
        assertTrue(speedUpgrades > 0);
        assertTrue(energyUpgrades > 0);
        assertTrue(gasUpgrades > 0);

        TileEntityChemicalDissolutionChamber immediate = new TileEntityChemicalDissolutionChamber();
        immediate.getComponent().setUpgrades(Upgrade.SPEED, speedUpgrades);
        immediate.getComponent().setUpgrades(Upgrade.ENERGY, energyUpgrades);
        immediate.getComponent().setUpgrades(Upgrade.GAS, gasUpgrades);

        TileEntityChemicalDissolutionChamber batched = new TileEntityChemicalDissolutionChamber();
        Map<Upgrade, Integer> installed = new LinkedHashMap<>();
        installed.put(Upgrade.SPEED, speedUpgrades);
        installed.put(Upgrade.ENERGY, energyUpgrades);
        installed.put(Upgrade.GAS, gasUpgrades);
        NBTTagCompound componentData = new NBTTagCompound();
        Upgrade.saveComponentMap(installed, componentData);
        batched.getComponent().read(componentData, false);

        batched.recalculateAllUpgradables(batched.getComponent().getSupportedTypes());

        assertEquals(immediate.ticksRequired, batched.ticksRequired);
        assertEquals(immediate.energyPerTick, batched.energyPerTick);
        assertEquals(immediate.getMaxEnergy(), batched.getMaxEnergy());
        assertEquals(immediate.injectUsage, batched.injectUsage);
        assertEquals(immediate.getRecipeGasUsagePerOperation(), batched.getRecipeGasUsagePerOperation());
    }

    @Test
    void factoryRecipeTypeChangeUsesOneCompleteBatch() {
        TrackingBasicFactory factory = new TrackingBasicFactory();
        factory.startTracking();

        factory.setRecipeType(RecipeType.CRUSHING);

        assertEquals(new ArrayList<>(factory.getComponent().getSupportedTypes()), factory.callbacks);
        assertEquals(factory.callbacks.size(), factory.batchedCallbacks);
        assertEquals(1, factory.completedBatches);
    }

    private static void assertStandardComponentBatch(TrackingTile tile) {
        assertEquals(Arrays.asList(Upgrade.SPEED, Upgrade.ENERGY), tile.callbacks);
        assertEquals(Arrays.asList(true, true), tile.batchStates);
        assertEquals(Arrays.asList(Upgrade.SPEED, Upgrade.ENERGY), tile.completedUpgrades);
        assertEquals(1, tile.completedBatches);
    }

    private static class TrackingTile extends TileEntityContainerBlock {

        private final List<Upgrade> callbacks = new ArrayList<>();
        private final List<Boolean> batchStates = new ArrayList<>();
        private final List<Upgrade> completedUpgrades = new ArrayList<>();
        private int completedBatches;
        private boolean batchStateAtCompletion;
        private Upgrade failOn;

        private TrackingTile() {
            super("upgrade_batch_test");
        }

        @Override
        public void recalculateUpgradables(Upgrade upgrade) {
            super.recalculateUpgradables(upgrade);
            callbacks.add(upgrade);
            batchStates.add(isRecalculatingAllUpgradables());
            if (upgrade == failOn) {
                throw new IllegalStateException("Expected recalculation failure");
            }
        }

        @Override
        protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
            super.onAllUpgradablesRecalculated(upgrades);
            completedBatches++;
            completedUpgrades.clear();
            completedUpgrades.addAll(upgrades);
            batchStateAtCompletion = isRecalculatingAllUpgradables();
        }

        private boolean isBatchActive() {
            return isRecalculatingAllUpgradables();
        }

        private void clearObservations() {
            callbacks.clear();
            batchStates.clear();
            completedUpgrades.clear();
            completedBatches = 0;
            batchStateAtCompletion = false;
        }
    }

    private static class CountingElectricPump extends TileEntityElectricPump {

        private int setEnergyCalls;

        @Override
        public void setEnergy(double energy) {
            setEnergyCalls++;
            super.setEnergy(energy);
        }

        private void resetSetEnergyCalls() {
            setEnergyCalls = 0;
        }
    }

    private static class TrackingBasicFactory extends TileEntityBasicFactory {

        private final List<Upgrade> callbacks = new ArrayList<>();
        private int batchedCallbacks;
        private int completedBatches;
        private boolean tracking;

        @Override
        public void recalculateUpgradables(Upgrade upgrade) {
            super.recalculateUpgradables(upgrade);
            if (tracking) {
                callbacks.add(upgrade);
                if (isRecalculatingAllUpgradables()) {
                    batchedCallbacks++;
                }
            }
        }

        @Override
        protected void onAllUpgradablesRecalculated(Set<Upgrade> upgrades) {
            super.onAllUpgradablesRecalculated(upgrades);
            if (tracking) {
                completedBatches++;
            }
        }

        private void startTracking() {
            tracking = true;
            callbacks.clear();
            batchedCallbacks = 0;
            completedBatches = 0;
        }
    }
}
