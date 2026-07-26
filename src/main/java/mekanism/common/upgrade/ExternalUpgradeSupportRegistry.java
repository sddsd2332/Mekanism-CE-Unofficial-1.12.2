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
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Allows addons to declare upgrade support for Mekanism tiles without injecting into tile constructors.
 */
public final class ExternalUpgradeSupportRegistry {

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static volatile List<Entry> snapshot = Collections.emptyList();

    private ExternalUpgradeSupportRegistry() {
    }

    public static void register(ResourceLocation id, Predicate<? super TileEntityContainerBlock> predicate, Upgrade... upgrades) {
        List<Upgrade> values = upgrades == null ? Collections.emptyList() : Arrays.asList(upgrades.clone());
        register(id, predicate, () -> values);
    }

    public static synchronized void register(ResourceLocation id, Predicate<? super TileEntityContainerBlock> predicate,
          Supplier<? extends Collection<Upgrade>> upgrades) {
        Objects.requireNonNull(id, "Support declaration id cannot be null");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate external upgrade support id: " + id);
        }
        ENTRIES.put(id, new Entry(Objects.requireNonNull(predicate, "Tile predicate cannot be null"),
              Objects.requireNonNull(upgrades, "Upgrade supplier cannot be null")));
        snapshot = Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        if (id == null || ENTRIES.remove(id) == null) {
            return false;
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
        return true;
    }

    public static boolean supports(@Nullable TileEntityContainerBlock tile, @Nullable Upgrade upgrade) {
        if (tile == null || upgrade == null) {
            return false;
        }
        for (Entry entry : snapshot) {
            if (entry.matches(tile) && entry.getUpgrades().contains(upgrade)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Upgrade> getSupported(@Nullable TileEntityContainerBlock tile) {
        if (tile == null || snapshot.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Upgrade> supported = new LinkedHashSet<>();
        for (Entry entry : snapshot) {
            if (entry.matches(tile)) {
                supported.addAll(entry.getUpgrades());
            }
        }
        return supported.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(supported);
    }

    private static final class Entry {

        private final Predicate<? super TileEntityContainerBlock> predicate;
        private final Supplier<? extends Collection<Upgrade>> upgrades;

        private Entry(Predicate<? super TileEntityContainerBlock> predicate, Supplier<? extends Collection<Upgrade>> upgrades) {
            this.predicate = predicate;
            this.upgrades = upgrades;
        }

        private boolean matches(TileEntityContainerBlock tile) {
            return predicate.test(tile);
        }

        private Set<Upgrade> getUpgrades() {
            Collection<Upgrade> values = upgrades.get();
            if (values == null || values.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Upgrade> valid = new LinkedHashSet<>();
            for (Upgrade upgrade : values) {
                if (Upgrade.isRegistered(upgrade)) {
                    valid.add(upgrade);
                }
            }
            return valid;
        }
    }
}
