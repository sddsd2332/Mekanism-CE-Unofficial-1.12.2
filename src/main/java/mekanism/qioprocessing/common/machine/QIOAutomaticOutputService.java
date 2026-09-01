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
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * QIO 自动输出升级的机器本地控制器。
 *
 * <p>输出在机器组件执行前通过持久缓冲、租约和 durable transfer 回传 QIO。
 * 正常路径与崩溃恢复共用同一协议，避免 QIO 已入账但调用方误判失败时恢复机器
 * 快照而复制资源。</p>
 */
public final class QIOAutomaticOutputService {

    public static final QIOAutomaticOutputService INSTANCE = new QIOAutomaticOutputService();
    /** 单台设备在一轮中最多建立的持久输出操作数，避免异常 Provider 无限产生日志/租约。 */
    private static final int MAX_OUTPUT_OPERATIONS_PER_TICK = 256;

    private QIOAutomaticOutputService() {
    }

    /**
     * Runs automatic output from the machine's own pre-component tick.
     *
     * <p>Every output which is accepted by QIO uses the durable protocol.  A production tick
     * probes the endpoint before acquiring a lease: a full or unavailable QIO must not create
     * ownership for an item which is still in the machine, so the ordinary ejector can continue
     * to serve that output.  Existing buffers and transfers always bypass this probe and use the
     * recovery protocol.</p>
     */
    void tickDevice(DefaultQIOAutomationHost host, TileEntity tile, long tick) {
        Objects.requireNonNull(host, "Automatic output host cannot be null");
        Objects.requireNonNull(tile, "Automatic output tile cannot be null");
        if (host.tile() != tile) {
            throw new IllegalArgumentException("Automatic output host is attached to a different tile");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Automatic output tick cannot be negative");
        }
        processDevice(host, tile, tick, true, true);
    }

    /**
     * 执行一次有界的机器输出步骤，包可见性便于协议单元测试。
     *
     * @param host 机器自动化主机
     * @param tile 与主机关联的机器方块
     * @param tick 当前游戏 tick
     */
    void processDevice(DefaultQIOAutomationHost host, TileEntity tile, long tick) {
        processDevice(host, tile, tick, true, false);
    }

    private void processDevice(DefaultQIOAutomationHost host, TileEntity tile, long tick,
          boolean startNewOperations, boolean preflightNewOperations) {
        Objects.requireNonNull(host, "Automatic output host cannot be null");
        Objects.requireNonNull(tile, "Automatic output tile cannot be null");
        if (host.tile() != tile) {
            throw new IllegalArgumentException("Automatic output host is attached to a different tile");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Automatic output tick cannot be negative");
        }
        if ((host.getState() != mekanism.qioprocessing.api.machine.QIOAutomationHost.State.ACTIVE &&
            host.getState() != mekanism.qioprocessing.api.machine.QIOAutomationHost.State.DRAINING_CHANGE) ||
            host.getEnabledMode() != QIOAutomationMode.OUTPUT_ONLY) {
            return;
        }
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference == null) {
            return;
        }
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
              reference.getFrequencyUUID()) != null) {
            pauseOutputRecoveryOrRetry(host, "processing network is isolated");
            return;
        }
        MachineRecipeProviderRegistry.BoundProvider provider;
        try {
            provider = MachineRecipeProviderRegistry.find(tile);
            if (provider == null || !provider.validateQIOConformance(
                  QIOAutomationMode.OUTPUT_ONLY).isConformant()) {
                pauseOutputRecoveryOrRetry(host, "automatic output provider is unavailable");
                return;
            }
        } catch (RuntimeException e) {
            // A dynamic provider can transiently throw while its ports are being rebuilt.
            // This is an availability condition, not a host data error.
            pauseOutputRecoveryOrRetry(host, "automatic output provider is rebuilding");
            return;
        }
        final List<MachinePort> ports;
        try {
            // Use one port snapshot for the whole device step. Calling a dynamic provider
            // repeatedly can observe two different lane layouts during a rebuild.
            ports = provider.getPorts();
        } catch (RuntimeException e) {
            pauseOutputRecoveryOrRetry(host, "automatic output ports are unavailable");
            return;
        }
        if (ports == null) {
            pauseOutputRecoveryOrRetry(host, "automatic output provider returned no port view");
            return;
        }
        host.clearRetryPending();
        QIOProcessingNetworkData network;
        try {
            if (host.hasDeferredOutputRecovery()) {
                // Deferred ownership must reconcile only against the already-loaded network.
                // Materializing it through getOrCreate would reset a DAMAGED network and could
                // erase the durable transfer needed to decide who owns the machine output.
                if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
                      reference.getFrequencyUUID()) != null) {
                    pauseOutputRecoveryOrRetry(host, "processing network is isolated");
                    return;
                }
                network = QIOProcessingNetworkManager.INSTANCE.get(
                      reference.getFrequencyUUID());
                if (network == null) {
                    pauseOutputRecoveryOrRetry(host, "processing network is unavailable");
                    return;
                }
            } else {
                network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
                      reference.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                            reference.getFrequencyName(), reference.getOwnerUUID(),
                            reference.getSecurityMode()));
            }
        } catch (RuntimeException e) {
            // The processing network may be reloading independently from the machine tile.
            // Keep the machine-side output untouched and retry after the network is ready.
            pauseOutputRecoveryOrRetry(host, "processing network is reloading");
            return;
        }
        if (host.hasDeferredOutputRecovery()) {
            DefaultQIOAutomationHost.DeferredOutputRecoveryResult recovery =
                  host.attemptDeferredOutputRecovery();
            if (recovery != DefaultQIOAutomationHost.DeferredOutputRecoveryResult.NONE) {
                return;
            }
        }
        if (recoverRolledBackOutputOperation(host, ports, network, tick)) {
            return;
        }
        // A failed operation can leave only an output token/lease behind when preparation
        // failed before the persistent buffer or durable transfer was created.  There is no
        // resource ownership to recover in that case, but the old contamination marker would
        // otherwise block every subsequent output attempt forever.
        clearUnownedOutputOperation(host, ports, network);
        try {
            settleReceiptedTransfers(host, network);
            pruneSettledOperations(host, network);
        } catch (RuntimeException e) {
            // Receipt settlement/pruning is part of the same durable output boundary. If its
            // checkpoint is unavailable, retain local ownership and yield ordinary output
            // through the normal quarantine path instead of escaping the pre-component hook.
            pauseOutputRecoveryOrRetry(host, "automatic output settlement checkpoint is unavailable");
            Mekanism.logger.warn("Unable to settle QIO automatic output history for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
            return;
        }

        /*
         * 一台机器可能同时暴露几十个独立输出端口，也可能通过一个 itemGroup 暴露
         * 多种物品。旧实现只取 map 的第一个缓冲，导致每个端口都要等待下一 tick。
         * 这里先处理一份稳定快照中的全部已有缓冲；端口发生变化时只使用当前 map
         * 中仍存在的条目，避免遍历期间修改集合。
         */
        Set<String> blockedGroups = new HashSet<>();
        List<QIOOutputBufferEntry> pendingBuffers = new ArrayList<>(
              host.getOutputBufferEntries().values());
        for (QIOOutputBufferEntry pending : pendingBuffers) {
            QIOOutputBufferEntry entry = host.getOutputBufferEntries().get(pending.bufferId());
            if (entry == null) {
                continue;
            }
            String groupId = entry.baseline().portGroupId();
            boolean wasPrepared = entry.phase() == QIOOutputBufferEntry.Phase.PREPARED;
            if (wasPrepared) {
                resumeMachineExtraction(host, ports, network, entry);
                entry = host.getOutputBufferEntries().get(entry.bufferId());
            }
            // Once the machine-to-buffer transfer is committed, the durable boundary is
            // complete and the buffer can be delivered in this same tick. A transient QIO
            // capacity/provider failure still leaves the entry held and blocks only its group.
            if (entry != null && entry.phase() != QIOOutputBufferEntry.Phase.PREPARED &&
                  reconcileHeldMachineTransfer(network, host, ports, entry)) {
                deliverToQIO(host, network, entry, reference);
                entry = host.getOutputBufferEntries().get(entry.bufferId());
            }
            if (entry != null) {
                // 未完成缓冲仍然独占其共享端口组；同组不能在同一轮建立第二个 lease。
                blockedGroups.add(groupId);
            }
        }
        if (startNewOperations && host.getState() ==
            mekanism.qioprocessing.api.machine.QIOAutomationHost.State.ACTIVE) {
            startMachineExtraction(host, ports, network, tick, reference,
                  blockedGroups, outputOperationLimit(ports), preflightNewOperations);
        }
    }

    /** 从网络中的孤立传输记录恢复尚未重新登记的机器输出操作。 */
    private boolean recoverRolledBackOutputOperation(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network,
          long tick) {
        if (!host.getOutputBufferEntries().isEmpty()) {
            return false;
        }
        String source = "machine/" + host.getPersistentDeviceUUID() + '/';
        String destination = "output-buffer/" + host.getPersistentDeviceUUID() + '/';
        QIODurableTransferRecord candidate = null;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
                transfer.getOwnerJobId() != null || transfer.getLeaseId() == null ||
                transfer.getMachineBaselines().size() != 1 || !transfer.getSource().startsWith(source) ||
                !transfer.getDestination().startsWith(destination) || transfer.getPlanRevision() != 0 ||
                !transfer.getNodeId().startsWith("output/")) {
                continue;
            }
            if (host.getOperationTokens().containsKey(transfer.getOwnerOperationId())) {
                continue;
            }
            if (candidate != null) {
                host.markRecoveryPending("Multiple rolled-back automatic output operations require recovery");
                return true;
            }
            candidate = transfer;
        }
        if (candidate == null) {
            return false;
        }
        MachinePortBaseline baseline = candidate.getMachineBaselines().get(0);
        MachinePort port = findPort(ports, baseline.portId());
        if (port == null || baseline.contents() == null || candidate.getResources().size() != 1) {
            host.markRecoveryPending("Rolled-back automatic output operation has no recoverable port baseline");
            return true;
        }
        Map.Entry<PortableResourceDescriptor, Long> resource =
              candidate.getResources().entrySet().iterator().next();
        MachineResourceStack expected = baseline.contents().withAmount(resource.getValue());
        if (describe(expected) == null || !resource.getKey().equals(describe(expected)) ||
            !baseline.matches(port) && !baseline.matchesAfterExtraction(port, expected)) {
            host.markRecoveryPending("Rolled-back automatic output port changed before recovery");
            return true;
        }
        boolean sourceIntact = baseline.matches(port);
        boolean sourceExtracted = baseline.matchesAfterExtraction(port, expected);
        boolean settledForwardTransfer = candidate.getPhase() ==
              QIODurableTransferRecord.Phase.COMMITTED &&
              candidate.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED;
        if ((candidate.getResolution() != QIODurableTransferRecord.Resolution.NONE ||
              candidate.getPhase() == QIODurableTransferRecord.Phase.ROLLBACK_REQUIRED) &&
              !(settledForwardTransfer && sourceIntact)) {
            // A settled or rollback-required transfer has crossed a durable ownership
            // boundary. Replaying it is only safe for the explicit whole-tile rollback case
            // where the exact original machine baseline is present again; otherwise leave it
            // quarantined for the network audit path instead of risking a second debit.
            host.markRecoveryPending("Rolled-back automatic output transfer requires durable audit");
            return true;
        }
        if (!sourceIntact && sourceExtracted &&
              candidate.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
              candidate.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
              candidate.getSourceReceipt() == null && candidate.getDestinationReceipt() == null) {
            // A PREPARED orphan has no durable source receipt. If the exact post-extraction
            // baseline is already visible, the ordinary ejector may have taken the stack while
            // the local capability was missing; discard only this unreceipted intent instead
            // of reconstructing a second QIO copy.
            try {
                network.discardPreparedGenericTransfer(candidate.getTransferId());
                flush(network);
            } catch (IOException | RuntimeException e) {
                host.markRetryPending();
                Mekanism.logger.warn("Unable to discard externally ejected rolled-back automatic output for device {}; retrying",
                      host.getPersistentDeviceUUID(), e);
            }
            return true;
        }
        UUID operationId = candidate.getOwnerOperationId();
        UUID leaseId = candidate.getLeaseId();
        QIOOutputBufferEntry entry;
        try {
            entry = QIOOutputBufferEntry.prepared(candidate.getTransferId(), operationId, leaseId,
                  baseline, expected, tick);
        } catch (RuntimeException e) {
            host.markRecoveryPending("Rolled-back automatic output buffer is malformed");
            return true;
        }
        if (!machineTransferMatches(candidate, host, entry, resource.getKey(), expected.amount())) {
            host.markRecoveryPending("Rolled-back automatic output transfer identity is invalid");
            return true;
        }
        MachineOperationLease lease = host.tryAcquireLease(leaseId, operationId,
              MachineOperationLease.Mode.OUTPUT_DRAIN, Math.max(0, port.laneId()), tick,
              candidate.getMachineBaselines());
        if (lease == null || !host.attachOperationToken(MachineOperationToken.outputDrain(
              operationId, leaseId, Math.max(0, port.laneId()))) ||
            !host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
                  MachineOperationLease.State.COLLECTING)) {
            host.markRecoveryPending("Unable to restore rolled-back automatic output ownership");
            return true;
        }
        if (!host.prepareOutputBuffer(entry)) {
            host.markRecoveryPending("Unable to restore rolled-back automatic output buffer");
            return true;
        }
        host.clearRecoveryPending();
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
                host.markRecoveryPending("Unable to restore extracted automatic output buffer");
            }
            } catch (IOException e) {
                pauseOutputRecoveryOrRetry(host,
                      "automatic output rollback checkpoint is unavailable");
                Mekanism.logger.warn("Unable to recover rolled-back QIO automatic output for device {}; retrying",
                      host.getPersistentDeviceUUID(), e);
            } catch (RuntimeException e) {
                if (isStructuralFailure(e)) {
                    host.markRecoveryPending("Automatic output rollback recovery failed: " + diagnostic(e));
                } else {
                    pauseOutputRecoveryOrRetry(host,
                          "automatic output rollback recovery endpoint is rebuilding");
                }
                Mekanism.logger.error("Unable to recover rolled-back QIO automatic output for device {}",
                      host.getPersistentDeviceUUID(), e);
            }
        return true;
    }

    /**
     * 在一轮中批量建立机器输出操作。
     *
     * <p>每次成功抽取后重新读取端口快照，并在机器到缓冲 transfer 已提交时立即尝试
     * 写回 QIO。独立端口和同一 itemGroup 中的多个资源因此可以在同一 tick 内推进；
     * 若 QIO 暂时无容量，未完成的缓冲会锁住该组并留到下一 tick 重试。</p>
     */
    private int startMachineExtraction(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network, long tick,
          QIOFrequencyReference reference, Set<String> blockedGroups, int operationLimit,
          boolean preflightNewOperations) {
        int started = 0;
        boolean progress;
        boolean endpointBlocked = false;
        do {
            progress = false;
            for (MachinePort port : ports) {
                if (started >= operationLimit ||
                      port == null || port.isConfiguration() || !port.role().allowsOutput()) {
                    continue;
                }
                String groupId = port.portGroupId();
                if (blockedGroups.contains(groupId) || isPortGroupOccupied(host, groupId)) {
                    blockedGroups.add(groupId);
                    continue;
                }
                List<MachineResourceStack> outputs;
                try {
                    outputs = port.peekAll();
                } catch (RuntimeException e) {
                    // A provider may rebuild its inventory between two calls. Keep all
                    // machine contents untouched and retry this group on a later tick.
                    host.markRetryPending();
                    blockedGroups.add(groupId);
                    continue;
                }
                if (outputs == null || outputs.isEmpty()) {
                    continue;
                }
                MachineResourceStack output = outputs.get(0);
                if (output == null || output.amount() <= 0) {
                    continue;
                }
                if (preflightNewOperations) {
                    long insertable = probeQIOCapacity(reference, output);
                    if (insertable < 0) {
                        // The endpoint could not be observed.  Do not acquire a lease for a
                        // still-local stack; retain the retry marker and leave ordinary
                        // ejection available for this tick.
                        host.markRetryPending();
                        endpointBlocked = true;
                        blockedGroups.add(groupId);
                        continue;
                    }
                    if (insertable == 0) {
                        // Full capacity is a normal back-pressure condition, not corruption.
                        // No local ownership is created, so the ordinary ejector may proceed.
                        blockedGroups.add(groupId);
                        continue;
                    }
                    if (insertable < output.amount()) {
                        output = output.withAmount(insertable);
                    }
                }
                QIOOutputBufferEntry created = startOneMachineExtraction(host, ports, network,
                      tick, port, output);
                if (created == null) {
                    // The operation may have been discarded as entirely local state, or it
                    // may have entered a retry/quarantine path. Either way do not spin on
                    // the same mutable port during this tick.
                    blockedGroups.add(groupId);
                    continue;
                }
                QIOOutputBufferEntry current = host.getOutputBufferEntries().get(created.bufferId());
                if (current != null && current.phase() != QIOOutputBufferEntry.Phase.PREPARED &&
                      reconcileHeldMachineTransfer(network, host, ports, current)) {
                    deliverToQIO(host, network, current, reference);
                    current = host.getOutputBufferEntries().get(current.bufferId());
                }
                if (current != null) {
                    // A buffer that could not be delivered remains the exclusive owner of this
                    // group. A fully delivered entry releases its lease and permits the next
                    // mixed resource to be handled in the same bounded loop.
                    blockedGroups.add(groupId);
                    if (preflightNewOperations) {
                        // A race or a transient storage failure after the probe left an owned
                        // buffer waiting for QIO.  Avoid creating more unbacked buffers this
                        // tick; the next tick will re-probe current capacity.
                        endpointBlocked = true;
                    }
                }
                started++;
                progress = true;
                // The port contents changed. Restart from the first provider entry so a
                // grouped port cannot use a stale resource list after a partial drain.
                break;
            }
        } while (progress && !endpointBlocked && started < operationLimit);
        return started;
    }

    /**
     * Probes the current QIO capacity for a new machine output without creating ownership.
     * A negative result means the endpoint could not be opened or queried; zero is a valid
     * full-capacity result.  The view is intentionally short lived because the durable transfer
     * created immediately afterwards is the authority across a concurrent QIO mutation.
     */
    private static long probeQIOCapacity(QIOFrequencyReference reference,
          MachineResourceStack output) {
        PortableResourceDescriptor resource = describe(output);
        if (resource == null || output.amount() <= 0) {
            return -1;
        }
        IQIOStorageView view = null;
        try {
            view = QIOFrequencyStorageAccess.INSTANCE.open(reference,
                  reference.getBindingPlayerUUID());
            return view == null ? -1 : simulateInsert(view, resource, output.amount());
        } catch (RuntimeException e) {
            return -1;
        } finally {
            if (view != null) {
                view.close();
            }
        }
    }

    /**
     * Existing output ownership must yield ordinary ejection when its QIO endpoint disappears;
     * a device with no buffered ownership keeps the lighter retry marker instead.
     */
    private static void pauseOutputRecoveryOrRetry(DefaultQIOAutomationHost host,
          String reason) {
        if (!host.pauseAutomaticOutputCollection(reason)) {
            host.markRetryPending();
        }
    }

    /**
     * 按 Provider 当前暴露的实际输出槽位/储罐数确定本 tick 的资源操作数。
     * 同一资源无论数量多少都只占一次操作；硬上限用于防止异常 Provider 暴露无限端口。
     */
    private static int outputOperationLimit(List<MachinePort> ports) {
        int units = 0;
        for (MachinePort port : ports) {
            if (port == null || port.isConfiguration() || !port.role().allowsOutput()) {
                continue;
            }
            int available = Math.max(1, port.storageUnitCount());
            if (units >= MAX_OUTPUT_OPERATIONS_PER_TICK - available) {
                return MAX_OUTPUT_OPERATIONS_PER_TICK;
            }
            units += available;
        }
        return units;
    }

    /** 为一个具体资源建立租约、token、缓冲和 durable transfer，并尝试抽取。 */
    @Nullable
    private QIOOutputBufferEntry startOneMachineExtraction(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network, long tick,
          MachinePort port, MachineResourceStack extraction) {
        PortableResourceDescriptor resource = describe(extraction);
        if (resource == null) {
            // A missing resource codec/mod is a retryable availability issue;
            // leave the machine untouched and do not expose a host error.
            host.markRetryPending();
            return null;
        }
        UUID operationId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID bufferId = UUID.randomUUID();
        MachinePortBaseline baseline;
        try {
            baseline = MachinePortBaseline.capture(port);
        } catch (RuntimeException e) {
            // Dynamic addon inventories can disappear between peekAll and baseline capture.
            // No ownership was acquired, so this is a pure retry condition.
            host.markRetryPending();
            Mekanism.logger.warn("Unable to capture QIO automatic output baseline for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
            return null;
        }
        MachineOperationLease lease;
        try {
            lease = host.tryAcquireLease(leaseId, operationId,
                  MachineOperationLease.Mode.OUTPUT_DRAIN, Math.max(0, port.laneId()), tick,
                  Collections.singletonList(baseline));
        } catch (RuntimeException e) {
            host.markRetryPending();
            Mekanism.logger.warn("Unable to acquire QIO automatic output lease for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
            return null;
        }
        if (lease == null) {
            return null;
        }
        boolean attached;
        try {
            attached = host.attachOperationToken(MachineOperationToken.outputDrain(operationId, leaseId,
                  Math.max(0, port.laneId())));
        } catch (RuntimeException e) {
            host.releaseUnattachedLease(leaseId);
            host.markRetryPending();
            Mekanism.logger.warn("Unable to attach QIO automatic output token for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
            return null;
        }
        if (!attached) {
            host.releaseUnattachedLease(leaseId);
            host.markRetryPending();
            return null;
        }
        boolean transitioned;
        boolean transitionRuntimeFailure = false;
        try {
            transitioned = host.transitionOperation(operationId, MachineOperationToken.State.COLLECTING,
                  MachineOperationLease.State.COLLECTING);
        } catch (RuntimeException e) {
            transitioned = false;
            transitionRuntimeFailure = true;
            host.markRetryPending();
            Mekanism.logger.warn("Unable to transition QIO automatic output operation for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
        }
        if (!transitioned) {
            if (!discardUnownedOutputOperation(host, ports, network, operationId) &&
                  !transitionRuntimeFailure) {
                host.contaminateLease(leaseId, "Unable to establish automatic output operation token");
            }
            return host.getOutputBufferEntries().get(bufferId);
        }
        QIOOutputBufferEntry entry;
        try {
            entry = QIOOutputBufferEntry.prepared(bufferId, operationId, leaseId,
                  baseline, extraction, tick);
        } catch (RuntimeException e) {
            if (!discardUnownedOutputOperation(host, ports, network, operationId)) {
                if (isStructuralFailure(e)) {
                    host.contaminateLease(leaseId,
                          "Automatic output buffer preparation failed: " + diagnostic(e));
                } else {
                    pauseOutputRecoveryOrRetry(host,
                          "automatic output buffer preparation endpoint is rebuilding");
                }
            }
            Mekanism.logger.error("Unable to create QIO automatic output buffer for device {}",
                  host.getPersistentDeviceUUID(), e);
            return host.getOutputBufferEntries().get(bufferId);
        }
        boolean prepared;
        boolean prepareRuntimeFailure = false;
        try {
            prepared = host.prepareOutputBuffer(entry);
        } catch (RuntimeException e) {
            prepared = false;
            prepareRuntimeFailure = true;
            if (!discardUnownedOutputOperation(host, ports, network, operationId)) {
                if (isStructuralFailure(e)) {
                    host.contaminateLease(leaseId,
                          "Automatic output buffer preparation failed: " + diagnostic(e));
                } else {
                    pauseOutputRecoveryOrRetry(host,
                          "automatic output buffer persistence endpoint is rebuilding");
                }
            }
            Mekanism.logger.error("Unable to persist QIO automatic output buffer for device {}",
                  host.getPersistentDeviceUUID(), e);
        }
        if (!prepared) {
            if (!prepareRuntimeFailure && !discardUnownedOutputOperation(host, ports, network, operationId)) {
                host.contaminateLease(leaseId, "Unable to persist automatic output buffer preparation");
            }
            return host.getOutputBufferEntries().get(bufferId);
        }
        try {
            ensureMachineTransfer(network, host, entry, resource, extraction.amount());
            flush(network);
            extractPreparedOutput(host, network, entry, port, extraction, resource);
        } catch (IOException e) {
            pauseOutputRecoveryOrRetry(host,
                  "automatic output transfer checkpoint is unavailable");
            Mekanism.logger.error("Unable to prepare QIO automatic output transfer for device {}",
                  host.getPersistentDeviceUUID(), e);
        } catch (RuntimeException e) {
            if (!discardUnownedOutputOperation(host, ports, network, operationId)) {
                if (isStructuralFailure(e)) {
                    host.contaminateLease(leaseId,
                          "Automatic output transfer preparation failed: " + diagnostic(e));
                } else {
                    pauseOutputRecoveryOrRetry(host,
                          "automatic output transfer endpoint is rebuilding");
                }
            }
            Mekanism.logger.error("Unable to prepare QIO automatic output transfer for device {}",
                  host.getPersistentDeviceUUID(), e);
        }
        return host.getOutputBufferEntries().get(bufferId);
    }

    /** 判断共享端口组当前是否仍被未释放租约占用。 */
    private static boolean isPortGroupOccupied(DefaultQIOAutomationHost host, String groupId) {
        for (MachineOperationLease lease : host.getLeases().values()) {
            if (lease.state() != MachineOperationLease.State.RELEASED &&
                  lease.portGroupIds().contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes only an automatic-output operation whose ownership is still entirely local.
     * The baseline check is important: without it, a crash after a machine debit but before
     * the buffer/transfer reached disk could silently lose the machine resource.
     */
    /** 清理已证明没有外部资源所有权的孤立自动输出操作。 */
    private boolean discardUnownedOutputOperation(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network, UUID operationId) {
        MachineOperationToken token = host.getOperationTokens().get(operationId);
        if (token == null || token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN) {
            return false;
        }
        MachineOperationLease lease = host.getLeases().get(token.leaseId());
        if (lease == null || lease.mode() != MachineOperationLease.Mode.OUTPUT_DRAIN ||
              lease.baselines().isEmpty() || !token.transferReceipts().isEmpty() ||
              host.getOutputBufferEntries().values().stream().anyMatch(entry ->
                    entry.operationId().equals(operationId))) {
            return false;
        }
        if (hasDurableOutputOwnership(network, host, operationId, lease)) {
            return false;
        }
        for (MachinePortBaseline baseline : lease.baselines()) {
            MachinePort port = findPort(ports, baseline.portId());
            if (port == null || !baseline.matches(port)) {
                return false;
            }
        }
        return host.discardUnownedOutputOperation(operationId);
    }

    /** 扫描并清除没有缓冲、回执或 durable transfer 的本地主机孤立状态。 */
    private void clearUnownedOutputOperation(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network) {
        MachineOperationToken[] tokens = host.getOperationTokens().values().toArray(
              new MachineOperationToken[0]);
        for (MachineOperationToken token : tokens) {
            if (token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN) {
                continue;
            }
            if (discardUnownedOutputOperation(host, ports, network, token.operationId())) {
                // Only one output operation can own a given port group. Continue scanning so
                // a malformed capability containing several independent unowned records is
                // cleaned up deterministically in the same tick.
            }
        }
    }

    /** 判断网络是否仍保存属于该机器的输出所有权记录。 */
    private boolean hasDurableOutputOwnership(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, UUID operationId, MachineOperationLease lease) {
        try {
            String devicePrefix = "machine/" + host.getPersistentDeviceUUID() + "/";
            String bufferPrefix = "output-buffer/" + host.getPersistentDeviceUUID() + "/";
            for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
                if (operationId.equals(transfer.getOwnerOperationId()) ||
                      lease.leaseId().equals(transfer.getLeaseId())) {
                    return true;
                }
                if (transfer.getType() == QIODurableTransferRecord.Type.MACHINE_TO_JOB &&
                      transfer.getOwnerJobId() == null &&
                      transfer.getSource().startsWith(devicePrefix) &&
                      transfer.getDestination().startsWith(bufferPrefix)) {
                    // A standalone machine-to-buffer transfer without a matching local token
                    // is still an ownership record; never infer that it is disposable.
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            // An unavailable/corrupt network view is not proof of absent ownership.
            return true;
        }
    }

    /** 根据 PREPARED 缓冲的端口基线继续尚未完成的机器抽取。 */
    private void resumeMachineExtraction(DefaultQIOAutomationHost host,
          List<MachinePort> ports, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry) {
        MachinePort port = findPort(ports, entry.baseline().portId());
        MachineResourceStack expected = entry.extraction();
        if (port == null || expected == null) {
            host.contaminateLease(entry.leaseId(), "Prepared automatic output port is no longer available");
            return;
        }
        PortableResourceDescriptor resource = describe(expected);
        if (resource == null) {
            // Resource codecs can disappear while an addon is being reloaded. The persisted
            // baseline still owns the machine stack, so keep the buffer and retry once the
            // codec/provider is available again.
            pauseOutputRecoveryOrRetry(host, "automatic output resource codec is unavailable");
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
            MachineOperationToken token = host.getOperationTokens().get(entry.operationId());
            if (!entry.baseline().matches(port) &&
                  transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  (token == null || !token.hasTransferReceipt(transfer.getTransferId())) &&
                  (entry.baseline().matchesAfterExtraction(port, expected) ||
                        host.isOrdinaryOutputHandoffAllowed(entry.operationId()))) {
                // No durable source receipt exists. Only an exact post-extraction baseline or
                // an explicit quarantine handoff marker proves that the ordinary ejector owns
                // this mutation; arbitrary changes remain a structural mismatch.
                discardPreparedExternalHandoff(host, network, entry, transfer);
                return;
            }
            if (entry.baseline().matchesAfterExtraction(port, expected)) {
                // A PREPARED transfer has no durable physical receipt. Once the live port has
                // moved to the post-extraction baseline, the ordinary ejector may have taken
                // this exact stack while QIO was quarantined. Do not infer a QIO debit here:
                // discard the intent and release the local lease. Only a transfer which already
                // crossed SOURCE_DEBITED is allowed to credit the QIO buffer.
                if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
                      transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                      (token == null || !token.hasTransferReceipt(transfer.getTransferId()))) {
                    discardPreparedExternalHandoff(host, network, entry, transfer);
                    return;
                }
                // The source was debited before the capability buffer reached disk. The persisted
                // phase and exact post-extraction observation make replaying the destination
                // credit safe, including partial drains.
                if (host.holdOutput(entry.bufferId(), resource, expected.amount())) {
                    completeMachineTransfer(network, transfer, host, entry);
                }
            } else {
                host.contaminateLease(entry.leaseId(), "Prepared output baseline changed outside its lease");
            }
        } catch (IOException e) {
            pauseOutputRecoveryOrRetry(host,
                  "automatic output recovery checkpoint is unavailable");
            Mekanism.logger.warn("Unable to resume QIO automatic output extraction for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
        } catch (RuntimeException e) {
            if (isStructuralFailure(e)) {
                host.contaminateLease(entry.leaseId(),
                      "Automatic output extraction recovery failed: " + diagnostic(e));
            } else {
                pauseOutputRecoveryOrRetry(host,
                      "automatic output extraction endpoint is rebuilding");
            }
            Mekanism.logger.error("Unable to resume QIO automatic output extraction for device {}",
                  host.getPersistentDeviceUUID(), e);
        }
    }

    /** Removes a PREPARED machine intent after ordinary ejection owns the physical stack. */
    private void discardPreparedExternalHandoff(DefaultQIOAutomationHost host,
          QIOProcessingNetworkData network, QIOOutputBufferEntry entry,
          QIODurableTransferRecord transfer) {
        try {
            if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED ||
                  transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE) {
                return;
            }
            network.discardPreparedGenericTransfer(transfer.getTransferId());
            flush(network);
            if (!host.discardPreparedOutputAfterExternalHandoff(entry.operationId(),
                  entry.bufferId())) {
                host.markRecoveryPending("Unable to release externally ejected automatic output ownership");
            }
        } catch (IOException | RuntimeException e) {
            // Keep the local record when the network checkpoint is unavailable. The next tick
            // can retry the same phase without creating a second QIO credit.
            pauseOutputRecoveryOrRetry(host,
                  "automatic output handoff checkpoint is unavailable");
            Mekanism.logger.warn("Unable to discard externally ejected QIO automatic output for device {}; retrying",
                  host.getPersistentDeviceUUID(), e);
        }
    }

    /** A PREPARED phase is creditable only when a separate durable receipt exists. */
    private static boolean isMachineExtractionDurablyReceipted(DefaultQIOAutomationHost host,
          QIOOutputBufferEntry entry, QIODurableTransferRecord transfer) {
        if (transfer == null || transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
            return true;
        }
        MachineOperationToken token = host.getOperationTokens().get(entry.operationId());
        return token != null && token.hasTransferReceipt(transfer.getTransferId());
    }

    /** 校验端口基线后抽取产物，并把实际数量写入输出缓冲。 */
    private void extractPreparedOutput(DefaultQIOAutomationHost host, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry, MachinePort port, MachineResourceStack expected,
          PortableResourceDescriptor resource) throws IOException {
        if (!entry.baseline().matches(port)) {
            host.contaminateLease(entry.leaseId(), "Automatic output port changed before extraction");
            return;
        }
        QIODurableTransferRecord transfer = ensureMachineTransfer(network, host, entry, resource, expected.amount());
        MachineResourceStack extracted;
        try {
            extracted = port.extract(expected);
        } catch (RuntimeException e) {
            // Addon-backed ports can be rebuilt between the simulation and execute calls.
            // If the exact baseline is still present, this is retryable and must not poison
            // the collecting ownership record.
            if (entry.baseline().matches(port)) {
                pauseOutputRecoveryOrRetry(host,
                      "automatic output extraction endpoint is rebuilding");
                return;
            }
            if (!isMachineExtractionDurablyReceipted(host, entry, transfer) &&
                  (entry.baseline().matchesAfterExtraction(port, expected) ||
                        host.isOrdinaryOutputHandoffAllowed(entry.operationId()))) {
                discardPreparedExternalHandoff(host, network, entry, transfer);
                return;
            }
            if (entry.baseline().matchesAfterExtraction(port, expected) &&
                  isMachineExtractionDurablyReceipted(host, entry, transfer)) {
                if (host.holdOutput(entry.bufferId(), resource, expected.amount())) {
                    completeMachineTransfer(network, transfer, host, entry);
                }
                return;
            }
            host.contaminateLease(entry.leaseId(),
                  "Automatic output extraction changed its prepared baseline");
            return;
        }
        if (extracted == null || extracted.amount() != expected.amount() || !extracted.sameResource(expected)) {
            // MachinePort performs a simulation before its execute phase. A failed
            // simulation/execute leaves the baseline untouched and is retryable; it
            // must not turn a valid PREPARED buffer into a contaminated token/lease.
            if (entry.baseline().matches(port)) {
                return;
            }
            if (!isMachineExtractionDurablyReceipted(host, entry, transfer) &&
                  (entry.baseline().matchesAfterExtraction(port, expected) ||
                        host.isOrdinaryOutputHandoffAllowed(entry.operationId()))) {
                discardPreparedExternalHandoff(host, network, entry, transfer);
                return;
            }
            // The source may have been debited before the returned stack reached us.
            // The exact post-extraction baseline is enough to credit the persistent
            // buffer and finish the already-prepared durable transfer exactly once.
            if (entry.baseline().matchesAfterExtraction(port, expected) &&
                  isMachineExtractionDurablyReceipted(host, entry, transfer) &&
                  host.holdOutput(entry.bufferId(), resource, expected.amount())) {
                completeMachineTransfer(network, transfer, host, entry);
                return;
            }
            host.contaminateLease(entry.leaseId(), "Automatic output extraction did not match its prepared baseline");
            return;
        }
        if (!host.holdOutput(entry.bufferId(), resource, extracted.amount())) {
            throw new IllegalStateException("Extracted automatic output could not be credited to its persistent buffer");
        }
        completeMachineTransfer(network, transfer, host, entry);
    }

    /** 将 HELD/DELIVERING 输出缓冲按容量和回执逐步写回 QIO。 */
    private long deliverToQIO(DefaultQIOAutomationHost host, QIOProcessingNetworkData network,
          QIOOutputBufferEntry entry, QIOFrequencyReference reference) {
        IQIOStorageView view;
        try {
            view = QIOFrequencyStorageAccess.INSTANCE.open(reference,
                  reference.getBindingPlayerUUID());
        } catch (RuntimeException e) {
            pauseOutputRecoveryOrRetry(host,
                  "automatic output delivery endpoint is rebuilding");
            return 0;
        }
        if (view == null) {
            pauseOutputRecoveryOrRetry(host, "automatic output QIO endpoint is unavailable");
            return 0;
        }
        try {
            QIOOutputBufferEntry delivering = entry;
            QIODurableTransferRecord transfer;
            if (entry.phase() == QIOOutputBufferEntry.Phase.HELD) {
                transfer = findDeliveryTransfer(network, host, entry, reference);
                long insertable;
                if (transfer == null) {
                    insertable = simulateInsert(view, entry.resource(), entry.amount());
                    if (insertable <= 0) {
                        host.updateActivitySnapshot(new MachineActivitySnapshot(
                        host.getOperationTokens().get(entry.operationId()).laneId(), entry.operationId(), "",
                              MachineActivitySnapshot.State.OUTPUT_BLOCKED, 0, 0, 0, "qio_full"));
                        return 0;
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
                transfer = requireDeliveryTransfer(network, host, entry, reference);
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
                return 0;
            }
            if (result.getTransferredAmount() != delivering.qioRequestedAmount()) {
                host.contaminateLease(entry.leaseId(), "QIO automatic output receipt amount disagrees with its request");
                return 0;
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
            return result.getTransferredAmount();
        } catch (IOException e) {
            pauseOutputRecoveryOrRetry(host,
                  "automatic output delivery checkpoint is unavailable");
            Mekanism.logger.error("Unable to deliver QIO automatic output for device {}",
                  host.getPersistentDeviceUUID(), e);
            return 0;
        } catch (RuntimeException e) {
            if (isStructuralFailure(e)) {
                host.contaminateLease(entry.leaseId(),
                      "Automatic output delivery reconciliation failed: " + diagnostic(e));
            } else {
                // Storage views and dynamic QIO implementations may throw while their backing
                // inventory is being rebuilt. The durable delivery record is still the source
                // of truth; leave it intact for an idempotent retry.
                pauseOutputRecoveryOrRetry(host,
                      "automatic output delivery endpoint is rebuilding");
            }
            Mekanism.logger.error("Unable to deliver QIO automatic output for device {}",
                  host.getPersistentDeviceUUID(), e);
            return 0;
        } finally {
            view.close();
        }
    }

    /** 查找或创建机器到输出缓冲的 durable transfer。 */
    private QIODurableTransferRecord ensureMachineTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry, PortableResourceDescriptor resource,
          long amount) {
        QIODurableTransferRecord existing = network.getDurableTransfer(entry.bufferId());
        if (existing != null) {
            if (!machineTransferMatches(existing, host, entry, resource, amount)) {
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

    /** 对账已有的机器抽取 transfer，避免重复取出或丢失资源。 */
    private boolean reconcileHeldMachineTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, List<MachinePort> ports,
          QIOOutputBufferEntry entry) {
        QIODurableTransferRecord transfer = network.getDurableTransfer(entry.bufferId());
        PortableResourceDescriptor resource = entry.resource();
        MachineResourceStack extraction = entry.extraction();
        if (resource == null || extraction == null) {
            host.contaminateLease(entry.leaseId(), "Held output disagrees with its durable machine transfer");
            return false;
        }
        try {
            // A committed machine-to-buffer transfer is the durable proof that the exact
            // resource already left the machine and is now owned by this buffer.  The provider
            // may rebuild its port list, or the machine may refill/reproduce output, before the
            // next tick.  Requiring a matching live port here would strand a valid buffer and
            // turn a transient endpoint change into a quarantine.  Identity/receipt checks are
            // still strict, so a genuinely mismatched transfer remains isolated.
            if (transfer != null &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED) {
                if (!machineTransferMatches(transfer, host, entry, resource, extraction.amount())) {
                    host.contaminateLease(entry.leaseId(),
                          "Held output disagrees with its durable machine transfer");
                    return false;
                }
                completeMachineTransfer(network, transfer, host, entry);
                return true;
            }

            MachinePort port = findPort(ports, entry.baseline().portId());
            if (port == null) {
                host.contaminateLease(entry.leaseId(),
                      "Held output disagrees with its durable machine transfer");
                return false;
            }
            if (transfer == null) {
                if (!entry.baseline().matchesAfterExtraction(port, extraction)) {
                    host.contaminateLease(entry.leaseId(),
                          "Held output disagrees with its durable machine transfer");
                    return false;
                }
                transfer = ensureMachineTransfer(network, host, entry, resource,
                      extraction.amount());
            } else if (!machineTransferMatches(transfer, host, entry, resource,
                  extraction.amount())) {
                host.contaminateLease(entry.leaseId(),
                      "Held output disagrees with its durable machine transfer");
                return false;
            }
            // Once the exact machine-to-buffer transfer is durably committed, the buffer is
            // authoritative. The machine may legitimately produce more output before the next
            // tick, so requiring the port to remain in its post-extraction state would strand an
            // already-owned buffer and could quarantine a healthy continuously running machine.
            if (!entry.baseline().matchesAfterExtraction(port, extraction)) {
                host.contaminateLease(entry.leaseId(),
                      "Held output disagrees with its durable machine transfer");
                return false;
            }
            completeMachineTransfer(network, transfer, host, entry);
            return true;
        } catch (IOException e) {
            pauseOutputRecoveryOrRetry(host,
                  "automatic output transfer reconciliation checkpoint is unavailable");
            return false;
        } catch (RuntimeException e) {
            if (isStructuralFailure(e)) {
                host.contaminateLease(entry.leaseId(),
                      "Held output transfer reconciliation failed: " + diagnostic(e));
            } else {
                pauseOutputRecoveryOrRetry(host,
                      "automatic output transfer endpoint is rebuilding");
            }
            return false;
        }
    }

    /** 在机器抽取已确认后结算对应 durable transfer。 */
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

    /** 创建输出缓冲到 QIO 的 durable transfer，并绑定操作所有权。 */
    private QIODurableTransferRecord createDeliveryTransfer(DefaultQIOAutomationHost host,
          QIOOutputBufferEntry entry, UUID transferId, long amount, IQIOStorageView view) {
        return new QIODurableTransferRecord(transferId, UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
              null, entry.operationId(), 0, "automatic-output", entry.leaseId(),
              "output-buffer/" + host.getPersistentDeviceUUID() + '/' + entry.bufferId(),
              "qio/" + view.getFrequencyUUID(), Collections.singletonMap(entry.resource(), amount),
              Collections.singletonMap(entry.resource(), storedAmount(view, entry.resource())));
    }

    /** 获取当前缓冲对应的投递 transfer；缺失时抛出结构诊断。 */
    private QIODurableTransferRecord requireDeliveryTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry,
          QIOFrequencyReference reference) {
        QIODurableTransferRecord transfer = network.getDurableTransfer(entry.qioTransferId());
        if (transfer == null || !deliveryTransferMatches(transfer, host, entry, reference)) {
            throw new IllegalStateException("Missing or mismatched automatic output QIO delivery transfer");
        }
        return transfer;
    }

    @Nullable
    /** 按缓冲/操作标识查找已有的输出投递 transfer。 */
    private QIODurableTransferRecord findDeliveryTransfer(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry,
          QIOFrequencyReference reference) {
        QIODurableTransferRecord pending = null;
        QIODurableTransferRecord replay = null;
        MachineOperationToken token = host.getOperationTokens().get(entry.operationId());
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (deliveryTransferMatches(transfer, host, entry, reference)) {
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

    /** 根据已确认回执推进主机缓冲和 token 的结算状态。 */
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

    /** 删除已经完成、释放且可安全忘记的本地操作。 */
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

    /** 模拟写入 QIO，返回当前容量实际可接收的数量。 */
    private static long simulateInsert(IQIOStorageView view, PortableResourceDescriptor resource, long amount) {
        if (resource == null || amount <= 0) {
            return 0;
        }
        return directInsert(view, resource, amount, Action.SIMULATE);
    }

    /** Inserts through the ordinary QIO storage path without creating a durable receipt. */
    private static long directInsert(IQIOStorageView view,
          PortableResourceDescriptor resource, long amount, Action action) {
        return switch (resource.getKind()) {
            case ITEM -> view.insert(resource.resolveItem(), amount, action);
            case FLUID -> view.insert(resource.resolveFluid(), amount, action);
            case GAS -> view.insert(resource.resolveGas(), amount, action);
            case CUSTOM -> view.insert(resource.getDescriptor(), amount, action);
        };
    }

    /** 校验机器抽取 transfer 是否仍对应给定端口和资源。 */
    private static boolean machineTransferMatches(QIODurableTransferRecord transfer,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry,
          PortableResourceDescriptor resource, long amount) {
        if (transfer == null || resource == null || amount <= 0 ||
              !transfer.getTransferId().equals(entry.bufferId()) ||
              transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
              transfer.getOwnerJobId() != null ||
              !entry.operationId().equals(transfer.getOwnerOperationId()) ||
              transfer.getPlanRevision() != 0 ||
              !transfer.getNodeId().equals("output/" + entry.baseline().portId()) ||
              !entry.leaseId().equals(transfer.getLeaseId()) ||
              !transfer.getSource().equals("machine/" + host.getPersistentDeviceUUID() + '/' +
                    entry.baseline().portGroupId()) ||
              !transfer.getDestination().equals("output-buffer/" + host.getPersistentDeviceUUID() + '/' +
                    entry.bufferId()) ||
              !Collections.singletonMap(resource, amount).equals(transfer.getResources()) ||
              !transfer.getQIOResourceUUIDs().isEmpty() || !transfer.getQIOBaselines().isEmpty() ||
              !transfer.getMachineBaselines().equals(Collections.singletonList(entry.baseline())) ||
              transfer.getExpectedContentsRevision() != -1 ||
              transfer.getExpectedClaimRevision() != -1 ||
              transfer.getCompensationTransferId() != null ||
              transfer.getPhase() == QIODurableTransferRecord.Phase.ROLLBACK_REQUIRED) {
            return false;
        }
        String sourceReceipt = "machine/" + host.getPersistentDeviceUUID() + "/lease/" +
              entry.leaseId();
        String destinationReceipt = "output-buffer/" + host.getPersistentDeviceUUID() + '/' +
              entry.bufferId();
        return switch (transfer.getPhase()) {
            case PREPARED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() == null && transfer.getDestinationReceipt() == null &&
                  transfer.getSourceStateRevision() == -1;
            case SOURCE_DEBITED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  sourceReceipt.equals(transfer.getSourceReceipt()) &&
                  transfer.getSourceStateRevision() >= 0 && transfer.getDestinationReceipt() == null;
            case DESTINATION_CREDITED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  sourceReceipt.equals(transfer.getSourceReceipt()) &&
                  destinationReceipt.equals(transfer.getDestinationReceipt()) &&
                  transfer.getSourceStateRevision() >= 0;
            case COMMITTED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED &&
                  sourceReceipt.equals(transfer.getSourceReceipt()) &&
                  destinationReceipt.equals(transfer.getDestinationReceipt()) &&
                  transfer.getSourceStateRevision() >= 0;
            case ROLLBACK_REQUIRED -> false;
        };
    }

    /** 校验 QIO 投递 transfer 是否仍对应指定输出缓冲。 */
    private static boolean deliveryTransferMatches(QIODurableTransferRecord transfer,
          DefaultQIOAutomationHost host, QIOOutputBufferEntry entry,
          QIOFrequencyReference reference) {
        PortableResourceDescriptor resource = entry.resource();
        boolean held = entry.phase() == QIOOutputBufferEntry.Phase.HELD;
        long amount = held && transfer != null ? transfer.getResources().getOrDefault(resource, 0L) :
              entry.qioRequestedAmount();
        boolean transferIdMatches = held ? transfer != null : entry.qioTransferId() != null &&
              transfer != null && entry.qioTransferId().equals(transfer.getTransferId());
        if (transfer == null || resource == null || amount <= 0 || !transferIdMatches ||
              held && amount > entry.amount() ||
              transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
              transfer.getOwnerJobId() != null ||
              !entry.operationId().equals(transfer.getOwnerOperationId()) ||
              transfer.getPlanRevision() != 0 || !transfer.getNodeId().equals("automatic-output") ||
              !entry.leaseId().equals(transfer.getLeaseId()) ||
              !transfer.getSource().equals("output-buffer/" + host.getPersistentDeviceUUID() + '/' +
                    entry.bufferId()) ||
              !transfer.getDestination().equals("qio/" + reference.getFrequencyUUID()) ||
              !Collections.singletonMap(resource, amount).equals(transfer.getResources()) ||
              !transfer.getQIOResourceUUIDs().isEmpty() ||
              !transfer.getQIOBaselines().keySet().equals(Collections.singleton(resource)) ||
              !transfer.getMachineBaselines().isEmpty() ||
              transfer.getExpectedContentsRevision() != -1 ||
              transfer.getExpectedClaimRevision() != -1 ||
              transfer.getCompensationTransferId() != null ||
              transfer.getPhase() == QIODurableTransferRecord.Phase.ROLLBACK_REQUIRED) {
            return false;
        }
        return switch (transfer.getPhase()) {
            case PREPARED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() == null && transfer.getDestinationReceipt() == null &&
                  transfer.getSourceStateRevision() == -1;
            case SOURCE_DEBITED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() != null && transfer.getDestinationReceipt() == null &&
                  transfer.getSourceStateRevision() >= 0;
            case DESTINATION_CREDITED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() != null && transfer.getDestinationReceipt() != null &&
                  transfer.getSourceStateRevision() >= 0;
            case COMMITTED -> transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED &&
                  transfer.getSourceReceipt() != null && transfer.getDestinationReceipt() != null &&
                  transfer.getSourceStateRevision() >= 0;
            case ROLLBACK_REQUIRED -> false;
        };
    }

    /** 将异常转换为有限长度、可显示的诊断文本。 */
    private static String diagnostic(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(384, message.length()));
    }

    /**
     * Runtime failures are normally availability events. Only errors which explicitly prove
     * that a durable identity, baseline, or ownership relationship changed may quarantine the
     * operation; treating every provider exception as corruption strands valid machines.
     */
    /** 判断异常是否代表结构/所有权不一致，而非普通暂时不可用。 */
    private static boolean isStructuralFailure(Throwable error) {
        String message = diagnostic(error).toLowerCase(java.util.Locale.ROOT);
        return message.contains("identity") || message.contains("mismatch") ||
              message.contains("baseline") || message.contains("ownership") ||
              message.contains("transfer conflict") || message.contains("multiple pending") ||
              message.contains("multiple unacknowledged") || message.contains("invalid request") ||
              message.contains("reconciliation conflict");
    }

    /** 执行一次实际 QIO 写入，并统一处理空结果。 */
    private static QIOTransferResult insert(IQIOStorageView view, PortableResourceDescriptor resource,
          UUID transferId, long amount, @Nonnull BigInteger baseline) {
        Objects.requireNonNull(resource, "Output resource cannot be null");
        Objects.requireNonNull(transferId, "Output transfer id cannot be null");
        Objects.requireNonNull(baseline, "Output transfer baseline cannot be null");
        return switch (resource.getKind()) {
            case ITEM -> view.insertIdempotent(transferId, resource.resolveItem(), amount, baseline);
            case FLUID -> view.insertIdempotent(transferId, resource.resolveFluid(), amount, baseline);
            case GAS -> view.insertIdempotent(transferId, resource.resolveGas(), amount, baseline);
            case CUSTOM -> view.insertIdempotent(transferId, resource.getDescriptor(), amount, baseline);
        };
    }

    /** 查询 QIO 中某资源当前已存储的总量。 */
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
    /** 将机器资源栈转换为可持久化的资源描述符。 */
    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        try {
            return PortableResourceDescriptor.fromDescriptor(stack.descriptor());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    /** 在单次 Provider 快照中按端口标识查找端口。 */
    private static MachinePort findPort(List<MachinePort> ports, String portId) {
        for (MachinePort port : ports) {
            if (port.portId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    /** 请求网络管理器把当前 durable transfer 变更写入持久层。 */
    private static void flush(QIOProcessingNetworkData network) throws IOException {
        QIOProcessingNetworkManager.INSTANCE.checkpointNetwork(network.getFrequencyUUID());
    }

}
