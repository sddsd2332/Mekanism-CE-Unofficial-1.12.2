package mekanism.common.tile;

import mekanism.common.config.MekanismConfig;
import mekanism.common.TestBootstrap;
import mekanism.api.gas.Gas;
import mekanism.common.lib.radiation.RadiationUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadioactiveWasteBarrelDecayTest {

    private static final Gas PLUTONIUM = new Gas("plutonium", 0);
    private static final Gas POLONIUM = new Gas("polonium", 0);
    private static final Gas NUCLEAR_WASTE = new Gas("nuclear_waste", 0);

    private String[] originalBlacklist;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @BeforeEach
    void saveConfig() {
        originalBlacklist = MekanismConfig.current().general.radioactiveWasteBarrelDecayBlacklist.get();
    }

    @AfterEach
    void restoreConfig() {
        MekanismConfig.current().general.radioactiveWasteBarrelDecayBlacklist.set(originalBlacklist);
    }

    @Test
    void plutoniumAndPoloniumAreBlacklistedByDefault() {
        assertTrue(RadiationUtil.isWasteBarrelDecayBlacklisted(PLUTONIUM));
        assertTrue(RadiationUtil.isWasteBarrelDecayBlacklisted(POLONIUM));
        assertFalse(RadiationUtil.isWasteBarrelDecayBlacklisted(NUCLEAR_WASTE));
    }

    @Test
    void blacklistCanBeReconfigured() {
        MekanismConfig.current().general.radioactiveWasteBarrelDecayBlacklist.set(new String[]{"custom_waste"});

        assertFalse(RadiationUtil.isWasteBarrelDecayBlacklisted(PLUTONIUM));
        assertTrue(RadiationUtil.isWasteBarrelDecayBlacklisted(new Gas("custom_waste", 0)));
    }
}
