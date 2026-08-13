package mekanism.common.tile.factory;

import mekanism.api.NBTConstants;
import mekanism.api.RelativeSide;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.TestBootstrap;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void dynamicSideConfigurationsSurviveReload() {
        for (RecipeType type : new RecipeType[]{RecipeType.COMPRESSING, RecipeType.PURIFYING, RecipeType.INJECTING}) {
            assertSideConfigurationSurvivesReload(type, TransmissionType.GAS);
        }
        assertSideConfigurationSurvivesReload(RecipeType.PRC, TransmissionType.GAS);
        assertSideConfigurationSurvivesReload(RecipeType.PRC, TransmissionType.FLUID);
    }

    private void assertSideConfigurationSurvivesReload(RecipeType type, TransmissionType transmission) {
        TileEntityBasicFactory source = new TileEntityBasicFactory();
        source.setRecipeType(type);
        ConfigInfo sourceConfig = source.getConfig().getConfigInfo(transmission);
        assertNotNull(sourceConfig, () -> type + " should support " + transmission);
        assertNotEquals(DataType.INPUT, sourceConfig.getDataType(RelativeSide.RIGHT),
              "The regression value must differ from the default configuration");
        sourceConfig.setDataType(DataType.INPUT, RelativeSide.RIGHT);

        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityBasicFactory loaded = new TileEntityBasicFactory();
        loaded.readCustomNBT(saved);

        assertEquals(type, loaded.getRecipeType());
        ConfigInfo loadedConfig = loaded.getConfig().getConfigInfo(transmission);
        assertNotNull(loadedConfig, () -> type + " should restore " + transmission + " support");
        assertEquals(DataType.INPUT, loadedConfig.getDataType(RelativeSide.RIGHT),
              () -> type + " should restore its " + transmission + " side configuration");
    }
}
