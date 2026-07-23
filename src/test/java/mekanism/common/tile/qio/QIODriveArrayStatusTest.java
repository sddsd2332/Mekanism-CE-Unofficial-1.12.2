package mekanism.common.tile.qio;

import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIODriveArrayStatusTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void compactStatusPreservesEveryDriveSlot() {
        long encoded = 0;
        TileEntityQIODriveArray.DriveStatus[] states = TileEntityQIODriveArray.DriveStatus.values();
        for (int slot = 0; slot < TileEntityQIODriveArray.DRIVE_SLOTS; slot++) {
            encoded = TileEntityQIODriveArray.updateStatus(slot, states[slot % states.length], encoded);
        }
        for (int slot = 0; slot < TileEntityQIODriveArray.DRIVE_SLOTS; slot++) {
            assertEquals(states[slot % states.length], TileEntityQIODriveArray.getStatus(slot, encoded));
        }
    }

    @Test
    void networkDataCarriesFullDriveStatus() {
        long encoded = 0;
        encoded = TileEntityQIODriveArray.updateStatus(0, TileEntityQIODriveArray.DriveStatus.READY, encoded);
        encoded = TileEntityQIODriveArray.updateStatus(7, TileEntityQIODriveArray.DriveStatus.NEAR_FULL, encoded);
        encoded = TileEntityQIODriveArray.updateStatus(11, TileEntityQIODriveArray.DriveStatus.DUPLICATE, encoded);
        TileEntityQIODriveArray tile = new TileEntityQIODriveArray();
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(NBTConstants.DRIVES, encoded);
        tile.readCustomNBT(data);

        TileNetworkList networkData = tile.getNetworkedData(new TileNetworkList());
        assertEquals(encoded, networkData.get(networkData.size() - 1));
        assertEquals(encoded, tile.getDriveStatusData());
    }

    @Test
    void nearFullCheckDoesNotOverflowAtLongCapacity() {
        long capacity = Long.MAX_VALUE;
        long threshold = capacity - capacity / 4;

        assertFalse(TileEntityQIODriveArray.isNearFull(threshold - 1, capacity));
        assertTrue(TileEntityQIODriveArray.isNearFull(threshold, capacity));
        assertTrue(TileEntityQIODriveArray.isNearFull(capacity - 1, capacity));
        assertFalse(TileEntityQIODriveArray.isNearFull(0, 0));
    }

    @Test
    void redstoneNetworkDataCarriesEmittingState() {
        TileEntityQIORedstoneAdapter tile = new TileEntityQIORedstoneAdapter();
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("qioThreshold", 42);
        data.setBoolean("qioPowering", true);
        tile.readSustainedQIOData(data);

        TileNetworkList networkData = tile.getNetworkedData(new TileNetworkList());
        assertEquals(true, networkData.get(networkData.size() - 1));
        assertEquals(42, tile.getThreshold());
    }

    @Test
    void redstoneConfigurationPersistsTargetModesAndAmount() {
        TileEntityQIORedstoneAdapter tile = new TileEntityQIORedstoneAdapter();
        tile.setFilter(new QIOItemStackFilter(new ItemStack(Blocks.STONE)));
        tile.setThreshold(123);
        tile.toggleFuzzyMode();
        tile.invertSignal();

        NBTTagCompound data = new NBTTagCompound();
        tile.writeSustainedQIOData(data);
        TileEntityQIORedstoneAdapter restored = new TileEntityQIORedstoneAdapter();
        restored.readSustainedQIOData(data);

        assertEquals(123, restored.getThreshold());
        assertEquals(true, restored.getFuzzyMode());
        assertEquals(true, restored.isInverted());
        assertEquals(new ItemStack(Blocks.STONE).getItem(),
              ((QIOItemStackFilter) restored.getTargetFilter()).getItemStack().getItem());
    }
}
