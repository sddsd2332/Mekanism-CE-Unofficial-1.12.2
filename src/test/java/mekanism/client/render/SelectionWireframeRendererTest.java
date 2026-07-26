package mekanism.client.render;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SelectionWireframeRendererTest {

    @Test
    void reusesPreparedGeometryForStableOutlineArray() {
        JsonModelSelectionBoxCache.OutlineBox[] outlines = createUnitCubeOutlines();

        SelectionWireframeRenderer.PreparedWireframe first = SelectionWireframeRenderer.getOrPrepareWireframe(outlines);
        SelectionWireframeRenderer.PreparedWireframe second = SelectionWireframeRenderer.getOrPrepareWireframe(outlines);

        assertSame(first, second);
        assertEquals(12, first.segmentCount());
    }

    @Test
    void doesNotSharePreparedGeometryBetweenDistinctOutlineArrays() {
        SelectionWireframeRenderer.PreparedWireframe first = SelectionWireframeRenderer.getOrPrepareWireframe(createUnitCubeOutlines());
        SelectionWireframeRenderer.PreparedWireframe second = SelectionWireframeRenderer.getOrPrepareWireframe(createUnitCubeOutlines());

        assertNotSame(first, second);
        assertEquals(first.segmentCount(), second.segmentCount());
    }

    private static JsonModelSelectionBoxCache.OutlineBox[] createUnitCubeOutlines() {
        return JsonModelSelectionBoxCache.toOutlineBoxes(new AxisAlignedBB[]{new AxisAlignedBB(0, 0, 0, 1, 1, 1)});
    }
}
