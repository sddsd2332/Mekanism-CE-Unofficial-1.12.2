package mekanism.qioprocessing.common.processor;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorHostRegistry;

import java.util.Objects;

/**
 * Fail-closed validation of persisted processor state against the frozen Host registry.
 */
public final class QIOCraftingProcessorStateValidator {
    public enum Failure {
        NONE, MISSING_HOST, HOST_MISMATCH, DEFINITION_MISMATCH,
        SIGNATURE_MISMATCH, LANE_OUT_OF_RANGE
    }

    public static final class Result {
        private final Failure failure;
        private final String diagnostic;

        private Result(Failure failure, String diagnostic) {
            this.failure = failure;
            this.diagnostic = diagnostic;
        }

        public boolean valid() {
            return failure == Failure.NONE;
        }

        public Failure failure() {
            return failure;
        }

        public String diagnostic() {
            return diagnostic;
        }
    }

    private QIOCraftingProcessorStateValidator() {
    }

    public static Result validate(QIOCraftingProcessorState state,
                                  QIOCraftingProcessorHostRegistry.ResolvedHost resolved) {
        Objects.requireNonNull(state, "state");
        if (resolved == null) return failure(Failure.MISSING_HOST, "No processor Host registration resolved");
        if (!state.hostId().equals(resolved.getHostId())) {
            return failure(Failure.HOST_MISMATCH, "Persisted processor Host identity changed");
        }
        QIOCraftingProcessorDefinition definition = resolved.getDefinition();
        if (!state.definitionId().equals(definition.getId())) {
            return failure(Failure.DEFINITION_MISMATCH, "Persisted processor definition identity changed");
        }
        if (!state.definitionSignature().equals(definition.getSignature())) {
            return failure(Failure.SIGNATURE_MISMATCH, "Persisted processor definition signature changed");
        }
        for (QIOCraftingProcessorState.Lane lane : state.lanes().values()) {
            if (lane.laneId() >= definition.getLaneCount()) {
                return failure(Failure.LANE_OUT_OF_RANGE, "Persisted processor lane exceeds definition capacity");
            }
        }
        return failure(Failure.NONE, "");
    }

    private static Result failure(Failure failure, String diagnostic) {
        return new Result(failure, diagnostic);
    }
}

