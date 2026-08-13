package mekanism.qioprocessing.common.machine;

import mekanism.api.Action;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOTransferResult;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.api.machine.MachineActivitySnapshot;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOOutputBufferEntry;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager.IsolatedNetworkException;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded main-thread controller for the QIO automatic output upgrade. */
public final class QIOAutomaticOutputService {

    public static final QIOAutomaticOutputService INSTANCE = new QIOAutomaticOutputService();
    private static final int MAX_DEVICES_PER_TICK = 64;
    private int deviceCursor;

    private QIOAutomaticOutputService() {
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient() ||
            !QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            return;
        }
        List<QIOAutomationDeviceRegistry.LoadedDevice> devices =
              QIOAutomationDeviceRegistry.INSTANCE.getOperationalDevices(
                    QIOAutomationMode.OUTPUT_ONLY);
        if (devices.isEmpty()) {
            deviceCursor = 0;
            return;
        }
        int start = Math.floorMod(deviceCursor, devices.size());
        int processed = 0;
        for (int offset = 0; offset < devices.size() && processed < MAX_DEVICES_PER_TICK; offset++, processed++) {
            QIOAutomationDeviceRegistry.LoadedDevice device = devices.get((start + offset) % devices.size());
            try {
                processDevice(device.mutableHost(), device.tile(),
                      Math.max(0, device.tile().getWorld().getTotalWorldTime()),
                      MekanismConfig.current().qioProcessing.automaticOutputTransferLimit.val());
            } catch (IsolatedNetworkException ignored) {
                // The network manager already emitted one frequency-level diagnostic.
            } catch (RuntimeException e) {
                Mekanism.logger.error("QIO automatic output controller failed for device {}",
                      device.host().getPersistentDeviceUUID(), e);
            }
        }
        deviceCursor = (start + processed) % devices.size();
    }

    /** Executes one bounded device step; package visibility keeps the transfer protocol directly testable. */
    void processDevice(DefaultQIOAutomationHost host, TileEntity tile, long tick, long transferLimit) {
        Objects.requireNonNull(host, "Automatic output host cannot be null");
        Objects.requireNonNull(tile, "Automatic output tile cannot be null");
        if (host.tile() != tile) {
            throw new IllegalArgumentException("Automatic output host is attached to a different tile");
        }
        if (tick < 0 || transferLimit <= 0) {
            throw new IllegalArgumentException("Automatic output tick and transfer limit must be non-negative/positive");
        }
        if (host.getState() != mekanism.qioprocessing.api.machine.QIOAutomationHost.State.ACTIVE &&
            host.getState() != mekanism.qioprocessing.api.machine.QIOAutomationHost.State.DRAINING_CHANGE ||
            host.getEnabledMode() != QIOAutomationMode.OUTPUT_ONLY) {
            return;
        }
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference == null) {
            return;
        }
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
              reference.getFrequencyUUID()) != null) {
            return;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        if (provider == null || !provider.validateQIOConformance(QIOAutomationMode.OUTPUT_ONLY).isConformant()) {
            host.enterDataError("Automatic output provider no longer satisfies OUTPUT_ONLY conformance");
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
              reference.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(reference.getFrequencyName(),
                    reference.getOwnerUUID(), reference.getSecurityMode()));
        if (recoverRolledBackOutputOperation(host, provider, network, tick)) {
            return;
        }
        settleReceiptedTransfers(host, network);
        pruneSettledOperations(host, network);

        if (!host.getOutputBufferEntries().isEmpty()) {
            QIOOutputBufferEntry entry = host.getOutputBufferEntries().values().iterator().next();
            if (entry.phase() == QIOOutputBufferEntry.Phase.PREPARED) {
                resumeMachineExtraction(host, provider, network, entry);
            } else {
                reconcileHeldMachineTransfer(network, host, entry);
                deliverToQIO(host, network, entry, reference, transferLimit);
            }
            return;
        }
        if (host.getState() ==
            mekanism.qioprocessing.api.machine.QIOAutomationHost.State.ACTIVE) {
            startMachineExtraction(host, provider, network, tick, transferLimit);
        }
    }

    private boolean recoverRolledBackOutputOperation(DefaultQIOAutomationHost host,
          MachineRecipeProviderRegistry.BoundProvider provider, QIOProcessingNetworkData network,
          long tick) {
        if (!host.getOutputBufferEntries().isEmpty()) {
            return false;
        }
        String source = "machine/" + host.getPersistentDeviceUUID() + '/';
        QIODurableTransferRecord candidate = null;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
                transfer.getOwnerJobId() != null || transfer.getLeaseId() == null ||
                transfer.getMachineBaselines().size() != 1 || !transfer.getSource().startsWith(source)) {
                continue;
            }
            if (host.getOperationTokens().containsKey(transfer.getOwnerOperationId())) {
                continue;
            }
            if (candidate != null) {
                host.enterDataError("Multiple rolled-back automatic output operations require recovery");
                return true;
            }
            candidate = transfer;
        }
        if (candidate == null) {
            return false;
        }
        MachinePortBaseline baseline = candidate.getMachineBaselines().get(0);
        MachinePort port = findPort(provider.getPorts(), baseline.portId());
        if (port == null || baseline.contents() == null || candidate.getResources().size() != 1) {
            host.enterDataError("Rolled-back automatic output operation has no recoverable port baseline");
            return true;
        }
        Map.Entry<PortableResourceDescriptor, Long> resource =
              candidate.getResources().entrySet().iterator().next();
        MachineResourceStack expected = baseline.contents().withAmount(resource.getValue());
        if (describe(expected) == null || !resource.getKey().equals(describe(expected)) ||
            !baseline.matches(port) && !baseline.matchesAfterExtraction(port, expected)) {
            host.enterDataError("Rolled-back automatic output port changed before recovery");
            return true;
        }
        UUID operationId = candidate.getOwnerOperationId();
        UUID leaseId = candidate.getLeaseId();
        MachineOperationLease lease = host.tryAcquireLease(leaseId, operationId,
              MachineOperationLease.Mode.OUTPUT_DRAIN, Math.max(0, port.laneId()), tick,
              candidate.getMachineBaselines());
        if (lease == null || !host.attachOperationToken(MachineOperationToken.outputDrain(
              operationId, leaseId, Math.max(0, port.laneId()))) ||
            !host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
                  MachineOperationLease.State.COLLECTING)) {
            host.enterDataError("Unable to restore rolled-back automatic output ownership");
            return true;
        }
        QIOOutputBufferEntry entry = QIOOutputBufferEntry.prepared(candidate.getTransferId(),
              operationId, leaseId, baseline, expected, tick);
        if (!host.prepareOutputBuffer(entry)) {
            host.enterDataError("Unable to restore rolled-back automatic output buffer");
            return true;
        }
        try {
            if (baseline.matches(port)) {
                if (candidate.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
                    candidate.restartAfterEndpointRollback();
                    network.markTransferChanged(candidate.getTransferId());
                    flush(network);
                }
                extractPreparedOutput(host, network, entry, port, expected, resource.getKey());
            } else if (host.holdOutput(entry.bufferId(), resource.getKey(), resource.getValue())) {
                completeMachineTransfer(network, candidate, host, entry);
            } else {
                host.enterDataError("Unable to restore extracted automatic output buffer");
            }
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to recover rolled-back QIO automatic output for device {}",
                  host.getPersistentDeviceUUID(), e);
        }
        return true;
    }

    private void startMachineExtraction(DefaultQIOAutomationHost host,
          MachineRecipeProviderRegistry.BoundProvider provider, QIOProcessingNetworkData network,
          long tick, long transferLimit) {
        for (MachinePort port : provider.getPorts()) {
            if (port.isConfiguration() || !port.role().allowsOutput()) {
                continue;
            }
            MachineResourceStack output = port.peek();
            if (output == null) {
                continue;
            }
            MachineResourceStack extraction = output.withAmount(Math.min(output.amount(), transferLimit));
            PortableResourceDescriptor resource = describe(extraction);
            if (resource == null) {
                host.enterDataError("Automatic output port contains an unresolvable resource: " + port.portId());
                return;
            }
            UUID operationId = UUID.randomUUID();
            UUID leaseId = UUID.randomUUID();
            UUID bufferId = UUID.randomUUID();
            MachinePortBaseline baseline = MachinePortBaseline.capture(port);
            MachineOperationLease lease = host.tryAcquireLease(leaseId, operationId,
                  MachineOperationLease.Mode.OUTPUT_DRAIN, Math.max(0, port.laneId()), tick,
                  Collections.singletonList(baseline));
            if (lease == null) {
                continue;
            }
            if (!host.attachOperationToken(MachineOperationToken.outputDrain(operationId, leaseId,
                  Math.max(0, port.laneId()))) ||
                !host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
                      MachineOperationLease.State.COLLECTING)) {
                host.contaminateLease(leaseId, "Unable to establish automatic output operation token");
                return;
            }
            QIOOutputBufferEntry entry = QIOOutputBufferEntry.prepared(bufferId, operationId, leaseId,
                  baseline, extraction, tick);
            if (!host.prepareOutputBuffer(entry)) {
                host.contaminateLease(leaseId, "Unable to persist automatic output buffer preparation");
                return;
            }
            try {
                ensureMachineTransfer(network, host, entry, resource, extraction.amount());
                flush(network);
                extractPreparedOutput(host, network, entry, port, extraction, resource);
            } catch (IOException | RuntimeException e) {
                Mekanism.logger.error("Unable to prepare QIO automatic output transfer for device {}",
                      host.getPersistentDeviceUUID(), e);
            }
            return;
        }
    }

    private void resumeMachineExtraction(DefaultQIOAutomationHost host,
          MachineRecipeProviderRegistry.BoundProvider provider, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry) {
        MachinePort port = findPort(provider.getPorts(), entry.baseline().portId());
        MachineResourceStack expected = entry.extraction();
        if (port == null || expected == null) {
            host.contaminateLease(entry.leaseId(), "Prepared automatic output port is no longer available");
            return;
        }
        PortableResourceDescriptor resource = describe(expected);
        if (resource == null) {
            host.contaminateLease(entry.leaseId(), "Prepared automatic output resource can no longer be resolved");
            return;
        }
        try {
            QIODurableTransferRecord transfer = ensureMachineTransfer(network, host, entry, resource, expected.amount());
            flush(network);
            if (entry.baseline().matches(port)) {
                if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
                    // The machine inventory and output buffer share one Tile NBT boundary. An
                    // unchanged baseline with a PREPARED buffer proves that boundary rolled back
                    // before extraction, even if the independently saved network log advanced.
                    transfer.restartAfterEndpointRollback();
                    network.markTransferChanged(transfer.getTransferId());
                    flush(network);
                }
                extractPreparedOutput(host, network, entry, port, expected, resource);
                return;
            }
            if (entry.baseline().matchesAfterExtraction(port, expected)) {
                // The source was debited before the capability buffer reached disk. The persisted transfer and exact
                // post-extraction observation make replaying the destination credit safe, including partial drains.
                if (host.holdOutput(entry.bufferId(), resource, expected.amount())) {
                    completeMachineTransfer(network, transfer, host, entry);
                }
            } else {
                host.contaminateLease(entry.leaseId(), "Prepared output baseline changed outside its lease");
            }
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to resume QIO automatic output extraction for device {}",
                  host.getPersistentDeviceUUID(), e);
        }
    }

    private void extractPreparedOutput(DefaultQIOAutomationHost host, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry, MachinePort port, MachineResourceStack expected,
          PortableResourceDescriptor resource) throws IOException {
        if (!entry.baseline().matches(port)) {
            host.contaminateLease(entry.leaseId(), "Automatic output port changed before extraction");
            return;
        }
        QIODurableTransferRecord transfer = ensureMachineTransfer(network, host, entry, resource, expected.amount());
        MachineResourceStack extracted = port.extract(expected);
        if (extracted == null || extracted.amount() != expected.amount() || !extracted.sameResource(expected)) {
            host.contaminateLease(entry.leaseId(), "Automatic output extraction did not match its prepared baseline");
            return;
        }
        if (!host.holdOutput(entry.bufferId(), resource, extracted.amount())) {
            throw new IllegalStateException("Extracted automatic output could not be credited to its persistent buffer");
        }
        completeMachineTransfer(network, transfer, host, entry);
    }

    private void deliverToQIO(DefaultQIOAutomationHost host, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry, QIOFrequencyReference reference, long transferLimit) {
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(reference, reference.getBindingPlayerUUID());
        if (view == null) {
            return;
        }
        try {
            QIOOutputBufferEntry delivering = entry;
            QIODurableTransferRecord transfer;
            if (entry.phase() == QIOOutputBufferEntry.Phase.HELD) {
                    transfer = findDeliveryTransfer(network, host, entry);
                long insertable;
                if (transfer == null) {
                    insertable = simulateInsert(view, entry.resource(), Math.min(entry.amount(), transferLimit));
                    if (insertable <= 0) {
                        host.updateActivitySnapshot(new MachineActivitySnapshot(
                              host.getOperationTokens().get(entry.operationId()).laneId(), entry.operationId(), "",
                              MachineActivitySnapshot.State.OUTPUT_BLOCKED, 0, 0, 0, "qio_full"));
                        return;
                    }
                    UUID transferId = UUID.randomUUID();
                    transfer = createDeliveryTransfer(host, entry, transferId, insertable, view);
                    network.addDurableTransfer(transfer);
                    flush(network);
                } else {
                    insertable = transfer.getResources().getOrDefault(entry.resource(), 0L);
                }
                if (!host.beginOutputDelivery(entry.bufferId(), transfer.getTransferId(), insertable)) {
                    throw new IllegalStateException("Unable to persist automatic output delivery state");
                }
                delivering = host.getOutputBufferEntries().get(entry.bufferId());
            } else {
                transfer = requireDeliveryTransfer(network, entry);
            }

            if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
                transfer.markSourceDebited("output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId(),
                      host.getConfigurationRevision());
                network.markTransferChanged(transfer.getTransferId());
                flush(network);
            }
            QIOTransferResult result = insert(view, delivering.resource(), delivering.qioTransferId(),
                  delivering.qioRequestedAmount(), transfer.getQIOBaselines().get(delivering.resource()));
            if (!result.isSuccess()) {
                if (result.getStatus() == QIOTransferResult.Status.TRANSFER_ID_CONFLICT ||
                    result.getStatus() == QIOTransferResult.Status.INVALID_REQUEST ||
                    result.getStatus() == QIOTransferResult.Status.RECONCILIATION_CONFLICT) {
                    host.contaminateLease(entry.leaseId(), "QIO rejected the durable automatic output transfer");
                }
                return;
            }
            if (result.getTransferredAmount() != delivering.qioRequestedAmount()) {
                host.contaminateLease(entry.leaseId(), "QIO automatic output receipt amount disagrees with its request");
                return;
            }
            if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
                transfer.markDestinationCredited("qio/" + reference.getFrequencyUUID() + "/receipt/" +
                      result.getTransferId());
                network.markTransferChanged(transfer.getTransferId());
                flush(network);
            }
            if (!host.applyOutputDeliveryReceipt(entry.bufferId(), result.getTransferId(),
                  result.getTransferredAmount())) {
                throw new IllegalStateException("Unable to apply QIO automatic output receipt to its source buffer");
            }
            if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
                transfer.commitForward();
                network.markTransferChanged(transfer.getTransferId());
                flush(network);
            }
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to deliver QIO automatic output for device {}",
                  host.getPersistentDeviceUUID(), e);
        } finally {
            view.close();
        }
    }

    private QIODurableTransferRecord ensureMachineTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry, PortableResourceDescriptor resource,
          long amount) {
        QIODurableTransferRecord existing = network.getDurableTransfer(entry.bufferId());
        if (existing != null) {
            if (existing.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
                !entry.operationId().equals(existing.getOwnerOperationId()) ||
                !Collections.singletonMap(resource, amount).equals(existing.getResources())) {
                throw new IllegalStateException("Automatic output machine transfer identity conflict");
            }
            return existing;
        }
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(entry.bufferId(), UUID.randomUUID(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, null, entry.operationId(), 0,
              "output/" + entry.baseline().portId(), entry.leaseId(),
              "machine/" + host.getPersistentDeviceUUID() + '/' + entry.baseline().portGroupId(),
              "output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId(),
              Collections.singletonMap(resource, amount), Collections.singletonList(entry.baseline()));
        network.addDurableTransfer(transfer);
        return transfer;
    }

    private void reconcileHeldMachineTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry) {
        QIODurableTransferRecord transfer = network.getDurableTransfer(entry.bufferId());
        if (transfer == null) {
            return;
        }
        PortableResourceDescriptor resource = entry.resource();
        if (resource == null || transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
            !entry.operationId().equals(transfer.getOwnerOperationId()) ||
            !Collections.singletonMap(resource, entry.extraction().amount()).equals(transfer.getResources())) {
            host.contaminateLease(entry.leaseId(), "Held output disagrees with its durable machine transfer");
            return;
        }
        try {
            completeMachineTransfer(network, transfer, host, entry);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to reconcile a held automatic output transfer", e);
        }
    }

    private void completeMachineTransfer(QIOProcessingNetworkData network, QIODurableTransferRecord transfer,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry) throws IOException {
        boolean changed = false;
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            transfer.markSourceDebited("machine/" + host.getPersistentDeviceUUID() + "/lease/" + entry.leaseId(),
                  host.getConfigurationRevision());
            changed = true;
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            transfer.markDestinationCredited("output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId());
            changed = true;
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            transfer.commitForward();
            changed = true;
        }
        if (changed) {
            network.markTransferChanged(transfer.getTransferId());
            flush(network);
        }
    }

    private QIODurableTransferRecord createDeliveryTransfer(DefaultQIOAutomationHost host,
          QIOOutputBufferEntry entry, UUID transferId, long amount, IQIOStorageView view) {
        return new QIODurableTransferRecord(transferId, UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
              null, entry.operationId(), 0, "automatic-output", entry.leaseId(),
              "output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId(),
              "qio/" + view.getFrequencyUUID(), Collections.singletonMap(entry.resource(), amount),
              Collections.singletonMap(entry.resource(), storedAmount(view, entry.resource())));
    }

    private QIODurableTransferRecord requireDeliveryTransfer(QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry) {
        QIODurableTransferRecord transfer = network.getDurableTransfer(entry.qioTransferId());
        if (transfer == null || transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
            !entry.operationId().equals(transfer.getOwnerOperationId()) ||
            !Collections.singletonMap(entry.resource(), entry.qioRequestedAmount()).equals(transfer.getResources())) {
            throw new IllegalStateException("Missing or mismatched automatic output QIO delivery transfer");
        }
        return transfer;
    }

    @Nullable
    private QIODurableTransferRecord findDeliveryTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry) {
        QIODurableTransferRecord pending = null;
        QIODurableTransferRecord replay = null;
        MachineOperationToken token = host.getOperationTokens().get(entry.operationId());
        String source = "output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId();
        String destination = "qio/" + network.getFrequencyUUID();
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO &&
                entry.operationId().equals(transfer.getOwnerOperationId()) &&
                entry.leaseId().equals(transfer.getLeaseId()) && source.equals(transfer.getSource()) &&
                destination.equals(transfer.getDestination()) &&
                transfer.getResources().size() == 1 &&
                transfer.getResources().getOrDefault(entry.resource(), 0L) > 0 &&
                transfer.getResources().get(entry.resource()) <= entry.amount()) {
                if (transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                    if (pending != null) {
                        throw new IllegalStateException("Output buffer has multiple pending QIO delivery transfers");
                    }
                    pending = transfer;
                } else if (transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED &&
                      token != null && !token.hasTransferReceipt(transfer.getTransferId())) {
                    if (replay != null) {
                        throw new IllegalStateException("Output buffer has multiple unacknowledged QIO delivery transfers");
                    }
                    replay = transfer;
                }
            }
        }
        if (pending != null && replay != null) {
                    throw new IllegalStateException("Output buffer has multiple pending QIO delivery transfers");
        }
        return pending == null ? replay : pending;
    }

    private void settleReceiptedTransfers(DefaultQIOAutomationHost host, QIOProcessingNetworkData network) {
        boolean changed = false;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
                transfer.getPhase() != QIODurableTransferRecord.Phase.DESTINATION_CREDITED ||
                transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE) {
                continue;
            }
            MachineOperationToken token = host.getOperationTokens().get(transfer.getOwnerOperationId());
            if (token != null && token.hasTransferReceipt(transfer.getTransferId())) {
                transfer.commitForward();
                network.markTransferChanged(transfer.getTransferId());
                changed = true;
            }
        }
        if (changed) {
            try {
                flush(network);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to settle recovered automatic output transfers", e);
            }
        }
    }

    private void pruneSettledOperations(DefaultQIOAutomationHost host, QIOProcessingNetworkData network) {
        for (MachineOperationToken token : host.getOperationTokens().values().toArray(new MachineOperationToken[0])) {
            if (token.state() != MachineOperationToken.State.COMPLETED ||
                !host.isPersistedCompletedOperation(token.operationId())) {
                continue;
            }
            boolean pending = false;
            for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
                if (token.operationId().equals(transfer.getOwnerOperationId()) &&
                    transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                    pending = true;
                    break;
                }
            }
            if (!pending) {
                try {
                    network.removeCommittedOperationTransfers(token.operationId());
                    flush(network);
                    host.forgetSettledOperation(token.operationId());
                } catch (IOException | RuntimeException e) {
                    throw new IllegalStateException(
                          "Unable to prune a durably settled automatic output operation", e);
                }
            }
        }
    }

    private static long simulateInsert(IQIOStorageView view, PortableResourceDescriptor resource, long amount) {
        if (resource == null || amount <= 0) {
            return 0;
        }
        return switch (resource.getKind()) {
            case ITEM -> view.insert(resource.resolveItem(), amount, Action.SIMULATE);
            case FLUID -> view.insert(resource.resolveFluid(), amount, Action.SIMULATE);
            case GAS -> view.insert(resource.resolveGas(), amount, Action.SIMULATE);
        };
    }

    private static QIOTransferResult insert(IQIOStorageView view, PortableResourceDescriptor resource,
          UUID transferId, long amount, @Nonnull BigInteger baseline) {
        Objects.requireNonNull(resource, "Output resource cannot be null");
        Objects.requireNonNull(transferId, "Output transfer id cannot be null");
        Objects.requireNonNull(baseline, "Output transfer baseline cannot be null");
        return switch (resource.getKind()) {
            case ITEM -> view.insertIdempotent(transferId, resource.resolveItem(), amount, baseline);
            case FLUID -> view.insertIdempotent(transferId, resource.resolveFluid(), amount, baseline);
            case GAS -> view.insertIdempotent(transferId, resource.resolveGas(), amount, baseline);
        };
    }

    private static BigInteger storedAmount(IQIOStorageView view,
          PortableResourceDescriptor resource) {
        BigInteger amount = BigInteger.ZERO;
        UUID matched = null;
        for (QIOStorageEntry entry : view.getSnapshot().getEntries()) {
            if (!resource.equals(PortableResourceDescriptor.fromStorageEntry(entry))) {
                continue;
            }
            if (matched != null && !matched.equals(entry.getResourceUUID())) {
                throw new IllegalStateException(
                      "QIO snapshot maps one portable resource to multiple UUIDs");
            }
            matched = entry.getResourceUUID();
            amount = entry.getExactStoredAmount();
        }
        return amount;
    }

    @Nullable
    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        try {
            return switch (stack.kind()) {
                case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
                case FLUID -> PortableResourceDescriptor.fluid(stack.fluidStack());
                case GAS -> PortableResourceDescriptor.gas(stack.gasStack());
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static MachinePort findPort(List<MachinePort> ports, String portId) {
        for (MachinePort port : ports) {
            if (port.portId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    private static void flush(QIOProcessingNetworkData network) throws IOException {
        QIOProcessingNetworkManager.INSTANCE.checkpointNetwork(network.getFrequencyUUID());
    }
}
