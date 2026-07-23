package mekanism.common.tile.transmitter;

import mekanism.api.heat.HeatAPI;
import mekanism.common.tile.transmitter.TileEntitySidedPipe.ConnectionType;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermodynamicConductorHeatTest {

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
    void disabledConnectionsStillExchangeHeatWithTheEnvironment() {
        TileEntityThermodynamicConductor conductor = new TileEntityThermodynamicConductor();
        for (EnumFacing side : EnumFacing.VALUES) {
            conductor.connectionTypes[side.ordinal()] = ConnectionType.NONE;
            assertTrue(conductor.getHeatCapacitors(side).isEmpty());
        }
        double heatCapacity = conductor.buffer.getHeatCapacity();
        conductor.buffer.setHeat(HeatAPI.multiplyHeat(400, heatCapacity));

        double environmentLoss = conductor.simulateEnvironment();

        double inverseConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + conductor.buffer.getInverseInsulation() +
              conductor.buffer.getInverseConduction();
        double expectedTemperature = HeatAPI.AMBIENT_TEMP + (400 - HeatAPI.AMBIENT_TEMP) *
              Math.pow(1 - 1 / inverseConduction, EnumFacing.VALUES.length);
        assertEquals(expectedTemperature, conductor.buffer.getTemperature(), EPSILON);
        assertEquals(400 - expectedTemperature, environmentLoss, EPSILON);
    }
}
