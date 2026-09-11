package mekanism.common.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeCatalogScanModeTest {

    @Test
    void exposesTheExpectedModeCapabilities() {
        assertEquals(QIORecipeCatalogScanMode.DISABLED,
              new QIOProcessingConfig().recipeCatalogScanMode.val());
        assertTrue(QIORecipeCatalogScanMode.FULL.scansEveryStartup());
        assertFalse(QIORecipeCatalogScanMode.FIRST_ONLY.scansEveryStartup());
        assertTrue(QIORecipeCatalogScanMode.CHANGED.scansAfterCatalogMiss());
        assertTrue(QIORecipeCatalogScanMode.CHANGED.allowsTargetedEncoding());
        assertTrue(QIORecipeCatalogScanMode.DISABLED.allowsTargetedEncoding());
        assertFalse(QIORecipeCatalogScanMode.DISABLED.usesPersistentCatalog());
        assertFalse(QIORecipeCatalogScanMode.DISABLED.allowsBatchEncoding());
        assertFalse(QIORecipeCatalogScanMode.DISABLED.allowsRecursiveImport());
    }

    @Test
    void loadsEveryRecipeCatalogPropertyIntoTheForgeConfiguration() {
        Configuration forgeConfig = new Configuration();
        QIOProcessingConfig qioConfig = new QIOProcessingConfig();

        qioConfig.load(forgeConfig);

        ConfigCategory recipeCatalog = forgeConfig.getCategory("recipe_catalog");
        assertTrue(recipeCatalog.containsKey("scanMode"));
        assertTrue(recipeCatalog.containsKey("capturesPerTick"));
        assertTrue(recipeCatalog.containsKey("captureTimePerTick"));
        assertTrue(recipeCatalog.containsKey("workerThreads"));
        assertEquals(4, recipeCatalog.size());
        assertEquals(9, forgeConfig.getCategoryNames().size());
    }
}
