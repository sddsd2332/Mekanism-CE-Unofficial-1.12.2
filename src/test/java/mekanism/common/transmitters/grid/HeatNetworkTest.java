package mekanism.common.transmitters.grid;

import mekanism.api.heat.HeatAPI;
import mekanism.common.tile.transmitter.TileEntityThermodynamicConductor;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatNetworkTest {

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
    void efficiencyDoesNotOverflowAtSaturatedFlow() {
        assertEquals(0.5, HeatNetwork.getEfficiency(HeatAPI.MAX_HEAT, HeatAPI.MAX_HEAT));
        assertEquals(1, HeatNetwork.getEfficiency(HeatAPI.MAX_HEAT, 0));
        assertEquals(0, HeatNetwork.getEfficiency(0, HeatAPI.MAX_HEAT));
    }

    @Test
    void quietNetworkSleepsAndUsesPeriodicSafetyProbes() {
        HeatNetwork network = new HeatNetwork();

        for (int tick = 0; tick < HeatNetwork.QUIET_TICKS_BEFORE_SLEEP; tick++) {
            assertTrue(network.shouldRunServerSimulation());
            network.updateSleepState(false, false);
        }
        assertTrue(network.isSleeping());

        for (int tick = 1; tick < HeatNetwork.SLEEP_PROBE_INTERVAL; tick++) {
            assertFalse(network.shouldRunServerSimulation());
        }
        assertTrue(network.shouldRunServerSimulation());
        network.updateSleepState(false, true);
        assertTrue(network.isSleeping());
        assertFalse(network.shouldRunServerSimulation());
    }

    @Test
    void activityNeverLetsNetworkSleepAndWakesAProbe() {
        HeatNetwork network = new HeatNetwork();

        for (int tick = 0; tick < HeatNetwork.QUIET_TICKS_BEFORE_SLEEP * 3; tick++) {
            assertTrue(network.shouldRunServerSimulation());
            network.updateSleepState(true, false);
        }
        assertFalse(network.isSleeping());

        putToSleep(network);
        for (int tick = 1; tick < HeatNetwork.SLEEP_PROBE_INTERVAL; tick++) {
            assertFalse(network.shouldRunServerSimulation());
        }
        assertTrue(network.shouldRunServerSimulation());
        network.updateSleepState(true, true);
        assertFalse(network.isSleeping());
        assertTrue(network.shouldRunServerSimulation());
    }

    @Test
    void conductorHeatChangeWakesSleepingNetworkImmediately() {
        HeatNetwork network = new HeatNetwork();
        TileEntityThermodynamicConductor conductor = attachConductor(network);
        network.getConductorSnapshot();
        putToSleep(network);

        conductor.buffer.handleHeat(10);

        assertFalse(network.isSleeping());
        assertTrue(network.shouldRunServerSimulation());
    }

    @Test
    void topologyChangesWakeNetworkAndRebuildStableSnapshotOnce() {
        HeatNetwork network = new HeatNetwork();
        TileEntityThermodynamicConductor firstConductor = attachConductor(network);
        List<TileEntityThermodynamicConductor> firstSnapshot = network.getConductorSnapshot();

        assertSame(firstSnapshot, network.getConductorSnapshot());
        assertEquals(new ArrayList<>(network.getTransmitters()), Collections.singletonList(firstConductor.getTransmitter()));
        putToSleep(network);

        TileEntityThermodynamicConductor secondConductor = attachConductor(network);
        assertFalse(network.isSleeping());
        List<TileEntityThermodynamicConductor> rebuiltSnapshot = network.getConductorSnapshot();
        assertNotSame(firstSnapshot, rebuiltSnapshot);
        assertEquals(2, rebuiltSnapshot.size());
        assertTrue(rebuiltSnapshot.contains(firstConductor));
        assertTrue(rebuiltSnapshot.contains(secondConductor));
        assertSame(rebuiltSnapshot, network.getConductorSnapshot());
    }

    @Test
    void invalidFlowValuesDoNotKeepNetworkActive() {
        assertFalse(HeatNetwork.hasEffectiveActivity(Double.NaN, Double.POSITIVE_INFINITY, false));
        assertFalse(HeatNetwork.hasEffectiveActivity(-HeatAPI.MAX_HEAT, 0, false));
        assertTrue(HeatNetwork.hasEffectiveActivity(HeatAPI.MAX_HEAT, 0, false));
        assertTrue(HeatNetwork.hasEffectiveActivity(0, 0, true));
    }

    private static TileEntityThermodynamicConductor attachConductor(HeatNetwork network) {
        TileEntityThermodynamicConductor conductor = new TileEntityThermodynamicConductor();
        network.addTransmitter(conductor.getTransmitter());
        conductor.getTransmitter().theNetwork = network;
        conductor.getTransmitter().orphaned = false;
        return conductor;
    }

    private static void putToSleep(HeatNetwork network) {
        for (int tick = 0; tick < HeatNetwork.QUIET_TICKS_BEFORE_SLEEP; tick++) {
            network.updateSleepState(false, false);
        }
        assertTrue(network.isSleeping());
    }
}
