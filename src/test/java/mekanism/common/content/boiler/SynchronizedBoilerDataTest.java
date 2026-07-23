package mekanism.common.content.boiler;

import mekanism.common.TestBootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedBoilerDataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void unchangedWaterDoesNotMaskSteamRenderChanges() {
        SynchronizedBoilerData data = new SynchronizedBoilerData();
        data.waterStored = new FluidStack(FluidRegistry.WATER, 100);
        data.prevWater = data.waterStored.copy();
        data.steamStored = new FluidStack(FluidRegistry.WATER, 200);
        data.prevSteam = new FluidStack(FluidRegistry.WATER, 199);

        assertTrue(data.needsRenderUpdate());

        data.prevSteam = data.steamStored.copy();
        assertFalse(data.needsRenderUpdate());
    }

    @Test
    void cacheRoundTripsStoredHeatAndCapacity() {
        SynchronizedBoilerData source = new SynchronizedBoilerData();
        source.getHeatCapacitor().setHeatCapacity(250, false);
        source.getHeatCapacitor().setHeat(125_000);
        BoilerCache savedCache = new BoilerCache();
        savedCache.sync(source);
        NBTTagCompound saved = new NBTTagCompound();
        savedCache.save(saved);

        BoilerCache loadedCache = new BoilerCache();
        loadedCache.load(saved);
        SynchronizedBoilerData loaded = new SynchronizedBoilerData();
        loadedCache.apply(loaded);

        assertEquals(250, loaded.getHeatCapacitor().getHeatCapacity());
        assertEquals(125_000, loaded.getHeatCapacitor().getHeat());
        assertEquals(500, loaded.getTemperature());
    }
}
