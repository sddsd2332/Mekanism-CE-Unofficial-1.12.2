package mekanism.qioprocessing.common.inventory.container;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class QIOSmartProcessingRequestLedgerTest {

    @Test
    void duplicateMutationReplaysOnlyTheMatchingCompletedResponse() {
        QIOSmartProcessingRequestLedger ledger = new QIOSmartProcessingRequestLedger();
        UUID requestId = UUID.randomUUID();
        assertEquals(QIOSmartProcessingRequestLedger.Status.NEW,
              ledger.begin(requestId, "REQUEST|iron|4").getStatus());
        assertEquals(QIOSmartProcessingRequestLedger.Status.PENDING,
              ledger.begin(requestId, "REQUEST|iron|4").getStatus());
        assertEquals(QIOSmartProcessingRequestLedger.Status.CONFLICT,
              ledger.begin(requestId, "REQUEST|gold|4").getStatus());

        QIOSmartProcessingRequestLedger.Response response =
              new QIOSmartProcessingRequestLedger.Response("ACCEPTED", null, null);
        ledger.complete(requestId, "REQUEST|iron|4", response);
        QIOSmartProcessingRequestLedger.Lookup replay = ledger.begin(requestId,
              "REQUEST|iron|4");
        assertEquals(QIOSmartProcessingRequestLedger.Status.REPLAY, replay.getStatus());
        assertSame(response, replay.getResponse());
    }

    @Test
    void abortedUnexecutedMutationCanBeRetried() {
        QIOSmartProcessingRequestLedger ledger = new QIOSmartProcessingRequestLedger();
        UUID requestId = UUID.randomUUID();
        assertEquals(QIOSmartProcessingRequestLedger.Status.NEW,
              ledger.begin(requestId, "CONFIRM|preview").getStatus());
        ledger.abort(requestId, "CONFIRM|preview");
        QIOSmartProcessingRequestLedger.Lookup retried = ledger.begin(requestId,
              "CONFIRM|preview");
        assertEquals(QIOSmartProcessingRequestLedger.Status.NEW, retried.getStatus());
        assertNull(retried.getResponse());
    }
}
