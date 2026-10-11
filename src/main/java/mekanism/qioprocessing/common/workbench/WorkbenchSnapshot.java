package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeDiscovery;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.*;

/**
 * One configuration revision. Safe to retain for an entire asynchronous query/import operation.
 */
public final class WorkbenchSnapshot implements RecipeDiscovery.Selection {
    public static final int MAX_PATTERNS_PER_ROUTE = 128;
    public static final int MAX_PATTERNS_PER_BOOK = 2048;
    public static final long MAX_PATTERN_BYTES_PER_BOOK = 16L * 1024 * 1024;
    private final UUID id;
    private final long revision;
    private final Map<String, WorkbenchRoute> routes;
    private final Map<String, String> preferences;

    WorkbenchSnapshot(UUID id, long revision, Map<String, WorkbenchRoute> routes, Map<String, String> preferences) {
        this.id = java.util.Objects.requireNonNull(id);
        if (revision < 0) throw new IllegalArgumentException("Negative workbench revision");
        this.revision = revision;
        int patternCount = 0;
        long patternBytes = 0;
        for (WorkbenchRoute route : routes.values()) {
            patternCount = Math.addExact(patternCount, route.patternCount());
            if (route.patternCount() > MAX_PATTERNS_PER_ROUTE || patternCount > MAX_PATTERNS_PER_BOOK) {
                throw new IllegalArgumentException("Workbench pattern count quota exceeded");
            }
            for (VerifiedWorkbenchPattern pattern : route.patterns().values()) {
                patternBytes = Math.addExact(patternBytes, pattern.retainedBytes());
                if (patternBytes > MAX_PATTERN_BYTES_PER_BOOK)
                    throw new IllegalArgumentException("Workbench pattern memory quota exceeded");
            }
        }
        this.routes = Collections.unmodifiableMap(new LinkedHashMap<>(routes));
        this.preferences = Collections.unmodifiableMap(new LinkedHashMap<>(preferences));
    }

    public UUID id() {
        return id;
    }

    public long revision() {
        return revision;
    }

    public Map<String, WorkbenchRoute> routes() {
        return routes;
    }

    public Map<String, String> preferences() {
        return preferences;
    }

    /**
     * Route iteration order is user priority, so ordinary Map.equals is not sufficient.
     */
    boolean sameContents(Map<String, WorkbenchRoute> otherRoutes, Map<String, String> otherPreferences) {
        if (routes.size() != otherRoutes.size() || !preferences.equals(otherPreferences)) return false;
        Iterator<Map.Entry<String, WorkbenchRoute>> current = routes.entrySet().iterator();
        for (Map.Entry<String, WorkbenchRoute> entry : otherRoutes.entrySet()) {
            if (!current.next().equals(entry)) return false;
        }
        return true;
    }

    /**
     * Planner-facing immutable view. Stale declarations or catalog generations yield no executable patterns.
     */
    public List<VerifiedWorkbenchPattern> validPatterns(RecipeDeclaration recipe, String catalogGeneration) {
        WorkbenchRoute route = routes.get(recipe.id());
        return route == null ? Collections.emptyList() : route.validPatterns(recipe, catalogGeneration);
    }

    @Override
    public String preferred(RecipeItemKey output) {
        return preferences.get(output.familyKey());
    }

    @Override
    public boolean enabled(RecipeDeclaration recipe) {
        WorkbenchRoute route = routes.get(recipe.id());
        return route == null || route.enabled();
    }

    @Override
    public List<RecipeItemKey> candidates(RecipeDeclaration recipe, int slot) {
        WorkbenchRoute route = routes.get(recipe.id());
        return route == null ? recipe.inputs().get(slot).candidates() : route.candidates(recipe, slot);
    }
}
