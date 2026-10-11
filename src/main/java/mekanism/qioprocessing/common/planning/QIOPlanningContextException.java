package mekanism.qioprocessing.common.planning;

/**
 * A recoverable validation context failure, distinct from a bug or a missing recipe.
 */
public final class QIOPlanningContextException extends RuntimeException {
    private final QIOPlanningResult.Status status;

    public QIOPlanningContextException(QIOPlanningResult.Status status, String message) {
        super(message);
        this.status = status;
    }

    public QIOPlanningResult.Status getStatus() {
        return status;
    }
}
