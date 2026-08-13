package mekanism.qioprocessing.common.content.job;

import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOJobRuntimeTest {

    private static PortableResourceDescriptor iron;
    private static PortableResourceDescriptor gold;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        iron = PortableResourceDescriptor.item(new ItemStack(Items.IRON_INGOT));
        gold = PortableResourceDescriptor.item(new ItemStack(Items.GOLD_INGOT));
    }

    @Test
    void stepAssignmentsAndProgressSurviveJobPersistence() throws Exception {
        QIOPlanStep step = new QIOPlanStep(4, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:route", "test:recipe", "iron", "signature", 3,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), gold, 3,
              Collections.singletonMap(iron, 3L), Collections.singletonList(step));
        QIOCraftingJob job = new QIOCraftingJob(UUID.randomUUID(), QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 0, 0, plan);
        UUID operation = UUID.randomUUID();
        job.startStepOperation(4, new QIOOperationAssignment(operation,
              QIOPlanStep.ProviderKind.WORKBENCH, UUID.randomUUID(), 2, 10));
        assertTrue(job.updateStepOperation(4, operation,
              QIOOperationAssignment.State.PROCESSING, 3, 20, null));
        long progressRevision = job.getRuntimeRevision();
        assertFalse(job.updateStepOperation(4, operation,
              QIOOperationAssignment.State.PROCESSING, 3, 20, null));
        assertEquals(progressRevision, job.getRuntimeRevision());
        assertTrue(job.requestCancellation());

        QIOCraftingJob restored = QIOCraftingJob.read(job.write());

        QIOStepRuntime runtime = restored.getStepRuntime(4);
        assertNotNull(runtime);
        assertTrue(restored.isCancellationRequested());
        assertFalse(restored.requestCancellation());
        assertEquals(2, runtime.getRemainingOperations());
        assertEquals(3, runtime.getOperation(operation).getCurrentTick());
        restored.completeStepOperation(4, operation);
        assertEquals(1, restored.getStepRuntime(4).getCompletedOperations());
        assertFalse(restored.areAllStepsComplete());
    }

    @Test
    void jobBufferStagesInputsAndReturnsWithoutDuplicatingOwnership() {
        QIOJobBuffer buffer = new QIOJobBuffer(UUID.randomUUID());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, iron, 2);
        buffer.add(QIOJobBuffer.Compartment.PRODUCED, iron, 3);

        buffer.stageMachineInput(Collections.singletonMap(iron, 4L));
        assertEquals(4, buffer.get(QIOJobBuffer.Compartment.IN_PROCESS_RETURN, iron));
        assertEquals(1, buffer.get(QIOJobBuffer.Compartment.RESERVED, iron));
        assertEquals(0, buffer.get(QIOJobBuffer.Compartment.PRODUCED, iron));
        buffer.commitMachineInput(Collections.singletonMap(iron, 4L));
        assertEquals(1, buffer.getReturnableResources().get(iron));

        buffer.stageReturn(iron, 1);
        assertTrue(buffer.getReturnableResources().isEmpty());
        assertEquals(1, buffer.get(QIOJobBuffer.Compartment.RETURNING, iron));
        buffer.commitReturn(iron, 1);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void batchedLaneCountsEveryRealRecipeOperation() throws Exception {
        QIOPlanStep step = new QIOPlanStep(7, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:route", "test:recipe", "iron", "signature", 5,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 1L),
              Collections.emptyMap(), Collections.emptyList());
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), gold, 5,
              Collections.singletonMap(iron, 5L), Collections.singletonList(step));
        QIOCraftingJob job = new QIOCraftingJob(UUID.randomUUID(),
              QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 0, 0, plan);
        UUID operation = UUID.randomUUID();
        job.startStepOperation(7, new QIOOperationAssignment(operation,
              QIOPlanStep.ProviderKind.WORKBENCH, UUID.randomUUID(), 0, 4, 10));

        assertEquals(1, job.getStepRuntime(7).getRemainingOperations());
        QIOCraftingJob restored = QIOCraftingJob.read(job.write());
        assertEquals(4, restored.getStepRuntime(7).getOperation(operation)
              .getOperationCount());
        restored.completeStepOperation(7, operation);
        assertEquals(4, restored.getStepRuntime(7).getCompletedOperations());
        assertEquals(1, restored.getStepRuntime(7).getRemainingOperations());
    }

    @Test
    void activeAssignmentsAreNotCappedAtLegacyLimit() {
        int assignments = 65_537;
        QIOStepRuntime runtime = new QIOStepRuntime(1, assignments);
        UUID device = new UUID(1, 0);
        for (int index = 0; index < assignments; index++) {
            runtime.start(new QIOOperationAssignment(new UUID(0, index + 1L),
                  QIOPlanStep.ProviderKind.WORKBENCH, device, index, 0));
        }

        assertEquals(assignments, runtime.getActiveAssignmentCount());
        assertEquals(0, runtime.getRemainingOperations());
    }

    @Test
    void condensedCycleRoundCursorSurvivesJobPersistence() throws Exception {
        QIOPlanStep forward = new QIOPlanStep(11, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:forward", "test:forward", "exact", "forward", 4,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 2L),
              Collections.emptyMap(), Collections.emptyList());
        QIOPlanStep reverse = new QIOPlanStep(12, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:reverse", "test:reverse", "exact", "reverse", 4,
              Collections.singletonMap(gold, 1L), Collections.singletonMap(iron, 1L),
              Collections.emptyMap(), Collections.emptyList());
        Map<Long, Long> quotas = new LinkedHashMap<>();
        quotas.put(11L, 1L);
        quotas.put(12L, 1L);
        QIOCyclePlanNode cycle = new QIOCyclePlanNode(10, 4, quotas,
              Arrays.asList(11L, 12L), Collections.singletonMap(iron, 1L),
              Collections.singletonMap(gold, 1L));
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), gold, 4,
              Collections.singletonMap(iron, 1L), Arrays.asList(forward, reverse),
              Collections.singletonList(cycle));
        QIOCraftingJob job = new QIOCraftingJob(UUID.randomUUID(),
              QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 0, 0, 0, plan);

        assertEquals(1, job.getCycleDispatchAllowance(11));
        assertEquals(0, job.getCycleDispatchAllowance(12));
        UUID first = UUID.randomUUID();
        job.startStepOperation(11, new QIOOperationAssignment(first,
              QIOPlanStep.ProviderKind.WORKBENCH, UUID.randomUUID(), 0, 1, 0));
        assertEquals(0, job.getCycleDispatchAllowance(11));
        job.completeStepOperation(11, first);

        QIOCraftingJob restored = QIOCraftingJob.read(job.write());
        QIOCycleRuntime runtime = restored.getCycleRuntimes().get(10L);
        assertNotNull(runtime);
        assertEquals(0, runtime.getCurrentRound());
        assertEquals(Collections.singletonMap(11L, 1L).get(11L),
              runtime.getCompletedThisRound().get(11L));
        assertEquals(1, restored.getCycleDispatchAllowance(12));
        UUID second = UUID.randomUUID();
        restored.startStepOperation(12, new QIOOperationAssignment(second,
              QIOPlanStep.ProviderKind.WORKBENCH, UUID.randomUUID(), 0, 1, 1));
        restored.completeStepOperation(12, second);
        assertEquals(1, restored.getCycleRuntimes().get(10L).getCurrentRound());
        assertEquals(1, restored.getCycleDispatchAllowance(11));
    }

    @Test
    void cycleRestoreRejectsCountsThatAreNotASchedulePrefix() {
        QIOCraftingJob job = cycleJob();
        NBTTagCompound stored = job.write();
        NBTTagCompound cycle = stored.getTagList("cycleRuntimes", NBT.TAG_COMPOUND)
              .getCompoundTagAt(0);
        NBTTagList members = cycle.getTagList("members", NBT.TAG_COMPOUND);
        members.getCompoundTagAt(0).setLong("completed", 0);
        members.getCompoundTagAt(1).setLong("completed", 1);

        assertThrows(Exception.class, () -> QIOCraftingJob.read(stored));
    }

    @Test
    void cycleRestoreRejectsProgressThatDisagreesWithItsStepRuntime() {
        QIOCraftingJob job = cycleJob();
        NBTTagCompound stored = job.write();
        NBTTagCompound cycle = stored.getTagList("cycleRuntimes", NBT.TAG_COMPOUND)
              .getCompoundTagAt(0);
        cycle.getTagList("members", NBT.TAG_COMPOUND).getCompoundTagAt(0)
              .setLong("completed", 1);

        assertThrows(Exception.class, () -> QIOCraftingJob.read(stored));
    }

    private static QIOCraftingJob cycleJob() {
        QIOPlanStep forward = new QIOPlanStep(11, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:forward", "test:forward", "exact", "forward", 2,
              Collections.singletonMap(iron, 1L), Collections.singletonMap(gold, 2L),
              Collections.emptyMap(), Collections.emptyList());
        QIOPlanStep reverse = new QIOPlanStep(12, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:workbench", "test:reverse", "test:reverse", "exact", "reverse", 2,
              Collections.singletonMap(gold, 1L), Collections.singletonMap(iron, 1L),
              Collections.emptyMap(), Collections.emptyList());
        Map<Long, Long> quotas = new LinkedHashMap<>();
        quotas.put(11L, 1L);
        quotas.put(12L, 1L);
        QIOCyclePlanNode cycle = new QIOCyclePlanNode(10, 2, quotas,
              Arrays.asList(11L, 12L), Collections.singletonMap(iron, 1L),
              Collections.singletonMap(gold, 1L));
        QIOCraftPlan plan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(1, 1, 1, 1, 1, 1, 1, 1), gold, 2,
              Collections.singletonMap(iron, 1L), Arrays.asList(forward, reverse),
              Collections.singletonList(cycle));
        return new QIOCraftingJob(UUID.randomUUID(), QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 0, 0, plan);
    }
}
