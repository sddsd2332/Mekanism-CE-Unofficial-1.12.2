package mekanism.common.lib.radiation;

import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.common.TestBootstrap;
import mekanism.common.config.MekanismConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RadiationPersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void sourcesRoundTripThroughWorldSavedData() {
        RadiationManager sourceManager = new RadiationManager();
        sourceManager.radiate(new Coord4D(3, 70, -2, 4), 2.5);
        RadiationManager.RadiationDataHandler writer = new RadiationManager.RadiationDataHandler("test");
        writer.setManagerAndSync(sourceManager);
        NBTTagCompound saved = writer.writeToNBT(new NBTTagCompound());

        RadiationManager.RadiationDataHandler reader = new RadiationManager.RadiationDataHandler("test");
        reader.readFromNBT(saved);
        RadiationManager loadedManager = new RadiationManager();
        reader.setManagerAndSync(loadedManager);

        assertEquals(1, loadedManager.getSourceCount(4));
        assertEquals(RadiationManager.BASELINE + 2.5,
              loadedManager.getRadiationLevel(new Coord4D(3, 70, -2, 4)));
    }

    @Test
    void malformedSourcesAreSkippedAndDuplicatePositionsMerge() {
        NBTTagList sources = new NBTTagList();
        sources.appendTag(sourceTag(new Coord4D(1, 64, 1, 0), 1));
        sources.appendTag(sourceTag(new Coord4D(1, 64, 1, 0), 2));
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setDouble(NBTConstants.RADIATION, Double.NaN);
        sources.appendTag(malformed);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(NBTConstants.RADIATION_LIST, sources);

        RadiationManager.RadiationDataHandler reader = new RadiationManager.RadiationDataHandler("test");
        reader.readFromNBT(root);
        RadiationManager manager = new RadiationManager();
        reader.setManagerAndSync(manager);

        assertEquals(1, manager.getSourceCount(0));
        assertEquals(RadiationManager.BASELINE + 3, manager.getRadiationLevel(new Coord4D(1, 64, 1, 0)));
    }

    @Test
    void disabledRadiationStillRestoresSavedSources() {
        boolean enabled = MekanismConfig.current().general.radiationEnabled.val();
        try {
            MekanismConfig.current().general.radiationEnabled.set(false);
            NBTTagList sources = new NBTTagList();
            sources.appendTag(sourceTag(new Coord4D(1, 64, 1, 2), 1));
            NBTTagCompound root = new NBTTagCompound();
            root.setTag(NBTConstants.RADIATION_LIST, sources);
            RadiationManager.RadiationDataHandler reader = new RadiationManager.RadiationDataHandler("test");
            reader.readFromNBT(root);
            RadiationManager manager = new RadiationManager();

            reader.setManagerAndSync(manager);

            assertEquals(1, manager.getSourceCount(2));
        } finally {
            MekanismConfig.current().general.radiationEnabled.set(enabled);
        }
    }

    @Test
    void malformedMeltdownDimensionDoesNotAbortLoading() {
        NBTTagCompound meltdowns = new NBTTagCompound();
        meltdowns.setTag("not_a_dimension", new NBTTagList());
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(NBTConstants.MELTDOWNS, meltdowns);
        RadiationManager.RadiationDataHandler reader = new RadiationManager.RadiationDataHandler("test");

        assertDoesNotThrow(() -> reader.readFromNBT(root));
    }

    @Test
    void meltdownRadiusPersistsAndOldDataDefaultsToEight() {
        Meltdown meltdown = new Meltdown(BlockPos.ORIGIN, new BlockPos(3, 3, 3), 1, 0.1, 12, UUID.randomUUID());
        NBTTagCompound saved = new NBTTagCompound();
        meltdown.write(saved);
        NBTTagCompound roundTripped = new NBTTagCompound();
        Meltdown.load(saved).write(roundTripped);
        assertEquals(12, roundTripped.getFloat(NBTConstants.RADIUS));

        saved.removeTag(NBTConstants.RADIUS);
        NBTTagCompound legacyRoundTrip = new NBTTagCompound();
        Meltdown.load(saved).write(legacyRoundTrip);
        assertEquals(8, legacyRoundTrip.getFloat(NBTConstants.RADIUS));
    }

    private static NBTTagCompound sourceTag(Coord4D coord, double magnitude) {
        NBTTagCompound tag = new NBTTagCompound();
        new RadiationSource(coord, magnitude).write(tag);
        return tag;
    }
}
