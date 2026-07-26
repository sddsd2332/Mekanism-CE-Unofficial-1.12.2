package mekanism.common.content.transporter;

import mekanism.api.Coord4D;
import mekanism.common.TestBootstrap;
import mekanism.common.content.transporter.PathfinderCache.PathData;
import mekanism.common.content.transporter.TransporterStack.Path;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransporterPathOptimizationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @AfterEach
    void clearPathCache() {
        PathfinderCache.reset();
    }

    @Test
    void pathCursorAdvancesAndRecoversFromUnexpectedCoordinates() {
        Coord4D destination = coord(0, 0);
        Coord4D finalTransporter = coord(1, 0);
        Coord4D middleTransporter = coord(2, 0);
        Coord4D start = coord(3, 0);
        TransporterStack stack = new TransporterStack();
        stack.setPath(Arrays.asList(destination, finalTransporter, middleTransporter, start), Path.DEST, false);

        assertEquals(3, stack.getPathIndex(start));
        assertEquals(3, stack.getPathIndex(start));
        assertEquals(2, stack.getPathIndex(middleTransporter));
        assertEquals(1, stack.getPathIndex(finalTransporter));

        assertEquals(-1, stack.getPathIndex(coord(99, 0)));
        assertEquals(1, stack.getPathIndex(finalTransporter));
        assertEquals(0, stack.getPathIndex(destination));
    }

    @Test
    void replacingOrReloadingPathResetsCursor() {
        TransporterStack stack = new TransporterStack();
        stack.originalLocation = coord(5, 0);
        stack.setPath(Arrays.asList(coord(0, 0), coord(1, 0), coord(2, 0)), Path.DEST, false);
        assertEquals(1, stack.getPathIndex(coord(1, 0)));

        Coord4D newStart = coord(12, 0);
        stack.setPath(Arrays.asList(coord(10, 0), coord(11, 0), newStart), Path.HOME, false);
        assertEquals(2, stack.getPathIndex(newStart));

        NBTTagCompound serialized = new NBTTagCompound();
        stack.write(serialized);
        TransporterStack loaded = TransporterStack.readFromNBT(serialized);
        assertFalse(loaded.hasPath());
        assertEquals(-1, loaded.getPathIndex(newStart));
    }

    @Test
    void cachedPathsAtTheSameCoordinatesRemainDimensionScoped() {
        EnumSet<EnumFacing> sides = EnumSet.of(EnumFacing.NORTH);
        Coord4D overworldStart = coord(0, 0);
        Coord4D overworldEnd = coord(3, 0);
        Coord4D netherStart = coord(0, -1);
        Coord4D netherEnd = coord(3, -1);
        List<Coord4D> overworldPath = Arrays.asList(overworldEnd, coord(2, 0), overworldStart);
        List<Coord4D> netherPath = Arrays.asList(netherEnd, coord(2, -1), netherStart);

        PathfinderCache.addCachedPath(new PathData(overworldStart, overworldEnd, EnumFacing.NORTH), overworldPath);
        PathfinderCache.addCachedPath(new PathData(netherStart, netherEnd, EnumFacing.NORTH), netherPath);

        assertSame(overworldPath, PathfinderCache.getCache(overworldStart, overworldEnd, sides));
        assertSame(netherPath, PathfinderCache.getCache(netherStart, netherEnd, sides));
    }

    private static Coord4D coord(int x, int dimension) {
        return new Coord4D(x, 64, 0, dimension);
    }
}
