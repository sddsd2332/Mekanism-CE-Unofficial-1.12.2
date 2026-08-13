package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOSmartProcessingClientCacheTest {

    private static final PortableResourceDescriptor IRON = PortableResourceDescriptor.named(
          PortableResourceDescriptor.Kind.ITEM, "minecraft:iron_ingot", 0, null);

    @Test
    void pagesRequireTheExpectedRequestAndAllowNonContiguousOffsets() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        QIOSmartProcessingResourceEntry entry = new QIOSmartProcessingResourceEntry(
              IRON, 0, 0, 0, 4, true, false);
        UUID first = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(first, QIOSmartProcessingResourceFilter.ALL, "", 0));
        assertFalse(cache.applyPage(nonce, UUID.randomUUID(),
              QIOSmartProcessingResourceFilter.ALL, "", 5, 0, 100,
              Collections.singletonList(entry)));
        assertTrue(cache.applyPage(nonce, first, QIOSmartProcessingResourceFilter.ALL, "",
              5, 0, 100, Collections.singletonList(entry)));

        UUID distant = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(distant, QIOSmartProcessingResourceFilter.ALL, "", 55));
        assertTrue(cache.applyPage(nonce, distant, QIOSmartProcessingResourceFilter.ALL, "",
              5, 55, 100, Collections.singletonList(entry)));
        assertEquals(entry, cache.getEntry(0));
        assertEquals(entry, cache.getEntry(55));
        assertNull(cache.getEntry(54));
    }

    @Test
    void changedRevisionMayResetAnOffsetRequestToTheFirstPage() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        UUID first = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(first, QIOSmartProcessingResourceFilter.ITEM, "iron", 0));
        assertTrue(cache.applyPage(nonce, first, QIOSmartProcessingResourceFilter.ITEM, "iron",
              2, 0, 0, Collections.emptyList()));

        UUID refresh = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(refresh, QIOSmartProcessingResourceFilter.ITEM,
              "iron", 55));
        assertTrue(cache.applyPage(nonce, refresh, QIOSmartProcessingResourceFilter.ITEM, "iron",
              3, 0, 0, Collections.emptyList()));
        assertEquals(3, cache.getSourceRevision());
    }

    @Test
    void distantScrollingRetainsOnlyABoundedNumberOfPages() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        QIOSmartProcessingResourceEntry entry = new QIOSmartProcessingResourceEntry(
              IRON, 0, 0, 0, 0, true, false);
        for (int page = 0; page < 12; page++) {
            int offset = page * 55;
            UUID request = UUID.randomUUID();
            assertTrue(cache.expectPageRequest(request,
                  QIOSmartProcessingResourceFilter.ALL, "", offset));
            assertTrue(cache.applyPage(nonce, request,
                  QIOSmartProcessingResourceFilter.ALL, "", 9, offset, 661,
                  Collections.singletonList(entry)));
        }
        assertNull(cache.getEntry(0));
        assertEquals(entry, cache.getEntry(11 * 55));
    }

    @Test
    void previewResponsesAreSessionIsolatedAndReplaceTheProjection() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        QIOSmartProcessingPreviewSnapshot preview = new QIOSmartProcessingPreviewSnapshot(
              UUID.randomUUID(), QIOOrderPreview.State.READY, QIOPlanningResult.Status.SUCCESS,
              "", IRON, 6, 0, 80, 10, 3, 1,
              Collections.singletonMap(IRON, 2L), Collections.emptyList(), null);
        UUID request = UUID.randomUUID();

        assertFalse(cache.applyPreview(UUID.randomUUID(), request, "READY", preview));
        assertTrue(cache.expectPreviewRequest(request));
        assertFalse(cache.applyPreview(UUID.randomUUID(), request, "READY", preview));
        assertTrue(cache.applyPreview(nonce, request, "READY", preview));
        assertEquals(preview.getPreviewId(), cache.getPreview().getPreviewId());
        UUID nextRequest = UUID.randomUUID();
        assertTrue(cache.expectPreviewRequest(nextRequest));
        assertTrue(cache.applyPreview(nonce, nextRequest, "ACCEPTED", null));
        assertNull(cache.getPreview());
    }

    @Test
    void stalePreviewResponseCannotReplaceTheCurrentRequest() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        assertTrue(cache.expectPreviewRequest(stale));
        cache.cancelExpectedPreviewRequest(stale);
        assertTrue(cache.expectPreviewRequest(current));

        assertFalse(cache.applyPreview(nonce, stale, "ACCEPTED", null));
        assertEquals(current, cache.getExpectedPreviewRequestId());
        assertTrue(cache.applyPreview(nonce, current, "ACCEPTED", null));
    }

    @Test
    void recipeViewerTargetsRequireALoadedAuthoritativeRoute() {
        QIOSmartProcessingClientCache cache = new QIOSmartProcessingClientCache();
        UUID nonce = UUID.randomUUID();
        cache.beginSession(nonce);
        QIOSmartProcessingResourceEntry schedulable = new QIOSmartProcessingResourceEntry(
              IRON, 0, 0, 0, 0, true, false);
        UUID request = UUID.randomUUID();
        assertTrue(cache.expectPageRequest(request, QIOSmartProcessingResourceFilter.ALL, "", 0));
        assertTrue(cache.applyPage(nonce, request, QIOSmartProcessingResourceFilter.ALL, "",
              1, 0, 1, Collections.singletonList(schedulable)));

        assertFalse(cache.selectRecipeViewerTarget(UUID.randomUUID(), IRON));
        assertTrue(cache.selectRecipeViewerTarget(nonce, IRON));
        assertEquals(IRON, cache.getRecipeViewerTarget());
        assertEquals(1, cache.getRecipeViewerTargetGeneration());

        cache.clear();
        assertNull(cache.getRecipeViewerTarget());
        assertFalse(cache.selectRecipeViewerTarget(nonce, IRON));
    }
}
