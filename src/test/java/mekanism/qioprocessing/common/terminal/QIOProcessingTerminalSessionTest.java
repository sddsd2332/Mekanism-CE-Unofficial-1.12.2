package mekanism.qioprocessing.common.terminal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QIOProcessingTerminalSessionTest {

    @Test
    void everyCommandIsBoundToTheExactOpenTargetAndAccessRevision() {
        UUID nonce = UUID.randomUUID();
        UUID playerUUID = UUID.randomUUID();
        UUID terminalUUID = UUID.randomUUID();
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(nonce,
              playerUUID, QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.MANAGEMENT, terminalUUID, 4,
              frequencyUUID, 9);

        assertEquals(QIOProcessingTerminalSession.Validation.ACCEPTED,
              validate(session, nonce, playerUUID, terminalUUID, frequencyUUID, 4, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.NONCE_MISMATCH,
              validate(session, UUID.randomUUID(), playerUUID, terminalUUID,
                    frequencyUUID, 4, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.PLAYER_MISMATCH,
              validate(session, nonce, UUID.randomUUID(), terminalUUID,
                    frequencyUUID, 4, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.TERMINAL_ID_MISMATCH,
              validate(session, nonce, playerUUID, UUID.randomUUID(),
                    frequencyUUID, 4, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.TARGET_REVISION_MISMATCH,
              validate(session, nonce, playerUUID, terminalUUID,
                    frequencyUUID, 3, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.FREQUENCY_MISMATCH,
              validate(session, nonce, playerUUID, terminalUUID,
                    UUID.randomUUID(), 4, 9));
        assertEquals(QIOProcessingTerminalSession.Validation.ACCESS_REVISION_MISMATCH,
              validate(session, nonce, playerUUID, terminalUUID,
                    frequencyUUID, 4, 10));
    }

    @Test
    void acceptedMutationAdvancesTheGenerationAndOldPacketsStayInvalid() {
        UUID nonce = UUID.randomUUID();
        UUID playerUUID = UUID.randomUUID();
        UUID terminalUUID = UUID.randomUUID();
        UUID oldFrequency = UUID.randomUUID();
        UUID newFrequency = UUID.randomUUID();
        QIOProcessingTerminalSession session = new QIOProcessingTerminalSession(nonce,
              playerUUID, QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              QIOProcessingTerminalType.SMART_PROCESSING, terminalUUID, 2,
              oldFrequency, 6);

        session.rebind(2, 3, newFrequency, 1);

        assertEquals(3, session.getTargetRevision());
        assertEquals(newFrequency, session.getFrequencyUUID());
        assertEquals(QIOProcessingTerminalSession.Validation.TARGET_REVISION_MISMATCH,
              validate(session, nonce, playerUUID, terminalUUID, oldFrequency, 2, 6));
        assertEquals(QIOProcessingTerminalSession.Validation.ACCEPTED,
              validate(session, nonce, playerUUID, terminalUUID, newFrequency, 3, 1));
        assertThrows(IllegalStateException.class,
              () -> session.advanceTargetRevision(2, 4));
        assertThrows(IllegalArgumentException.class,
              () -> session.advanceTargetRevision(3, 3));

        session.close();
        assertEquals(QIOProcessingTerminalSession.Validation.CLOSED,
              validate(session, nonce, playerUUID, terminalUUID, newFrequency, 3, 1));
    }

    @Test
    void unboundSessionsUseTheExplicitNoAccessRevisionSentinel() {
        assertThrows(IllegalArgumentException.class, () ->
              new QIOProcessingTerminalSession(UUID.randomUUID(),
                    QIOProcessingTerminalSession.TargetKind.BLOCK,
                    QIOProcessingTerminalType.MAINTENANCE, UUID.randomUUID(),
                    0, null, 0));
        assertThrows(IllegalArgumentException.class, () ->
              new QIOProcessingTerminalSession(UUID.randomUUID(),
                    QIOProcessingTerminalSession.TargetKind.BLOCK,
                    QIOProcessingTerminalType.MAINTENANCE, UUID.randomUUID(),
                    0, UUID.randomUUID(), -1));
    }

    private static QIOProcessingTerminalSession.Validation validate(
          QIOProcessingTerminalSession session, UUID nonce, UUID playerUUID,
          UUID terminalUUID, UUID frequencyUUID, long generation,
          long accessRevision) {
        return session.validate(nonce, playerUUID,
              QIOProcessingTerminalSession.TargetKind.PORTABLE_ITEM,
              session.getTerminalType(), terminalUUID, generation,
              frequencyUUID, accessRevision);
    }

}
