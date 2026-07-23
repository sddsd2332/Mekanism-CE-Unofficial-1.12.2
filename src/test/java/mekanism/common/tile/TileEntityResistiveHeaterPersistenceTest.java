package mekanism.common.tile;

import mekanism.common.TestBootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileEntityResistiveHeaterPersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void missingEnergyUsageKeepsModernDefault() {
        TileEntityResistiveHeater heater = new TileEntityResistiveHeater();

        heater.readCustomNBT(new NBTTagCompound());

        assertEquals(100, heater.energyUsage);
        assertEquals(40_000, heater.getMaxEnergy());
    }

    @Test
    void explicitEnergyUsageStillRoundTrips() {
        TileEntityResistiveHeater source = new TileEntityResistiveHeater();
        source.energyUsage = 250;
        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityResistiveHeater loaded = new TileEntityResistiveHeater();
        loaded.readCustomNBT(saved);

        assertEquals(250, loaded.energyUsage);
        assertEquals(100_000, loaded.getMaxEnergy());
    }
}
