package mekanism.qioprocessing.common.config;

import mekanism.api.Coord4D;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationRecipeConfigClientCacheTest {

    private final Coord4D coord = new Coord4D(new BlockPos(3, 4, 5), 0);
    private final QIOAutomationRecipeConfigType type = QIOAutomationRecipeConfigType.SCHEDULED;

    @AfterEach
    void clearCache() {
        QIOAutomationRecipeConfigClientCache.clear(coord, type);
    }

    @Test
    void broadcastDoesNotCompletePendingRequest() {
        UUID requestId = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot snapshot = snapshot(0, "");
        QIOAutomationRecipeConfigClientCache.expect(coord, type, requestId);

        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, null,
              QIOAutomationRecipeConfigClientCache.Status.OK, snapshot));

        QIOAutomationRecipeConfigClientCache.View view =
              QIOAutomationRecipeConfigClientCache.get(coord, type);
        assertTrue(view.isPending());
        assertSame(snapshot, view.getSnapshot());
    }

    @Test
    void lateCorrelatedResponseCannotOverwriteCompletedRequest() {
        UUID requestId = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot current = snapshot(0, "");
        QIOAutomationRecipeConfigSnapshot late = snapshot(0, "route");
        QIOAutomationRecipeConfigClientCache.expect(coord, type, requestId);
        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, requestId,
              QIOAutomationRecipeConfigClientCache.Status.OK, current));

        assertFalse(QIOAutomationRecipeConfigClientCache.apply(coord, type, requestId,
              QIOAutomationRecipeConfigClientCache.Status.OK, late));

        QIOAutomationRecipeConfigClientCache.View view =
              QIOAutomationRecipeConfigClientCache.get(coord, type);
        assertFalse(view.isPending());
        assertSame(current, view.getSnapshot());
        assertEquals("", view.getSnapshot().getQuery());
    }

    @Test
    void differentBroadcastPageDoesNotInterruptPendingPage() {
        UUID firstRequest = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot first = snapshot(0, "");
        QIOAutomationRecipeConfigClientCache.expect(coord, type, firstRequest);
        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, firstRequest,
              QIOAutomationRecipeConfigClientCache.Status.OK, first));

        UUID nextRequest = UUID.randomUUID();
        QIOAutomationRecipeConfigClientCache.expect(coord, type, nextRequest);
        QIOAutomationRecipeConfigSnapshot otherPage = snapshot(1, "");
        assertFalse(QIOAutomationRecipeConfigClientCache.apply(coord, type, null,
              QIOAutomationRecipeConfigClientCache.Status.OK, otherPage));

        QIOAutomationRecipeConfigClientCache.View view =
              QIOAutomationRecipeConfigClientCache.get(coord, type);
        assertTrue(view.isPending());
        assertSame(first, view.getSnapshot());
        assertFalse(view.isStale());
    }

    @Test
    void revisionBroadcastStaysStaleAfterAnOlderResponse() {
        UUID firstRequest = UUID.randomUUID();
        QIOAutomationRecipeConfigSnapshot first = snapshot(0, "");
        QIOAutomationRecipeConfigClientCache.expect(coord, type, firstRequest);
        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, firstRequest,
              QIOAutomationRecipeConfigClientCache.Status.OK, first));

        UUID nextRequest = UUID.randomUUID();
        QIOAutomationRecipeConfigClientCache.expect(coord, type, nextRequest);
        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, null,
              QIOAutomationRecipeConfigClientCache.Status.OK,
              snapshot(1, "", 1)));
        assertTrue(QIOAutomationRecipeConfigClientCache.apply(coord, type, nextRequest,
              QIOAutomationRecipeConfigClientCache.Status.OK, snapshot(1, "")));

        QIOAutomationRecipeConfigClientCache.View view =
              QIOAutomationRecipeConfigClientCache.get(coord, type);
        assertFalse(view.isPending());
        assertTrue(view.isStale());
    }

    private QIOAutomationRecipeConfigSnapshot snapshot(int offset, String query) {
        return snapshot(offset, query, 0);
    }

    private QIOAutomationRecipeConfigSnapshot snapshot(int offset, String query,
          long profileRevision) {
        UUID deviceUUID = UUID.randomUUID();
        return new QIOAutomationRecipeConfigSnapshot(type, UUID.randomUUID(), deviceUUID,
              "test:provider", profileRevision, 0, offset, offset, query, false, 1,
              RouteFilterMode.BLACKLIST, true, true, 1, 1, Collections.emptyList());
    }
}
