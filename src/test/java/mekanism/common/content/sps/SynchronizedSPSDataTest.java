package mekanism.common.content.sps;

import mekanism.api.Coord4D;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedSPSDataTest {

    @Test
    void soundSourcesMatchModernSpsPositions() {
        SynchronizedSPSData data = new SynchronizedSPSData();
        BlockPos min = new BlockPos(10, 20, 30);
        BlockPos max = new BlockPos(16, 26, 36);

        assertFalse(data.shouldPlaySoundAt(min));

        data.minLocation = new Coord4D(min, 0);
        data.maxLocation = new Coord4D(max, 0);

        assertTrue(data.shouldPlaySoundAt(new BlockPos(13, 20, 30)));
        assertTrue(data.shouldPlaySoundAt(new BlockPos(13, 26, 36)));
        assertFalse(data.shouldPlaySoundAt(min));
        assertFalse(data.shouldPlaySoundAt(max));
        assertFalse(data.shouldPlaySoundAt(new BlockPos(13, 20, 31)));
    }
}
