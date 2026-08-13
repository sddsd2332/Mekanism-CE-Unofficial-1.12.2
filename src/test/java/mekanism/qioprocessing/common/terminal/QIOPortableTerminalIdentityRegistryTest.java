package mekanism.qioprocessing.common.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOPortableTerminalIdentityRegistryTest {

    private final QIOPortableTerminalIdentityRegistry registry =
          QIOPortableTerminalIdentityRegistry.INSTANCE;

    @AfterEach
    void cleanup() {
        registry.shutdown();
    }

    @Test
    void twoReachableStackInstancesQuarantineBothCopies() {
        UUID terminalUUID = UUID.randomUUID();
        Object firstStack = new Object();
        Object copiedStack = new Object();
        QIOPortableTerminalIdentityRegistry.Lease first = registry.acquire(terminalUUID,
              firstStack, "player-a/main/0");
        assertTrue(first.isUsable());

        QIOPortableTerminalIdentityRegistry.Lease copy = registry.acquire(terminalUUID,
              copiedStack, "player-b/offhand/40");

        assertFalse(first.isUsable());
        assertFalse(copy.isUsable());
        assertTrue(first.isQuarantined());
        assertTrue(copy.isQuarantined());
        assertEquals(Arrays.asList("player-a/main/0", "player-b/offhand/40"),
              registry.getActiveAuditLocations(terminalUUID));
        assertFalse(registry.acknowledgeRecovery(terminalUUID));

        first.close();
        copy.close();
        assertTrue(registry.isQuarantined(terminalUUID));
        assertTrue(registry.acknowledgeRecovery(terminalUUID));
        assertFalse(registry.isQuarantined(terminalUUID));
    }

    @Test
    void reopeningTheSameExactStackDoesNotCreateAFalseIdentityConflict() {
        UUID terminalUUID = UUID.randomUUID();
        Object stack = new Object();
        QIOPortableTerminalIdentityRegistry.Lease first = registry.acquire(terminalUUID,
              stack, "player/main/0");
        QIOPortableTerminalIdentityRegistry.Lease duplicateOpen = registry.acquire(
              terminalUUID, stack, "player/main/0");

        assertTrue(first.isUsable());
        assertTrue(duplicateOpen.isAlreadyOpenRejection());
        assertFalse(duplicateOpen.isUsable());
        assertFalse(registry.isQuarantined(terminalUUID));
        duplicateOpen.close();
        assertTrue(first.isUsable());
        first.close();
    }
}
