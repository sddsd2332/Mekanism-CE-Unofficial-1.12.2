package mekanism.qioprocessing.common.execution;

import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QIOProcessingPipelineDispatchTest {

    private static PortableResourceDescriptor iron;
    private static PortableResourceDescriptor gold;
    private static PortableResourceDescriptor diamond;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
        gold = PortableResourceDescriptor.item(new ItemStack(Items.GOLD_INGOT));
        diamond = PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND));
    }

    @Test
    void downstreamCanDispatchAsSoonAsItsInputsExist() {
        QIOPlanStep producer = new QIOPlanStep(1, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:producer", "test:producer", "iron", "producer", 4,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOPlanStep consumer = new QIOPlanStep(2, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:consumer", "test:consumer", "gold", "consumer", 4,
              Collections.singletonMap(gold, 1L), Collections.singletonMap(diamond, 1L),
              Collections.emptyMap(), Collections.singletonList(1L));
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), diamond, 4,
              Collections.singletonMap(iron, 4L), Arrays.asList(producer, consumer));
        QIOCraftingJob job = new QIOCraftingJob(UUID.randomUUID(),
              QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 0, 0, plan);
        QIOJobBuffer buffer = new QIOJobBuffer(job.getJobId());

        assertNull(QIOProcessingExecutionService.nextRunnableStep(job, buffer));

        buffer.add(QIOJobBuffer.Compartment.PRODUCED, gold, 1);

        assertEquals(consumer.getNodeId(),
              QIOProcessingExecutionService.nextRunnableStep(job, buffer).getNodeId());
    }

    @Test
    void equalPriorityProvidersRotateWithoutBypassingHigherPriority() {
        Candidate highA = new Candidate(new UUID(0, 1), 10);
        Candidate highB = new Candidate(new UUID(0, 2), 10);
        Candidate low = new Candidate(new UUID(0, 3), 0);
        List<Candidate> candidates = Arrays.asList(low, highB, highA);
        Map<Long, UUID> cursors = new LinkedHashMap<>();

        List<Candidate> first = QIOProcessingExecutionService.orderProvidersRoundRobin(
              candidates, candidate -> candidate.id, candidate -> candidate.priority,
              cursors::get);
        assertEquals(Arrays.asList(highA, highB, low), first);

        cursors.put(10L, highA.id);
        List<Candidate> second = QIOProcessingExecutionService.orderProvidersRoundRobin(
              candidates, candidate -> candidate.id, candidate -> candidate.priority,
              cursors::get);
        assertEquals(Arrays.asList(highB, highA, low), second);
    }

    @Test
    void removedRoundRobinProviderFallsBackToStableOrder() {
        Candidate first = new Candidate(new UUID(0, 1), 4);
        Candidate second = new Candidate(new UUID(0, 2), 4);
        List<Candidate> ordered = QIOProcessingExecutionService.orderProvidersRoundRobin(
              Arrays.asList(second, first), candidate -> candidate.id,
              candidate -> candidate.priority, priority -> new UUID(0, 99));

        assertEquals(Arrays.asList(first, second), ordered);
    }

    @Test
    void disabledConcurrencyLimitUsesFullOnlineCapacity() {
        assertEquals(100_000, QIOProcessingExecutionService.effectiveConcurrencyLimit(
              false, 8, 100_000));
        assertEquals(8, QIOProcessingExecutionService.effectiveConcurrencyLimit(
              true, 8, 100_000));
        assertEquals(3, QIOProcessingExecutionService.effectiveConcurrencyLimit(
              true, 8, 3));
    }

    @Test
    void cappedProviderDoesNotHideRunnableStepFromOtherProvider() {
        QIOPlanStep workbench = new QIOPlanStep(1, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:workbench", "test:workbench", "iron", "workbench", 1,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOPlanStep machine = new QIOPlanStep(2, QIOPlanStep.ProviderKind.MEKANISM,
              "test:machine", "test:machine", "test:machine", "iron", "machine", 1,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(diamond, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), diamond, 1,
              Collections.singletonMap(iron, 2L), Arrays.asList(workbench, machine));
        QIOCraftingJob job = new QIOCraftingJob(UUID.randomUUID(),
              QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 0, 0, plan);
        QIOJobBuffer buffer = new QIOJobBuffer(job.getJobId());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, iron, 2);

        assertEquals(machine.getNodeId(), QIOProcessingExecutionService.nextRunnableStep(
              job, buffer, step -> step.getProviderKind() == QIOPlanStep.ProviderKind.MEKANISM)
              .getNodeId());
    }

    @Test
    void activeOperationsCannotConsumeEveryDispatchOpportunity() {
        assertEquals(64, QIOProcessingExecutionService.activeOperationBudget(128, true, true));
        assertEquals(128, QIOProcessingExecutionService.activeOperationBudget(128, false, true));
        assertEquals(128, QIOProcessingExecutionService.activeOperationBudget(128, true, false));
        assertEquals(1, QIOProcessingExecutionService.activeOperationBudget(1, true, true));
    }

    private static final class Candidate {

        private final UUID id;
        private final long priority;

        private Candidate(UUID id, long priority) {
            this.id = id;
            this.priority = priority;
        }
    }
}
