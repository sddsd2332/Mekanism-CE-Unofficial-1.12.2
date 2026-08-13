package mekanism.qioprocessing.common.inventory.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementPageThrottleTest {

    @Test
    void deviceAndPolicyPagesMayBeRequestedInTheSameTick() {
        QIOManagementPageRequestThrottle container =
              new QIOManagementPageRequestThrottle();

        assertTrue(container.tryDevice(20));
        assertTrue(container.tryPolicy(20));
        assertTrue(container.tryPolicyLookup(20));
        assertFalse(container.tryDevice(20));
        assertFalse(container.tryPolicy(20));
        assertFalse(container.tryPolicyLookup(20));
        assertTrue(container.tryDevice(21));
        assertTrue(container.tryPolicy(21));
        assertTrue(container.tryPolicyLookup(21));
    }

    @Test
    void workbenchCommandsAndDependentPagesMayBeRequestedInTheSameTick() {
        QIOManagementPageRequestThrottle container =
              new QIOManagementPageRequestThrottle();

        assertTrue(container.tryWorkbenchConfiguration(30,
              QIOWorkbenchConfigurationContainer.RequestStream.COMMANDS));
        assertTrue(container.tryWorkbenchConfiguration(30,
              QIOWorkbenchConfigurationContainer.RequestStream.PRODUCTS));
        assertTrue(container.tryWorkbenchConfiguration(30,
              QIOWorkbenchConfigurationContainer.RequestStream.RECIPES));
        assertTrue(container.tryWorkbenchConfiguration(30,
              QIOWorkbenchConfigurationContainer.RequestStream.CANDIDATES));

        for (QIOWorkbenchConfigurationContainer.RequestStream stream :
              QIOWorkbenchConfigurationContainer.RequestStream.values()) {
            assertFalse(container.tryWorkbenchConfiguration(30, stream));
            assertTrue(container.tryWorkbenchConfiguration(31, stream));
        }
    }
}
