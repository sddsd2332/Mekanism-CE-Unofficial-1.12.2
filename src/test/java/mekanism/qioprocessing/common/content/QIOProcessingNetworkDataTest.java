package mekanism.qioprocessing.common.content;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOResourceClaim;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIODriveStorage;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.QIODriveTier;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;
import mekanism.qioprocessing.common.content.material.QIOPlanReassignmentCoordinator;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.content.transfer.QIOReservationCoordinator;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingNetworkDataTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private File worldDirectory;
    private TestHolder holder;
    private QIOFrequency frequency;

    @BeforeEach
    void setup() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-processing-network-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        holder = new TestHolder(new ItemStack(new TestDriveItem()));
        frequency = new QIOFrequency("processing", null, SecurityMode.PUBLIC);
        frequency.addHolder(holder);
        frequency.refresh();
    }

    @AfterEach
    void cleanup() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void partialClaimIsPersistedThenIncrementallyCompletedBeforeSlotAcquisition() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(6, frequency.massInsert(stone, 6, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot(frequency.getName(), null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, UUID.randomUUID(),
              20, 100, plan(input, 10), 100);
        AtomicInteger persistenceBarriers = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, persistenceBarriers);

        QIOMaterialClaimCoordinator.RefreshResult partial = QIOMaterialClaimCoordinator.refresh(network,
              job.getJobId(), view, ignored -> {
                  assertNotNull(network.write());
                  persistenceBarriers.incrementAndGet();
              });

        assertEquals(QIOMaterialClaimCoordinator.Outcome.PARTIALLY_COMMITTED, partial.getOutcome());
        assertEquals(6, network.getCommitment(job.getJobId()).getCommittedAmounts().get(input));
        assertEquals(4, network.getCommitment(job.getJobId()).getMissingAmounts().get(input));
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
        assertEquals(job.getJobId(), network.getWaitingJobs(input).get(0).getJobId());
        assertTrue(persistenceBarriers.get() >= 2);

        assertEquals(4, frequency.massInsert(stone, 4, Action.EXECUTE));
        QIOMaterialClaimCoordinator.RefreshResult complete = QIOMaterialClaimCoordinator.refresh(network,
              job.getJobId(), view, ignored -> persistenceBarriers.incrementAndGet());

        assertEquals(QIOMaterialClaimCoordinator.Outcome.FULLY_COMMITTED, complete.getOutcome());
        assertEquals(10, network.getCommitment(job.getJobId()).getCommittedAmounts().get(input));
        assertTrue(network.getCommitment(job.getJobId()).getMissingAmounts().isEmpty());
        assertTrue(network.getWaitingJobs(input).isEmpty());
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());

        assertNotNull(network.acquireExecutionSlot(job.getJobId(), 1));
        assertEquals(QIOCraftingJobState.RESERVING, job.getState());
        QIOCraftingJob queued = network.createJob(QIOCraftingJobSource.MAINTENANCE, null, 0,
              101, plan(input, 0), 100);
        assertNull(network.acquireExecutionSlot(queued.getJobId(), 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, queued.getState());

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency.getFrequencyUUID());
        assertEquals(2, restored.getJobs().size());
        assertEquals(1, restored.getActiveExecutionSlots().size());
        assertEquals(job.getExecutionSlotToken(), restored.getJob(job.getJobId()).getExecutionSlotToken());
    }

    @Test
    void emptyExternalRequirementCompletesWithoutCreatingAnEmptyClaimMutation() throws Exception {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    "empty-claim", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 9, 0, plan(input, 0), 10);
        TestStorageView view = new TestStorageView(frequency, new AtomicInteger());

        QIOMaterialClaimCoordinator.RefreshResult result =
              QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                    ignored -> { });

        assertEquals(QIOMaterialClaimCoordinator.Outcome.FULLY_COMMITTED,
              result.getOutcome());
        assertNull(network.getCommitment(job.getJobId()).getPendingMutation());
        assertTrue(network.getCommitment(job.getJobId()).getCommittedAmounts().isEmpty());
        assertNull(frequency.getResourceClaim(
              network.getCommitment(job.getJobId()).getClaimId()));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
    }

    @Test
    void settledTerminalJobCanBeRemovedWithoutLeavingPersistedPlanState() throws Exception {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    "settled", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 0, plan(input, 0), 10);

        assertNotNull(network.acquireExecutionSlot(job.getJobId(), 1));
        network.completeEmptyReservation(job.getJobId(), 0);
        assertTrue(network.releaseExecutionSlot(job.getJobId(),
              QIOCraftingJobState.COMPLETED));
        assertEquals(QIOMaterialCommitment.State.CONSUMED,
              network.getCommitment(job.getJobId()).getState());

        assertTrue(network.removeSettledTerminalJob(job.getJobId()));
        assertNull(network.getJob(job.getJobId()));
        assertNull(network.getCommitment(job.getJobId()));
        assertNull(network.getJobBuffer(job.getJobId()));
        assertTrue(network.getJobs().isEmpty());

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency.getFrequencyUUID());
        assertTrue(restored.getJobs().isEmpty());
    }

    @Test
    void waitingIndexIsRebuiltButSlotOwnershipMismatchRejectsTheFile() throws Exception {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData waitingNetwork = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("waiting", null, SecurityMode.PUBLIC));
        QIOCraftingJob waiting = waitingNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0,
              0, plan(input, 3), 10);
        NBTTagCompound repairable = waitingNetwork.write();
        repairable.setTag("waitingMaterialIndex", new NBTTagList());

        QIOProcessingNetworkData repaired = QIOProcessingNetworkData.read(repairable,
              frequency.getFrequencyUUID());
        assertTrue(repaired.wasRepairedOnLoad());
        assertEquals(waiting.getJobId(), repaired.getWaitingJobs(input).get(0).getJobId());

        QIOProcessingNetworkData slottedNetwork = new QIOProcessingNetworkData(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("slots", null, SecurityMode.PUBLIC));
        QIOCraftingJob slotted = slottedNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0,
              0, plan(input, 0), 10);
        slottedNetwork.acquireExecutionSlot(slotted.getJobId(), Integer.MAX_VALUE);
        NBTTagCompound corrupted = slottedNetwork.write();
        corrupted.getTagList("jobs", 10).getCompoundTagAt(0)
              .setString("executionSlotToken", UUID.randomUUID().toString());

        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(corrupted, slottedNetwork.getFrequencyUUID()));
    }

    @Test
    void developmentSchemasAndMissingCurrentDirectoriesAreRejected() {
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("strict", null, SecurityMode.PUBLIC));
        NBTTagCompound oldSchema = network.write();
        oldSchema.setInteger("networkDataSchemaVersion", 1);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(oldSchema, frequency.getFrequencyUUID()));

        NBTTagCompound schemaFive = network.write();
        schemaFive.setInteger("networkDataSchemaVersion", 5);
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(schemaFive, frequency.getFrequencyUUID()));

        NBTTagCompound missingProviderCatalog = network.write();
        missingProviderCatalog.removeTag("providerCatalog");
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(missingProviderCatalog,
                    frequency.getFrequencyUUID()));

        NBTTagCompound missingWorkbenchConfiguration = network.write();
        missingWorkbenchConfiguration.removeTag("workbenchConfiguration");
        assertThrows(QIOProcessingDataException.class, () ->
              QIOProcessingNetworkData.read(missingWorkbenchConfiguration,
                    frequency.getFrequencyUUID()));
    }

    @Test
    void currentSchemaPersistsFrequencyLocalWorkbenchConfiguration() throws Exception {
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("workbench", null, SecurityMode.PUBLIC));
        String signature = String.format("%064x", 1);
        assertTrue(network.getWorkbenchConfiguration().setRecipeEnabled(
              "test:persisted", signature, false));

        NBTTagCompound persisted = network.write();
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              persisted.getInteger("networkDataSchemaVersion"));
        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(persisted,
              frequency.getFrequencyUUID());
        assertEquals(network.getWorkbenchConfiguration().getConfigUUID(),
              restored.getWorkbenchConfiguration().getConfigUUID());
        assertEquals(network.getWorkbenchConfiguration().getRevision(),
              restored.getWorkbenchConfiguration().getRevision());
        assertFalse(restored.getWorkbenchConfiguration().isRecipeEnabled(
              "test:persisted", signature));
    }

    @Test
    void currentSchemaWithoutConfigurationExchangeFieldRemainsCompatible() throws Exception {
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    "configuration-compat", null, SecurityMode.PUBLIC));
        NBTTagCompound legacyCurrent = network.write();
        legacyCurrent.removeTag("configurationExchanges");

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(legacyCurrent,
              frequency.getFrequencyUUID());

        assertTrue(restored.getConfigurationExchanges().isEmpty());
        assertTrue(restored.write().hasKey("configurationExchanges", 9));
    }

    @Test
    void persistedClaimIntentReplaysAfterCoreAppliedBeforeModuleResultSave() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("replay", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 5), 10);
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<NBTTagCompound> persistedIntent = new AtomicReference<>();
        TestStorageView view = new TestStorageView(frequency, saves);

        assertThrows(java.io.IOException.class, () -> QIOMaterialClaimCoordinator.refresh(network,
              job.getJobId(), view, current -> {
                  int save = saves.incrementAndGet();
                  if (save == 1) {
                      persistedIntent.set(current.write().copy());
                  } else {
                      throw new java.io.IOException("simulated result save failure");
                  }
              }));
        long appliedClaimRevision = frequency.getClaimRevision();
        assertNotNull(persistedIntent.get());

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(persistedIntent.get(),
              frequency.getFrequencyUUID());
        QIOMaterialClaimCoordinator.RefreshResult replay = QIOMaterialClaimCoordinator.refresh(restored,
              job.getJobId(), view, ignored -> saves.incrementAndGet());

        assertEquals(QIOMaterialClaimCoordinator.Outcome.FULLY_COMMITTED, replay.getOutcome());
        assertEquals(appliedClaimRevision, frequency.getClaimRevision());
        assertNull(restored.getCommitment(job.getJobId()).getPendingMutation());
        assertEquals(5, restored.getCommitment(job.getJobId()).getCommittedAmounts().get(input));
    }

    @Test
    void persistedReplanIntentReplaysAtomicClaimReassignment() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("replan-replay", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 5, 1), 10);
        AtomicInteger saves = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, saves);
        QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
              ignored -> saves.incrementAndGet());
        UUID oldClaimId = network.getCommitment(job.getJobId()).getClaimId();

        network.requestJobReplan(job.getJobId(), plan(input, 3, 2));
        assertEquals(QIOCraftingJobState.REPLAN_REQUIRED, job.getState());
        UUID targetClaimId = job.getRevisionTransition().getTargetClaimId();
        QIOProcessingNetworkData durableTransition = QIOProcessingNetworkData.read(network.write(),
              frequency.getFrequencyUUID());
        assertEquals(2, durableTransition.getJob(job.getJobId()).getRevisionTransition()
              .getPendingPlan().getRevision());

        AtomicReference<NBTTagCompound> persistedIntent = new AtomicReference<>();
        AtomicInteger transitionSaves = new AtomicInteger();
        assertThrows(java.io.IOException.class, () ->
              QIOPlanReassignmentCoordinator.advance(network, job.getJobId(), view, current -> {
                  if (transitionSaves.incrementAndGet() == 1) {
                      persistedIntent.set(current.write().copy());
                  } else {
                      throw new java.io.IOException("simulated replan result save failure");
                  }
              }));
        assertNull(frequency.getResourceClaim(oldClaimId));
        assertNotNull(frequency.getResourceClaim(targetClaimId));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(persistedIntent.get(),
              frequency.getFrequencyUUID());
        assertEquals(QIOPlanReassignmentCoordinator.Outcome.ACTIVATED,
              QIOPlanReassignmentCoordinator.advance(restored, job.getJobId(), view,
                    ignored -> transitionSaves.incrementAndGet()));
        QIOCraftingJob activated = restored.getJob(job.getJobId());
        assertEquals(2, activated.getActivePlan().getRevision());
        assertNull(activated.getRevisionTransition());
        assertEquals(targetClaimId, restored.getCommitment(job.getJobId()).getClaimId());
        assertEquals(3, restored.getCommitment(job.getJobId()).getCommittedAmounts().get(input));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, activated.getState());
    }

    @Test
    void cancellingAnUnreservedWaitingJobReleasesItsCoreClaimExactlyOnce() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(4, frequency.massInsert(stone, 4, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("cancel", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 10), 10);
        AtomicInteger saves = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, saves);
        QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
              ignored -> saves.incrementAndGet());
        assertEquals(0, frequency.getAvailable(
              frequency.getExternalStorageSnapshot().getEntries().get(0).getResourceUUID()));

        QIOMaterialClaimCoordinator.CancellationOutcome outcome =
              QIOMaterialClaimCoordinator.cancelUnreserved(network, job.getJobId(), view,
                    ignored -> saves.incrementAndGet());

        assertEquals(QIOMaterialClaimCoordinator.CancellationOutcome.CANCELLED, outcome);
        assertEquals(QIOCraftingJobState.CANCELLED, job.getState());
        assertNull(frequency.getResourceClaim(network.getCommitment(job.getJobId()).getClaimId()));
        assertEquals(4, frequency.getAvailable(
              frequency.getExternalStorageSnapshot().getEntries().get(0).getResourceUUID()));
        assertTrue(network.getWaitingJobs(input).isEmpty());
    }

    @Test
    void cancellationIntentPersistsAndCannotAcquireANewExecutionSlot() throws Exception {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
              "cancel-intent", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL,
              UUID.randomUUID(), 0, 0, plan(input, 0), 10);

        assertEquals(1, network.getExecutionSlotCandidates().size());
        assertTrue(network.requestJobCancellation(job.getJobId()));
        assertTrue(network.getExecutionSlotCandidates().isEmpty());
        assertEquals(job.getJobId(), network.getCancellationCandidates().get(0).getJobId());
        assertThrows(IllegalStateException.class, () ->
              network.acquireExecutionSlot(job.getJobId(), 8));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency.getFrequencyUUID());
        assertTrue(restored.getJob(job.getJobId()).isCancellationRequested());
        assertTrue(restored.getExecutionSlotCandidates().isEmpty());
    }

    @Test
    void unchangedPartialClaimRefreshDoesNotRewriteNetworkState() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(2, frequency.massInsert(stone, 2, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("unchanged", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 5), 10);
        AtomicInteger saves = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, saves);
        assertEquals(QIOMaterialClaimCoordinator.Outcome.PARTIALLY_COMMITTED,
              QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                    ignored -> saves.incrementAndGet()).getOutcome());
        int savesAfterClaim = saves.get();

        assertEquals(QIOMaterialClaimCoordinator.Outcome.PARTIALLY_COMMITTED,
              QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                    ignored -> saves.incrementAndGet()).getOutcome());

        assertEquals(savesAfterClaim, saves.get());
        assertEquals(2, network.getCommitment(job.getJobId()).getCommittedAmounts().get(input));
    }

    @Test
    void reservationReplaysAfterCoreDebitEvenWhenTheResourceLeavesTheSnapshot() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("reservation", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 5), 10);
        AtomicInteger claimSaves = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, claimSaves);
        assertEquals(QIOMaterialClaimCoordinator.Outcome.FULLY_COMMITTED,
              QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                    ignored -> claimSaves.incrementAndGet()).getOutcome());
        assertNotNull(network.acquireExecutionSlot(job.getJobId(), 8));

        AtomicInteger reservationSaves = new AtomicInteger();
        AtomicReference<NBTTagCompound> persistedPrepared = new AtomicReference<>();
        assertThrows(java.io.IOException.class, () -> QIOReservationCoordinator.reserve(network,
              job.getJobId(), view, current -> {
                  int save = reservationSaves.incrementAndGet();
                  if (save == 1) {
                      persistedPrepared.set(current.write().copy());
                  } else {
                      throw new java.io.IOException("simulated source receipt save failure");
                  }
              }));
        assertEquals(0, frequency.getStored(stone));
        assertNull(frequency.getResourceClaim(network.getCommitment(job.getJobId()).getClaimId()));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(persistedPrepared.get(),
              frequency.getFrequencyUUID());
        QIOReservationCoordinator.Outcome outcome = QIOReservationCoordinator.reserve(restored,
              job.getJobId(), view, ignored -> reservationSaves.incrementAndGet());

        assertEquals(QIOReservationCoordinator.Outcome.READY, outcome);
        assertEquals(QIOCraftingJobState.READY, restored.getJob(job.getJobId()).getState());
        assertEquals(QIOMaterialCommitment.State.CONSUMED,
              restored.getCommitment(job.getJobId()).getState());
        assertEquals(5, restored.getJobBuffer(job.getJobId())
              .get(QIOJobBuffer.Compartment.RESERVED, input));
        assertTrue(restored.getDurableTransfers().isEmpty());
        assertEquals(1, restored.getActiveExecutionSlots().size());
        assertEquals(4, reservationSaves.get());

        restored.transitionJobState(job.getJobId(), QIOCraftingJobState.PROCESSING);

        QIOProcessingNetworkData secondReload = QIOProcessingNetworkData.read(restored.write(),
              frequency.getFrequencyUUID());
        assertEquals(QIOCraftingJobState.PROCESSING, secondReload.getJob(job.getJobId()).getState());
        assertEquals(5, secondReload.getJobBuffer(job.getJobId())
              .get(QIOJobBuffer.Compartment.RESERVED, input));
        assertTrue(secondReload.getDurableTransfers().isEmpty());
    }

    @Test
    void reservationRaceReturnsAnUnderbackedClaimToMaterialWaiting() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor input = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("reservation-race", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(input, 5), 10);
        AtomicInteger saves = new AtomicInteger();
        TestStorageView view = new TestStorageView(frequency, saves);
        assertEquals(QIOMaterialClaimCoordinator.Outcome.FULLY_COMMITTED,
              QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                    ignored -> saves.incrementAndGet()).getOutcome());
        assertNotNull(network.acquireExecutionSlot(job.getJobId(), 8));

        frequency.removeHolder(holder);
        frequency.refresh();
        QIOReservationCoordinator.Outcome outcome = QIOReservationCoordinator.reserve(network,
              job.getJobId(), view, ignored -> saves.incrementAndGet());

        assertEquals(QIOReservationCoordinator.Outcome.WAITING_MATERIALS, outcome);
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
        assertEquals(0, network.getActiveExecutionSlotCount());
        assertNull(network.getReservationTransfer(job.getJobId()));
        assertEquals(5, network.getCommitment(job.getJobId()).getMissingAmounts().get(input));
        assertNotNull(frequency.getResourceClaim(
              network.getCommitment(job.getJobId()).getClaimId()));
    }

    @Test
    void jobOperationTransferHistoryPrunesOnlyAfterEveryHandoffCommits() {
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    "transfer-prune", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null,
              0, 0, plan(resource, 1), 8);
        UUID operationId = UUID.randomUUID();
        String nodeId = "1/" + operationId;
        QIODurableTransferRecord committed = genericJobTransfer(job, nodeId, resource);
        QIODurableTransferRecord pending = genericJobTransfer(job, nodeId, resource);
        network.addDurableTransfer(committed);
        network.addDurableTransfer(pending);
        commitGeneric(network, committed);

        assertThrows(IllegalStateException.class, () ->
              network.removeCommittedJobOperationTransfers(job.getJobId(), nodeId));
        assertEquals(2, network.getDurableTransfers().size());

        commitGeneric(network, pending);
        assertEquals(2, network.removeCommittedJobOperationTransfers(job.getJobId(),
              operationId));
        assertTrue(network.getDurableTransfers().isEmpty());
    }

    @Test
    void uncreditedMachineInputRollbackReturnsOnlyTheStillOwnedInput() throws Exception {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(
              frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    "cancel-loading", null, SecurityMode.PUBLIC));
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null,
              0, 0, plan(input, 0), 8);
        network.creditJobProduced(job.getJobId(), Collections.singletonMap(input, 4L));
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE,
              job.getJobId(), null, job.getActivePlan().getRevision(),
              "1/" + UUID.randomUUID(), UUID.randomUUID(), "job/input",
              "machine/input", Collections.singletonMap(input, 4L));
        network.addDurableTransfer(transfer);
        network.debitJobMachineTransfer(transfer.getTransferId());
        assertEquals(0, network.getJobBuffer(job.getJobId()).get(
              QIOJobBuffer.Compartment.PRODUCED, input));
        assertEquals(4, network.getJobBuffer(job.getJobId()).get(
              QIOJobBuffer.Compartment.IN_PROCESS_RETURN, input));

        network.rollbackUncreditedJobMachineTransfer(transfer.getTransferId());

        assertEquals(4, network.getJobBuffer(job.getJobId()).get(
              QIOJobBuffer.Compartment.PRODUCED, input));
        assertEquals(0, network.getJobBuffer(job.getJobId()).get(
              QIOJobBuffer.Compartment.IN_PROCESS_RETURN, input));
        assertTrue(network.getDurableTransfers().isEmpty());
        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequency.getFrequencyUUID());
        assertEquals(4, restored.getJobBuffer(job.getJobId()).get(
              QIOJobBuffer.Compartment.PRODUCED, input));
        assertTrue(restored.getDurableTransfers().isEmpty());
    }

    @Test
    void progressiveRootSettlementRetainsInputsRequiredByUnfinishedCycleOperations() {
        PortableResourceDescriptor root = PortableResourceDescriptor.item(
              new ItemStack(Blocks.DIRT));
        QIOCraftingJob job = newJob(processingPlan(root, 10, 10,
              Collections.singletonMap(root, 1L)));
        QIOJobBuffer buffer = processingNetwork.getJobBuffer(job.getJobId());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, root, 1);
        processingNetwork.creditJobProduced(job.getJobId(), Collections.singletonMap(root, 12L));

        assertEquals(3, processingNetwork.settleProgressiveRootOutput(job.getJobId()));
        assertEquals(9, buffer.get(QIOJobBuffer.Compartment.PRODUCED, root));
        assertEquals(3, buffer.get(QIOJobBuffer.Compartment.SETTLED_OUTPUT, root));
        assertEquals(1, buffer.get(QIOJobBuffer.Compartment.RESERVED, root));
    }

    @Test
    void progressiveRootTransferLeavesReservedInputsUntouchedAndRecordsDelivery() {
        PortableResourceDescriptor root = PortableResourceDescriptor.item(
              new ItemStack(Blocks.DIRT));
        PortableResourceDescriptor ingredient = PortableResourceDescriptor.item(
              new ItemStack(Blocks.STONE));
        QIOCraftingJob job = newJob(processingPlan(root, 4, 4,
              Collections.singletonMap(ingredient, 1L)));
        processingNetwork.transitionJobState(job.getJobId(), QIOCraftingJobState.PROCESSING);
        QIOJobBuffer buffer = processingNetwork.getJobBuffer(job.getJobId());
        buffer.add(QIOJobBuffer.Compartment.RESERVED, root, 1);
        processingNetwork.creditJobProduced(job.getJobId(), Collections.singletonMap(root, 10L));
        assertEquals(4, processingNetwork.settleProgressiveRootOutput(job.getJobId()));

        QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
              job.getJobId(), null, job.getActivePlan().getRevision(),
              QIOProcessingNetworkData.PROGRESSIVE_ROOT_DELIVERY_NODE_ID, null,
              "job/settled-output", "qio/test", Collections.singletonMap(root, 4L),
              Collections.singletonMap(root, BigInteger.ZERO));
        processingNetwork.addDurableTransfer(transfer);
        processingNetwork.stageJobReturnTransfer(transfer.getTransferId());

        assertEquals(1, buffer.get(QIOJobBuffer.Compartment.RESERVED, root));
        assertEquals(0, buffer.get(QIOJobBuffer.Compartment.SETTLED_OUTPUT, root));
        assertEquals(4, buffer.get(QIOJobBuffer.Compartment.RETURNING, root));

        processingNetwork.markGenericTransferDestinationCredited(transfer.getTransferId(),
              "qio-receipt");
        processingNetwork.completeJobReturnTransfer(transfer.getTransferId());

        assertEquals(4, job.getDeliveredRootAmount());
        assertEquals(0, buffer.get(QIOJobBuffer.Compartment.RETURNING, root));
        assertEquals(QIOCraftingJobState.PROCESSING, job.getState());
    }

    private static QIODurableTransferRecord genericJobTransfer(QIOCraftingJob job,
          String nodeId, PortableResourceDescriptor resource) {
        return new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, job.getJobId(), null,
              job.getActivePlan().getRevision(), nodeId, null, "machine/test",
              "job/test", Collections.singletonMap(resource, 1L));
    }

    private static void commitGeneric(QIOProcessingNetworkData network,
          QIODurableTransferRecord transfer) {
        network.markGenericTransferSourceDebited(transfer.getTransferId(), "source", -1);
        network.markGenericTransferDestinationCredited(transfer.getTransferId(),
              "destination");
        network.commitGenericTransfer(transfer.getTransferId());
    }

    private QIOCraftPlan plan(PortableResourceDescriptor input, long amount) {
        return plan(input, amount, 1);
    }

    private QIOCraftPlan plan(PortableResourceDescriptor input, long amount, int revision) {
        QIOStorageSnapshot snapshot = frequency.getExternalStorageSnapshot();
        Map<PortableResourceDescriptor, Long> requirements = amount == 0 ? Collections.emptyMap() :
              Collections.singletonMap(input, amount);
        return new QIOCraftPlan(UUID.randomUUID(), revision,
              new QIOPlanSourceRevisions(snapshot.getContentsRevision(), snapshot.getCapacityRevision(),
                    snapshot.getClaimRevision(), snapshot.getAccessRevision(), 0, 0, 0, 0),
              PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT)), 1, requirements);
    }

    private QIOProcessingNetworkData processingNetwork;

    private QIOCraftingJob newJob(QIOCraftPlan plan) {
        processingNetwork = new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot("progressive-output", null,
                    SecurityMode.PUBLIC));
        return processingNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan, 8);
    }

    private QIOCraftPlan processingPlan(PortableResourceDescriptor root, long rootAmount,
          long operations, Map<PortableResourceDescriptor, Long> inputs) {
        QIOStorageSnapshot snapshot = frequency.getExternalStorageSnapshot();
        QIOPlanStep step = new QIOPlanStep(1, QIOPlanStep.ProviderKind.WORKBENCH,
              "test:provider", "test:route", "test:recipe", "default", "signature",
              operations, inputs, Collections.singletonMap(root, 1L),
              Collections.emptyMap(), Collections.emptyList());
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(snapshot.getContentsRevision(),
                    snapshot.getCapacityRevision(), snapshot.getClaimRevision(),
                    snapshot.getAccessRevision(), 0, 0, 0, 0), root, rootAmount,
              Collections.emptyMap(), Collections.singletonList(step));
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private static final class TestStorageView implements IQIOStorageView {

        private final QIOFrequency frequency;
        private final AtomicInteger persistenceBarriers;

        private TestStorageView(QIOFrequency frequency, AtomicInteger persistenceBarriers) {
            this.frequency = frequency;
            this.persistenceBarriers = persistenceBarriers;
        }

        @Nonnull
        @Override
        public UUID getFrequencyUUID() {
            return frequency.getFrequencyUUID();
        }

        @Nonnull
        @Override
        public String getFrequencyName() {
            return frequency.getName();
        }

        @Override
        public long getContentsRevision() {
            return frequency.getContentsRevision();
        }

        @Override
        public long getCapacityRevision() {
            return frequency.getCapacityRevision();
        }

        @Override
        public long getClaimRevision() {
            return frequency.getClaimRevision();
        }

        @Override
        public long getAccessRevision() {
            return frequency.getAccessRevision();
        }

        @Nonnull
        @Override
        public QIOStorageSnapshot getSnapshot() {
            return frequency.getExternalStorageSnapshot();
        }

        @Nullable
        @Override
        public QIOStorageEntry getResource(UUID resourceUUID) {
            return frequency.getExternalStorageEntry(resourceUUID);
        }

        @Nullable
        @Override
        public QIOResourceClaim getClaim(UUID claimId) {
            return frequency.getResourceClaim(claimId);
        }

        @Nullable
        @Override
        public QIOClaimBacking getClaimBacking(UUID claimId) {
            return frequency.getResourceClaimBacking(claimId);
        }

        @Nonnull
        @Override
        public QIOClaimResult submitClaim(QIOClaimRequest request) {
            assertTrue(persistenceBarriers.get() > 0, "claim intent must be persisted before submission");
            return frequency.submitClaimRequest(request);
        }

        @Override
        public long insert(ItemStack stack, long amount, Action action) {
            return frequency.massInsert(stack, amount, action);
        }

        @Override
        public long insert(FluidStack stack, long amount, Action action) {
            return frequency.massInsert(stack, amount, action);
        }

        @Override
        public long insert(GasStack stack, long amount, Action action) {
            return frequency.massInsert(stack, amount, action);
        }

        @Override
        public long extract(ItemStack stack, long amount, Action action) {
            return frequency.massExtract(stack, amount, action);
        }

        @Override
        public long extract(FluidStack stack, long amount, Action action) {
            return frequency.massExtract(stack, amount, action);
        }

        @Override
        public long extract(GasStack stack, long amount, Action action) {
            return frequency.massExtract(stack, amount, action);
        }

        @Override
        public boolean addListener(IQIOStorageListener listener) {
            return frequency.addExternalStorageListener(listener);
        }

        @Override
        public boolean removeListener(IQIOStorageListener listener) {
            return frequency.removeExternalStorageListener(listener);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void close() {
        }
    }

    private static final class TestDriveItem extends Item implements IQIODriveItem {

        private TestDriveItem() {
            setMaxStackSize(1);
        }

        @Override
        public QIODriveTier getDriveTier() {
            return QIODriveTier.BASE;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private ItemStack drive;

        private TestHolder(ItemStack drive) {
            this.drive = drive;
        }

        @Override
        public int getQIODimension() {
            return 0;
        }

        @Override
        public BlockPos getQIOPosition() {
            return BlockPos.ORIGIN;
        }

        @Override
        public List<ItemStack> getQIODriveStacks() {
            return Collections.singletonList(drive);
        }

        @Override
        public void updateQIODriveStack(int slot, ItemStack stack) {
            drive = stack.copy();
        }
    }
}
