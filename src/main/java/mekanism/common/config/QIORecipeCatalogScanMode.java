package mekanism.common.config;

/**
 * Controls when the global QIO workbench recipe directory is rebuilt.
 */
public enum QIORecipeCatalogScanMode {
    AUTO_DISCOVERY,
    MANUAL_ONLY,
    /**
     * Restore a compatible cache for immediate use, then validate every Forge recipe.
     */
    FULL,
    /**
     * Rebuild only when no compatible, readable cache exists.
     */
    FIRST_ONLY,
    /**
     * Rebuild after a player encodes a recipe which is absent from the cached directory.
     */
    CHANGED,
    /**
     * Publish an empty global directory. Direct nine-slot encoding and UUID configuration copies
     * remain available, while batch and recursive recipe discovery are disabled.
     */
    DISABLED;

    public QIORecipeCatalogScanMode canonical() {
        return this == DISABLED || this == MANUAL_ONLY ? MANUAL_ONLY : AUTO_DISCOVERY;
    }

    public static QIORecipeCatalogScanMode readLegacy(String value) {
        String mode = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return mode.equals("DISABLED") || mode.equals("MANUAL_ONLY") ? MANUAL_ONLY : AUTO_DISCOVERY;
    }

    public boolean usesPersistentCatalog() {
        return false;
    }

    public boolean scansEveryStartup() {
        return canonical() == AUTO_DISCOVERY;
    }

    public boolean scansAfterCatalogMiss() {
        return false;
    }

    public boolean allowsTargetedEncoding() {
        return true;
    }

    public boolean allowsBatchEncoding() {
        return canonical() == AUTO_DISCOVERY;
    }

    public boolean allowsRecursiveImport() {
        return canonical() == AUTO_DISCOVERY;
    }
}
