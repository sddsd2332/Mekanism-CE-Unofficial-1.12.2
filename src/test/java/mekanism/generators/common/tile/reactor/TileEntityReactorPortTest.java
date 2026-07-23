package mekanism.generators.common.tile.reactor;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntityReactorPortTest {

    @Test
    void energyOutputFollowsPortMode() {
        TileEntityReactorPort port = new TileEntityReactorPort();

        port.fluidEject = false;
        assertFalse(port.canOutputEnergy(EnumFacing.NORTH));
        assertFalse(port.sideIsOutput(EnumFacing.NORTH));

        port.fluidEject = true;
        assertTrue(port.canOutputEnergy(EnumFacing.NORTH));
        assertTrue(port.sideIsOutput(EnumFacing.NORTH));
    }
}
