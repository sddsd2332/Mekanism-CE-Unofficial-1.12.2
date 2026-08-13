package mekanism.qioprocessing.common.terminal;

import mekanism.common.TestBootstrap;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingMonitorServiceTest {

    private static PortableResourceDescriptor iron;
    private static PortableResourceDescriptor gold;

    @BeforeAll
    static void boot() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
        gold = PortableResourceDescriptor.item(new ItemStack(Items.GOLD_INGOT));
    }

    @Test
    void savedPlanRuntimeAndMutationsUseExactRevisions() {
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency);
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 0, plan(), 8);
        QIOProcessingTerminalSession session = session(frequency,
              QIOProcessingTerminalType.CRAFTING_MONITOR, 3);

        QIOPage<QIOCraftingMonitorEntry> page = QIOCraftingMonitorService.getPage(
              session, network, 3, null, 8);
        assertEquals(1, page.getEntries().size());
        QIOCraftingMonitorEntry entry = page.getEntries().get(0);
        assertEquals(job.getJobId(), entry.getEntryId());
        assertEquals(4, entry.getRootAmount());
        assertEquals(1, entry.getPlanRevision());
        assertEquals(0, entry.getBasePriority());

        QIOPage<QIOCraftingMonitorPlanEntry> planPage =
              QIOCraftingMonitorService.getPlanPage(session, network, 3,
                    job.getJobId(), 1, null, 8);
        assertEquals(1, planPage.getEntries().size());
        assertEquals("smelt", planPage.getEntries().get(0).getStep().getRouteId());
        QIOCraftingMonitorRuntimeSnapshot runtime =
              QIOCraftingMonitorService.getRuntimeSnapshot(session, network, 3,
                    job.getJobId(), 1, -1, true, 0,
                    Collections.singletonMap(1L, -1L), 20);
        assertTrue(runtime.isBaseline());
        assertEquals(1, runtime.getNodes().size());
        assertEquals(64, runtime.getConfiguredExecutionSlots());

        assertEquals(QIOCraftingMonitorService.MutationStatus.REVISION_CONFLICT,
              QIOCraftingMonitorService.updatePriority(session, network, 3,
                    job.getJobId(), job.getRuntimeRevision() + 1, 10));
        assertEquals(QIOCraftingMonitorService.MutationStatus.ACCEPTED,
              QIOCraftingMonitorService.updatePriority(session, network, 3,
                    job.getJobId(), job.getRuntimeRevision(), 10));
        assertEquals(10, job.getBasePriority());
        assertEquals(10, network.getCommitment(job.getJobId()).getPriority());

        assertEquals(QIOCraftingMonitorService.CancelStatus.REVISION_CONFLICT,
              QIOCraftingMonitorService.cancel(session, network, 3, job.getJobId(),
                    job.getRuntimeRevision() + 1));
        assertEquals(QIOCraftingMonitorService.CancelStatus.ACCEPTED,
              QIOCraftingMonitorService.cancel(session, network, 3, job.getJobId(),
                    job.getRuntimeRevision()));

        job.transitionTo(mekanism.qioprocessing.common.content.job.QIOCraftingJobState.FAILED);
        assertEquals(QIOCraftingMonitorService.MutationStatus.INVALID_STATE,
              QIOCraftingMonitorService.updatePriority(session, network, 3,
                    job.getJobId(), job.getRuntimeRevision(), 11));
        assertTrue(QIOCraftingMonitorService.getPage(session, network, 3,
              null, 8).getEntries().isEmpty());
    }

    @Test
    void otherTerminalCannotReadMonitor() {
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency);
        assertThrows(SecurityException.class, () -> QIOCraftingMonitorService.getPage(
              session(frequency, QIOProcessingTerminalType.MAINTENANCE, 1),
              network, 1, null, 1));
    }

    @Test
    void jobDirectoryExcludesPassiveOperationsAndKeepsCursorAcrossRuntimeProgress() {
        UUID frequency = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequency);
        network.createJob(QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 0,
              plan(), 8);
        network.createJob(QIOCraftingJobSource.MAINTENANCE, null, 0, 1,
              plan(), 8);
        network.addPassiveOperation(new QIOPassiveOperation(UUID.randomUUID(),
              UUID.randomUUID(), "test:provider", "test:route", "test:recipe", 0,
              0, 0, Collections.singletonMap(iron, UUID.randomUUID()),
              Collections.singletonMap(iron, 1L)));
        QIOProcessingTerminalSession session = session(frequency,
              QIOProcessingTerminalType.CRAFTING_MONITOR, 3);

        QIOPage<QIOCraftingMonitorEntry> first = QIOCraftingMonitorService.getPage(
              session, network, 3, null, 1);
        assertEquals(2, first.getTotalSize());
        assertNotNull(first.getNextCursor());
        QIOCraftingJob progressed = network.getJob(first.getEntries().get(0).getEntryId());
        assertNotNull(progressed);
        assertTrue(network.updateJobPriority(progressed.getJobId(),
              progressed.getRuntimeRevision(), 4));

        QIOPage<QIOCraftingMonitorEntry> second = QIOCraftingMonitorService.getPage(
              session, network, 3, first.getNextCursor(), 1);
        assertEquals(1, second.getOffset());
        assertEquals(2, second.getTotalSize());
        assertNotEquals(first.getEntries().get(0).getEntryId(),
              second.getEntries().get(0).getEntryId());
    }

    private static QIOCraftPlan plan() {
        QIOPlanStep step = new QIOPlanStep(1, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:processor", "smelt", "test:smelt", "exact", "signature", 4,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 1L),
              Collections.emptyMap(), Collections.emptyList());
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(0, 0, 0, 0, 0, 0, 0, 0), gold, 4,
              Collections.singletonMap(iron, 4L), Collections.singletonList(step));
    }

    private static QIOProcessingNetworkData network(UUID frequency) {
        return new QIOProcessingNetworkData(frequency,
              new QIOFrequencyIdentitySnapshot("monitor", null, SecurityMode.PUBLIC));
    }

    private static QIOProcessingTerminalSession session(UUID frequency,
          QIOProcessingTerminalType type, long accessRevision) {
        return new QIOProcessingTerminalSession(UUID.randomUUID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK, type, UUID.randomUUID(), 0,
              frequency, accessRevision);
    }
}
