package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.order.QIOOrderPreview;
import mekanism.qioprocessing.common.order.QIOOrderService;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread smart-processing catalog and bounded preview projection. */
/**
 * QIO 处理模块中的 QIOSmartProcessingService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOSmartProcessingService {

    private static final int MAX_CACHED_CATALOGS = 8;
    private static final int MAX_CACHED_VIEWS = 8;
    private static final Map<CatalogKey, CatalogSnapshot> CATALOG_CACHE =
          new LinkedHashMap<>(16, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<CatalogKey, CatalogSnapshot> eldest) {
                  return size() > MAX_CACHED_CATALOGS;
              }
          };

    private QIOSmartProcessingService() {
    }

    @Nonnull
    /** 查询智能处理终端的资源分页。 */
    public static QIOPage<QIOSmartProcessingResourceEntry> getResourcePage(
          @Nonnull QIOProcessingTerminalSession session, @Nonnull QIOProcessingNetworkData network,
          @Nonnull QIOStorageSnapshot storage, @Nonnull World world, @Nonnull UUID requester,
          @Nonnull QIOSmartProcessingResourceFilter filter, @Nullable String query, int offset,
          int requestedPageSize, long expectedSourceRevision) {
        validate(session, network, storage);
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(filter, "filter");
        if (offset < 0 || requestedPageSize <= 0 || requestedPageSize > 256 ||
              expectedSourceRevision < -1) {
            throw new IllegalArgumentException("Invalid smart-processing page bounds");
        }
        QIOOrderService.SchedulableResources schedulable = QIOOrderService.INSTANCE
              .getSchedulableResources(world, network);
        long sourceRevision = catalogRevision(schedulable, network);
        CatalogKey key = new CatalogKey(network.getFrequencyUUID(), requester, sourceRevision);
        CatalogSnapshot catalog;
        synchronized (CATALOG_CACHE) {
            catalog = CATALOG_CACHE.get(key);
            if (catalog == null) {
                CATALOG_CACHE.keySet().removeIf(previous -> previous.sameScope(key));
                catalog = buildCatalog(network, requester, schedulable);
                CATALOG_CACHE.put(key, catalog);
            }
        }
        List<QIOSmartProcessingResourceEntry> rows = catalog.view(filter, normalizeQuery(query));
        if (expectedSourceRevision >= 0 && expectedSourceRevision != sourceRevision ||
              offset > rows.size()) {
            offset = 0;
        }
        int end = Math.min(rows.size(), offset + requestedPageSize);
        return new QIOPage<>(sourceRevision, offset, rows.size(), rows.subList(offset, end), null);
    }

    @Nonnull
    private static CatalogSnapshot buildCatalog(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID requester, @Nonnull QIOOrderService.SchedulableResources schedulable) {
        Map<PortableResourceDescriptor, MutableEntry> entries = new LinkedHashMap<>();
        for (PortableResourceDescriptor resource : schedulable.getVisibleResources()) {
            entries.computeIfAbsent(resource, ignored -> new MutableEntry()).visible = true;
        }
        for (PortableResourceDescriptor resource : schedulable.getResources()) {
            MutableEntry entry = entries.computeIfAbsent(resource, ignored -> new MutableEntry());
            entry.visible = true;
            entry.schedulable = true;
        }
        for (QIOCraftingJob job : network.getJobs()) {
            if (job.getState().isTerminal()) continue;
            PortableResourceDescriptor root = job.getActivePlan().getRootResource();
            MutableEntry entry = entries.computeIfAbsent(root, ignored -> new MutableEntry());
            long remaining = job.getRemainingGuaranteedRootAmount();
            entry.inProduction = saturatedAdd(entry.inProduction, remaining);
            if (remaining > 0 && job.getSource() == QIOCraftingJobSource.MANUAL &&
                  requester.equals(job.getRequester())) {
                entry.mergeableInProduction = saturatedAdd(entry.mergeableInProduction, remaining);
                entry.mergeGroupId = mergeGroupId(network.getFrequencyUUID(), requester, root);
            }
        }
        List<QIOSmartProcessingResourceEntry> rows = new ArrayList<>();
        for (Map.Entry<PortableResourceDescriptor, MutableEntry> value : entries.entrySet()) {
            MutableEntry entry = value.getValue();
            if (entry.visible || entry.inProduction > 0) {
                rows.add(new QIOSmartProcessingResourceEntry(value.getKey(), 0, 0, 0,
                      entry.inProduction, entry.schedulable, false,
                      entry.mergeableInProduction, entry.mergeGroupId));
            }
        }
        rows.sort(Comparator
              .comparingInt((QIOSmartProcessingResourceEntry entry) ->
                    entry.getResource().getKind().ordinal())
              .thenComparing(entry -> entry.getResource().getRegistryName())
              .thenComparing(QIOSmartProcessingResourceEntry::getResource));
        return new CatalogSnapshot(rows);
    }

    @Nonnull
    /** 将订单预览转换为终端可显示的快照。 */
    public static QIOSmartProcessingPreviewSnapshot previewSnapshot(@Nonnull QIOOrderPreview preview) {
        return QIOSmartProcessingPreviewSnapshot.fromPreview(preview, null);
    }

    @Nonnull
    /** 将订单预览转换为带版本和筛选信息的快照。 */
    public static QIOSmartProcessingPreviewSnapshot previewSnapshot(@Nonnull QIOOrderPreview preview,
          @Nullable UUID jobId) {
        return QIOSmartProcessingPreviewSnapshot.fromPreview(preview, jobId);
    }

    /** 校验智能处理请求会话和频率访问权限。 */
    public static void validate(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, @Nonnull QIOStorageSnapshot storage) {
        Objects.requireNonNull(session, "session"); Objects.requireNonNull(network, "network");
        Objects.requireNonNull(storage, "storage");
        if (!session.isOpen() || session.getTerminalType() != QIOProcessingTerminalType.SMART_PROCESSING ||
            session.getFrequencyUUID() == null || !session.getFrequencyUUID().equals(network.getFrequencyUUID()) ||
            session.getAccessRevision() != storage.getAccessRevision()) {
            throw new SecurityException("Smart-processing session is stale or unauthorized");
        }
    }

    private static long catalogRevision(QIOOrderService.SchedulableResources schedulable,
          QIOProcessingNetworkData network) {
        long value = 17;
        value = mix(value, schedulable.getRevision());
        value = mix(value, schedulable.getResources().size());
        value = mix(value, schedulable.getVisibleResources().size());
        for (QIOCraftingJob job : network.getJobs()) {
            if (job.getState().isTerminal()) continue;
            value = mix(value, job.getJobId().getMostSignificantBits());
            value = mix(value, job.getJobId().getLeastSignificantBits());
            value = mix(value, job.getSource().ordinal());
            value = mix(value, job.getRequester() == null ? 0 : job.getRequester().hashCode());
            value = mix(value, job.getActivePlan().getRootResource().hashCode());
            // A job can keep the same root and route set while its delivered amount, state, or
            // active operation changes.  Include the runtime revision so a cached terminal page
            // is invalidated in the same tick that production progress is committed.
            value = mix(value, job.getRuntimeRevision());
            value = mix(value, job.getRemainingGuaranteedRootAmount());
        }
        return value & Long.MAX_VALUE;
    }

    @Nonnull
    private static UUID mergeGroupId(@Nonnull UUID frequency, @Nonnull UUID requester,
          @Nonnull PortableResourceDescriptor resource) {
        String identity = frequency + "|" + requester + "|" + resource;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    @Nonnull
    private static String normalizeQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static boolean matchesQuery(QIOSmartProcessingResourceEntry entry, String query) {
        if (query.isEmpty()) return true;
        PortableResourceDescriptor resource = entry.getResource();
        StringBuilder searchable = new StringBuilder(resource.getRegistryName().toLowerCase(Locale.ROOT));
        searchable.append(' ').append(resource.getKind().name().toLowerCase(Locale.ROOT));
        try {
            switch (resource.getKind()) {
                case ITEM -> searchable.append(' ').append(resource.resolveItem().getDisplayName());
                case FLUID -> {
                    if (resource.resolveFluid() != null) {
                        searchable.append(' ').append(resource.resolveFluid().getLocalizedName());
                    }
                }
                case GAS -> {
                    if (resource.resolveGas() != null && resource.resolveGas().getGas() != null) {
                        searchable.append(' ').append(resource.resolveGas().getGas().getLocalizedName());
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        String haystack = searchable.toString().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (!token.isEmpty() && !haystack.contains(token)) return false;
        }
        return true;
    }

    private static long mix(long value, long next) {
        return (value ^ next) * 0x9E3779B97F4A7C15L + 0xBF58476D1CE4E5B9L;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static final class MutableEntry {
        private long inProduction, mergeableInProduction;
        private boolean visible, schedulable;
        private UUID mergeGroupId;
    }

    private static final class CatalogKey {
        private final UUID frequency, requester;
        private final long revision;

        private CatalogKey(UUID frequency, UUID requester, long revision) {
            this.frequency = frequency;
            this.requester = requester;
            this.revision = revision;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CatalogKey other && revision == other.revision &&
                  frequency.equals(other.frequency) && requester.equals(other.requester);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequency, requester, revision);
        }

        private boolean sameScope(CatalogKey other) {
            return frequency.equals(other.frequency) && requester.equals(other.requester);
        }
    }

    private static final class CatalogSnapshot {
        private final List<QIOSmartProcessingResourceEntry> rows;
        private final Map<ViewKey, List<QIOSmartProcessingResourceEntry>> views =
              new LinkedHashMap<>(8, 0.75F, true) {
                  @Override
                  protected boolean removeEldestEntry(
                        Map.Entry<ViewKey, List<QIOSmartProcessingResourceEntry>> eldest) {
                      return size() > MAX_CACHED_VIEWS;
                  }
              };

        private CatalogSnapshot(List<QIOSmartProcessingResourceEntry> rows) {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        private synchronized List<QIOSmartProcessingResourceEntry> view(
              QIOSmartProcessingResourceFilter filter, String query) {
            ViewKey key = new ViewKey(filter, query);
            List<QIOSmartProcessingResourceEntry> cached = views.get(key);
            if (cached != null) return cached;
            List<QIOSmartProcessingResourceEntry> filtered = new ArrayList<>();
            for (QIOSmartProcessingResourceEntry row : rows) {
                if (filter.matches(row.getResource()) && matchesQuery(row, query)) {
                    filtered.add(row);
                }
            }
            cached = Collections.unmodifiableList(filtered);
            views.put(key, cached);
            return cached;
        }
    }

    private static final class ViewKey {
        private final QIOSmartProcessingResourceFilter filter;
        private final String query;

        private ViewKey(QIOSmartProcessingResourceFilter filter, String query) {
            this.filter = filter;
            this.query = query;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ViewKey other && filter == other.filter && query.equals(other.query);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filter, query);
        }
    }
}
