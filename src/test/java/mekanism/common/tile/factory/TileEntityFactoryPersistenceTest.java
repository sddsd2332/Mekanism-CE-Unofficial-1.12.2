package mekanism.common.tile.factory;

import mekanism.api.NBTConstants;
import mekanism.common.TestBootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntityFactoryPersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void usedSoFarWritesAsIndividualLongTags() {
        TileEntityBasicFactory source = new TileEntityBasicFactory();
        source.setSavedUsedSoFar(0, 3_000_000_000L);
        source.setSavedUsedSoFar(1, 42L);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setLong(NBTConstants.USED_SO_FAR, 99L);

        source.writeCustomNBT(saved);

        assertFalse(saved.hasKey(NBTConstants.USED_SO_FAR));
        assertTrue(saved.hasKey(NBTConstants.USED_SO_FAR + "0", NBT.TAG_LONG));
        assertTrue(saved.hasKey(NBTConstants.USED_SO_FAR + "1", NBT.TAG_LONG));
        assertEquals(3_000_000_000L, saved.getLong(NBTConstants.USED_SO_FAR + "0"));
        assertEquals(42L, saved.getLong(NBTConstants.USED_SO_FAR + "1"));

        TileEntityBasicFactory loaded = new TileEntityBasicFactory();
        loaded.readCustomNBT(saved);

        assertEquals(3_000_000_000L, loaded.getSavedUsedSoFar(0));
        assertEquals(42L, loaded.getSavedUsedSoFar(1));
        assertEquals(0L, loaded.getSavedUsedSoFar(2));
    }
}
