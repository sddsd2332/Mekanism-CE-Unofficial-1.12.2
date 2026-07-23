package mekanism.common.capabilities.heat;

import mekanism.api.IHeatTransfer;
import mekanism.api.NBTConstants;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatCapacitorWrapper;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.multiblock.MultiblockManager;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.util.HeatUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatSystemTest {

    private static final double EPSILON = 1.0E-8;

    @Test
    void heatEpsilonMatchesModernApi() {
        assertEquals(1.0E-6, HeatAPI.EPSILON);
    }

    @Test
    void capacitorSupportsAbsoluteZeroAndSaturatesExtremeTransfers() {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(10, () -> 250, null);

        capacitor.setHeat(0);
        assertEquals(0, capacitor.getTemperature());

        capacitor.handleHeat(Double.MAX_VALUE);
        assertEquals(HeatAPI.MAX_HEAT, capacitor.getHeat());
        assertTrue(HeatAPI.isFinite(capacitor.getTemperature()));

        capacitor.handleHeat(-Double.MAX_VALUE);
        assertEquals(0, capacitor.getHeat());

        capacitor.setHeat(Double.NaN);
        assertEquals(2_500, capacitor.getHeat());
        capacitor.handleHeat(Double.POSITIVE_INFINITY);
        assertEquals(2_500, capacitor.getHeat());
    }

    @Test
    void malformedAmbientAndFallbackValuesStayFiniteAndBounded() {
        assertEquals(HeatAPI.AMBIENT_TEMP, HeatAPI.getAmbientTemp(Double.NaN));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeTemperature(Double.MAX_VALUE));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeHeat(Double.NaN, Double.MAX_VALUE));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeInverseConduction(Double.MAX_VALUE));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeInverseInsulation(Double.MAX_VALUE));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeInverseConduction(Double.POSITIVE_INFINITY));
        assertEquals(HeatAPI.MAX_HEAT, HeatAPI.sanitizeInverseInsulation(Double.POSITIVE_INFINITY));
        assertTrue(HeatAPI.isFinite(HeatAPI.getFinalTemperature(Double.MAX_VALUE, 1, 0, 1)));
    }

    @Test
    void invalidCapacitorParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(Double.NaN, null, null));
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(Double.POSITIVE_INFINITY, null, null));
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(1, Double.NaN, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(1, 1, -1, null, null));
    }

    @Test
    void changingCapacityKeepsAmbientEnergyAndNotifiesAtomically() {
        AtomicInteger changes = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(10, () -> 250, changes::incrementAndGet);
        assertEquals(250, capacitor.getTemperature());

        capacitor.updateHeatAndCapacity(20);
        assertEquals(250, capacitor.getTemperature());
        assertEquals(5_000, capacitor.getHeat());
        assertEquals(1, changes.get());

        capacitor.updateHeatAndCapacity(5);
        assertEquals(250, capacitor.getTemperature());
        assertEquals(1_250, capacitor.getHeat());
        assertEquals(2, changes.get());
    }

    @Test
    void directCapacityChangeNotifiesWithoutChangingStoredHeat() {
        AtomicInteger changes = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(10, () -> 250, changes::incrementAndGet);
        double heat = capacitor.getHeat();

        capacitor.setHeatCapacity(20, false);

        assertEquals(heat, capacitor.getHeat());
        assertEquals(125, capacitor.getTemperature());
        assertEquals(1, changes.get());
    }

    @Test
    void finalTemperatureCalculationIsStableForDifferentAndExtremeCapacities() {
        assertEquals((1_000D * 100 + 300) / 101D, HeatAPI.getFinalTemperature(1_000, 100, 300, 1), EPSILON);

        double extreme = HeatAPI.getFinalTemperature(1.0E300, Double.MAX_VALUE / 2, 300, 1);
        assertTrue(HeatAPI.isFinite(extreme));
        assertTrue(extreme >= 300 && extreme <= 1.0E300);
    }

    @Test
    void aggregateCapacitySaturatesAndAnEmptyHandlerHasNoCapacity() {
        IHeatHandler empty = handler();
        IHeatHandler enormous = handler(HeatAPI.MAX_HEAT, HeatAPI.MAX_HEAT);
        IHeatHandler malformed = handler(new double[]{Double.NaN, Double.POSITIVE_INFINITY},
              new double[]{Double.NaN, Double.NEGATIVE_INFINITY}, new double[]{1, 1});

        assertEquals(0, empty.getTotalHeatCapacity());
        assertEquals(HeatAPI.MAX_HEAT, enormous.getTotalHeatCapacity());
        assertTrue(HeatAPI.isFinite(malformed.getTotalTemperature()));
        assertTrue(HeatAPI.isFinite(malformed.getTotalInverseConduction()));
        assertTrue(malformed.getTotalTemperature() >= HeatAPI.AMBIENT_TEMP);
        assertTrue(malformed.getTotalTemperature() <= HeatAPI.MAX_HEAT);
        assertEquals(HeatAPI.DEFAULT_INVERSE_CONDUCTION, malformed.getTotalInverseConduction());
    }

    @Test
    void saturatedAggregateCapacityStillDistributesExactlyOneTransfer() {
        double[] received = new double[2];
        IHeatHandler enormous = new IHeatHandler() {
            @Override
            public int getHeatCapacitorCount() {
                return 2;
            }

            @Override
            public double getTemperature(int capacitor) {
                return capacitor == 0 ? 400 : 600;
            }

            @Override
            public double getInverseConduction(int capacitor) {
                return capacitor == 0 ? 1 : 3;
            }

            @Override
            public double getHeatCapacity(int capacitor) {
                return HeatAPI.MAX_HEAT;
            }

            @Override
            public void handleHeat(int capacitor, double transfer) {
                received[capacitor] += transfer;
            }
        };

        enormous.handleHeat(100);

        assertEquals(100, received[0] + received[1], EPSILON);
        assertEquals(50, received[0], EPSILON);
        assertEquals(50, received[1], EPSILON);
        assertEquals(500, enormous.getTotalTemperature(), EPSILON);
        assertEquals(2, enormous.getTotalInverseConduction(), EPSILON);
    }

    @Test
    void mekanismHandlerDistributesAcrossSaturatedCapacities() {
        BasicHeatCapacitor first = BasicHeatCapacitor.create(HeatAPI.MAX_HEAT, null, null);
        BasicHeatCapacitor second = BasicHeatCapacitor.create(HeatAPI.MAX_HEAT, null, null);
        first.setHeat(0);
        second.setHeat(0);
        TestMultiCapacitorHandler handler = new TestMultiCapacitorHandler(first, second);
        double firstHeat = first.getHeat();
        double secondHeat = second.getHeat();

        handler.handleHeat(100, null);

        assertEquals(50, first.getHeat() - firstHeat, EPSILON);
        assertEquals(50, second.getHeat() - secondHeat, EPSILON);
    }

    @Test
    void mekanismHandlerRejectsNonFiniteAndNegligibleTransfersBeforeCustomCapacitors() {
        AtomicReference<Double> received = new AtomicReference<>();
        IHeatCapacitor recordingCapacitor = new RecordingHeatCapacitor(received);
        TestMultiCapacitorHandler handler = new TestMultiCapacitorHandler(recordingCapacitor);

        handler.handleHeat(Double.NaN, null);
        handler.handleHeat(Double.POSITIVE_INFINITY, null);
        handler.handleHeat(HeatAPI.EPSILON, null);
        handler.handleHeat(0, 0.5 * HeatAPI.EPSILON, null);

        assertEquals(null, received.get());

        handler.handleHeat(2 * HeatAPI.EPSILON, null);
        assertEquals(2 * HeatAPI.EPSILON, received.get(), EPSILON);
    }

    @Test
    void partiallySaturatedMultiCapacitorSinkRollsBackOnlyUnacceptedHeat() {
        BasicHeatCapacitor sourceCapacitor = capacitorAt(500, 100);
        BasicHeatCapacitor saturated = BasicHeatCapacitor.create(HeatAPI.MAX_HEAT, null, null);
        BasicHeatCapacitor accepting = BasicHeatCapacitor.create(HeatAPI.MAX_HEAT, null, null);
        saturated.setHeat(HeatAPI.MAX_HEAT);
        accepting.setHeat(0);
        TestTileHeatHandler source = new TestTileHeatHandler(sourceCapacitor);
        source.adjacent = new TestMultiCapacitorHandler(saturated, accepting);
        double sourceBefore = sourceCapacitor.getHeat();
        double acceptingBefore = accepting.getHeat();

        source.simulateAdjacent();

        double removed = sourceBefore - sourceCapacitor.getHeat();
        double accepted = accepting.getHeat() - acceptingBefore;
        assertTrue(accepted > 0);
        assertEquals(accepted, removed, Math.max(EPSILON, accepted * 1.0E-12));
        assertEquals(HeatAPI.MAX_HEAT, saturated.getHeat());
        assertEquals(HeatAPI.MAX_HEAT, ITileHeatHandler.getHandlerHeat(source.adjacent, null));
    }

    @Test
    void environmentTransferOnlyUsesSidesThatExposeTheCapacitor() {
        BasicHeatCapacitor capacitor = capacitorAt(400, 10);
        TestTileHeatHandler handler = new TestTileHeatHandler(capacitor);

        double loss = handler.simulateEnvironment();
        double expectedTemperatureLoss = 100D / (HeatAPI.AIR_INVERSE_COEFFICIENT + 1);

        assertEquals(expectedTemperatureLoss, loss, EPSILON);
        assertEquals(400 - expectedTemperatureLoss, capacitor.getTemperature(), EPSILON);
    }

    @Test
    void environmentLossRemainsVisibleWhenAggregateStoredHeatIsSaturated() {
        double capacity = HeatAPI.MAX_HEAT * 0.75 / 400;
        BasicHeatCapacitor first = capacitorAt(400, capacity);
        BasicHeatCapacitor second = capacitorAt(400, capacity);
        TestMultiCapacitorHandler handler = new TestMultiCapacitorHandler(first, second);
        double expectedTemperatureLoss = 100D / (HeatAPI.AIR_INVERSE_COEFFICIENT + 1);

        double loss = handler.simulateEnvironment(EnumFacing.EAST);

        assertEquals(expectedTemperatureLoss, loss, expectedTemperatureLoss * 1.0E-10);
        assertEquals(400 - expectedTemperatureLoss, first.getTemperature(), EPSILON);
        assertEquals(400 - expectedTemperatureLoss, second.getTemperature(), EPSILON);
    }

    @Test
    void sharedHeatIdentityPreventsAProxyFromTransferringIntoItself() {
        BasicHeatCapacitor capacitor = capacitorAt(500, 10);
        TestTileHeatHandler source = new TestTileHeatHandler(capacitor);
        source.adjacent = new HeatCapacitorWrapper(capacitor);
        double heat = capacitor.getHeat();

        assertSame(capacitor.getHeatIdentity(), source.adjacent.getHeatIdentity());
        assertEquals(0, source.simulateAdjacent());
        assertEquals(heat, capacitor.getHeat());
    }

    @Test
    void equalHeatIdentitiesPreventSelfTransferAcrossDistinctAdapters() {
        EqualIdentityHeatCapacitor sourceCapacitor = new EqualIdentityHeatCapacitor(500, new String("shared-frequency"));
        EqualIdentityHeatCapacitor sinkCapacitor = new EqualIdentityHeatCapacitor(300, new String("shared-frequency"));
        TestTileHeatHandler source = new TestTileHeatHandler(sourceCapacitor);
        source.adjacent = sinkCapacitor;
        double sourceHeat = sourceCapacitor.getHeat();
        double sinkHeat = sinkCapacitor.getHeat();

        assertNotSame(sourceCapacitor.getHeatIdentity(), sinkCapacitor.getHeatIdentity());
        assertEquals(sourceCapacitor.getHeatIdentity(), sinkCapacitor.getHeatIdentity());
        assertEquals(0, source.simulateAdjacent());
        assertEquals(sourceHeat, sourceCapacitor.getHeat());
        assertEquals(sinkHeat, sinkCapacitor.getHeat());
    }

    @Test
    void thirteenSharedProxiesRemainFiniteAndConserveHeat() {
        BasicHeatCapacitor sharedFrequency = capacitorAt(1.0E12, 100);
        BasicHeatCapacitor fusionCasing = capacitorAt(300, 1);
        double initialHeat = sharedFrequency.getHeat() + fusionCasing.getHeat();

        for (int i = 0; i < 13; i++) {
            TestTileHeatHandler proxy = new TestTileHeatHandler(sharedFrequency);
            proxy.adjacent = fusionCasing;
            proxy.simulateAdjacent();
            assertTrue(HeatAPI.isFinite(sharedFrequency.getTemperature()));
            assertTrue(HeatAPI.isFinite(fusionCasing.getTemperature()));
        }

        assertEquals(initialHeat, sharedFrequency.getHeat() + fusionCasing.getHeat(), initialHeat * 1.0E-12);
        assertTrue(sharedFrequency.getTemperature() >= fusionCasing.getTemperature());
        assertTrue(fusionCasing.getTemperature() > 300);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyAndModernAdaptersPreserveAbsoluteTemperatureAndHeatFlow() {
        TestLegacyHeatTransfer legacy = new TestLegacyHeatTransfer(100, 10);
        LegacyHeatHandlerAdapter modern = new LegacyHeatHandlerAdapter(legacy);
        assertEquals(400, modern.getTotalTemperature());
        modern.handleHeat(100);
        assertEquals(410, modern.getTotalTemperature());

        BasicHeatCapacitor capacitor = capacitorAt(400, 10);
        LegacyHeatTransferAdapter oldView = new LegacyHeatTransferAdapter(capacitor);
        assertEquals(100, oldView.getTemp());
        oldView.transferHeatTo(100);
        assertEquals(110, oldView.getTemp());
        assertSame(capacitor.getHeatIdentity(), oldView.getHeatIdentity());

        LegacyHeatHandlerAdapter roundTrip = new LegacyHeatHandlerAdapter(oldView);
        assertSame(capacitor.getHeatIdentity(), roundTrip.getHeatIdentity());
        assertEquals(capacitor.getTemperature(), roundTrip.getTotalTemperature());
    }

    @Test
    @SuppressWarnings("removal")
    void legacyTileAdapterKeepsAdjacentSimulationContext() {
        BasicHeatCapacitor sourceCapacitor = capacitorAt(500, 10);
        BasicHeatCapacitor sink = capacitorAt(300, 10);
        TestTileHeatHandler tile = new TestTileHeatHandler(sourceCapacitor);
        tile.adjacent = sink;
        LegacyHeatTransferAdapter oldView = new LegacyHeatTransferAdapter(tile, tile, null);

        IHeatTransfer adjacent = oldView.getAdjacent(EnumFacing.EAST);
        assertSame(sink.getHeatIdentity(), adjacent.getHeatIdentity());
        assertTrue(oldView.simulateHeat()[0] > 0);
        assertTrue(sourceCapacitor.getTemperature() < 500);
        assertTrue(sink.getTemperature() > 300);
    }

    @Test
    @SuppressWarnings("removal")
    void legacySideProxyExposesTheCapacitorsInsulation() {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(10, 1, 25, null, null);
        TestTileHeatHandler tile = new TestTileHeatHandler(capacitor);
        ProxyHeatHandler proxy = new ProxyHeatHandler(tile, EnumFacing.EAST, null);
        LegacyHeatTransferAdapter oldView = new LegacyHeatTransferAdapter(proxy, tile, EnumFacing.EAST);

        assertEquals(25, oldView.getInsulationCoefficient(EnumFacing.EAST), EPSILON);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyRawSideAdapterScopesReadsWritesAndSimulation() {
        BasicHeatCapacitor source = capacitorAt(500, 10);
        BasicHeatCapacitor sink = capacitorAt(300, 10);
        TestTileHeatHandler tile = new TestTileHeatHandler(source);
        tile.adjacent = sink;
        LegacyHeatTransferAdapter oldView = new LegacyHeatTransferAdapter(tile, tile, EnumFacing.EAST);

        assertEquals(500, oldView.getTemp() + HeatAPI.AMBIENT_TEMP, EPSILON);
        double beforeWest = source.getHeat();
        oldView.transferHeatTo(10);
        assertEquals(beforeWest + 10, source.getHeat(), EPSILON);
        double[] transfer = oldView.simulateHeat();
        assertTrue(transfer[0] > 0);
        assertTrue(sink.getTemperature() > 300);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyAdapterMakesDeferredHeatImmediatelyVisible() {
        DeferredLegacyHeatTransfer legacy = new DeferredLegacyHeatTransfer(100, 10, EnumFacing.EAST);
        LegacyHeatHandlerAdapter modern = new LegacyHeatHandlerAdapter(legacy);

        modern.handleHeat(100);

        assertEquals(410, modern.getTotalTemperature(), EPSILON);
        assertEquals(0, legacy.pendingHeat, EPSILON);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyHeatUtilsUsesEquilibriumAndIndependentEnvironmentalTransfer() {
        DeferredLegacyHeatTransfer source = new DeferredLegacyHeatTransfer(200, 10, EnumFacing.EAST);
        DeferredLegacyHeatTransfer sink = new DeferredLegacyHeatTransfer(0, 10, EnumFacing.WEST);
        source.insulation = 99;
        source.adjacent = sink;

        double[] transfer = HeatUtils.simulate(source);
        double temperatureAfterAdjacent = 450;
        double inverseEnvironmentConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + source.insulation +
              source.getInverseConductionCoefficient();
        double expectedTemperature = HeatAPI.AMBIENT_TEMP + (temperatureAfterAdjacent - HeatAPI.AMBIENT_TEMP) *
              Math.pow(1 - 1 / inverseEnvironmentConduction, EnumFacing.VALUES.length);

        assertEquals(expectedTemperature, source.getTemperature(), EPSILON);
        assertEquals(350, sink.getTemperature(), EPSILON);
        assertEquals(50, transfer[0], EPSILON);
        assertEquals(temperatureAfterAdjacent - expectedTemperature, transfer[1], EPSILON);

        source.adjacent = null;
        double before = source.getTemperature();
        transfer = HeatUtils.simulate(source);
        expectedTemperature = HeatAPI.AMBIENT_TEMP + (before - HeatAPI.AMBIENT_TEMP) *
              Math.pow(1 - 1 / inverseEnvironmentConduction, EnumFacing.VALUES.length);
        double expectedLoss = before - expectedTemperature;
        assertEquals(expectedLoss, transfer[1], EPSILON);
        assertEquals(expectedTemperature, source.getTemperature(), EPSILON);
    }

    @Test
    @SuppressWarnings("removal")
    void legacyHeatUtilsDoesNotTransferBetweenSharedIdentities() {
        DeferredLegacyHeatTransfer source = new DeferredLegacyHeatTransfer(200, 10, EnumFacing.EAST);
        DeferredLegacyHeatTransfer sink = new DeferredLegacyHeatTransfer(0, 10, EnumFacing.WEST);
        source.identity = new String("shared-heat");
        sink.identity = new String("shared-heat");
        assertFalse(source.identity == sink.identity);
        source.insulation = HeatAPI.MAX_HEAT;
        source.adjacent = sink;

        double[] transfer = HeatUtils.simulate(source);

        assertEquals(500, source.getTemperature(), EPSILON);
        assertEquals(300, sink.getTemperature(), EPSILON);
        assertEquals(0, transfer[0], EPSILON);
    }

    @Test
    void energyContainerRejectsMalformedNbtAndClampsFiniteValues() {
        BasicEnergyContainer container = BasicEnergyContainer.create(1_000, null);
        NBTTagCompound nbt = new NBTTagCompound();

        nbt.setDouble(NBTConstants.STORED, Double.NaN);
        container.deserializeNBT(nbt);
        assertEquals(0, container.getEnergy());

        nbt.setDouble(NBTConstants.STORED, Double.POSITIVE_INFINITY);
        container.deserializeNBT(nbt);
        assertEquals(0, container.getEnergy());

        nbt.setDouble(NBTConstants.STORED, 2_000);
        container.deserializeNBT(nbt);
        assertEquals(1_000, container.getEnergy());
    }

    @Test
    void heatCapacitorNbtSanitizesCapacityAndStoredHeatTogether() {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(10, null, null);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble(NBTConstants.HEAT_CAPACITY, Double.NaN);
        nbt.setDouble(NBTConstants.STORED, Double.POSITIVE_INFINITY);

        capacitor.deserializeNBT(nbt);

        assertEquals(HeatAPI.DEFAULT_HEAT_CAPACITY, capacitor.getHeatCapacity());
        assertEquals(HeatAPI.AMBIENT_TEMP, capacitor.getHeat());
        assertEquals(HeatAPI.AMBIENT_TEMP, capacitor.getTemperature());

        nbt.setDouble(NBTConstants.HEAT_CAPACITY, 20);
        nbt.setDouble(NBTConstants.STORED, 10_000);
        capacitor.deserializeNBT(nbt);
        assertEquals(20, capacitor.getHeatCapacity());
        assertEquals(500, capacitor.getTemperature());
    }

    @Test
    void heatCapacitorNbtNotifiesAfterHeatAndCapacityAreBothLoaded() {
        AtomicInteger changes = new AtomicInteger();
        AtomicReference<Double> observedTemperature = new AtomicReference<>();
        BasicHeatCapacitor[] reference = new BasicHeatCapacitor[1];
        reference[0] = BasicHeatCapacitor.create(10, null, () -> {
            changes.incrementAndGet();
            observedTemperature.set(reference[0].getTemperature());
        });
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble(NBTConstants.HEAT_CAPACITY, 20);
        nbt.setDouble(NBTConstants.STORED, 10_000);

        reference[0].deserializeNBT(nbt);

        assertEquals(1, changes.get());
        assertEquals(500, observedTemperature.get());
    }

    @Test
    void clientAndServerSuperheatingStateAreIndependent() {
        assertTrue(SynchronizedBoilerData.hotMap != SynchronizedBoilerData.clientHotMap);
        SynchronizedBoilerData.hotMap.put("heat-test", true);
        SynchronizedBoilerData.clientHotMap.put("heat-test", true);

        SynchronizedBoilerData.clientHotMap.clear();

        assertEquals(Boolean.TRUE, SynchronizedBoilerData.hotMap.remove("heat-test"));
    }

    @Test
    void multiblockStructureCanOnlyProcessOncePerServerTick() {
        TestSynchronizedData data = new TestSynchronizedData();

        assertTrue(data.tryClaimServerTick(42));
        assertFalse(data.tryClaimServerTick(42));
        assertTrue(data.tryClaimServerTick(43));
        assertTrue(data.tryClaimServerTick(1));
    }

    @Test
    void persistentStructureTickClaimSurvivesDataReplacement() {
        MultiblockManager<SynchronizedBoilerData> manager = new MultiblockManager<>("tick-claim-test");
        String inventoryID = "persistent-structure";

        assertTrue(manager.tryClaimServerTick(0, inventoryID, 42));
        //A reconstructed SynchronizedData instance still represents the same persistent inventory.
        assertFalse(manager.tryClaimServerTick(0, inventoryID, 42));
        assertTrue(manager.tryClaimServerTick(1, inventoryID, 42));
        assertTrue(manager.tryClaimServerTick(0, inventoryID, 43));
    }

    @Test
    void structureTickClaimFollowsCacheIdReplacement() {
        MultiblockManager<SynchronizedBoilerData> manager = new MultiblockManager<>("replacement-tick-claim-test");
        String staleID = "stale-structure";
        String replacementID = "replacement-structure";

        assertTrue(manager.tryClaimServerTick(0, staleID, 42));
        manager.inheritServerTickClaim(0, Collections.singleton(staleID), replacementID, 42);

        assertFalse(manager.tryClaimServerTick(0, replacementID, 42));
        assertTrue(manager.tryClaimServerTick(0, replacementID, 43));
    }

    @Test
    void invalidatedMultiblockCacheIdsSurviveSerialization() {
        MultiblockManager.InvalidatedCacheData saved =
              new MultiblockManager.InvalidatedCacheData("invalidated-cache-test");
        saved.invalidate("consumed-cache");

        NBTTagCompound serialized = saved.writeToNBT(new NBTTagCompound());
        MultiblockManager.InvalidatedCacheData loaded =
              new MultiblockManager.InvalidatedCacheData("invalidated-cache-test");
        loaded.readFromNBT(serialized);

        assertTrue(loaded.contains("consumed-cache"));
        assertFalse(loaded.contains("still-valid-cache"));
    }

    @Test
    void variableHeatCapacitorReadsLiveCoefficientSuppliers() {
        double[] coefficients = {5, 10};
        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(1, () -> coefficients[0], () -> coefficients[1], null, null);

        coefficients[0] = 25;
        coefficients[1] = 400;

        assertEquals(25, capacitor.getInverseConduction(), EPSILON);
        assertEquals(400, capacitor.getInverseInsulation(), EPSILON);
    }

    private static BasicHeatCapacitor capacitorAt(double temperature, double capacity) {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(capacity, null, null);
        capacitor.setHeat(HeatAPI.multiplyHeat(temperature, capacity));
        return capacitor;
    }

    private static class EqualIdentityHeatCapacitor extends BasicHeatCapacitor {

        private final Object identity;

        private EqualIdentityHeatCapacitor(double temperature, Object identity) {
            super(10, HeatAPI.DEFAULT_INVERSE_CONDUCTION, HeatAPI.DEFAULT_INVERSE_INSULATION, null, null);
            this.identity = identity;
            setHeat(HeatAPI.multiplyHeat(temperature, getHeatCapacity()));
        }

        @Override
        public Object getHeatIdentity() {
            return identity;
        }
    }

    private static IHeatHandler handler(double... capacities) {
        double[] temperatures = new double[capacities.length];
        double[] inverseConductions = new double[capacities.length];
        java.util.Arrays.fill(temperatures, 300);
        java.util.Arrays.fill(inverseConductions, 1);
        return handler(temperatures, inverseConductions, capacities);
    }

    private static IHeatHandler handler(double[] temperatures, double[] inverseConductions, double[] capacities) {
        return new IHeatHandler() {
            @Override
            public int getHeatCapacitorCount() {
                return capacities.length;
            }

            @Override
            public double getTemperature(int capacitor) {
                return temperatures[capacitor];
            }

            @Override
            public double getInverseConduction(int capacitor) {
                return inverseConductions[capacitor];
            }

            @Override
            public double getHeatCapacity(int capacitor) {
                return capacities[capacitor];
            }

            @Override
            public void handleHeat(int capacitor, double transfer) {
            }
        };
    }

    private static class TestTileHeatHandler implements ITileHeatHandler {

        private final IHeatCapacitor capacitor;
        private IHeatHandler adjacent;

        private TestTileHeatHandler(IHeatCapacitor capacitor) {
            this.capacitor = capacitor;
        }

        @Override
        public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
            return side == null || side == EnumFacing.EAST ? Collections.singletonList(capacitor) : Collections.emptyList();
        }

        @Nullable
        @Override
        public IHeatHandler getAdjacent(EnumFacing side) {
            return side == EnumFacing.EAST ? adjacent : null;
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static class TestMultiCapacitorHandler implements ITileHeatHandler {

        private final List<IHeatCapacitor> capacitors;

        private TestMultiCapacitorHandler(IHeatCapacitor... capacitors) {
            this.capacitors = java.util.Arrays.asList(capacitors);
        }

        @Override
        public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
            return capacitors;
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static class RecordingHeatCapacitor implements IHeatCapacitor {

        private final AtomicReference<Double> received;

        private RecordingHeatCapacitor(AtomicReference<Double> received) {
            this.received = received;
        }

        @Override
        public double getTemperature() {
            return HeatAPI.AMBIENT_TEMP;
        }

        @Override
        public double getInverseConduction() {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }

        @Override
        public double getInverseInsulation() {
            return HeatAPI.DEFAULT_INVERSE_INSULATION;
        }

        @Override
        public double getHeatCapacity() {
            return HeatAPI.DEFAULT_HEAT_CAPACITY;
        }

        @Override
        public double getHeat() {
            return HeatAPI.AMBIENT_TEMP;
        }

        @Override
        public void setHeat(double heat) {
        }

        @Override
        public void handleHeat(double transfer) {
            received.set(transfer);
        }
    }

    @SuppressWarnings("removal")
    private static class DeferredLegacyHeatTransfer implements IHeatTransfer {

        private double relativeTemperature;
        private final double heatCapacity;
        private final EnumFacing exposedSide;
        private double pendingHeat;
        private double insulation;
        private Object identity = this;
        @Nullable
        private IHeatTransfer adjacent;

        private DeferredLegacyHeatTransfer(double relativeTemperature, double heatCapacity, EnumFacing exposedSide) {
            this.relativeTemperature = relativeTemperature;
            this.heatCapacity = heatCapacity;
            this.exposedSide = exposedSide;
        }

        @Override
        public Object getHeatIdentity() {
            return identity;
        }

        @Override
        public double getTemp() {
            return relativeTemperature;
        }

        @Override
        public double getInverseConductionCoefficient() {
            return 1;
        }

        @Override
        public double getInsulationCoefficient(EnumFacing side) {
            return insulation;
        }

        @Override
        public double getHeatCapacity() {
            return heatCapacity;
        }

        @Override
        public void transferHeatTo(double heat) {
            pendingHeat += heat;
        }

        @Override
        public double[] simulateHeat() {
            return HeatUtils.simulate(this);
        }

        @Override
        public double applyTemperatureChange() {
            relativeTemperature += pendingHeat / heatCapacity;
            pendingHeat = 0;
            return relativeTemperature;
        }

        @Override
        public boolean canConnectHeat(EnumFacing side) {
            return side == exposedSide;
        }

        @Nullable
        @Override
        public IHeatTransfer getAdjacent(EnumFacing side) {
            return side == exposedSide ? adjacent : null;
        }
    }

    private static class TestSynchronizedData extends SynchronizedData<TestSynchronizedData> {
    }

    @SuppressWarnings("removal")
    private static class TestLegacyHeatTransfer implements IHeatTransfer {

        private double relativeTemperature;
        private final double heatCapacity;

        private TestLegacyHeatTransfer(double relativeTemperature, double heatCapacity) {
            this.relativeTemperature = relativeTemperature;
            this.heatCapacity = heatCapacity;
        }

        @Override
        public double getTemp() {
            return relativeTemperature;
        }

        @Override
        public double getInverseConductionCoefficient() {
            return 1;
        }

        @Override
        public double getInsulationCoefficient(EnumFacing side) {
            return 0;
        }

        @Override
        public double getHeatCapacity() {
            return heatCapacity;
        }

        @Override
        public void transferHeatTo(double heat) {
            relativeTemperature += heat / heatCapacity;
        }

        @Override
        public double[] simulateHeat() {
            return new double[]{0, 0};
        }

        @Override
        public double applyTemperatureChange() {
            return relativeTemperature;
        }

        @Override
        public boolean canConnectHeat(EnumFacing side) {
            return true;
        }

        @Nullable
        @Override
        public IHeatTransfer getAdjacent(EnumFacing side) {
            return null;
        }
    }
}
