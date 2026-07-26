package mekanism.common.lib.radiation.capability;

import mekanism.api.NBTConstants;
import mekanism.common.config.MekanismConfig;
import mekanism.common.TestBootstrap;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultRadiationEntityTest {

    private double originalDecayRate;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @BeforeEach
    void saveConfig() {
        originalDecayRate = MekanismConfig.current().general.radiationTargetDecayRate.val();
    }

    @AfterEach
    void restoreConfig() {
        MekanismConfig.current().general.radiationTargetDecayRate.set(originalDecayRate);
    }

    @Test
    void valuesClampToBaselineAndSaturate() {
        DefaultRadiationEntity radiation = new DefaultRadiationEntity();
        assertEquals(RadiationManager.BASELINE, radiation.getRadiation());

        radiation.set(Double.NaN);
        assertEquals(RadiationManager.BASELINE, radiation.getRadiation());
        radiation.radiate(-1);
        assertEquals(RadiationManager.BASELINE, radiation.getRadiation());
        radiation.radiate(Double.POSITIVE_INFINITY);
        assertEquals(Double.MAX_VALUE, radiation.getRadiation());
    }

    @Test
    void zeroDecayRateReturnsDoseToBaseline() {
        DefaultRadiationEntity radiation = new DefaultRadiationEntity();
        radiation.set(1);
        MekanismConfig.current().general.radiationTargetDecayRate.set(0);

        radiation.decay();

        assertEquals(RadiationManager.BASELINE, radiation.getRadiation());
    }

    @Test
    void nbtReadSanitizesMalformedDose() {
        DefaultRadiationEntity radiation = new DefaultRadiationEntity();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(NBTConstants.RADIATION, Double.NaN);

        radiation.deserializeNBT(tag);

        assertEquals(RadiationManager.BASELINE, radiation.getRadiation());
    }
}
