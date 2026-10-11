package mekanism.qioprocessing.common.processor;

import mekanism.api.qio.external.QIOFrequencyReference;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Frequency-scoped Processor monitor/control facade for a future terminal network handler.
 * Authorization is intentionally performed by the caller before this facade is reached.
 */
public final class QIOCraftingProcessorMonitorService {
    private QIOCraftingProcessorMonitorService() {
    }

    public static List<QIOCraftingProcessorSnapshot> list(QIOFrequencyReference reference) {
        Objects.requireNonNull(reference, "reference");
        List<QIOCraftingProcessorEndpointDirectory.LoadedProcessor> processors =
                QIOCraftingProcessorEndpointDirectory.INSTANCE.bound(reference);
        if (processors.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(processors.stream()
                .map(value -> QIOCraftingProcessorControlService.snapshot(value.tile()))
                .collect(Collectors.toList()));
    }

    public static QIOCraftingProcessorControlService.Result cancel(QIOFrequencyReference reference,
                                                                   UUID processorId, UUID operationId, String reason) {
        return cancel(reference, processorId, -1, operationId, reason);
    }

    public static QIOCraftingProcessorControlService.Result cancel(QIOFrequencyReference reference,
                                                                   UUID processorId, long expectedEndpointRevision, UUID operationId, String reason) {
        QIOCraftingProcessorTile tile = find(reference, processorId);
        if (tile != null && expectedEndpointRevision >= 0
                && tile.endpointRevision() != expectedEndpointRevision) {
            return QIOCraftingProcessorControlService.Result.CONFLICT;
        }
        return tile == null ? QIOCraftingProcessorControlService.Result.NOT_FOUND
                : QIOCraftingProcessorControlService.cancel(tile, operationId, reason);
    }

    public static QIOCraftingProcessorControlService.Result reconcile(QIOFrequencyReference reference,
                                                                      UUID processorId, UUID operationId, String reason) {
        return reconcile(reference, processorId, -1, operationId, reason);
    }

    public static QIOCraftingProcessorControlService.Result reconcile(QIOFrequencyReference reference,
                                                                      UUID processorId, long expectedEndpointRevision, UUID operationId, String reason) {
        QIOCraftingProcessorTile tile = find(reference, processorId);
        if (tile != null && expectedEndpointRevision >= 0
                && tile.endpointRevision() != expectedEndpointRevision) {
            return QIOCraftingProcessorControlService.Result.CONFLICT;
        }
        return tile == null ? QIOCraftingProcessorControlService.Result.NOT_FOUND
                : QIOCraftingProcessorControlService.markNeedsReconciliation(tile, operationId, reason);
    }

    private static QIOCraftingProcessorTile find(QIOFrequencyReference reference, UUID processorId) {
        if (reference == null || processorId == null) return null;
        for (QIOCraftingProcessorEndpointDirectory.LoadedProcessor processor :
                QIOCraftingProcessorEndpointDirectory.INSTANCE.bound(reference)) {
            if (processorId.equals(processor.processorId())) return processor.tile();
        }
        return null;
    }
}

