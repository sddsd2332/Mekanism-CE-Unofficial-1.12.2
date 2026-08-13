package mekanism.qioprocessing.common.content.scheduling;

import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOExecutionSlotSchedulerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void grantsAreBoundedAndDoNotScaleWithTheConfiguredMaximum() {
        QIOProcessingNetworkData network = network();
        for (int i = 0; i < 20; i++) {
            network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, emptyPlan(), 100);
        }

        int granted = QIOExecutionSlotScheduler.grantAvailableSlots(network,
              Integer.MAX_VALUE, 3, 20, 100);

        assertEquals(3, granted);
        assertEquals(3, network.getActiveExecutionSlotCount());
        assertEquals(17, network.getExecutionSlotCandidates().size());
    }

    @Test
    void priorityAgesFromContinuousRunnableTime() {
        QIOProcessingNetworkData network = network();
        QIOCraftingJob olderLowPriority = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        for (int i = 0; i < 20; i++) {
            network.advanceSchedulerClock();
        }
        QIOCraftingJob newerHighPriority = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 5, 0, emptyPlan(), 10);

        assertEquals(1, QIOExecutionSlotScheduler.grantAvailableSlots(network,
              1, 1, 1, 100));

        assertEquals(QIOCraftingJobState.RESERVING, olderLowPriority.getState());
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT,
              newerHighPriority.getState());
    }

    @Test
    void neverDispatchedFifoOrderSurvivesRestart() throws Exception {
        QIOProcessingNetworkData network = network();
        UUID firstId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        QIOCraftingJob first = network.createJob(firstId, QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        QIOCraftingJob second = network.createJob(secondId, QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        assertEquals(1, QIOExecutionSlotScheduler.grantAvailableSlots(restored,
              1, 1, 20, 100));

        assertEquals(QIOCraftingJobState.RESERVING, restored.getJob(first.getJobId()).getState());
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT,
              restored.getJob(second.getJobId()).getState());
    }

    @Test
    void releasedJobMovesBehindNeverDispatchedPeerAcrossRestart() throws Exception {
        QIOProcessingNetworkData network = network();
        QIOCraftingJob first = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        QIOCraftingJob second = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        QIOExecutionSlotScheduler.grantAvailableSlots(network, 1, 1, 20, 100);
        assertTrue(network.releaseExecutionSlot(first.getJobId(),
              QIOCraftingJobState.WAITING_EXECUTION_SLOT));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        QIOExecutionSlotScheduler.grantAvailableSlots(restored, 1, 1, 20, 100);

        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT,
              restored.getJob(first.getJobId()).getState());
        assertEquals(QIOCraftingJobState.RESERVING,
              restored.getJob(second.getJobId()).getState());
        assertEquals(0, restored.getJob(first.getJobId()).getLastDispatchSequence());
        assertEquals(1, restored.getJob(second.getJobId()).getLastDispatchSequence());
    }

    @Test
    void loweringTheLimitDoesNotPreemptExistingSlots() {
        QIOProcessingNetworkData network = network();
        QIOCraftingJob first = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        QIOCraftingJob second = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        QIOCraftingJob waiting = network.createJob(QIOCraftingJobSource.MANUAL,
              null, 0, 0, emptyPlan(), 10);
        assertEquals(2, QIOExecutionSlotScheduler.grantAvailableSlots(network,
              2, 10, 20, 100));

        assertEquals(0, QIOExecutionSlotScheduler.grantAvailableSlots(network,
              1, 10, 20, 100));

        assertEquals(2, network.getActiveExecutionSlotCount());
        assertFalse(first.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT);
        assertFalse(second.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT);
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, waiting.getState());
    }

    private static QIOProcessingNetworkData network() {
        return new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("scheduler", null, SecurityMode.PUBLIC));
    }

    private static QIOCraftPlan emptyPlan() {
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(0, 0, 0, 0, 0, 0, 0, 0),
              PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT)), 1,
              Collections.emptyMap());
    }
}
