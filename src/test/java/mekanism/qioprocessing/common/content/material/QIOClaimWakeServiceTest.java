package mekanism.qioprocessing.common.content.material;

import mekanism.api.Action;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.IQIOFrequencyStorageAccess;
import mekanism.api.qio.external.IQIOStorageListener;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOClaimBacking;
import mekanism.api.qio.external.QIOFrequencyReference;
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
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOClaimWakeServiceTest {

    private static CapabilityItem capabilityItem;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
        capabilityItem = new CapabilityItem();
        capabilityItem.setRegistryName(new ResourceLocation("test", "qio_claim_capability_item"));
        ForgeRegistries.ITEMS.register(capabilityItem);
    }

    private File worldDirectory;
    private QIOFrequency frequency;
    private TestHolder holder;
    private TestStorageAccess storageAccess;
    private AtomicInteger persistenceBarriers;

    @BeforeEach
    void setup() throws Exception {
        QIOClaimWakeService.INSTANCE.shutdown();
        worldDirectory = Files.createTempDirectory("qio-claim-wake-test").toFile();
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        TestDriveItem driveItem = new TestDriveItem();
        ItemStack drive = new ItemStack(driveItem);
        frequency = new QIOFrequency("wake", null, SecurityMode.PUBLIC);
        holder = new TestHolder(drive);
        frequency.addHolder(holder);
        frequency.refresh();
        persistenceBarriers = new AtomicInteger();
        storageAccess = new TestStorageAccess(frequency, persistenceBarriers);
    }

    @AfterEach
    void cleanup() throws Exception {
        QIOClaimWakeService.INSTANCE.shutdown();
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        delete(worldDirectory);
    }

    @Test
    void initialScanAndExactResourceEventCompleteAPartialClaim() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(2, frequency.massInsert(stone, 2, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
        assertEquals(2, network.getCommitment(job.getJobId()).getCommittedAmounts().get(resource));

        assertEquals(3, frequency.massInsert(stone, 3, Action.EXECUTE));
        frequency.tick(true);
        assertEquals(1, tick(network, 1));

        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
        assertEquals(5, network.getCommitment(job.getJobId()).getCommittedAmounts().get(resource));
    }

    @Test
    void anyEnabledCandidateWakesAndCompletesAWaitingWorkbenchClaim() {
        PortableResourceDescriptor iron = PortableResourceDescriptor.item(
              new ItemStack(Items.IRON_INGOT));
        PortableResourceDescriptor gold = PortableResourceDescriptor.item(
              new ItemStack(Items.GOLD_INGOT));
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              candidatePlan(iron, gold), 10);

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
        assertTrue(network.getCommitment(job.getJobId()).getMissingResourceKeys()
              .containsAll(Arrays.asList(iron, gold)));

        assertEquals(1, frequency.massInsert(new ItemStack(Items.GOLD_INGOT), 1,
              Action.EXECUTE));
        frequency.tick(true);
        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
        assertEquals(1L, network.getCommitment(job.getJobId()).getCommittedAmounts()
              .get(gold));
    }

    @Test
    void capabilityDistinctQioResourcesDoNotCollideDuringClaimRefresh() {
        NBTTagCompound firstCapabilities = new NBTTagCompound();
        firstCapabilities.setLong("identity", 1);
        NBTTagCompound secondCapabilities = new NBTTagCompound();
        secondCapabilities.setLong("identity", 2);
        ItemStack first = new ItemStack(capabilityItem, 1, 0, firstCapabilities);
        ItemStack second = new ItemStack(capabilityItem, 1, 0, secondCapabilities);
        assertEquals(1, frequency.massInsert(first, 1, Action.EXECUTE));
        assertEquals(1, frequency.massInsert(second, 1, Action.EXECUTE));
        assertEquals(2, frequency.getExternalStorageSnapshot().getEntries().size());
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(first);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);

        assertEquals(1, tick(network, 1));

        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
        assertEquals(1L, network.getCommitment(job.getJobId()).getCommittedAmounts()
              .get(resource));
    }

    @Test
    void unrelatedResourceChangeDoesNotRefreshWaitingJob() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);
        assertEquals(1, tick(network, 1));
        frequency.tick(true);
        tick(network, 1);
        int savedBeforeUnrelatedChange = persistenceBarriers.get();

        assertEquals(1, frequency.massInsert(new ItemStack(Blocks.DIRT), 1, Action.EXECUTE));
        frequency.tick(true);

        assertEquals(0, tick(network, 1));
        assertEquals(savedBeforeUnrelatedChange, persistenceBarriers.get());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
    }

    @Test
    void perTickBudgetAndPriorityAreAppliedAcrossPendingJobs() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(10, frequency.massInsert(stone, 10, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob low = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);
        QIOCraftingJob high = network.createJob(QIOCraftingJobSource.MANUAL, null, 10, 0,
              plan(resource, 1), 10);
        QIOCraftingJob middle = network.createJob(QIOCraftingJobSource.MANUAL, null, 5, 0,
              plan(resource, 1), 10);

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, high.getState());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, middle.getState());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, low.getState());
        assertEquals(2, QIOClaimWakeService.INSTANCE.getPendingJobCount(network.getFrequencyUUID()));

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, middle.getState());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, low.getState());
    }

    @Test
    void reprioritizingAJobRefreshesItsDurableCoreClaim() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);
        assertEquals(1, tick(network, 1));
        UUID claimId = network.getCommitment(job.getJobId()).getClaimId();
        assertNotNull(frequency.getResourceClaim(claimId));
        assertEquals(0, frequency.getResourceClaim(claimId).getPriority());

        assertTrue(network.updateJobPriority(job.getJobId(), job.getRuntimeRevision(), 20));
        assertEquals(20, network.getCommitment(job.getJobId()).getPriority());
        assertEquals(1, tick(network, 1));
        assertEquals(20, frequency.getResourceClaim(claimId).getPriority());
    }

    @Test
    void runtimeQueueRebuildsAfterServiceRestart() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);
        assertFalse(network.write().hasKey("claimWakeGeneration"));
        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        QIOClaimWakeService.INSTANCE.shutdown();

        assertEquals(1, tick(restored, 1));

        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT,
              restored.getJob(job.getJobId()).getState());
        assertEquals(1, QIOClaimWakeService.INSTANCE.getSessionCount());
    }

    @Test
    void repeatedRefreshFailuresAreReportedOnlyOncePerFrequency() {
        UUID frequencyUUID = frequency.getFrequencyUUID();
        IllegalStateException duplicateResource = new IllegalStateException(
              "QIO snapshot maps one portable resource to multiple UUIDs");

        assertTrue(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              frequencyUUID, duplicateResource));
        assertFalse(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              frequencyUUID, new IllegalStateException(duplicateResource.getMessage())));
        assertTrue(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              frequencyUUID, new IllegalStateException("different failure")));
        assertFalse(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              frequencyUUID, duplicateResource));
        assertTrue(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              UUID.randomUUID(), duplicateResource));

        QIOClaimWakeService.INSTANCE.shutdown();
        assertTrue(QIOClaimWakeService.INSTANCE.shouldReportRefreshFailure(
              frequencyUUID, duplicateResource));
    }

    @Test
    void refreshBudgetRotatesAcrossFrequencies() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        QIOFrequency secondFrequency = createFrequency("wake_second");
        assertEquals(1, secondFrequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData firstNetwork = network();
        QIOProcessingNetworkData secondNetwork = new QIOProcessingNetworkData(
              secondFrequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                    secondFrequency.getName(), secondFrequency.getOwner(),
                    secondFrequency.getSecurity()));
        QIOCraftingJob first = firstNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);
        QIOStorageSnapshot secondSnapshot = secondFrequency.getExternalStorageSnapshot();
        QIOCraftPlan secondPlan = new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(secondSnapshot.getContentsRevision(),
                    secondSnapshot.getCapacityRevision(), secondSnapshot.getClaimRevision(),
                    secondSnapshot.getAccessRevision(), 0, 0, 0, 0),
              PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT)), 1,
              Collections.singletonMap(resource, 1L));
        QIOCraftingJob second = secondNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              secondPlan, 10);
        Map<UUID, QIOFrequency> frequencies = new LinkedHashMap<>();
        frequencies.put(frequency.getFrequencyUUID(), frequency);
        frequencies.put(secondFrequency.getFrequencyUUID(), secondFrequency);
        IQIOFrequencyStorageAccess access = (reference, requester) -> {
            QIOFrequency found = frequencies.get(reference.getFrequencyUUID());
            return found == null ? null : new TestStorageView(found, persistenceBarriers);
        };

        assertEquals(1, QIOClaimWakeService.INSTANCE.tick(
              java.util.Arrays.asList(firstNetwork, secondNetwork), 1, access,
              ignored -> persistenceBarriers.incrementAndGet()));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, first.getState());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, second.getState());

        assertEquals(1, QIOClaimWakeService.INSTANCE.tick(
              java.util.Arrays.asList(firstNetwork, secondNetwork), 1, access,
              ignored -> persistenceBarriers.incrementAndGet()));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, second.getState());
    }

    @Test
    void unavailableViewMovesJobToAccessStateAndRecoversLater() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);

        assertEquals(0, QIOClaimWakeService.INSTANCE.tick(Collections.singletonList(network), 1,
              (reference, requester) -> null,
              ignored -> persistenceBarriers.incrementAndGet()));
        assertEquals(0, QIOClaimWakeService.INSTANCE.getSessionCount());
        assertEquals(QIOCraftingJobState.WAITING_ACCESS, job.getState());
        assertEquals(1, persistenceBarriers.get());

        assertEquals(0, QIOClaimWakeService.INSTANCE.tick(Collections.singletonList(network), 1,
              (reference, requester) -> null,
              ignored -> persistenceBarriers.incrementAndGet()));
        assertEquals(1, persistenceBarriers.get());

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
    }

    @Test
    void listenerRegistrationFailureMovesJobToAccessState() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 1), 10);

        assertEquals(0, QIOClaimWakeService.INSTANCE.tick(Collections.singletonList(network), 1,
              (reference, requester) -> new ListenerRejectingStorageView(frequency,
                    persistenceBarriers),
              ignored -> persistenceBarriers.incrementAndGet()));

        assertEquals(QIOCraftingJobState.WAITING_ACCESS, job.getState());
        assertEquals(1, persistenceBarriers.get());
        assertEquals(0, QIOClaimWakeService.INSTANCE.getSessionCount());
    }

    @Test
    void jobCreatedAfterListenerRegistrationRebuildsQueueByPriority() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob low = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);
        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, low.getState());

        QIOCraftingJob high = network.createJob(QIOCraftingJobSource.MANUAL, null, 10, 0,
              plan(resource, 1), 10);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));

        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, high.getState());
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, low.getState());
    }

    @Test
    void invalidatedStorageViewMovesWaitingClaimsToAccessState() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(1, frequency.massInsert(stone, 1, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);
        assertEquals(1, tick(network, 1));
        assertNull(network.getCommitment(job.getJobId()).getPendingMutation());

        frequency.onRemove();
        int savesBeforeInvalidation = persistenceBarriers.get();
        assertEquals(0, tick(network, 1));

        assertEquals(QIOCraftingJobState.WAITING_ACCESS, job.getState());
        assertTrue(persistenceBarriers.get() > savesBeforeInvalidation);
        assertEquals(0, QIOClaimWakeService.INSTANCE.getSessionCount());
    }

    @Test
    void driveUnloadDemotesACompleteClaimWithoutReleasingLogicalOwnership() {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);
        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
        UUID claimId = network.getCommitment(job.getJobId()).getClaimId();
        assertNotNull(frequency.getResourceClaim(claimId));

        frequency.removeHolder(holder);
        frequency.tick(true);
        assertEquals(1, tick(network, 1));

        assertEquals(QIOCraftingJobState.WAITING_MATERIALS, job.getState());
        assertEquals(5, network.getCommitment(job.getJobId()).getMissingAmounts().get(resource));
        assertNotNull(frequency.getResourceClaim(claimId));
        assertEquals(5, frequency.getCommitted(
              frequency.getResourceClaim(claimId).getResourceAmounts().keySet().iterator().next()));

        int savesWhileWaiting = persistenceBarriers.get();
        assertEquals(0, tick(network, 1));
        assertEquals(savesWhileWaiting, persistenceBarriers.get());

        frequency.addHolder(holder);
        frequency.tick(true);
        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());
        assertEquals(5, network.getCommitment(job.getJobId()).getCommittedAmounts().get(resource));
    }

    @Test
    void restartAuditsCompleteClaimsWithoutPersistingWakeSignals() throws Exception {
        ItemStack stone = new ItemStack(Blocks.STONE);
        assertEquals(5, frequency.massInsert(stone, 5, Action.EXECUTE));
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(stone);
        QIOProcessingNetworkData network = network();
        QIOCraftingJob job = network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0,
              plan(resource, 5), 10);
        assertEquals(1, tick(network, 1));
        assertEquals(QIOCraftingJobState.WAITING_EXECUTION_SLOT, job.getState());

        frequency.removeHolder(holder);
        frequency.tick(true);
        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              network.getFrequencyUUID());
        QIOClaimWakeService.INSTANCE.shutdown();

        assertEquals(1, tick(restored, 1));
        assertEquals(QIOCraftingJobState.WAITING_MATERIALS,
              restored.getJob(job.getJobId()).getState());
        assertEquals(5, restored.getCommitment(job.getJobId()).getMissingAmounts().get(resource));
        assertFalse(restored.write().hasKey("claimWakeGeneration"));
    }

    private int tick(QIOProcessingNetworkData network, int budget) {
        return QIOClaimWakeService.INSTANCE.tick(Collections.singletonList(network), budget,
              storageAccess, ignored -> persistenceBarriers.incrementAndGet());
    }

    private QIOProcessingNetworkData network() {
        return new QIOProcessingNetworkData(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot(frequency.getName(), frequency.getOwner(),
                    frequency.getSecurity()));
    }

    private QIOFrequency createFrequency(String name) {
        QIOFrequency created = new QIOFrequency(name, null, SecurityMode.PUBLIC);
        created.addHolder(new TestHolder(new ItemStack(new TestDriveItem())));
        created.refresh();
        return created;
    }

    private QIOCraftPlan plan(PortableResourceDescriptor input, long amount) {
        QIOStorageSnapshot snapshot = frequency.getExternalStorageSnapshot();
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(snapshot.getContentsRevision(),
                    snapshot.getCapacityRevision(), snapshot.getClaimRevision(),
                    snapshot.getAccessRevision(), 0, 0, 0, 0),
              PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT)), 1,
              Collections.singletonMap(input, amount));
    }

    private QIOCraftPlan candidatePlan(PortableResourceDescriptor preferred,
          PortableResourceDescriptor alternative) {
        QIOStorageSnapshot snapshot = frequency.getExternalStorageSnapshot();
        PortableResourceDescriptor output = PortableResourceDescriptor.item(
              new ItemStack(Blocks.DIRT));
        QIOCandidateInputGroup group = new QIOCandidateInputGroup(
              Collections.singletonList(0), Arrays.asList(
                    new QIOCandidateOption(hash('a'), preferred, 1, false),
                    new QIOCandidateOption(hash('b'), alternative, 1, false)),
              Collections.singletonMap(preferred, 1L));
        QIOPlanStep step = new QIOPlanStep(0, ProviderKind.WORKBENCH,
              "mekanism:qio_workbench", "test:candidate", "test:candidate",
              "i1.0.0.0.0.0.0.0.0.0", hash('c'), 1,
              Collections.singletonMap(preferred, 1L),
              Collections.singletonMap(output, 1L), Collections.emptyMap(),
              Collections.emptyList(), Collections.singletonList(group));
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(snapshot.getContentsRevision(),
                    snapshot.getCapacityRevision(), snapshot.getClaimRevision(),
                    snapshot.getAccessRevision(), 0, 0, 0, 0), output, 1,
              Collections.singletonMap(preferred, 1L), Collections.singletonList(step));
    }

    private static String hash(char character) {
        char[] value = new char[64];
        Arrays.fill(value, character);
        return new String(value);
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

    private static final class TestStorageAccess implements IQIOFrequencyStorageAccess {

        private final QIOFrequency frequency;
        private final AtomicInteger persistenceBarriers;

        private TestStorageAccess(QIOFrequency frequency, AtomicInteger persistenceBarriers) {
            this.frequency = frequency;
            this.persistenceBarriers = persistenceBarriers;
        }

        @Nullable
        @Override
        public IQIOStorageView open(@Nonnull QIOFrequencyReference reference,
              @Nullable UUID requester) {
            return reference.getFrequencyUUID().equals(frequency.getFrequencyUUID()) &&
                  frequency.isValid() && !frequency.isRemoved() ?
                  new TestStorageView(frequency, persistenceBarriers) : null;
        }
    }

    private static class TestStorageView implements IQIOStorageView {

        private final QIOFrequency frequency;
        private final AtomicInteger persistenceBarriers;
        private final Set<IQIOStorageListener> listeners = new LinkedHashSet<>();
        private int lastSubmitBarrierCount;
        private boolean closed;

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
            assertTrue(persistenceBarriers.get() > lastSubmitBarrierCount,
                  "claim intent must be persisted before every core submission");
            lastSubmitBarrierCount = persistenceBarriers.get();
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
            if (closed || !listeners.add(listener)) {
                return false;
            }
            return frequency.addExternalStorageListener(listener);
        }

        @Override
        public boolean removeListener(IQIOStorageListener listener) {
            listeners.remove(listener);
            return frequency.removeExternalStorageListener(listener);
        }

        @Override
        public boolean isValid() {
            return !closed;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                for (IQIOStorageListener listener : new LinkedHashSet<>(listeners)) {
                    frequency.removeExternalStorageListener(listener);
                }
                listeners.clear();
            }
        }
    }

    private static final class ListenerRejectingStorageView extends TestStorageView {

        private ListenerRejectingStorageView(QIOFrequency frequency,
              AtomicInteger persistenceBarriers) {
            super(frequency, persistenceBarriers);
        }

        @Override
        public boolean addListener(IQIOStorageListener listener) {
            return false;
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

    private static final class CapabilityItem extends Item {

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack,
              @Nullable NBTTagCompound nbt) {
            return new CapabilityState(nbt);
        }
    }

    private static final class CapabilityState implements ICapabilityProvider,
          INBTSerializable<NBTTagCompound> {

        private NBTTagCompound state;

        private CapabilityState(@Nullable NBTTagCompound state) {
            this.state = state == null ? new NBTTagCompound() : state.copy();
        }

        @Override
        public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
            return false;
        }

        @Nullable
        @Override
        public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
            return null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return state.copy();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            state = nbt.copy();
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
