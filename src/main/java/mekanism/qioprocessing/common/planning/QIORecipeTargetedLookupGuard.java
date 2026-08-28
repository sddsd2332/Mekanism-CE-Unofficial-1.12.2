package mekanism.qioprocessing.common.planning;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded lifecycle cache and per-tick budget for capability-neutral recipe fallbacks. */
final class QIORecipeTargetedLookupGuard {

    static final int DEFAULT_MAX_ENTRIES = 4_096;
    static final int DEFAULT_SEARCHES_PER_TICK = 1;
    static final long DEFAULT_SUCCESS_TTL_TICKS = 1_200;
    static final long DEFAULT_FAILURE_TTL_TICKS = 200;

    private final int maximumEntries;
    private final int searchesPerTick;
    private final long successTtlTicks;
    private final long failureTtlTicks;
    private final Map<Key, Entry> cache;
    private long lifecycleTick;
    private int searchesThisTick;

    QIORecipeTargetedLookupGuard() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_SEARCHES_PER_TICK,
              DEFAULT_SUCCESS_TTL_TICKS, DEFAULT_FAILURE_TTL_TICKS);
    }

    QIORecipeTargetedLookupGuard(int maximumEntries, int searchesPerTick,
          long successTtlTicks, long failureTtlTicks) {
        if (maximumEntries <= 0 || searchesPerTick <= 0 || successTtlTicks <= 0 ||
            failureTtlTicks <= 0) {
            throw new IllegalArgumentException("Targeted recipe lookup limits must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.searchesPerTick = searchesPerTick;
        this.successTtlTicks = successTtlTicks;
        this.failureTtlTicks = failureTtlTicks;
        cache = new LinkedHashMap<>(Math.min(maximumEntries, 256), 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(
                  Map.Entry<Key, QIORecipeTargetedLookupGuard.Entry> eldest) {
                return size() > QIORecipeTargetedLookupGuard.this.maximumEntries;
            }
        };
    }

    void beginTick() {
        if (lifecycleTick == Long.MAX_VALUE) {
            cache.clear();
            lifecycleTick = 0;
        } else {
            lifecycleTick++;
        }
        searchesThisTick = 0;
    }

    boolean tryAcquireFullSearch() {
        if (searchesThisTick >= searchesPerTick) return false;
        searchesThisTick++;
        return true;
    }

    Lookup lookup(int dimension, List<ItemStack> grid) {
        Key key = key(dimension, grid);
        if (key == null) return Lookup.UNCACHED;
        Entry entry = cache.get(key);
        if (entry == null) return Lookup.UNCACHED;
        if (lifecycleTick >= entry.expiresAtTick) {
            cache.remove(key);
            return Lookup.UNCACHED;
        }
        return new Lookup(true, entry.recipeId);
    }

    void remember(int dimension, List<ItemStack> grid, @Nullable ResourceLocation recipeId) {
        Key key = key(dimension, grid);
        if (key == null) return;
        long ttl = recipeId == null ? failureTtlTicks : successTtlTicks;
        long expiresAt = lifecycleTick > Long.MAX_VALUE - ttl ? Long.MAX_VALUE :
              lifecycleTick + ttl;
        cache.put(key, new Entry(recipeId, expiresAt));
    }

    void forget(int dimension, List<ItemStack> grid) {
        Key key = key(dimension, grid);
        if (key != null) cache.remove(key);
    }

    void clear() {
        cache.clear();
        lifecycleTick = 0;
        searchesThisTick = 0;
    }

    int size() {
        return cache.size();
    }

    @Nullable
    private static Key key(int dimension, List<ItemStack> grid) {
        try {
            return new Key(dimension, QIORecipeCatalogEnvironment.targetedGridFingerprint(grid));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static final class Lookup {

        private static final Lookup UNCACHED = new Lookup(false, null);
        private final boolean cached;
        @Nullable private final ResourceLocation recipeId;

        private Lookup(boolean cached, @Nullable ResourceLocation recipeId) {
            this.cached = cached;
            this.recipeId = recipeId;
        }

        boolean isCached() {
            return cached;
        }

        @Nullable
        ResourceLocation getRecipeId() {
            return recipeId;
        }
    }

    private static final class Key {

        private final int dimension;
        private final String gridFingerprint;

        private Key(int dimension, String gridFingerprint) {
            this.dimension = dimension;
            this.gridFingerprint = Objects.requireNonNull(gridFingerprint, "gridFingerprint");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && dimension == key.dimension &&
                  gridFingerprint.equals(key.gridFingerprint);
        }

        @Override
        public int hashCode() {
            return 31 * dimension + gridFingerprint.hashCode();
        }
    }

    private static final class Entry {

        @Nullable private final ResourceLocation recipeId;
        private final long expiresAtTick;

        private Entry(@Nullable ResourceLocation recipeId, long expiresAtTick) {
            this.recipeId = recipeId;
            this.expiresAtTick = expiresAtTick;
        }
    }
}
