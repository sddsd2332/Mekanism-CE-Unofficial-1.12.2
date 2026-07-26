package mekanism.generators.common.content.fission;

import mekanism.api.Coord4D;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.config.GeneratorsConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorPort;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedFissionDataTest {

    private static final double EPSILON = 1.0E-8;

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
    void fractionalRemainderBurnsWithoutWholeFuelInTank() {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.rateLimit = 0.25;
        data.burnRemaining = 0.5;
        double heatBefore = data.getHeatCapacitor().getHeat();

        data.burnFuel(null);

        assertEquals(0.25, data.lastBurnRate, EPSILON);
        assertEquals(0.25, data.burnRemaining, EPSILON);
        assertEquals(0, data.fuelTank.getStored());
        assertEquals(SynchronizedFissionData.ENERGY_PER_FISSION_FUEL * 0.25,
              data.getHeatCapacitor().getHeat() - heatBefore, EPSILON);
    }

    @Test
    void fullTankAndFractionalRemainderStayConservedWhileBurning() {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, data.fuelTank.getCapacity()));
        data.burnRemaining = 0.5;
        data.rateLimit = 0.25;
        double fuelBefore = representedFuel(data);

        data.burnFuel(null);

        assertEquals(0.25, data.lastBurnRate, EPSILON);
        assertEquals(fuelBefore - data.lastBurnRate, representedFuel(data), EPSILON);
        assertEquals(data.fuelTank.getCapacity(), data.fuelTank.getStored());
        assertEquals(0.25, data.burnRemaining, EPSILON);
    }

    @Test
    void shrinkingFuelCapacityMovesOverflowIntoRemainder() {
        SynchronizedFissionData data = new SynchronizedFissionData();
        data.fuelAssemblies = 2;
        data.updateCapacities();
        data.fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, 12_000));
        data.burnRemaining = 0.5;
        double fuelBefore = representedFuel(data);

        data.fuelAssemblies = 1;
        data.updateCapacities();

        assertEquals(8_000, data.fuelTank.getStored());
        assertEquals(4_000.5, data.burnRemaining, EPSILON);
        assertEquals(fuelBefore, representedFuel(data), EPSILON);
    }

    @Test
    void configuredTankContributionsDriveRebuiltCapacities() {
        GeneratorsConfig previous = MekanismConfig.current().generators;
        GeneratorsConfig config = new GeneratorsConfig();
        config.fissionFuelPerAssembly.set(1_234);
        config.fissionCooledCoolantPerTank.set(2_000);
        config.fissionHeatedCoolantPerTank.set(3_000);
        MekanismConfig.current().generators = config;
        try {
            SynchronizedFissionData data = new SynchronizedFissionData();
            data.fuelAssemblies = 2;
            data.volume = 3;

            data.updateCapacities();

            assertEquals(2_468, data.fuelTank.getCapacity());
            assertEquals(2_468, data.wasteTank.getCapacity());
            assertEquals(6_000, data.coolantTank.getCapacity());
            assertEquals(6_000, data.gasCoolantTank.getCapacity());
            assertEquals(9_000, data.steamTank.getCapacity());
            assertEquals(9_000, data.heatedCoolantTank.getCapacity());
        } finally {
            MekanismConfig.current().generators = previous;
        }
    }

    @Test
    void cacheMergeMovesIntegerOverflowIntoBurnRemainder() {
        FissionReactorCache target = new FissionReactorCache();
        target.fuel = new GasStack(MekanismFluids.FissileFuel, Integer.MAX_VALUE);
        target.burnRemaining = 0.25;
        FissionReactorCache incoming = new FissionReactorCache();
        incoming.fuel = new GasStack(MekanismFluids.FissileFuel, 10);
        incoming.burnRemaining = 0.5;
        FissionReactorUpdateProtocol protocol = new FissionReactorUpdateProtocol(null);

        protocol.mergeCaches(Collections.emptyList(), target, incoming);

        assertEquals(Integer.MAX_VALUE, target.fuel.amount);
        assertEquals(10.75, target.burnRemaining, EPSILON);
    }

    @Test
    void meltdownPreservesUnventedContentsAndClearsHeatedCoolant() {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.volume = 1;
        data.updateCapacities();
        data.fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, 100));
        data.coolantTank.setFluid(new FluidStack(FluidRegistry.WATER, 100));
        data.heatedCoolantTank.setGas(new GasStack(MekanismFluids.SuperheatedSodium, 100));

        data.onMeltdown();

        assertEquals(100, data.fuelTank.getStored());
        assertEquals(100, data.coolantTank.getFluidAmount());
        assertEquals(0, data.heatedCoolantTank.getStored());
    }

    @Test
    void fissionCasingsAndPortsDoNotExposeHeatHandlers() {
        assertFalse(new TileEntityFissionReactorCasing().canHandleHeat());
        assertFalse(new TileEntityFissionReactorPort().canHandleHeat());
    }

    @Test
    void sodiumCoolingAboveFortyBurnRateStillWorksWithFullHeatedOutput() {
        SynchronizedFissionData data = new SynchronizedFissionData();
        data.fuelAssemblies = 64;
        data.surfaceArea = data.fuelAssemblies * 4;
        data.volume = 128;
        data.updateCapacities();
        data.getHeatCapacitor().updateHeatAndCapacity(100_000);
        data.fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, data.fuelTank.getCapacity()));
        data.heatedCoolantTank.setGas(new GasStack(MekanismFluids.SuperheatedSodium, data.heatedCoolantTank.getCapacity()));
        data.rateLimit = 50;
        data.active = true;

        for (int tick = 0; tick < 200; tick++) {
            data.gasCoolantTank.setGas(new GasStack(MekanismFluids.Sodium, data.gasCoolantTank.getCapacity()));

            data.tick(null);

            assertEquals(50, data.lastBurnRate, EPSILON);
            assertTrue(data.lastBoilRate > 0);
            assertTrue(data.getTemperature() < SynchronizedFissionData.MIN_DAMAGE_TEMPERATURE);
            assertEquals(0, data.reactorDamage, EPSILON);
            assertEquals(data.heatedCoolantTank.getCapacity(), data.heatedCoolantTank.getStored());
        }
    }

    @Test
    void criticalDamageCanRiseAboveOneHundredLikeModernMekanism() {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.reactorDamage = SynchronizedFissionData.MAX_DAMAGE;
        data.getHeatCapacitor().setHeat(data.getHeatCapacitor().getHeatCapacity() *
              SynchronizedFissionData.MAX_DAMAGE_TEMPERATURE);

        data.tick(null);

        assertTrue(data.reactorDamage > SynchronizedFissionData.MAX_DAMAGE);
    }

    @Test
    void cachePreservesCriticalDamageAboveOneHundred() {
        FissionReactorCache cache = new FissionReactorCache();
        cache.reactorDamage = 125;
        SynchronizedFissionData data = reactorWithOneAssembly();

        cache.apply(data);

        assertEquals(125, data.reactorDamage, EPSILON);
        cache.sync(data);
        assertEquals(125, cache.reactorDamage, EPSILON);
    }

    @Test
    void cacheRoundTripsStoredHeatAndCapacity() {
        FissionReactorCache savedCache = new FissionReactorCache();
        savedCache.storedHeat = 900_000;
        savedCache.heatCapacity = 1_500;
        NBTTagCompound saved = new NBTTagCompound();
        savedCache.save(saved);

        FissionReactorCache loadedCache = new FissionReactorCache();
        loadedCache.load(saved);
        SynchronizedFissionData loaded = reactorWithOneAssembly();
        loadedCache.apply(loaded);

        assertEquals(1_500, loaded.getHeatCapacitor().getHeatCapacity(), EPSILON);
        assertEquals(900_000, loaded.getHeatCapacitor().getHeat(), EPSILON);
        assertEquals(600, loaded.getTemperature(), EPSILON);
    }

    @Test
    void existingWasteStackGrowsInPlaceAndNotifiesOnce() {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.fuelTank.setGas(new GasStack(MekanismFluids.FissileFuel, 10));
        data.wasteTank.setGas(new GasStack(MekanismFluids.NuclearWaste, 5));
        data.rateLimit = 1;
        GasStack waste = data.wasteTank.getGas();
        AtomicInteger changes = new AtomicInteger();
        data.wasteTank.addContentsListener(changes::incrementAndGet);

        data.burnFuel(null);

        assertSame(waste, data.wasteTank.getGas());
        assertEquals(6, data.wasteTank.getStored());
        assertEquals(1, changes.get());
    }

    @Test
    void reactorGeometryIsReusedUntilFormationDataChanges() {
        SynchronizedFissionData data = new SynchronizedFissionData();
        data.minLocation = new Coord4D(0, 0, 0, 7);
        data.maxLocation = new Coord4D(4, 6, 8, 7);

        data.updateDerivedGeometry();
        Coord4D firstCenter = data.getReactorCenter();
        net.minecraft.util.math.AxisAlignedBB firstBounds = data.getRadiationBounds();

        assertSame(firstCenter, data.getReactorCenter());
        assertSame(firstBounds, data.getRadiationBounds());
        assertEquals(new Coord4D(2, 3, 4, 7), firstCenter);
        assertEquals(new net.minecraft.util.math.AxisAlignedBB(1, 1, 1, 4, 6, 8), firstBounds);

        data.maxLocation = new Coord4D(6, 8, 10, 7);
        data.updateDerivedGeometry();
        assertNotSame(firstCenter, data.getReactorCenter());
        assertNotSame(firstBounds, data.getRadiationBounds());
        assertEquals(new Coord4D(3, 4, 5, 7), data.getReactorCenter());
    }

    @Test
    void renderSnapshotsReuseStableStackTypesAndClearWithTanks() throws ReflectiveOperationException {
        SynchronizedFissionData data = reactorWithOneAssembly();
        data.volume = 1;
        data.updateCapacities();
        data.wasteTank.setGas(new GasStack(MekanismFluids.NuclearWaste, 5));
        data.coolantTank.setFluid(new FluidStack(FluidRegistry.WATER, 10));
        data.syncPrev();
        GasStack firstWaste = getField(data, "prevWaste", GasStack.class);
        FluidStack firstCoolant = getField(data, "prevCoolant", FluidStack.class);

        data.wasteTank.growStack(2, mekanism.api.Action.EXECUTE);
        data.coolantTank.growStack(3, mekanism.api.Action.EXECUTE);
        data.syncPrev();

        assertSame(firstWaste, getField(data, "prevWaste", GasStack.class));
        assertSame(firstCoolant, getField(data, "prevCoolant", FluidStack.class));
        assertEquals(7, firstWaste.amount);
        assertEquals(13, firstCoolant.amount);

        data.wasteTank.setEmpty();
        data.coolantTank.setEmpty();
        data.syncPrev();
        assertNull(getField(data, "prevWaste", GasStack.class));
        assertNull(getField(data, "prevCoolant", FluidStack.class));
    }

    private static SynchronizedFissionData reactorWithOneAssembly() {
        SynchronizedFissionData data = new SynchronizedFissionData();
        data.fuelAssemblies = 1;
        data.updateCapacities();
        return data;
    }

    private static double representedFuel(SynchronizedFissionData data) {
        return data.fuelTank.getStored() + data.burnRemaining;
    }

    private static <T> T getField(SynchronizedFissionData data, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = SynchronizedFissionData.class.getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(data));
    }
}
