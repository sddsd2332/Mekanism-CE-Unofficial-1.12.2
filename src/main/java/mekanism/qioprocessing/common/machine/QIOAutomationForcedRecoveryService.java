package mekanism.qioprocessing.common.machine;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 协调需要玩家确认的破坏性自动化恢复协议。
 *
 * <p>强制恢复先在网络侧记录受影响的任务、被动操作和配置声明，再清理机器本地的
 * lease/token/buffer。网络尚未加载或无法完成审计时，手动恢复保持隔离；物理卸载升级
 * 则按显式移除语义允许本地解绑并等待网络正常持久化。</p>
 */
public final class QIOAutomationForcedRecoveryService {

    private static final String JOB_DIAGNOSTIC =
          "gui.mekanismqioprocessing.monitor_recovery_material_loss";
    private static final String PASSIVE_DIAGNOSTIC =
          "QIO automatic-processing operation was ended during forced recovery";

    private QIOAutomationForcedRecoveryService() {
    }

    /**
     * 执行玩家确认的强制恢复。
     *
     * @param host 要恢复的自动化主机
     * @return 网络所有权已审计并完成清理时返回 true
     */
    public static boolean forceClear(@Nonnull DefaultQIOAutomationHost host) {
        Objects.requireNonNull(host, "host");
        if (host.getRecoveryState() != QIOAutomationHost.RecoveryState.QUARANTINED &&
              host.getState() != QIOAutomationHost.State.DATA_ERROR) {
            return false;
        }
        return clear(host, false);
    }

    /**
     * 处理物理移除自动化升级：这是明确丢弃自动化所有权的请求，不能让 capability 永久排空。
     *
     * @param host 被卸载升级的自动化主机
     * @return 本地状态清理成功时返回 true
     */
    public static boolean clearAfterUpgradeRemoval(@Nonnull DefaultQIOAutomationHost host) {
        Objects.requireNonNull(host, "host");
        return clear(host, true);
    }

    /** 按手动恢复或升级移除语义执行网络审计和本地主机清理。 */
    private static boolean clear(DefaultQIOAutomationHost host, boolean removeBinding) {
        QIOFrequencyReference reference = host.getFrequencyReference();
        QIOProcessingNetworkData network = reference == null ? null :
              QIOProcessingNetworkManager.INSTANCE.get(reference.getFrequencyUUID());
        if (!removeBinding && network == null && host.hasPotentialExternalOwnership()) {
            // A manual force-clear must be able to account for every external owner before it
            // removes the local capability records.  If the frequency data is not loaded yet,
            // retaining the quarantine is safer than silently losing a job/passive assignment
            // (or a durable machine transfer) that the network would have reported later.
            return false;
        }
        Set<UUID> operationIds = network == null ? Collections.emptySet() :
              operationIdsForHost(network, host);
        try {
            if (network != null) {
                releaseConfigurationClaims(network, reference, operationIds);
                recordAffectedOperations(network, host, operationIds);
                QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            }
        } catch (IOException | RuntimeException e) {
            // Network mutations are listener-backed and remain dirty for the regular flush.
            // Physical upgrade removal is an explicit request to detach this endpoint, so it
            // must not leave a machine with no installed upgrade permanently bound. Manual
            // force recovery remains conservative and keeps its ownership records when the
            // durable audit cannot be checkpointed.
            if (network != null) {
                try {
                    network.markMaintenanceRuntimeChanged();
                } catch (RuntimeException ignored) {
                    // Preserve the original persistence failure in the diagnostic log below.
                }
            }
            if (!removeBinding) {
                return false;
            }
            Mekanism.logger.warn(
                  "QIO automation upgrade removal could not checkpoint network ownership for {}",
                  host.getPersistentDeviceUUID(), e);
        }
        boolean cleared = removeBinding ? host.forceClearAfterUpgradeRemoval() :
              host.forceRecoverAfterDataError();
        if (!cleared) {
            return false;
        }
        QIOAutomationDeviceRegistry.INSTANCE.refreshHost(host);
        if (reference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeStorage(reference.getFrequencyUUID());
        }
        // A removal succeeds locally even when the network save is temporarily unavailable; the
        // dirty network will be retried by its normal flush loop. Manual recovery returned above
        // before reaching this point when its durable audit failed.
        return true;
    }

    /** 释放受影响配置交换中的 claim；存储暂不可用时标记为待恢复。 */
    private static boolean releaseConfigurationClaims(QIOProcessingNetworkData network,
          QIOFrequencyReference reference, Set<UUID> operationIds) throws IOException {
        boolean hasOutstandingClaim = operationIds.stream().anyMatch(operationId ->
              network.getOperationConfigurationExchanges(operationId).stream()
                    .anyMatch(QIOConfigurationExchangeRecord::hasOutstandingClaim));
        if (!hasOutstandingClaim) return true;
        if (reference == null) {
            markConfigurationClaimsPending(network, operationIds);
            return true;
        }
        IQIOStorageView view;
        try {
            view = QIOFrequencyStorageAccess.INSTANCE.open(reference,
                  reference.getBindingPlayerUUID());
        } catch (RuntimeException e) {
            markConfigurationClaimsPending(network, operationIds);
            return true;
        }
        if (view == null) {
            markConfigurationClaimsPending(network, operationIds);
            return true;
        }
        try {
            for (UUID operationId : operationIds) {
                for (QIOConfigurationExchangeRecord exchange :
                      network.getOperationConfigurationExchanges(operationId)) {
                    if (!exchange.hasOutstandingClaim()) continue;
                    QIOClaimResult result = view.submitClaim(QIOClaimRequest.release(
                          exchange.getReleaseRequestId(), exchange.getClaimId(),
                          MekanismQIOProcessing.MODID,
                          "configuration/" + operationId + "/" + exchange.getPortId(),
                          QIOClaimRequest.ANY_REVISION, Collections.emptyMap()));
                    if (!result.isSuccess() &&
                          result.getStatus() != QIOClaimResult.Status.NOT_FOUND) {
                        exchange.contaminate(
                              QIOConfigurationExchangeRecord.FORCE_RECOVERY_PENDING_PREFIX +
                                    "QIO access is unavailable");
                        network.markConfigurationExchangeChanged(exchange.getExchangeId());
                        continue;
                    }
                    exchange.markClaimReleased();
                    network.markConfigurationExchangeChanged(exchange.getExchangeId());
                }
            }
            return true;
        } catch (RuntimeException e) {
            markConfigurationClaimsPending(network, operationIds);
            return true;
        } finally {
            view.close();
        }
    }

    /** 将尚未能释放的配置 claim 标记为强制恢复待处理。 */
    private static void markConfigurationClaimsPending(QIOProcessingNetworkData network,
          Set<UUID> operationIds) {
        for (UUID operationId : operationIds) {
            for (QIOConfigurationExchangeRecord exchange :
                  network.getOperationConfigurationExchanges(operationId)) {
                if (!exchange.hasOutstandingClaim()) continue;
                if (!exchange.isForceRecoveryPending()) {
                    exchange.contaminate(
                          QIOConfigurationExchangeRecord.FORCE_RECOVERY_PENDING_PREFIX +
                                "QIO access is unavailable");
                }
                network.markConfigurationExchangeChanged(exchange.getExchangeId());
            }
        }
    }

    /** 从 durable transfer、配置交换、被动操作和任务中收集机器操作标识。 */
    private static Set<UUID> operationIdsForHost(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host) {
        Set<UUID> operationIds = new LinkedHashSet<>(host.getOperationTokens().keySet());
        UUID deviceUUID = host.getPersistentDeviceUUID();
        String machinePrefix = "machine/" + deviceUUID + '/';
        String bufferPrefix = "output-buffer/" + deviceUUID + '/';
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            UUID operationId = transfer.getOwnerOperationId();
            if (operationId == null && transfer.getOwnerJobId() != null) {
                operationId = operationIdFromNode(transfer.getNodeId());
            }
            if (operationId != null &&
                (transfer.getSource().startsWith(machinePrefix) ||
                      transfer.getDestination().startsWith(machinePrefix) ||
                      transfer.getSource().startsWith(bufferPrefix) ||
                      transfer.getDestination().startsWith(bufferPrefix))) {
                operationIds.add(operationId);
            }
        }
        for (QIOConfigurationExchangeRecord exchange : network.getConfigurationExchanges()) {
            if (deviceUUID.equals(exchange.getDeviceUUID())) {
                operationIds.add(exchange.getOperationId());
            }
        }
        // A capability parse failure can erase the local token list before a machine input
        // transfer or configuration exchange is created. Discover those still-active owners
        // directly from the frequency records so force recovery can mark the crafting job or
        // passive operation instead of silently dropping its material-loss diagnostic.
        for (QIOPassiveOperation operation : network.getPassiveOperations()) {
            if (deviceUUID.equals(operation.getDeviceUUID())) {
                operationIds.add(operation.getOperationId());
            }
        }
        for (QIOCraftingJob job : network.getJobs()) {
            for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
                for (QIOOperationAssignment assignment : runtime.getActiveOperations().values()) {
                    if (deviceUUID.equals(assignment.getDeviceUUID())) {
                        operationIds.add(assignment.getOperationId());
                    }
                }
            }
        }
        NBTTagCompound raw = host.getQuarantinedDataCopy();
        if (raw != null && raw.hasKey("tokens", NBT.TAG_LIST)) {
            NBTTagList tokens = raw.getTagList("tokens", NBT.TAG_COMPOUND);
            for (int index = 0; index < tokens.tagCount(); index++) {
                NBTTagCompound token = tokens.getCompoundTagAt(index);
                if (token.hasKey("operationId", NBT.TAG_STRING)) {
                    try {
                        operationIds.add(UUID.fromString(token.getString("operationId")));
                    } catch (IllegalArgumentException ignored) {
                        // Keep the raw record quarantined; force recovery must not guess IDs.
                    }
                }
            }
        }
        return operationIds;
    }

    @javax.annotation.Nullable
    private static UUID operationIdFromNode(String nodeId) {
        int slash = nodeId.lastIndexOf('/');
        if (slash < 0 || slash == nodeId.length() - 1) {
            return null;
        }
        try {
            return UUID.fromString(nodeId.substring(slash + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 把主机本地记录对应的任务/被动操作写入网络恢复诊断。 */
    private static void recordAffectedOperations(QIOProcessingNetworkData network,
          DefaultQIOAutomationHost host, Set<UUID> operationIds) {
        Set<UUID> handled = new LinkedHashSet<>();
        for (MachineOperationToken token : host.getOperationTokens().values()) {
            handled.add(token.operationId());
            if (token.kind() == MachineOperationToken.Kind.JOB && token.jobId() != null) {
                recordJobOperation(network, token.jobId(), token.operationId());
            } else if (token.kind() == MachineOperationToken.Kind.PASSIVE) {
                recordPassiveOperation(network, token.operationId());
            } else {
                network.forceDiscardOperationOwnership(token.operationId());
            }
        }
        for (UUID operationId : operationIds) {
            if (!handled.contains(operationId)) {
                recordOrphanedOperation(network, operationId);
            }
        }
    }

    /** 记录一个被动处理操作在强制恢复中的结束原因。 */
    private static void recordPassiveOperation(QIOProcessingNetworkData network,
          UUID operationId) {
        QIOPassiveOperation passive = network.getPassiveOperation(operationId);
        if (passive != null && !passive.getState().isTerminal()) {
            boolean retainInput = passiveInputBufferIsConfirmed(network, passive);
            boolean retainOutput = passive.getState() == QIOPassiveOperation.State.COLLECTING ||
                  passive.getState() == QIOPassiveOperation.State.DELIVERING ||
                  passive.getState() == QIOPassiveOperation.State.RETURNING;
            passive.forceCancelAfterHostRecovery(PASSIVE_DIAGNOSTIC, retainInput,
                  retainOutput);
            network.markPassiveOperationChanged(passive.getOperationId());
            network.forceDiscardPassiveMachineOwnershipAfterLossAccepted(passive.getOperationId());
        } else {
            network.forceDiscardOperationOwnership(operationId);
        }
    }

    /** 为无法直接定位所有者的操作建立保守的孤立恢复记录。 */
    private static void recordOrphanedOperation(QIOProcessingNetworkData network,
          UUID operationId) {
        QIOPassiveOperation passive = network.getPassiveOperation(operationId);
        if (passive != null) {
            recordPassiveOperation(network, operationId);
            return;
        }
        UUID jobId = null;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (transfer.getOwnerJobId() != null && operationId.equals(
                  operationIdFromNode(transfer.getNodeId()))) {
                jobId = transfer.getOwnerJobId();
                break;
            }
        }
        if (jobId == null) {
            for (QIOConfigurationExchangeRecord exchange : network.getConfigurationExchanges()) {
                if (operationId.equals(exchange.getOperationId()) &&
                      exchange.getOwnerJobId() != null) {
                    jobId = exchange.getOwnerJobId();
                    break;
                }
            }
        }
        if (jobId == null) {
            for (QIOCraftingJob job : network.getJobs()) {
                if (findJobOperation(job, operationId) != null) {
                    jobId = job.getJobId();
                    break;
                }
            }
        }
        if (jobId != null) {
            recordJobOperation(network, jobId, operationId);
        } else {
            network.forceDiscardOperationOwnership(operationId);
        }
    }

    /** 判断被动输入 buffer 是否已有唯一且确认的 durable transfer。 */
    private static boolean passiveInputBufferIsConfirmed(QIOProcessingNetworkData network,
          QIOPassiveOperation passive) {
        if (passive.getState() == QIOPassiveOperation.State.RESERVED ||
              passive.getState() == QIOPassiveOperation.State.RETURNING) {
            return true;
        }
        if (passive.getState() != QIOPassiveOperation.State.LOADING) {
            return false;
        }
        java.util.List<QIODurableTransferRecord> transfers =
              network.getActiveOperationTransfers(passive.getOperationId(),
                    QIODurableTransferRecord.Type.JOB_TO_MACHINE);
        if (transfers.size() != 1) {
            return false;
        }
        QIODurableTransferRecord transfer = transfers.get(0);
        return transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED;
    }

    /** 把任务中的指定机器操作转换为取消/材料损失诊断。 */
    private static void recordJobOperation(QIOProcessingNetworkData network,
          UUID jobId, UUID operationId) {
        QIOCraftingJob job = network.getJob(jobId);
        if (job == null) {
            network.forceDiscardOperationOwnership(operationId);
            return;
        }
        if (!job.getState().isTerminal() && !job.isCancellationRequested()) {
            Map.Entry<Long, QIOOperationAssignment> operation = findJobOperation(job, operationId);
            if (operation != null) {
                network.markForcedRecoveryOperation(job.getJobId(), operation.getKey(),
                      operationId, JOB_DIAGNOSTIC);
            } else {
                network.markForcedRecoveryJob(job.getJobId(), JOB_DIAGNOSTIC);
            }
        }
        network.forceDiscardOperationOwnership(operationId);
    }

    /** 在任务步骤中按操作 UUID 查找机器操作分配。 */
    private static Map.Entry<Long, QIOOperationAssignment> findJobOperation(
          QIOCraftingJob job, UUID operationId) {
        for (Map.Entry<Long, QIOStepRuntime> entry : job.getStepRuntimes().entrySet()) {
            QIOOperationAssignment assignment = entry.getValue().getOperation(operationId);
            if (assignment != null) {
                return new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(), assignment);
            }
        }
        return null;
    }
}
