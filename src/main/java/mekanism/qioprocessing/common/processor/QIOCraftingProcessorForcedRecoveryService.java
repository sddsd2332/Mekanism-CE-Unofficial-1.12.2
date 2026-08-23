package mekanism.qioprocessing.common.processor;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Network-audited, explicitly player-triggered recovery for ambiguous processor state. */
public final class QIOCraftingProcessorForcedRecoveryService {

    private static final String DIAGNOSTIC =
          "QIO crafting processor was force-recovered; affected operations require player review";

    private QIOCraftingProcessorForcedRecoveryService() {
    }

    /**
     * Performs a lossless normalization first, then uses the network ownership records before
     * clearing an ambiguous local lane/raw NBT state. A failed checkpoint leaves the processor
     * isolated so the player can retry after the network becomes writable.
     */
    public static boolean forceClear(@Nonnull QIOCraftingProcessor processor) {
        QIOCraftingProcessorState state = processor.getProcessorState();
        if (state.forceRecoverAfterDataError(processor.getExpectedProcessorHostId(),
              processor.getExpectedProcessorDefinitionId())) {
            refreshAndWake(processor);
            return true;
        }
        if (!state.hasRecoveryPending()) {
            return false;
        }

        QIOFrequencyReference reference = processor.getFrequencyReference();
        QIOProcessingNetworkData network = reference == null ? null :
              QIOProcessingNetworkManager.INSTANCE.get(reference.getFrequencyUUID());
        NBTTagCompound raw = state.getQuarantinedDataCopy();
        Set<UUID> operationIds = collectOperationIds(network, processor, raw);
        boolean localOwnership = !state.getActiveLanes().isEmpty() || raw != null &&
              raw.hasKey("activeLanes", NBT.TAG_LIST) &&
              raw.getTagList("activeLanes", NBT.TAG_COMPOUND).tagCount() > 0;
        if (network == null && reference != null &&
              QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
                    reference.getFrequencyUUID()) != null) {
            // An isolated/damaged network is intentionally absent from the loaded map.  Even a
            // diagnostic-only processor marker must wait for that authoritative file to become
            // available before the player can clear it.
            return false;
        }
        if (network == null && (raw != null || localOwnership || !operationIds.isEmpty())) {
            // No network snapshot means there is no authoritative owner inventory to audit.
            return false;
        }
        try {
            if (network != null) {
                recordAffectedOperations(network, operationIds);
                QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            }
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.warn("Unable to checkpoint QIO processor recovery {}",
                  state.getProcessorUUID(), e);
            return false;
        }
        if (!state.forceClearAfterAuditedRecovery(processor.getExpectedProcessorHostId(),
              processor.getExpectedProcessorDefinitionId())) {
            return false;
        }
        refreshAndWake(processor);
        if (reference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeStorage(reference.getFrequencyUUID());
        }
        return true;
    }

    @Nonnull
    private static Set<UUID> collectOperationIds(@Nullable QIOProcessingNetworkData network,
          @Nonnull QIOCraftingProcessor processor, @Nullable NBTTagCompound raw) {
        Set<UUID> operationIds = new LinkedHashSet<>();
        processor.getProcessorState().getActiveLanes().values().forEach(lane ->
              operationIds.add(lane.getOperationId()));
        collectRawOperationIds(raw, operationIds);
        if (network == null) {
            return operationIds;
        }
        String prefix = "processor/" + processor.getProcessorState().getProcessorUUID() + "/";
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            UUID operationId = transfer.getOwnerOperationId();
            if (operationId == null && transfer.getOwnerJobId() != null) {
                operationId = operationIdFromNode(transfer.getNodeId());
            }
            if (transfer.getSource().startsWith(prefix) || transfer.getDestination().startsWith(prefix) ||
                  operationId != null && operationIds.contains(operationId)) {
                if (operationId != null) {
                    operationIds.add(operationId);
                }
            }
        }
        for (QIOCraftingJob job : network.getJobs()) {
            for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
                for (QIOOperationAssignment assignment : runtime.getActiveOperations().values()) {
                    if (processor.getProcessorState().getProcessorUUID().equals(
                          assignment.getDeviceUUID()) || operationIds.contains(
                                assignment.getOperationId())) {
                        operationIds.add(assignment.getOperationId());
                    }
                }
            }
        }
        return operationIds;
    }

    private static void collectRawOperationIds(@Nullable NBTTagCompound raw,
          @Nonnull Set<UUID> operationIds) {
        if (raw == null || !raw.hasKey("activeLanes", NBT.TAG_LIST)) {
            return;
        }
        NBTTagList lanes = raw.getTagList("activeLanes", NBT.TAG_COMPOUND);
        for (int index = 0; index < lanes.tagCount(); index++) {
            NBTTagCompound lane = lanes.getCompoundTagAt(index);
            if (!lane.hasKey("operationId", NBT.TAG_STRING)) {
                continue;
            }
            try {
                operationIds.add(UUID.fromString(lane.getString("operationId")));
            } catch (IllegalArgumentException ignored) {
                // Keep the raw record for diagnostics; never guess an operation identity.
            }
        }
    }

    private static void recordAffectedOperations(@Nonnull QIOProcessingNetworkData network,
          @Nonnull Set<UUID> operationIds) {
        Map<UUID, UUID> ownerJobs = new LinkedHashMap<>();
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            UUID operationId = transfer.getOwnerOperationId();
            if (operationId == null && transfer.getOwnerJobId() != null) {
                operationId = operationIdFromNode(transfer.getNodeId());
            }
            if (operationId != null && operationIds.contains(operationId) &&
                  transfer.getOwnerJobId() != null) {
                ownerJobs.put(operationId, transfer.getOwnerJobId());
            }
        }
        for (mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord exchange :
              network.getConfigurationExchanges()) {
            if (operationIds.contains(exchange.getOperationId()) &&
                  exchange.getOwnerJobId() != null) {
                ownerJobs.put(exchange.getOperationId(), exchange.getOwnerJobId());
            }
        }
        Set<UUID> markedJobs = new LinkedHashSet<>();
        for (QIOCraftingJob job : network.getJobs()) {
            for (java.util.Map.Entry<Long, QIOStepRuntime> runtimeEntry :
                  job.getStepRuntimes().entrySet()) {
                for (QIOOperationAssignment assignment :
                      runtimeEntry.getValue().getActiveOperations().values()) {
                    UUID operationId = assignment.getOperationId();
                    if (!operationIds.contains(operationId)) {
                        continue;
                    }
                    if (!job.getState().isTerminal() && !job.isCancellationRequested()) {
                        if (network.markForcedRecoveryOperation(job.getJobId(),
                              runtimeEntry.getKey(), operationId, DIAGNOSTIC)) {
                            markedJobs.add(job.getJobId());
                        }
                    }
                }
            }
        }
        for (UUID operationId : operationIds) {
            UUID ownerJob = ownerJobs.get(operationId);
            if (ownerJob != null && !markedJobs.contains(ownerJob)) {
                network.markForcedRecoveryJob(ownerJob, DIAGNOSTIC);
                markedJobs.add(ownerJob);
            }
            network.forceDiscardOperationOwnership(operationId);
        }
    }

    @Nullable
    private static UUID operationIdFromNode(@Nonnull String nodeId) {
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

    private static void refresh(@Nonnull QIOCraftingProcessor processor) {
        QIOCraftingProcessorDeviceRegistry.INSTANCE.refresh(processor);
    }

    private static void refreshAndWake(@Nonnull QIOCraftingProcessor processor) {
        refresh(processor);
        QIOFrequencyReference reference = processor.getFrequencyReference();
        if (reference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDeviceContents(
                  reference.getFrequencyUUID(), processor.getProcessorState().getProcessorUUID());
        }
    }
}
