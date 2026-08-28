package mekanism.common.config;

/** Controls when the global QIO workbench recipe directory is rebuilt. */
public enum QIORecipeCatalogScanMode {
    /** Restore a compatible cache for immediate use, then validate every Forge recipe. */
    FULL,
    /** Rebuild only when no compatible, readable cache exists. */
    FIRST_ONLY,
    /** Rebuild after a player encodes a recipe which is absent from the cached directory. */
    CHANGED,
    /**
     * Publish an empty global directory. Direct nine-slot encoding and UUID configuration copies
     * remain available, while batch and recursive recipe discovery are disabled.
     */
    DISABLED;

    public boolean usesPersistentCatalog() {
        return this != DISABLED;
    }

    public boolean scansEveryStartup() {
        return this == FULL;
    }

    public boolean scansAfterCatalogMiss() {
        return this == CHANGED;
    }

    public boolean allowsTargetedEncoding() {
        return this == CHANGED || this == DISABLED;
    }

    public boolean allowsBatchEncoding() {
        return this != DISABLED;
    }

    public boolean allowsRecursiveImport() {
        return this != DISABLED;
    }
}
