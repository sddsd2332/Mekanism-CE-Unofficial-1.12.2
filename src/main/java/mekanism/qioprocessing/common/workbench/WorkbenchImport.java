package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeDiscovery;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Background preparation; does not touch world, registry, book mutations, or executable recipe validation.
 */
public final class WorkbenchImport {
    private WorkbenchImport() {
    }

    public static Preview prepare(WorkbenchSnapshot base, RecipeCatalog catalog, Iterable<RecipeItemKey> targets, RecipeDiscovery.Mode mode) {
        return prepare(base, catalog, targets, mode, base);
    }

    public static Preview prepare(WorkbenchSnapshot base, RecipeCatalog catalog,
                                  Iterable<RecipeItemKey> targets, RecipeDiscovery.Mode mode,
                                  RecipeDiscovery.Selection selection) {
        return prepare(base, catalog, targets, mode, selection, false);
    }

    public static Preview prepare(WorkbenchSnapshot base, RecipeCatalog catalog,
                                  Iterable<RecipeItemKey> targets, RecipeDiscovery.Mode mode,
                                  RecipeDiscovery.Selection selection, boolean skipCycles) {
        RecipeDiscovery.Result discovered = RecipeDiscovery.discover(catalog, targets, mode, selection, skipCycles);
        Map<String, WorkbenchRoute> additions = new LinkedHashMap<>();
        int updated = 0, unchanged = 0;
        for (RecipeDeclaration recipe : discovered.recipes()) {
            WorkbenchRoute old = base.routes().get(recipe.id());
            if (old != null && old.matches(recipe)) {
                unchanged++;
                continue;
            }
            // Changed declarations must be reviewed and get fresh choices, not stale candidate overrides.
            additions.put(recipe.id(), WorkbenchRoute.of(recipe));
            if (old != null) updated++;
        }
        return new Preview(base, catalog.generation(), discovered, additions, updated, unchanged);
    }

    public static final class Preview {
        private final WorkbenchSnapshot base;
        private final String generation;
        private final RecipeDiscovery.Result discovery;
        private final Map<String, WorkbenchRoute> additions;
        private final int updated, unchanged;

        private Preview(WorkbenchSnapshot base, String generation, RecipeDiscovery.Result discovery,
                        Map<String, WorkbenchRoute> additions, int updated, int unchanged) {
            this.base = base;
            this.generation = generation;
            this.discovery = discovery;
            this.additions = Collections.unmodifiableMap(additions);
            this.updated = updated;
            this.unchanged = unchanged;
        }

        public WorkbenchSnapshot base() {
            return base;
        }

        public Map<String, WorkbenchRoute> additions() {
            return additions;
        }

        public String generation() {
            return generation;
        }

        public RecipeDiscovery.Result discovery() {
            return discovery;
        }

        public int added() {
            return additions.size() - updated;
        }

        public int updated() {
            return updated;
        }

        public int unchanged() {
            return unchanged;
        }

        public Preview retainRoutes(Set<String> recipeIds) {
            Map<String, WorkbenchRoute> retained = new LinkedHashMap<>();
            for (Map.Entry<String, WorkbenchRoute> entry : additions.entrySet()) {
                if (recipeIds.contains(entry.getKey())) retained.put(entry.getKey(), entry.getValue());
            }
            int retainedUpdates = 0;
            for (String recipeId : retained.keySet()) if (base.routes().containsKey(recipeId)) retainedUpdates++;
            return new Preview(base, generation, discovery, retained, retainedUpdates, unchanged);
        }
    }
}
