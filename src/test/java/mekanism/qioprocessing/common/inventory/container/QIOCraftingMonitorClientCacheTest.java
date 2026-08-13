package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorPlanEntry;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeNode;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorRuntimeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOCraftingMonitorService.CancelStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingMonitorClientCacheTest {

    @Test
    void cancellationInvalidatesPageAndKeepsRequestIdentity() {
        UUID nonce = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        QIOCraftingMonitorEntry entry = monitorEntry(job);
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();
        assertTrue(cache.applyPage(nonce, 2, 0, 1,
              Collections.singletonList(entry), null));
        assertTrue(cache.applyCancel(nonce, request, job, CancelStatus.ACCEPTED));
        assertTrue(cache.getEntries().isEmpty());
        assertEquals(request, cache.getLastCancelRequestId());
        assertEquals(1, cache.getCancelGeneration());
    }

    @Test
    void delayedOlderFirstPageCannotReplaceNewerJobs() {
        UUID nonce = UUID.randomUUID();
        QIOCraftingMonitorEntry newest = monitorEntry(UUID.randomUUID());
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();

        assertTrue(cache.applyPage(nonce, 9, 0, 1,
              Collections.singletonList(newest), null));
        assertFalse(cache.applyPage(nonce, 8, 0, 0, Collections.emptyList(), null));
        assertEquals(Collections.singletonList(newest), cache.getEntries());
        assertEquals(9, cache.getSourceRevision());
    }

    @Test
    void planIdentityAndRuntimeGapRequireANewBaseline() {
        UUID nonce = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();
        cache.applyPage(nonce, 2, 0, 1, Collections.singletonList(monitorEntry(job)), null);
        cache.selectDetail(job, 1);
        PortableResourceDescriptor iron = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        QIOPlanStep step = new QIOPlanStep(4, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:processor", "test:route", "test:recipe", "exact", "sig", 3,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(iron, 1L),
              Collections.emptyMap(), Collections.emptyList());
        assertTrue(cache.applyPlanPage(nonce, job, 1, "plan-signature", 7, 0, 1,
              Collections.singletonList(QIOCraftingMonitorPlanEntry.step(step)), null));
        QIOCraftingMonitorRuntimeNode node = QIOCraftingMonitorRuntimeNode.step(
              new QIOStepRuntime(4, 3));
        QIOCraftingMonitorRuntimeSnapshot baseline = snapshot(job, -1, 2, true,
              Collections.singletonList(node));
        assertTrue(cache.applyRuntime(nonce, baseline));
        assertFalse(cache.isRuntimeBaselineRequired());
        assertEquals(2, cache.getDetailRuntimeRevision());
        assertEquals(missingIron(), cache.getRuntimeHeader().getMissingResources());

        QIOCraftingMonitorRuntimeSnapshot skippedRevision = snapshot(job, 3, 4, false,
              Collections.emptyList());
        assertFalse(cache.applyRuntime(nonce, skippedRevision));
        assertTrue(cache.isRuntimeBaselineRequired());
    }

    @Test
    void mutationReceiptsAreSessionBoundAndClearRemovesTheirIdentity() {
        UUID nonce = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();
        assertTrue(cache.applyPage(nonce, 1, 0, 0, Collections.emptyList(), null));

        assertFalse(cache.applyMutation(UUID.randomUUID(), request, job,
              "ACCEPTED"));
        assertTrue(cache.applyMutation(nonce, request, job, "ACCEPTED"));
        assertEquals(1, cache.getMutationGeneration());
        assertEquals(request, cache.getLastMutationRequestId());
        assertEquals(job, cache.getLastMutationJobId());
        assertEquals("ACCEPTED", cache.getLastMutationStatus());

        cache.clear();
        assertNull(cache.getLastMutationRequestId());
        assertNull(cache.getLastMutationJobId());
        assertNull(cache.getLastMutationStatus());
    }

    @Test
    void listInvalidationPreservesSelectedPlanForSplitView() {
        UUID nonce = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();
        assertTrue(cache.applyPage(nonce, 2, 0, 1,
              Collections.singletonList(monitorEntry(job)), null));
        cache.selectDetail(job, 1);
        PortableResourceDescriptor iron = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        QIOPlanStep step = new QIOPlanStep(1, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:processor", "test:route", "test:recipe", "exact", "sig", 1,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(iron, 1L),
              Collections.emptyMap(), Collections.emptyList());
        assertTrue(cache.applyPlanPage(nonce, job, 1, "plan", 3, 0, 1,
              Collections.singletonList(QIOCraftingMonitorPlanEntry.step(step)), null));
        assertTrue(cache.isPlanComplete());

        cache.clearEntries();
        assertTrue(cache.getEntries().isEmpty());
        assertEquals(job, cache.getDetailJobId());
        assertTrue(cache.isPlanComplete());
        assertEquals(1, cache.getPlanEntries().size());
    }

    @Test
    void runtimeRevisionSweepCoversNodesBeyondOneProtocolPacket() {
        UUID nonce = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        QIOCraftingMonitorClientCache cache = new QIOCraftingMonitorClientCache();
        assertTrue(cache.applyPage(nonce, 1, 0, 1,
              Collections.singletonList(monitorEntry(job)), null));
        cache.selectDetail(job, 1);
        PortableResourceDescriptor iron = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);
        List<QIOCraftingMonitorPlanEntry> entries = new ArrayList<>();
        for (int i = 0; i < 1_100; i++) {
            QIOPlanStep step = new QIOPlanStep(i, QIOPlanStep.ProviderKind.WORKBENCH,
                  "test:processor", "test:route", "test:recipe", "exact", "sig", 1,
                  Collections.singletonMap(iron, 1L), Collections.singletonMap(iron, 1L),
                  Collections.emptyMap(), Collections.emptyList());
            entries.add(QIOCraftingMonitorPlanEntry.step(step));
        }
        assertTrue(cache.applyPlanPage(nonce, job, 1, "large-plan", 2, 0,
              entries.size(), entries, null));
        assertEquals(1_024, cache.getKnownNodeRevisions().size());
        List<QIOCraftingMonitorRuntimeNode> firstNodes = new ArrayList<>();
        for (int index = 0; index < 1_024; index++) {
            firstNodes.add(QIOCraftingMonitorRuntimeNode.step(
                  new QIOStepRuntime(index, 1)));
        }
        assertTrue(cache.applyRuntime(nonce, largeSnapshot(job, -1, 2, true,
              0, 1_024, firstNodes)));
        assertTrue(cache.isRuntimeBaselineRequired());
        assertTrue(cache.isRuntimeSweepPending());
        assertEquals(1_024, cache.getRuntimeNodeOffset());
        assertEquals(76, cache.getKnownNodeRevisions().size());

        List<QIOCraftingMonitorRuntimeNode> remainingNodes = new ArrayList<>();
        for (int index = 1_024; index < 1_100; index++) {
            remainingNodes.add(QIOCraftingMonitorRuntimeNode.step(
                  new QIOStepRuntime(index, 1)));
        }
        assertTrue(cache.applyRuntime(nonce, largeSnapshot(job, -1, 3, true,
              1_024, 76, remainingNodes)));
        assertFalse(cache.isRuntimeBaselineRequired());
        assertFalse(cache.isRuntimeSweepPending());
        assertEquals(1_100, cache.getRuntimeNodes().size());
    }

    private static QIOCraftingMonitorRuntimeSnapshot snapshot(UUID job, long base,
          long revision, boolean baseline, java.util.List<QIOCraftingMonitorRuntimeNode> nodes) {
        return new QIOCraftingMonitorRuntimeSnapshot(job, 1, "plan-signature", base,
              revision, baseline, 0, 1, "WAITING_MATERIALS", 0, 1, 0, 10, 3, 0,
              0, 1, missingIron(), false, 0, 0, 64,
              QIOCraftingMonitorRuntimeSnapshot.SlotState.UNREQUESTED,
              null, -1, nodes);
    }

    private static QIOCraftingMonitorRuntimeSnapshot largeSnapshot(UUID job, long base,
          long revision, boolean baseline, int offset, int count,
          List<QIOCraftingMonitorRuntimeNode> nodes) {
        return new QIOCraftingMonitorRuntimeSnapshot(job, 1, "large-plan", base,
              revision, baseline, offset, count, "WAITING_MATERIALS", 0, 1, 0,
              10, 1_100, 0, 0, 1, missingIron(), false, 0, 0, 64,
              QIOCraftingMonitorRuntimeSnapshot.SlotState.UNREQUESTED,
              null, -1, nodes);
    }

    private static Map<PortableResourceDescriptor, Long> missingIron() {
        return Collections.singletonMap(PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null), 1L);
    }

    private static QIOCraftingMonitorEntry monitorEntry(UUID job) {
        return new QIOCraftingMonitorEntry(QIOCraftingMonitorEntry.Kind.JOB, job,
              "MANUAL", "WAITING_MATERIALS", PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null),
              1, 0, 1, 2, 5, 0, 0, 0, 1, false, null, -1, null, null);
    }
}
