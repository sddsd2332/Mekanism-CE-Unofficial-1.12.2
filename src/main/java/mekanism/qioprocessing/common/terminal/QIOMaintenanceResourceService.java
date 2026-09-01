package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.order.QIOOrderService;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, session-independent directory used by the maintenance rule editor.
 *
 * <p>The directory deliberately contains configured resources even when their recipe route is
 * temporarily unavailable. This keeps a rule editable instead of making it disappear during a
 * reload or while a machine is unloaded.
 */
/**
 * QIO 处理模块中的 QIOMaintenanceResourceService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOMaintenanceResourceService {

    private static final int MAX_CACHED_CATALOGS = 8;
    private static final int MAX_CACHED_VIEWS = 8;
    private static final Map<CatalogKey, CatalogSnapshot> CATALOG_CACHE =
          new LinkedHashMap<CatalogKey, CatalogSnapshot>(16, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<CatalogKey, CatalogSnapshot> eldest) {
                  return size() > MAX_CACHED_CATALOGS;
              }
          };

    private QIOMaintenanceResourceService() {
    }

    @Nonnull
    public static QIOPage<QIOSmartProcessingResourceEntry> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, @Nonnull World world,
          @Nonnull QIOSmartProcessingResourceFilter filter, @Nullable String query,
          int offset, int requestedPageSize, long expectedSourceRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(filter, "filter");
        if (!session.isOpen() || session.getTerminalType() != QIOProcessingTerminalType.MAINTENANCE ||
              session.getFrequencyUUID() == null ||
              !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Invalid maintenance resource session");
        }
        if (offset < 0 || requestedPageSize <= 0 || requestedPageSize > 256 ||
              expectedSourceRevision < -1) {
            throw new IllegalArgumentException("Invalid maintenance resource page bounds");
        }
        String normalizedQuery = normalizeQuery(query);
        QIOOrderService.SchedulableResources schedulable = QIOOrderService.INSTANCE
              .getSchedulableResources(world, network);
        long sourceRevision = revision(schedulable.getRevision(),
              network.getMaintenanceRules().getRulesRevision());
        CatalogKey key = new CatalogKey(network, sourceRevision);
        CatalogSnapshot catalog;
        synchronized (CATALOG_CACHE) {
            catalog = CATALOG_CACHE.get(key);
            if (catalog == null) {
                CATALOG_CACHE.keySet().removeIf(previous -> previous.sameScope(key));
                catalog = buildCatalog(network, schedulable);
                CATALOG_CACHE.put(key, catalog);
            }
        }
        List<QIOSmartProcessingResourceEntry> rows = catalog.view(filter, normalizedQuery);
        if (expectedSourceRevision >= 0 && expectedSourceRevision != sourceRevision ||
              offset > rows.size()) {
            offset = 0;
        }
        int end = Math.min(rows.size(), offset + requestedPageSize);
        return new QIOPage<>(sourceRevision, offset, rows.size(),
              rows.subList(offset, end), null);
    }

    private static CatalogSnapshot buildCatalog(QIOProcessingNetworkData network,
          QIOOrderService.SchedulableResources schedulable) {
        Map<PortableResourceDescriptor, QIOSmartProcessingResourceEntry> entries =
              new LinkedHashMap<>();
        for (PortableResourceDescriptor resource : schedulable.getResources()) {
            entries.put(resource, new QIOSmartProcessingResourceEntry(resource, 0, 0, 0,
                  0, true, false));
        }
        for (QIOMaintenanceRule rule : network.getMaintenanceRules().getRules()) {
            PortableResourceDescriptor resource = rule.getResource();
            QIOSmartProcessingResourceEntry existing = entries.get(resource);
            if (existing == null) {
                entries.put(resource, new QIOSmartProcessingResourceEntry(resource, 0, 0, 0,
                      0, false, true));
            }
        }
        List<QIOSmartProcessingResourceEntry> rows = new ArrayList<>(entries.values());
        rows.sort(Comparator.comparingInt((QIOSmartProcessingResourceEntry entry) ->
                    entry.getResource().getKind().ordinal())
              .thenComparing(entry -> entry.getResource().getRegistryName())
              .thenComparing(QIOSmartProcessingResourceEntry::getResource));
        return new CatalogSnapshot(rows);
    }

    private static long revision(long schedulableRevision, long rulesRevision) {
        long value = 17;
        value = (value ^ schedulableRevision) * 0x9E3779B97F4A7C15L + 0xBF58476D1CE4E5B9L;
        value = (value ^ rulesRevision) * 0x9E3779B97F4A7C15L + 0xBF58476D1CE4E5B9L;
        return value & Long.MAX_VALUE;
    }

    private static String normalizeQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static boolean matchesQuery(QIOSmartProcessingResourceEntry entry, String query) {
        if (query.isEmpty()) {
            return true;
        }
        PortableResourceDescriptor resource = entry.getResource();
        StringBuilder searchable = new StringBuilder(resource.getRegistryName().toLowerCase(Locale.ROOT))
              .append(' ').append(resource.getKind().name().toLowerCase(Locale.ROOT));
        try {
            switch (resource.getKind()) {
                case ITEM -> {
                    if (!resource.resolveItem().isEmpty()) {
                        searchable.append(' ').append(resource.resolveItem().getDisplayName());
                    }
                }
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
                case CUSTOM -> searchable.append(' ').append(resource.getFamily())
                      .append(' ').append(resource.getCodecId());
            }
        } catch (RuntimeException ignored) {
        }
        String haystack = searchable.toString().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (!token.isEmpty() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static final class CatalogKey {
        private final java.util.UUID frequencyUUID;
        private final QIOProcessingNetworkData network;
        private final long revision;

        private CatalogKey(QIOProcessingNetworkData network, long revision) {
            this.network = network;
            this.frequencyUUID = network.getFrequencyUUID();
            this.revision = revision;
        }

        private boolean sameScope(CatalogKey other) {
            return frequencyUUID.equals(other.frequencyUUID);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CatalogKey other && network == other.network &&
                  revision == other.revision &&
                  frequencyUUID.equals(other.frequencyUUID);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(network) + Objects.hash(frequencyUUID, revision);
        }
    }

    private static final class CatalogSnapshot {
        private final List<QIOSmartProcessingResourceEntry> rows;
        private final Map<ViewKey, List<QIOSmartProcessingResourceEntry>> views =
              new LinkedHashMap<ViewKey, List<QIOSmartProcessingResourceEntry>>(8, 0.75F, true) {
                  @Override
                  protected boolean removeEldestEntry(
                        Map.Entry<ViewKey, List<QIOSmartProcessingResourceEntry>> eldest) {
                      return size() > MAX_CACHED_VIEWS;
                  }
              };

        private CatalogSnapshot(List<QIOSmartProcessingResourceEntry> rows) {
            this.rows = java.util.Collections.unmodifiableList(new ArrayList<>(rows));
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
            cached = java.util.Collections.unmodifiableList(filtered);
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
