package mekanism.qioprocessing.common.planning;

/**
 * A bounded search stopped without proving that no usable recipe exists.
 */
public final class QIOPlanningLimitException extends RuntimeException {
    private final boolean searchLimit;

    QIOPlanningLimitException(String message) {
        this(message, false);
    }

    private QIOPlanningLimitException(String message, boolean searchLimit) {
        super(message, null, false, false);
        this.searchLimit = searchLimit;
    }

    static QIOPlanningLimitException search(String message) {
        return new QIOPlanningLimitException(message, true);
    }

    boolean isSearchLimit() {
        return searchLimit;
    }
}
