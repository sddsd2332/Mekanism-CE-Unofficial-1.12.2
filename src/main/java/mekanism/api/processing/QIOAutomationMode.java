package mekanism.api.processing;

/**
 * QIO automation modes that a machine processing provider may explicitly support.
 */
public enum QIOAutomationMode {
    /** Accepts scheduled operations owned by a QIO crafting job. */
    SCHEDULED,
    /** Starts standalone operations from an administrator-defined passive policy. */
    PASSIVE,
    /** Only drains actual machine outputs and never exposes a production route. */
    OUTPUT_ONLY
}
