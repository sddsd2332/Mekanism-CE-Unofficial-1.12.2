package mekanism.common.upgrade;

import mekanism.common.Upgrade;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Allows addons to declare upgrade support for Mekanism tiles without injecting into tile constructors.
 */
public final class ExternalUpgradeSupportRegistry {

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile Snapshot snapshot = new Snapshot(Collections.emptyList(), 0);
    // Weak keys do not retain unloaded tiles or superseded dependent snapshots.
    private static final Map<UpgradeSupportValidity, Boolean> validityTokens = new WeakHashMap<>();

    private ExternalUpgradeSupportRegistry() {
    }

    public static void register(ResourceLocation id, Predicate<? super TileEntityContainerBlock> predicate, Upgrade... upgrades) {
        Set<Upgrade> values = upgrades == null ? Collections.emptySet() :
              Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(upgrades.clone())));
        register(id, new Entry(Objects.requireNonNull(predicate, "Tile predicate cannot be null"), values, null));
    }

    public static void register(ResourceLocation id, Predicate<? super TileEntityContainerBlock> predicate,
          Supplier<? extends Collection<Upgrade>> upgrades) {
        register(id, new Entry(Objects.requireNonNull(predicate, "Tile predicate cannot be null"), null,
              Objects.requireNonNull(upgrades, "Upgrade supplier cannot be null")));
    }

    /**
     * Declares support determined only by the tile's Java type. The predicate must not depend on mutable
     * configuration, world or registry state. Change such a declaration by unregistering and registering it again.
     */
    public static void registerClassSupport(ResourceLocation id, Predicate<Class<?>> predicate, Upgrade... upgrades) {
        Set<Upgrade> values = upgrades == null ? Collections.emptySet() :
              Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(upgrades.clone())));
        register(id, new Entry(Objects.requireNonNull(predicate, "Type predicate cannot be null"), values));
    }

    private static synchronized void register(ResourceLocation id, Entry entry) {
        Objects.requireNonNull(id, "Support declaration id cannot be null");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate external upgrade support id: " + id);
        }
        ENTRIES.put(id, entry);
        snapshot = new Snapshot(new ArrayList<>(ENTRIES.values()), snapshot.version + 1);
        invalidateTrackedSupport();
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        if (id == null || ENTRIES.remove(id) == null) {
            return false;
        }
        snapshot = new Snapshot(new ArrayList<>(ENTRIES.values()), snapshot.version + 1);
        invalidateTrackedSupport();
        return true;
    }

    /** Attaches a one-shot dependent cache to exactly the declaration revision it observed. */
    public static synchronized void trackSupportValidity(long expectedVersion, UpgradeSupportValidity token) {
        Objects.requireNonNull(token, "Validity token cannot be null");
        if (snapshot.version != expectedVersion) token.invalidate();
        else if (token.getAsBoolean()) validityTokens.put(token, Boolean.TRUE);
    }

    private static void invalidateTrackedSupport() {
        validityTokens.keySet().forEach(UpgradeSupportValidity::invalidate);
        validityTokens.clear();
    }

    /** Changes when declarations are registered or removed; dynamic predicates and suppliers remain live. */
    public static long getVersion() {
        return snapshot.version;
    }

    /** Whether external support can be reused for a tile type until the declaration version changes. */
    public static boolean isSupportStable(Upgrade upgrade) {
        Snapshot current = snapshot;
        return current.stableUpgrades.contains(upgrade) ||
              !current.candidates.containsKey(upgrade) && current.dynamicEntries.isEmpty();
    }

    public static boolean supports(@Nullable TileEntityContainerBlock tile, @Nullable Upgrade upgrade) {
        if (tile == null || !Upgrade.isRegistered(upgrade)) {
            return false;
        }
        Snapshot current = snapshot;
        for (Entry entry : current.candidates.getOrDefault(upgrade, current.dynamicEntries)) {
            if (entry.matches(tile) && entry.contains(upgrade)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Upgrade> getSupported(@Nullable TileEntityContainerBlock tile) {
        Snapshot current = snapshot;
        if (tile == null || current.entries.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Upgrade> supported = new LinkedHashSet<>();
        for (Entry entry : current.entries) {
            if (entry.matches(tile)) {
                entry.addSupportedTo(supported);
            }
        }
        return supported.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(supported);
    }

    private static final class Snapshot {

        private final long version;
        private final List<Entry> entries;
        private final List<Entry> dynamicEntries;
        private final Map<Upgrade, List<Entry>> candidates;
        private final Set<Upgrade> stableUpgrades;

        private Snapshot(List<Entry> entries, long version) {
            this.version = version;
            this.entries = Collections.unmodifiableList(entries);
            List<Entry> dynamic = new ArrayList<>();
            Set<Upgrade> declared = new LinkedHashSet<>();
            for (Entry entry : entries) {
                if (entry.fixedUpgrades == null) dynamic.add(entry);
                else declared.addAll(entry.fixedUpgrades);
            }
            dynamicEntries = Collections.unmodifiableList(dynamic);
            Map<Upgrade, List<Entry>> indexed = new LinkedHashMap<>();
            Set<Upgrade> stable = new LinkedHashSet<>();
            for (Upgrade upgrade : declared) {
                if (upgrade == null) continue;
                List<Entry> matching = new ArrayList<>();
                boolean classBased = true;
                // Keep declaration order, including dynamic suppliers, without allocating during lookup.
                for (Entry entry : entries) {
                    if (entry.fixedUpgrades == null || entry.fixedUpgrades.contains(upgrade)) {
                        matching.add(entry);
                        classBased &= entry.classMatches != null;
                    }
                }
                indexed.put(upgrade, Collections.unmodifiableList(matching));
                if (classBased) stable.add(upgrade);
            }
            candidates = Collections.unmodifiableMap(indexed);
            stableUpgrades = Collections.unmodifiableSet(stable);
        }
    }

    private static final class Entry {

        @Nullable
        private final Predicate<? super TileEntityContainerBlock> predicate;
        @Nullable
        private final ClassValue<Boolean> classMatches;
        @Nullable
        private final Set<Upgrade> fixedUpgrades;
        @Nullable
        private final Supplier<? extends Collection<Upgrade>> upgrades;

        private Entry(Predicate<? super TileEntityContainerBlock> predicate, @Nullable Set<Upgrade> fixedUpgrades,
              @Nullable Supplier<? extends Collection<Upgrade>> upgrades) {
            this.predicate = predicate;
            this.fixedUpgrades = fixedUpgrades;
            this.upgrades = upgrades;
            this.classMatches = null;
        }

        private Entry(Predicate<Class<?>> predicate, Set<Upgrade> upgrades) {
            this.predicate = null;
            this.fixedUpgrades = upgrades;
            this.upgrades = null;
            this.classMatches = new ClassValue<Boolean>() {
                @Override protected Boolean computeValue(Class<?> type) { return predicate.test(type); }
            };
        }

        private boolean matches(TileEntityContainerBlock tile) {
            return classMatches == null ? predicate.test(tile) : classMatches.get(tile.getClass());
        }

        private boolean contains(Upgrade upgrade) {
            Collection<Upgrade> values = fixedUpgrades == null ? upgrades.get() : fixedUpgrades;
            return values != null && values.contains(upgrade);
        }

        private void addSupportedTo(Set<Upgrade> supported) {
            Collection<Upgrade> values = fixedUpgrades == null ? upgrades.get() : fixedUpgrades;
            if (values == null) return;
            for (Upgrade upgrade : values) {
                if (Upgrade.isRegistered(upgrade)) {
                    supported.add(upgrade);
                }
            }
        }
    }
}
