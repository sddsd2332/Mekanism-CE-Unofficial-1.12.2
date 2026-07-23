package mekanism.common.frequency;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.common.TestBootstrap;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.frequency.FrequencyManager.FrequencyDataHandler;
import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryFrequencyHeatPersistenceTest {

    private static final double HEAT_CAPACITY = 2_500;
    private static final double STORED_HEAT = 1_250_000;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void heatRoundTripsThroughFrequencyNbt() {
        InventoryFrequency source = createFrequency("nbt");
        NBTTagCompound saved = new NBTTagCompound();

        source.write(saved);
        InventoryFrequency loaded = new InventoryFrequency(saved);

        assertHeatSnapshot(loaded);
    }

    @Test
    void heatRoundTripsThroughClientFrequencySnapshot() {
        InventoryFrequency source = createFrequency("network");
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.write(buffer);

            InventoryFrequency loaded = new InventoryFrequency(buffer);

            assertHeatSnapshot(loaded);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void heatSurvivesFrequencyManagerSaveLoadAndRebuild() {
        FrequencyManager<InventoryFrequency> sourceManager = new FrequencyManager<>(FrequencyType.INVENTORY);
        FrequencyManager<InventoryFrequency> loadedManager = new FrequencyManager<>(FrequencyType.INVENTORY);
        try {
            InventoryFrequency source = createFrequency("manager");
            sourceManager.addFrequency(source);
            FrequencyDataHandler sourceData = new FrequencyDataHandler("source");
            sourceData.setManager(sourceManager);
            NBTTagCompound saved = sourceData.writeToNBT(new NBTTagCompound());

            FrequencyDataHandler loadedData = new FrequencyDataHandler("loaded");
            loadedData.readFromNBT(saved);
            loadedData.setManager(loadedManager);
            loadedData.syncManager();

            assertHeatSnapshot(loadedManager.getFrequency("manager"));
        } finally {
            FrequencyManager.unregister(sourceManager);
            FrequencyManager.unregister(loadedManager);
        }
    }

    private static InventoryFrequency createFrequency(String name) {
        InventoryFrequency frequency = new InventoryFrequency(name, UUID.randomUUID(), SecurityMode.PUBLIC);
        frequency.storedHeat.updateHeatAndCapacity(HEAT_CAPACITY);
        frequency.storedHeat.setHeat(STORED_HEAT);
        return frequency;
    }

    private static void assertHeatSnapshot(InventoryFrequency frequency) {
        assertEquals(HEAT_CAPACITY, frequency.storedHeat.getHeatCapacity());
        assertEquals(STORED_HEAT, frequency.storedHeat.getHeat());
        assertEquals(STORED_HEAT / HEAT_CAPACITY, frequency.getTemperature());
    }
}
