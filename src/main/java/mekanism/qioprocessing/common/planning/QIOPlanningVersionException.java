package mekanism.qioprocessing.common.planning;

/**
 * A recipe generation changed; no previously selected plan may be returned.
 */
final class QIOPlanningVersionException extends IllegalStateException {
    QIOPlanningVersionException() {
        super("Workbench declaration generation changed while planning");
    }
}
