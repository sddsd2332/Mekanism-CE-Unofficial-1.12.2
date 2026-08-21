package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceFilter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session-aware bounded page cache for the smart-processing order window. */
/**
 * QIO 处理模块中的 QIOSmartProcessingClientCache 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOSmartProcessingClientCache {

    private static final int MAX_CACHED_PAGES = 8;

    private final Map<Integer, CachedPage> pages = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CachedPage> eldest) {
            return size() > MAX_CACHED_PAGES;
        }
    };
    @Nullable private UUID sessionNonce;
    private long sourceRevision = -1;
    private int totalSize;
    private QIOSmartProcessingResourceFilter filter = QIOSmartProcessingResourceFilter.ALL;
    private String query = "";
    private long pageGeneration;
    private long previewGeneration;
    @Nullable private QIOSmartProcessingPreviewSnapshot preview;
    @Nullable private UUID lastRequestId;
    @Nullable private String lastActionStatus;
    @Nullable private UUID expectedPageRequestId;
    private QIOSmartProcessingResourceFilter expectedPageFilter =
          QIOSmartProcessingResourceFilter.ALL;
    private String expectedPageQuery = "";
    private int expectedPageOffset;
    @Nullable private UUID expectedPreviewRequestId;
    private long recipeViewerTargetGeneration;
    @Nullable private PortableResourceDescriptor recipeViewerTarget;

    public void beginSession(@Nonnull UUID nonce) {
        Objects.requireNonNull(nonce, "nonce");
        if (!nonce.equals(sessionNonce)) {
            clear();
            sessionNonce = nonce;
        }
    }

    public boolean expectPageRequest(@Nonnull UUID requestId,
          @Nonnull QIOSmartProcessingResourceFilter requestedFilter,
          @Nonnull String requestedQuery, int requestedOffset) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestedFilter, "requestedFilter");
        Objects.requireNonNull(requestedQuery, "requestedQuery");
        if (expectedPageRequestId != null || requestedOffset < 0) return false;
        expectedPageRequestId = requestId;
        expectedPageFilter = requestedFilter;
        expectedPageQuery = requestedQuery;
        expectedPageOffset = requestedOffset;
        return true;
    }

    public void cancelExpectedPageRequest(@Nullable UUID requestId) {
        if (requestId != null && requestId.equals(expectedPageRequestId)) {
            expectedPageRequestId = null;
        }
    }

    public boolean applyPage(@Nonnull UUID responseNonce, @Nonnull UUID requestId,
          @Nonnull QIOSmartProcessingResourceFilter responseFilter, @Nonnull String responseQuery,
          long revision, int offset, int total,
          @Nonnull List<QIOSmartProcessingResourceEntry> page) {
        if (!responseNonce.equals(sessionNonce) || !requestId.equals(expectedPageRequestId) ||
              responseFilter != expectedPageFilter || !responseQuery.equals(expectedPageQuery) ||
              revision < 0 || offset < 0 || total < 0 || offset > total ||
              page.size() > total - offset) return false;
        boolean changedRevision = revision != sourceRevision || responseFilter != filter ||
              !responseQuery.equals(query);
        if (offset != expectedPageOffset && !(changedRevision && offset == 0)) return false;
        expectedPageRequestId = null;
        if (changedRevision) {
            pages.clear();
            sourceRevision = revision;
            filter = responseFilter;
            query = responseQuery;
            totalSize = total;
            recipeViewerTarget = null;
        } else if (totalSize != total) {
            return false;
        }
        pages.put(offset, new CachedPage(offset, page));
        pageGeneration = next(pageGeneration);
        return true;
    }

    public void resetDirectory(@Nonnull QIOSmartProcessingResourceFilter nextFilter,
          @Nonnull String nextQuery) {
        pages.clear();
        sourceRevision = -1;
        totalSize = 0;
        filter = Objects.requireNonNull(nextFilter, "nextFilter");
        query = Objects.requireNonNull(nextQuery, "nextQuery");
        expectedPageRequestId = null;
        recipeViewerTarget = null;
        pageGeneration = next(pageGeneration);
    }

    @Nullable
    public QIOSmartProcessingResourceEntry getEntry(int index) {
        if (index < 0 || index >= totalSize) return null;
        for (CachedPage page : new ArrayList<>(pages.values())) {
            if (index >= page.offset && index < page.offset + page.entries.size()) {
                pages.get(page.offset);
                return page.entries.get(index - page.offset);
            }
        }
        return null;
    }

    public boolean isPageLoaded(int offset) {
        return pages.containsKey(offset);
    }

    public boolean applyPreview(@Nonnull UUID responseNonce, @Nonnull UUID requestId,
          @Nullable String actionStatus, @Nullable QIOSmartProcessingPreviewSnapshot response) {
        if (!responseNonce.equals(sessionNonce) || !requestId.equals(expectedPreviewRequestId)) {
            return false;
        }
        preview = response;
        lastRequestId = requestId;
        lastActionStatus = actionStatus;
        expectedPreviewRequestId = null;
        previewGeneration = next(previewGeneration);
        return true;
    }

    public boolean expectPreviewRequest(@Nonnull UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        if (expectedPreviewRequestId != null) return false;
        expectedPreviewRequestId = requestId;
        return true;
    }

    public void cancelExpectedPreviewRequest(@Nullable UUID requestId) {
        if (requestId != null && requestId.equals(expectedPreviewRequestId)) {
            expectedPreviewRequestId = null;
        }
    }

    public void clearPreviewProjection() {
        if (preview != null || lastActionStatus != null) {
            preview = null;
            lastActionStatus = null;
            previewGeneration = next(previewGeneration);
        }
    }

    public void clear() {
        pages.clear();
        sessionNonce = null;
        sourceRevision = -1;
        totalSize = 0;
        filter = QIOSmartProcessingResourceFilter.ALL;
        query = "";
        preview = null;
        lastRequestId = null;
        lastActionStatus = null;
        expectedPageRequestId = null;
        expectedPreviewRequestId = null;
        recipeViewerTarget = null;
        pageGeneration = next(pageGeneration);
        previewGeneration = next(previewGeneration);
    }

    public boolean canSelectRecipeViewerTarget(@Nonnull UUID nonce,
          @Nonnull PortableResourceDescriptor target) {
        if (!nonce.equals(sessionNonce)) return false;
        for (QIOSmartProcessingResourceEntry entry : getResources()) {
            if (entry.isSchedulable() && entry.getResource().equals(target)) return true;
        }
        return false;
    }

    public boolean selectRecipeViewerTarget(@Nonnull UUID nonce,
          @Nonnull PortableResourceDescriptor target) {
        if (!canSelectRecipeViewerTarget(nonce, target)) return false;
        recipeViewerTarget = target;
        recipeViewerTargetGeneration = next(recipeViewerTargetGeneration);
        return true;
    }

    @Nonnull
    public List<QIOSmartProcessingResourceEntry> getResources() {
        List<CachedPage> orderedPages = new ArrayList<>(pages.values());
        orderedPages.sort(Comparator.comparingInt(value -> value.offset));
        List<QIOSmartProcessingResourceEntry> resources = new ArrayList<>();
        for (CachedPage page : orderedPages) resources.addAll(page.entries);
        return Collections.unmodifiableList(resources);
    }

    public long getSourceRevision() { return sourceRevision; }
    public int getTotalSize() { return totalSize; }
    @Nonnull public QIOSmartProcessingResourceFilter getFilter() { return filter; }
    @Nonnull public String getQuery() { return query; }
    public long getPageGeneration() { return pageGeneration; }
    public long getPreviewGeneration() { return previewGeneration; }
    @Nullable public QIOSmartProcessingPreviewSnapshot getPreview() { return preview; }
    @Nullable public UUID getLastRequestId() { return lastRequestId; }
    @Nullable public String getLastActionStatus() { return lastActionStatus; }
    @Nullable public UUID getExpectedPageRequestId() { return expectedPageRequestId; }
    @Nullable public UUID getExpectedPreviewRequestId() { return expectedPreviewRequestId; }
    public long getRecipeViewerTargetGeneration() { return recipeViewerTargetGeneration; }
    @Nullable public PortableResourceDescriptor getRecipeViewerTarget() { return recipeViewerTarget; }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 0 : value + 1;
    }

    private static final class CachedPage {
        private final int offset;
        private final List<QIOSmartProcessingResourceEntry> entries;

        private CachedPage(int offset, List<QIOSmartProcessingResourceEntry> entries) {
            this.offset = offset;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }
}
