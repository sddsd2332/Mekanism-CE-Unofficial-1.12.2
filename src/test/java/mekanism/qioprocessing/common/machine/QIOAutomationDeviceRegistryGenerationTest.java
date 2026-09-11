package mekanism.qioprocessing.common.machine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression coverage for recipe-generation notifications entering the QIO directory. */
class QIOAutomationDeviceRegistryGenerationTest {

    @Test
    void observedGenerationIsMonotonicAndNegativeValuesAreRejected() {
        QIOAutomationDeviceRegistry registry = QIOAutomationDeviceRegistry.INSTANCE;
        long before = registry.getObservedRecipeGeneration();
        registry.onRecipeGenerationChanged(before + 2);
        assertEquals(before + 2, registry.getObservedRecipeGeneration());
        org.junit.jupiter.api.Assertions.assertTrue(registry.isRecipeGenerationRefreshPending());

        registry.onRecipeGenerationChanged(before + 1);
        assertEquals(before + 2, registry.getObservedRecipeGeneration());
        assertThrows(IllegalArgumentException.class,
              () -> registry.onRecipeGenerationChanged(-1));
    }

    @Test
    void refreshPassConsumesThePendingGenerationNotification() throws Exception {
        QIOAutomationDeviceRegistry registry = QIOAutomationDeviceRegistry.INSTANCE;
        registry.onRecipeGenerationChanged(registry.getObservedRecipeGeneration() + 1);
        Method refresh = QIOAutomationDeviceRegistry.class.getDeclaredMethod("refreshRecipeProviders");
        refresh.setAccessible(true);
        refresh.invoke(registry);
        org.junit.jupiter.api.Assertions.assertFalse(registry.isRecipeGenerationRefreshPending());
    }
}
