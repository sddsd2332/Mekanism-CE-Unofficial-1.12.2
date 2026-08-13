package mekanism.qioprocessing.common.order;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOOrderPreviewTest {

    @Test
    void preparationRetryAndCompletionAccumulateEndToEndTimings() {
        UUID owner = UUID.randomUUID();
        QIOOrderPreview preview = new QIOOrderPreview(UUID.randomUUID(), owner,
              new QIOFrequencyReference(UUID.randomUUID(), "test", owner,
                    SecurityMode.PRIVATE, owner),
              PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
                    "test:output", 0, null), 1, 0, 10, 100, false,
              System.nanoTime(), 11);

        assertEquals(QIOOrderPreview.State.PREPARING, preview.getState());
        preview.bindTask(UUID.randomUUID());
        preview.beginPlanning();
        assertEquals(QIOOrderPreview.State.PLANNING, preview.getState());
        assertTrue(preview.retry(13, 17, 19, 41));
        assertEquals(QIOOrderPreview.State.PREPARING, preview.getState());
        assertEquals(2, preview.getPlanningAttempt());

        preview.bindTask(UUID.randomUUID());
        preview.beginPlanning();
        preview.complete(QIOPlanningResult.failure(QIOPlanningResult.Status.NO_ROUTE,
              "test", 0, 0), 23, 29, 61);

        assertEquals(QIOOrderPreview.State.FAILED, preview.getState());
        assertEquals(24, preview.getMainThreadPreparationNanos());
        assertEquals(40, preview.getRoutePreparationNanos());
        assertEquals(48, preview.getPlanningNanos());
        assertEquals(14, preview.getSchedulingNanos());
        assertTrue(preview.getTotalNanos() >= 0);
    }
}
