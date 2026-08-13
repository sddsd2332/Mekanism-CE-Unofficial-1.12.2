package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.PageMode;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementPolicyClientCacheTest {

    @Test
    void policyPagesRequireOneSessionRevisionAndContiguousOffsets() {
        UUID nonce = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOPolicyCatalog catalog = new QIOPolicyCatalog();
        catalog.setGlobalRoutePolicy("test:provider", "route", "recipe",
              QIOPolicyCatalog.Toggle.ENABLED, 2L);
        catalog.setDeviceDefaultPolicy(deviceUUID, QIOPolicyCatalog.Toggle.ENABLED, 3, 4);
        List<QIOPolicyEntrySnapshot> entries = catalog.getPolicyEntries();
        QIOManagementPolicyClientCache cache = new QIOManagementPolicyClientCache();
        QIOPageCursor cursor = new QIOPageCursor(nonce, catalog.getRevision(), 2);

        assertEquals(0, cache.getPageGeneration());
        assertTrue(cache.applyPage(nonce, catalog.getRevision(), 0, entries.size(),
              entries.subList(0, 2), cursor));
        assertEquals(1, cache.getPageGeneration());
        assertFalse(cache.applyPage(UUID.randomUUID(), catalog.getRevision(), 2,
              entries.size(), entries.subList(2, 3), null));
        assertFalse(cache.applyPage(nonce, catalog.getRevision(), 1, entries.size(),
              entries.subList(2, 3), null));
        assertTrue(cache.applyPage(nonce, catalog.getRevision(), 2, entries.size(),
              entries.subList(2, 3), null));
        assertEquals(2, cache.getPageGeneration());

        assertEquals(3, cache.getEntries().size());
        assertNull(cache.getNextCursor());
    }

    @Test
    void authoritativeMutationInvalidatesPagesFromTheOldRevision() {
        UUID nonce = UUID.randomUUID();
        QIOPolicyCatalog catalog = new QIOPolicyCatalog();
        QIOManagementPolicyClientCache cache = new QIOManagementPolicyClientCache();
        QIOPolicyEntrySnapshot oldEntry = catalog.snapshotGlobalDefault();
        assertTrue(cache.applyPage(nonce, 0, 0, 1,
              Collections.singletonList(oldEntry), null));
        UUID stalePageRequestId = UUID.randomUUID();
        cache.expectPage(stalePageRequestId);

        catalog.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.DISABLED, 7);
        QIOPolicyEntrySnapshot authoritative = catalog.snapshotGlobalDefault();
        UUID requestId = UUID.randomUUID();
        assertFalse(cache.applyMutation(UUID.randomUUID(), requestId,
              MutationStatus.APPLIED,
              catalog.getRevision(), authoritative));
        assertTrue(cache.applyMutation(nonce, requestId, MutationStatus.APPLIED,
              catalog.getRevision(), authoritative));

        assertTrue(cache.getEntries().isEmpty());
        assertEquals(-1, cache.getSourceRevision());
        assertEquals(MutationStatus.APPLIED, cache.getLastMutationStatus());
        assertEquals(requestId, cache.getLastMutationRequestId());
        assertEquals(1, cache.getMutationGeneration());
        assertEquals(authoritative, cache.getLastAuthoritativeEntry());
        assertFalse(cache.applyPage(nonce, stalePageRequestId, PageMode.CONFIGURED,
              -1, 0, 0, 1, Collections.singletonList(oldEntry), null));
    }

    @Test
    void deviceLookupIsSessionBoundAndAdvancesItsOwnGeneration() {
        UUID nonce = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        QIOPolicyCatalog catalog = new QIOPolicyCatalog();
        QIOPolicyEntrySnapshot entry = catalog.snapshotDeviceDefault(deviceUUID);
        QIOManagementPolicyClientCache cache = new QIOManagementPolicyClientCache();

        assertTrue(cache.applyPage(nonce, catalog.getRevision(), 0, 1,
              Collections.singletonList(catalog.snapshotGlobalDefault()), null));
        assertFalse(cache.applyLookup(UUID.randomUUID(), requestId, entry));
        assertTrue(cache.applyLookup(nonce, requestId, entry));

        assertEquals(1, cache.getLookupGeneration());
        assertEquals(requestId, cache.getLastLookupRequestId());
        assertEquals(entry, cache.getLastLookupEntry());
        assertEquals(1, cache.getEntries().size());
    }

    @Test
    void staleSearchResponsesCannotReplaceTheExpectedWorkbenchPage() {
        UUID nonce = UUID.randomUUID();
        UUID staleRequest = UUID.randomUUID();
        UUID currentRequest = UUID.randomUUID();
        QIOPolicyCatalog catalog = new QIOPolicyCatalog();
        QIOPolicyEntrySnapshot entry = catalog.snapshotGlobalRoute(
              "mekanismqioprocessing:workbench", "test:recipe", "test:recipe");
        QIOManagementPolicyClientCache cache = new QIOManagementPolicyClientCache();

        cache.expectPage(staleRequest);
        cache.expectPage(currentRequest);
        assertFalse(cache.applyPage(nonce, staleRequest, PageMode.WORKBENCH, 4,
              catalog.getRevision(), 0, 1, Collections.singletonList(entry), null));
        assertTrue(cache.applyPage(nonce, currentRequest, PageMode.WORKBENCH, 4,
              catalog.getRevision(), 0, 1, Collections.singletonList(entry), null));

        assertEquals(PageMode.WORKBENCH, cache.getPageMode());
        assertEquals(4, cache.getRecipeCatalogRevision());
        assertEquals(Collections.singletonList(entry), cache.getEntries());
    }
}
