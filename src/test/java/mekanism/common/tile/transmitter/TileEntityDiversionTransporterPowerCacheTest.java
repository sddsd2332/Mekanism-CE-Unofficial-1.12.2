package mekanism.common.tile.transmitter;

import mekanism.common.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntityDiversionTransporterPowerCacheTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void powerStateIsCachedUntilNeighborRefresh() {
        TestDiversionTransporter transporter = new TestDiversionTransporter();

        assertFalse(transporter.currentPowerState());
        assertFalse(transporter.currentPowerState());
        assertEquals(1, transporter.powerQueries);

        transporter.queriedPower = true;
        assertFalse(transporter.currentPowerState());
        assertEquals(1, transporter.powerQueries);

        assertTrue(transporter.refreshCachedPowerState());
        assertTrue(transporter.currentPowerState());
        assertEquals(2, transporter.powerQueries);

        assertFalse(transporter.refreshCachedPowerState());
        assertEquals(3, transporter.powerQueries);

        transporter.queriedPower = false;
        assertTrue(transporter.refreshCachedPowerState());
        assertFalse(transporter.currentPowerState());
        assertEquals(4, transporter.powerQueries);
    }

    private static class TestDiversionTransporter extends TileEntityDiversionTransporter {

        private boolean queriedPower;
        private int powerQueries;

        @Override
        protected boolean queryPowerState() {
            powerQueries++;
            return queriedPower;
        }

        private boolean currentPowerState() {
            return getPowerState();
        }

        private boolean refreshCachedPowerState() {
            return refreshPowerState();
        }
    }
}
