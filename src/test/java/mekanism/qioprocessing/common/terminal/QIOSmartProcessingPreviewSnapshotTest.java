package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOSmartProcessingPreviewSnapshotTest {

    private static final PortableResourceDescriptor INPUT = PortableResourceDescriptor.named(
          PortableResourceDescriptor.Kind.ITEM, "test:input", 0, null);
    private static final PortableResourceDescriptor OUTPUT = PortableResourceDescriptor.named(
          PortableResourceDescriptor.Kind.ITEM, "test:output", 0, null);

    @Test
    void failureAnalysisRoundTripPreservesTreeErrorsAndMergeState() throws Exception {
        QIOPlanStep step = new QIOPlanStep(7, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:processor", "test:route", "test:recipe", "exact", "signature", 3,
              Collections.singletonMap(INPUT, 2L), Collections.singletonMap(OUTPUT, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOSmartProcessingPreviewSnapshot snapshot = new QIOSmartProcessingPreviewSnapshot(
              UUID.randomUUID(), QIOOrderPreview.State.FAILED,
              QIOPlanningResult.Status.NO_ROUTE, "missing route", OUTPUT, 3, 12, 200,
              4_000_000, 3, 1, Collections.emptyMap(),
              Collections.singletonList(QIOCraftingMonitorPlanEntry.step(step)), false, false,
              new LinkedHashSet<>(Collections.singletonList(7L)),
              new LinkedHashSet<>(Collections.singletonList(INPUT)), false, true,
              1_000_000, 2_000_000, 3_000_000, 7_000_000, 2, null);

        QIOSmartProcessingPreviewSnapshot restored =
              QIOSmartProcessingPreviewSnapshot.read(snapshot.write());

        assertEquals(1, restored.getPlanEntries().size());
        assertEquals(7, restored.getPlanEntries().get(0).getNodeId());
        assertEquals(Collections.singleton(7L), restored.getErrorNodeIds());
        assertEquals(Collections.singleton(INPUT), restored.getErrorResources());
        assertFalse(restored.isRootError());
        assertTrue(restored.isMergeOrder());
        assertEquals(12, restored.getPriority());
        assertEquals(1_000_000, restored.getMainThreadPreparationNanos());
        assertEquals(2_000_000, restored.getRoutePreparationNanos());
        assertEquals(3_000_000, restored.getSchedulingNanos());
        assertEquals(4_000_000, restored.getPlanningNanos());
        assertEquals(7_000_000, restored.getTotalNanos());
        assertEquals(2, restored.getPlanningAttempt());
    }
}
