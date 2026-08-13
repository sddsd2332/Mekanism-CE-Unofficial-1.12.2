package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.ProviderConformanceDescriptor;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.TestBootstrap;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.sync.ISyncableData;
import mekanism.common.inventory.container.sync.SyncableFrequencyList;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.api.machine.MachineActivitySnapshot;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.machine.QIOOutputBufferEntry;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QIOAutomationStateTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        QIOAutomationCapabilities.register();
    }

    @Test
    void unregisteredProviderDescriptorIsClosedByDefault() {
        ProviderConformanceDescriptor descriptor = ProviderConformanceDescriptor.unregistered();
        assertFalse(descriptor.isRegistered());
        assertTrue(descriptor.modes().isEmpty());
        assertFalse(descriptor.supports(QIOAutomationMode.SCHEDULED));
    }

    @Test
    void leaseAndTokenRoundTripPreservesOwnershipAndReceipts() {
        UUID leaseId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("item_input", "item_input", MachineResourceKind.ITEM,
              MachineResourceStack.item("item_input", new net.minecraft.item.ItemStack(Items.IRON_INGOT)));
        MachineOperationLease lease = MachineOperationLease.acquire(leaseId, operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 3, 11, Collections.singletonList(baseline))
              .transition(MachineOperationLease.State.LOADING)
              .transition(MachineOperationLease.State.ACTIVE);
        MachineOperationToken token = MachineOperationToken.job(operationId, leaseId, UUID.randomUUID(), 7,
              "route", "recipe", 3).transition(MachineOperationToken.State.LOADING)
              .transition(MachineOperationToken.State.ACTIVE).withTransferReceipt(UUID.randomUUID());

        assertEquals(lease, MachineOperationLease.read(lease.write()));
        assertEquals(token, MachineOperationToken.read(token.write()));
        assertThrows(IllegalStateException.class, () -> lease.transition(MachineOperationLease.State.COMPLETED));
    }

    @Test
    void hostRejectsConflictingPortGroupsAndRestoresState() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(), "test", null,
              SecurityMode.PUBLIC, null);
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.SCHEDULED, true));
        UUID operationId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("input", "shared", MachineResourceKind.ITEM,
              MachineResourceStack.item("input", new net.minecraft.item.ItemStack(Items.IRON_INGOT)));
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1, Collections.singletonList(baseline));
        assertNotNull(lease);
        MachineOperationLease conflict = host.tryAcquireLease(UUID.randomUUID(), UUID.randomUUID(),
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 1, 1, Collections.singletonList(
                    new MachinePortBaseline("output", "shared", MachineResourceKind.ITEM, null)));
        assertNull(conflict);

        MachineOperationToken token = MachineOperationToken.job(operationId, lease.leaseId(), UUID.randomUUID(), 0,
              "route", "recipe", 0);
        assertTrue(host.attachOperationToken(token));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.LOADING,
              MachineOperationLease.State.LOADING));
        UUID transfer = UUID.randomUUID();
        assertTrue(host.recordTransferReceipt(operationId, transfer));
        assertTrue(host.recordTransferReceipt(operationId, transfer));

        NBTTagCompound saved = host.serializeNBT();
        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(saved);
        assertEquals(QIOAutomationHost.State.WAITING_ACCESS, restored.getState());
        assertEquals(1, restored.getLeases().size());
        assertTrue(restored.getOperationTokens().get(operationId).hasTransferReceipt(transfer));
    }

    @Test
    void activeScheduledJobCanBeAbandonedAndPrunedAfterReload() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "cancel-active", null, SecurityMode.PUBLIC, null);
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.SCHEDULED, true));
        UUID operationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, null);
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(baseline));
        assertNotNull(lease);
        assertTrue(host.attachOperationToken(MachineOperationToken.job(operationId,
              lease.leaseId(), jobId, 1, "route", "recipe", 0)));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.LOADING,
              MachineOperationLease.State.LOADING));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.ACTIVE,
              MachineOperationLease.State.ACTIVE));
        UUID inputReceipt = UUID.randomUUID();
        assertTrue(host.recordTransferReceipt(operationId, inputReceipt));
        host.updateActivitySnapshot(new MachineActivitySnapshot(0, operationId, "recipe",
              MachineActivitySnapshot.State.RUNNING, 4, 20, 1, null));

        assertTrue(host.abandonJobOperation(operationId));
        assertEquals(MachineOperationToken.State.CANCELLED,
              host.getOperationTokens().get(operationId).state());
        assertEquals(MachineOperationLease.State.RELEASED,
              host.getLeases().get(lease.leaseId()).state());
        assertTrue(host.getOperationTokens().get(operationId).hasTransferReceipt(inputReceipt));
        assertTrue(host.getActivitySnapshots().isEmpty());
        assertTrue(host.getOutputBufferEntries().isEmpty());

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(host.serializeNBT());
        assertEquals(MachineOperationToken.State.CANCELLED,
              restored.getOperationTokens().get(operationId).state());
        assertEquals(MachineOperationLease.State.RELEASED,
              restored.getLeases().get(lease.leaseId()).state());
        assertTrue(restored.abandonJobOperation(operationId));
        assertTrue(restored.forgetSettledOperation(operationId));
        assertTrue(restored.getOperationTokens().isEmpty());
        assertTrue(restored.getLeases().isEmpty());
    }

    @Test
    void selectedModeWithoutFrequencySurvivesReloadAsUnbound() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        assertTrue(host.selectMode(QIOAutomationMode.OUTPUT_ONLY));
        assertEquals(QIOAutomationHost.State.UNBOUND, host.getState());

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(host.serializeNBT());

        assertEquals(QIOAutomationMode.OUTPUT_ONLY, restored.getEnabledMode());
        assertNull(restored.getFrequencyReference());
        assertEquals(QIOAutomationHost.State.UNBOUND, restored.getState());
        assertNull(restored.getDataError());
    }

    @Test
    void removingIdleAutomationModeClearsFrequencyAndSerializedBinding() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "idle-removal", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.PASSIVE, true));

        assertTrue(host.clearMode());

        assertEquals(QIOAutomationHost.State.UNBOUND, host.getState());
        assertNull(host.getEnabledMode());
        assertNull(host.getFrequencyReference());
        NBTTagCompound saved = host.serializeNBT();
        assertFalse(saved.hasKey("enabledMode"));
        assertFalse(saved.hasKey("frequency"));
        assertFalse(saved.getBoolean("clearModeWhenDrained"));
    }

    @Test
    void removingBusyAutomationModeDrainsAcrossReloadThenClearsBinding() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "busy-removal", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.PASSIVE, true));
        UUID operationId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, null);
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(baseline));
        assertNotNull(lease);

        assertTrue(host.clearMode());
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, host.getState());
        assertEquals(QIOAutomationMode.PASSIVE, host.getEnabledMode());
        assertEquals(frequency, host.getFrequencyReference());
        assertNull(host.tryAcquireLease(UUID.randomUUID(), UUID.randomUUID(),
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 1, 2,
              Collections.singletonList(baseline)));
        assertFalse(host.setAccessValidated(false));
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, host.getState());

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(host.serializeNBT());
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, restored.getState());
        assertEquals(QIOAutomationMode.PASSIVE, restored.getEnabledMode());
        assertNotNull(restored.getFrequencyReference());

        assertTrue(restored.releaseUnattachedLease(lease.leaseId()));
        assertEquals(QIOAutomationHost.State.UNBOUND, restored.getState());
        assertNull(restored.getEnabledMode());
        assertNull(restored.getFrequencyReference());
        NBTTagCompound drained = restored.serializeNBT();
        assertFalse(drained.hasKey("enabledMode"));
        assertFalse(drained.hasKey("frequency"));
        assertFalse(drained.getBoolean("clearModeWhenDrained"));
    }

    @Test
    void removingBusyProcessingUpgradeDetachesFrequencyWhileOperationDrains() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "detached-drain", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.PASSIVE, true));
        UUID operationId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, null);
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(baseline));
        assertNotNull(lease);

        assertTrue(host.clearMode(true));
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, host.getState());
        assertEquals(QIOAutomationMode.PASSIVE, host.getEnabledMode());
        assertNull(host.getFrequencyReference());
        NBTTagCompound draining = host.serializeNBT();
        assertFalse(draining.hasKey("frequency"));
        assertTrue(draining.getBoolean("clearModeWhenDrained"));

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(draining);
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, restored.getState());
        assertNull(restored.getFrequencyReference());
        assertEquals(QIOAutomationMode.PASSIVE, restored.getEnabledMode());

        assertTrue(restored.releaseUnattachedLease(lease.leaseId()));
        assertEquals(QIOAutomationHost.State.UNBOUND, restored.getState());
        assertNull(restored.getEnabledMode());
        assertFalse(restored.serializeNBT().hasKey("frequency"));
    }

    @Test
    void managementPausePersistsAndRejectsOnlyNewLeases() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(),
              "paused", null, SecurityMode.PUBLIC, null);
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.SCHEDULED, true));
        long before = host.getConfigurationRevision();
        assertTrue(host.setManagementPaused(true));
        assertTrue(host.isManagementPaused());
        assertEquals(before + 1, host.getConfigurationRevision());
        assertNull(host.tryAcquireLease(UUID.randomUUID(), UUID.randomUUID(),
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(new MachinePortBaseline("input", "input",
                    MachineResourceKind.ITEM, null))));

        DefaultQIOAutomationHost restored = new DefaultQIOAutomationHost();
        restored.deserializeNBT(host.serializeNBT());
        assertTrue(restored.isManagementPaused());
        assertEquals(host.getConfigurationRevision(), restored.getConfigurationRevision());
        assertTrue(restored.setManagementPaused(false));
        assertFalse(restored.isManagementPaused());
    }

    @Test
    void containerSummaryKeepsExactUuidAndRejectsSameNameReplacement() {
        UUID expectedUUID = UUID.randomUUID();
        QIOFrequencyReference reference = new QIOFrequencyReference(expectedUUID, "factory", null,
              SecurityMode.PUBLIC, UUID.randomUUID());
        QIOAutomationContainerState state = new QIOAutomationContainerState(new TestContainer(),
              new TileEntityFurnace());
        NBTTagCompound summary = new NBTTagCompound();
        summary.setString("state", QIOAutomationHost.State.ACTIVE.name());
        summary.setTag("reference", reference.write());
        state.readSummary(summary);

        assertEquals(expectedUUID, state.getFrequency().getFrequencyUUID());
        QIOFrequency replacement = new QIOFrequency("factory", null, SecurityMode.PUBLIC);
        publicFrequencyTracker(state).set(Collections.singletonList(replacement));

        QIOFrequency selected = state.getFrequency();
        assertNotSame(replacement, selected);
        assertEquals(expectedUUID, selected.getFrequencyUUID());

        QIOFrequency exact = frequency("factory", expectedUUID);
        publicFrequencyTracker(state).set(Arrays.asList(replacement, exact));
        assertSame(exact, state.getFrequency());
    }

    @Test
    void containerSummaryImmediatelyMirrorsBindingAndDetachedDrainState() {
        TestAutomationTile tile = new TestAutomationTile();
        QIOAutomationContainerState state = new QIOAutomationContainerState(
              new TestContainer(), tile);
        QIOFrequencyReference reference = new QIOFrequencyReference(UUID.randomUUID(),
              "container-sync", null, SecurityMode.PUBLIC, UUID.randomUUID());
        assertTrue(tile.host.configureBinding(reference, QIOAutomationMode.PASSIVE, true));

        state.readSummary(state.writeSummary(tile.host));
        assertEquals(QIOAutomationHost.State.ACTIVE, state.getState());
        assertEquals(QIOAutomationMode.PASSIVE, state.getMode());
        assertEquals(reference, state.getReference());

        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, null);
        assertNotNull(tile.host.tryAcquireLease(UUID.randomUUID(), UUID.randomUUID(),
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(baseline)));
        assertTrue(tile.host.clearMode(true));

        state.readSummary(state.writeSummary(tile.host));
        assertEquals(QIOAutomationHost.State.DRAINING_CHANGE, state.getState());
        assertEquals(QIOAutomationMode.PASSIVE, state.getMode());
        assertNull(state.getReference());
    }

    @Test
    void damagedHostDataIsIsolatedInsteadOfBecomingIdle() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        assertTrue(host.setManagementPaused(true));
        host.deserializeNBT(new NBTTagCompound());
        assertEquals(QIOAutomationHost.State.DATA_ERROR, host.getState());
        assertFalse(host.isManagementPaused());
        assertNotNull(host.getDataError());
        assertNull(host.tryAcquireLease(UUID.randomUUID(), UUID.randomUUID(),
              MachineOperationLease.Mode.OUTPUT_DRAIN, 0, 0, Collections.singletonList(
                    new MachinePortBaseline("out", "out", MachineResourceKind.ITEM, null))));
    }

    @Test
    void activitySnapshotValidatesOperationIdentity() {
        UUID operation = UUID.randomUUID();
        MachineActivitySnapshot snapshot = new MachineActivitySnapshot(2, operation, "recipe",
              MachineActivitySnapshot.State.RUNNING, 4, 10, 20, null);
        assertEquals(snapshot, MachineActivitySnapshot.read(snapshot.write()));
        assertThrows(IllegalArgumentException.class, () -> new MachineActivitySnapshot(0, null, "recipe",
              MachineActivitySnapshot.State.RUNNING, 0, 1, 0, null));
    }

    @Test
    void baselineRecognizesAnInsertionCommittedBeforeItsReceipt() {
        BasicInventorySlot slot = BasicInventorySlot.at(null, 0, 0);
        MachinePort port = MachinePort.item("input", MachinePort.Role.INPUT, slot);
        MachinePortBaseline empty = MachinePortBaseline.capture(port);
        MachineResourceStack inserted = MachineResourceStack.item("input",
              new net.minecraft.item.ItemStack(Items.IRON_INGOT, 3));

        assertFalse(empty.matchesAfterInsertion(port, inserted));
        assertTrue(port.insert(inserted));
        assertTrue(empty.matchesAfterInsertion(port, inserted));
        assertFalse(empty.matchesAfterInsertion(port, inserted.withAmount(2)));
    }

    @Test
    void outputBufferSurvivesEveryDeliveryBoundary() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(), "output", null,
              SecurityMode.PUBLIC, null);
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.OUTPUT_ONLY, true));
        MachineResourceStack stack = MachineResourceStack.item("output", new net.minecraft.item.ItemStack(Items.GOLD_INGOT, 9));
        MachinePortBaseline baseline = new MachinePortBaseline("output", "output", MachineResourceKind.ITEM, stack);
        UUID operationId = UUID.randomUUID();
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.OUTPUT_DRAIN, 0, 3, Collections.singletonList(baseline));
        assertNotNull(lease);
        assertTrue(host.attachOperationToken(MachineOperationToken.outputDrain(operationId, lease.leaseId(), 0)));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
              MachineOperationLease.State.COLLECTING));
        UUID bufferId = UUID.randomUUID();
        assertTrue(host.prepareOutputBuffer(QIOOutputBufferEntry.prepared(bufferId, operationId, lease.leaseId(), baseline, 3)));

        DefaultQIOAutomationHost prepared = new DefaultQIOAutomationHost();
        prepared.deserializeNBT(host.serializeNBT());
        assertEquals(QIOOutputBufferEntry.Phase.PREPARED,
              prepared.getOutputBufferEntries().get(bufferId).phase());
        PortableResourceDescriptor resource = PortableResourceDescriptor.item(new net.minecraft.item.ItemStack(Items.GOLD_INGOT));
        assertTrue(prepared.holdOutput(bufferId, resource, 9));
        UUID firstTransfer = UUID.randomUUID();
        assertTrue(prepared.beginOutputDelivery(bufferId, firstTransfer, 4));
        assertTrue(prepared.applyOutputDeliveryReceipt(bufferId, firstTransfer, 4));
        assertEquals(5, prepared.getOutputBufferEntries().get(bufferId).amount());
        UUID secondTransfer = UUID.randomUUID();
        assertTrue(prepared.beginOutputDelivery(bufferId, secondTransfer, 5));
        assertTrue(prepared.applyOutputDeliveryReceipt(bufferId, secondTransfer, 5));

        assertTrue(prepared.getOutputBufferEntries().isEmpty());
        assertEquals(MachineOperationToken.State.COMPLETED, prepared.getOperationTokens().get(operationId).state());
        assertEquals(MachineOperationLease.State.RELEASED, prepared.getLeases().get(lease.leaseId()).state());
        assertTrue(prepared.getOperationTokens().get(operationId).hasTransferReceipt(firstTransfer));
        assertTrue(prepared.getOperationTokens().get(operationId).hasTransferReceipt(secondTransfer));
    }

    @Test
    void completedOperationIsConfirmedOnlyAfterItsLeaseIsReleased() {
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost();
        QIOFrequencyReference frequency = new QIOFrequencyReference(UUID.randomUUID(), "checkpoint", null,
              SecurityMode.PUBLIC, null);
        assertTrue(host.configureBinding(frequency, QIOAutomationMode.SCHEDULED, true));
        UUID operationId = UUID.randomUUID();
        MachinePortBaseline baseline = new MachinePortBaseline("input", "input",
              MachineResourceKind.ITEM, MachineResourceStack.item("input",
                    new net.minecraft.item.ItemStack(Items.IRON_INGOT)));
        MachineOperationLease lease = host.tryAcquireLease(UUID.randomUUID(), operationId,
              MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, 0, 1,
              Collections.singletonList(baseline));
        assertNotNull(lease);
        assertTrue(host.attachOperationToken(MachineOperationToken.job(operationId,
              lease.leaseId(), UUID.randomUUID(), 0, "route", "recipe", 0)));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.LOADING,
              MachineOperationLease.State.LOADING));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.ACTIVE,
              MachineOperationLease.State.ACTIVE));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
              MachineOperationLease.State.COLLECTING));
        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.COMPLETED,
              MachineOperationLease.State.COMPLETED));

        host.confirmCompletedOperationPersisted(operationId);
        assertFalse(host.isPersistedCompletedOperation(operationId));
        DefaultQIOAutomationHost restoredBeforeRelease = new DefaultQIOAutomationHost();
        restoredBeforeRelease.deserializeNBT(host.serializeNBT());
        assertFalse(restoredBeforeRelease.isPersistedCompletedOperation(operationId));

        assertTrue(host.transitionOperation(operationId, MachineOperationToken.State.COMPLETED,
              MachineOperationLease.State.RELEASED));
        host.confirmCompletedOperationPersisted(operationId);
        assertTrue(host.isPersistedCompletedOperation(operationId));
    }

    @SuppressWarnings("unchecked")
    private static SyncableFrequencyList<QIOFrequency> publicFrequencyTracker(
          QIOAutomationContainerState state) {
        List<ISyncableData> tracked = state.getSpecificSyncableData();
        return (SyncableFrequencyList<QIOFrequency>) tracked.get(SecurityMode.PUBLIC.ordinal());
    }

    private static QIOFrequency frequency(String name, UUID uuid) {
        QIOFrequency original = new QIOFrequency(name, null, SecurityMode.PUBLIC);
        NBTTagCompound data = new NBTTagCompound();
        original.write(data);
        data.setString("qioFrequencyUUID", uuid.toString());
        return new QIOFrequency(data);
    }

    private static final class TestContainer extends MekanismTileContainer<TileEntityContainerBlock> {

        private TestContainer() {
            super(null, null);
        }
    }

    private static final class TestAutomationTile extends TileEntityContainerBlock {

        private final DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(this);

        private TestAutomationTile() {
            super("qio_automation_container_sync_test");
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ||
                  super.hasCapability(capability, side);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> capability,
              @Nullable EnumFacing side) {
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
                  (T) host : super.getCapability(capability, side);
        }
    }
}
