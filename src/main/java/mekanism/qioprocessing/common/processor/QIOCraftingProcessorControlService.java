package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-side monitor/maintenance operations with exact operation identity checks.
 * No method silently clears contaminated or quarantined state.
 */
public final class QIOCraftingProcessorControlService {
    public enum Result {APPLIED, NOT_FOUND, CONFLICT, REQUIRES_RECONCILIATION}

    private QIOCraftingProcessorControlService() {
    }

    public static QIOCraftingProcessorSnapshot snapshot(QIOCraftingProcessorTile tile) {
        return QIOCraftingProcessorSnapshot.capture(tile);
    }

    public static Result cancel(QIOCraftingProcessorTile tile, UUID operationId, String reason) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(operationId, "operationId");
        QIOCraftingProcessorState state = tile.processorState();
        for (QIOCraftingProcessorState.Lane lane : state.lanes().values()) {
            if (!operationId.equals(lane.operationId())) continue;
            if (lane.token().state().isTerminal()) {
                return lane.token().state() == MachineOperationToken.State.CANCELLED
                        ? Result.APPLIED : Result.CONFLICT;
            }
            if (lane.state() == QIOCraftingProcessorState.LaneState.COLLECTING) {
                return Result.REQUIRES_RECONCILIATION;
            }
            tile.cancelOperation(lane.laneId(), boundedReason(reason));
            return Result.APPLIED;
        }
        return Result.NOT_FOUND;
    }

    public static Result markNeedsReconciliation(QIOCraftingProcessorTile tile, UUID operationId,
                                                 String reason) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(operationId, "operationId");
        QIOCraftingProcessorState state = tile.processorState();
        for (QIOCraftingProcessorState.Lane lane : state.lanes().values()) {
            if (!operationId.equals(lane.operationId())) continue;
            if (lane.token().state().isTerminal()) return Result.CONFLICT;
            tile.updateProcessorState(current -> current.updateLane(lane.laneId(), lane.currentTick(),
                    QIOCraftingProcessorState.LaneState.NEEDS_RECONCILIATION, boundedReason(reason)));
            return Result.REQUIRES_RECONCILIATION;
        }
        return Result.NOT_FOUND;
    }

    private static String boundedReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) return "operator action";
        return reason.length() > 512 ? reason.substring(0, 512) : reason;
    }
}

