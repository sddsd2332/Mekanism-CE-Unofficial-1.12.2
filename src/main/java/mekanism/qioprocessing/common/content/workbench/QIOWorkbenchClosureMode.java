package mekanism.qioprocessing.common.content.workbench;

/** Controls how an explicitly encoded workbench recipe discovers its dependencies. */
public enum QIOWorkbenchClosureMode {
    NONE,
    PREFERRED,
    ALL;

    public QIOWorkbenchClosureMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
