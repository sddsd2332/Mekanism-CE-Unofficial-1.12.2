package mekanism.common.tile.transmitter;

import mekanism.common.tile.transmitter.TileEntitySidedPipe.ConnectionType;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalCableConnectionTest {

    @Test
    void rawSideModeAdvertisesEnergyBeforeAVisualConnectionExists() {
        TileEntityUniversalCable cable = new TileEntityUniversalCable();
        EnumFacing side = EnumFacing.NORTH;

        cable.connectionTypes[side.ordinal()] = ConnectionType.NORMAL;
        assertTrue(cable.canReceiveEnergy(side));
        assertTrue(cable.canOutputEnergy(side));
        assertTrue(cable.canConnectEnergy(side));

        cable.connectionTypes[side.ordinal()] = ConnectionType.PULL;
        assertTrue(cable.canReceiveEnergy(side));
        assertFalse(cable.canOutputEnergy(side));

        cable.connectionTypes[side.ordinal()] = ConnectionType.PUSH;
        assertFalse(cable.canReceiveEnergy(side));
        assertTrue(cable.canOutputEnergy(side));

        cable.connectionTypes[side.ordinal()] = ConnectionType.NONE;
        assertFalse(cable.canReceiveEnergy(side));
        assertFalse(cable.canOutputEnergy(side));
        assertFalse(cable.canConnectEnergy(side));
    }

    @Test
    void redstoneFluxHandshakeCanActuallyTransferEnergyInBothDirections() {
        TileEntityUniversalCable cable = new TileEntityUniversalCable();
        EnumFacing side = EnumFacing.NORTH;

        cable.connectionTypes[side.ordinal()] = ConnectionType.NORMAL;
        int received = cable.receiveEnergy(side, 1_000, false);

        assertTrue(received > 0);
        assertTrue(cable.getEnergy() > 0);

        cable.connectionTypes[side.ordinal()] = ConnectionType.PUSH;
        int extracted = cable.extractEnergy(side, received, false);

        assertEquals(received, extracted);
        assertEquals(0, cable.getEnergy());
    }
}
