package mekanism.common.integration.groovyscript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasRegistrationTest {

    @Test
    void exposesStableGroovyPropertyAliases() {
        GasRegistration registration = new GasRegistration();

        assertEquals("gas", registration.getName());
        assertTrue(registration.getAliases().contains("gas"));
        assertTrue(registration.getAliases().contains("gases"));
        assertTrue(registration.getAliases().contains("gas_registry"));
    }

    @Test
    void exposesDeferredFluidNameOverload() throws NoSuchMethodException {
        assertNotNull(GasRegistration.class.getMethod("setFluid", String.class, String.class));
    }
}
