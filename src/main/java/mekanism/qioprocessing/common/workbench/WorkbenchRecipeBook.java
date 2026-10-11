package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.*;

/**
 * Owner-thread mutations with atomic immutable revision publication. Network authorization belongs to its caller.
 */
public final class WorkbenchRecipeBook {
    public enum Rejection {
        WRONG_BOOK, STALE_REVISION, STALE_CATALOG, UNKNOWN_RECIPE, NOT_IMPORTED, INVALID_CHOICE,
        INVALID_PATTERN, PATTERN_NOT_FOUND, PATTERN_QUOTA_EXCEEDED
    }

    public static final class Rejected extends IllegalArgumentException {
        public final Rejection reason;

        Rejected(Rejection reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    private final Thread owner = Thread.currentThread();
    private volatile WorkbenchSnapshot snapshot;

    public WorkbenchRecipeBook(UUID id) {
        this(new WorkbenchSnapshot(id, 0, Collections.emptyMap(), Collections.emptyMap()));
    }

    public WorkbenchRecipeBook(WorkbenchSnapshot restored) {
        snapshot = java.util.Objects.requireNonNull(restored);
    }

    public WorkbenchSnapshot snapshot() {
        return snapshot;
    }

    public WorkbenchSnapshot commit(RecipeCatalog catalog, WorkbenchImport.Preview preview) {
        check(preview.base().revision());
        if (!snapshot.id().equals(preview.base().id())) throw new Rejected(Rejection.WRONG_BOOK);
        if (!catalog.generation().equals(preview.generation())) throw new Rejected(Rejection.STALE_CATALOG);
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>(snapshot.routes());
        routes.putAll(preview.additions());
        return publish(routes, snapshot.preferences());
    }

    public WorkbenchSnapshot setEnabled(long revision, String recipeId, boolean enabled) {
        check(revision);
        WorkbenchRoute old = imported(recipeId);
        return replace(new WorkbenchRoute(recipeId, old.signature(), enabled, old.choices(), old.patterns()));
    }

    public WorkbenchSnapshot setPreferred(long revision, RecipeCatalog catalog, String recipeId) {
        check(revision);
        RecipeDeclaration recipe = declaration(catalog, recipeId);
        Map<String, String> preferences = new LinkedHashMap<>(snapshot.preferences());
        preferences.put(recipe.output().familyKey(), recipeId);
        return publish(snapshot.routes(), preferences);
    }

    /**
     * The list contains enabled candidate fingerprints, in priority order. Unlisted candidates are disabled.
     */
    public WorkbenchSnapshot setChoices(long revision, RecipeCatalog catalog, String recipeId, int slot, List<String> enabled) {
        check(revision);
        WorkbenchRoute old = imported(recipeId);
        RecipeDeclaration recipe = declaration(catalog, recipeId);
        if (!old.matches(recipe)) throw new Rejected(Rejection.STALE_CATALOG);
        if (slot < 0 || slot >= recipe.inputs().size() || recipe.inputs().get(slot).isEmptySlot() || enabled.isEmpty()) {
            throw new Rejected(Rejection.INVALID_CHOICE);
        }
        List<String> defaults = new ArrayList<>();
        for (RecipeItemKey key : recipe.inputs().get(slot).candidates()) defaults.add(key.fingerprint());
        Set<String> available = new HashSet<>(defaults), selected = new HashSet<>();
        for (String key : enabled)
            if (!available.contains(key) || !selected.add(key)) throw new Rejected(Rejection.INVALID_CHOICE);
        Map<Integer, List<String>> choices = new LinkedHashMap<>(old.choices());
        if (defaults.equals(enabled)) choices.remove(slot);
        else choices.put(slot, enabled);
        return replace(new WorkbenchRoute(recipeId, old.signature(), old.enabled(), choices, old.patterns()));
    }

    /**
     * Reset changes preferences, not the set of imported routes. It also works for an empty book.
     */
    public WorkbenchSnapshot resetAll(long revision) {
        check(revision);
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>();
        for (WorkbenchRoute route : snapshot.routes().values()) {
            routes.put(route.recipeId(), new WorkbenchRoute(route.recipeId(), route.signature(), true, Collections.emptyMap()));
        }
        return publish(routes, Collections.emptyMap());
    }

    /**
     * The old GUI's confirmed "reset all" means removing all imported routes as well as choices.
     */
    public WorkbenchSnapshot clearAll(long revision) {
        check(revision);
        return publish(Collections.emptyMap(), Collections.emptyMap());
    }

    public WorkbenchSnapshot moveRecipe(long revision, List<String> siblings, String recipeId,
                                        int direction, boolean edge) {
        return moveRecipes(revision, siblings, Collections.singletonList(recipeId), direction, edge);
    }

    public WorkbenchSnapshot moveRecipes(long revision, List<String> siblings,
                                         List<String> recipeIds, int direction, boolean edge) {
        check(revision);
        if (direction == 0 || recipeIds == null || recipeIds.isEmpty()) return snapshot;
        List<String> order = checkedSiblings(siblings);
        Set<String> selected = new HashSet<>();
        for (String recipeId : recipeIds) {
            if (!selected.add(recipeId) || !order.contains(recipeId)) {
                throw new Rejected(Rejection.UNKNOWN_RECIPE);
            }
        }
        if (edge) {
            List<String> selectedInOrder = new ArrayList<>();
            for (String recipeId : order) if (selected.contains(recipeId)) selectedInOrder.add(recipeId);
            order.removeIf(selected::contains);
            order.addAll(direction < 0 ? 0 : order.size(), selectedInOrder);
        } else if (direction < 0) {
            for (int index = 1; index < order.size(); index++) {
                if (selected.contains(order.get(index)) && !selected.contains(order.get(index - 1))) {
                    Collections.swap(order, index, index - 1);
                }
            }
        } else {
            for (int index = order.size() - 2; index >= 0; index--) {
                if (selected.contains(order.get(index)) && !selected.contains(order.get(index + 1))) {
                    Collections.swap(order, index, index + 1);
                }
            }
        }
        return reorder(siblings, order);
    }

    private List<String> checkedSiblings(List<String> siblings) {
        if (siblings == null || siblings.isEmpty()) throw new Rejected(Rejection.UNKNOWN_RECIPE);
        List<String> checked = new ArrayList<>(siblings.size());
        Set<String> seen = new HashSet<>();
        for (String recipeId : siblings) {
            if (recipeId == null || !seen.add(recipeId) || !snapshot.routes().containsKey(recipeId)) {
                throw new Rejected(Rejection.UNKNOWN_RECIPE);
            }
            checked.add(recipeId);
        }
        return checked;
    }

    private WorkbenchSnapshot reorder(List<String> siblings, List<String> desired) {
        if (siblings.equals(desired)) return snapshot;
        Set<String> siblingSet = new HashSet<>(siblings);
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, WorkbenchRoute> entry : snapshot.routes().entrySet()) {
            if (siblingSet.contains(entry.getKey())) {
                String recipeId = desired.get(index++);
                routes.put(recipeId, snapshot.routes().get(recipeId));
            } else {
                routes.put(entry.getKey(), entry.getValue());
            }
        }
        return publish(routes, snapshot.preferences());
    }

    public WorkbenchSnapshot syncChoices(long revision, RecipeCatalog catalog, String recipeId, int sourceSlot) {
        check(revision);
        WorkbenchRoute route = imported(recipeId);
        RecipeDeclaration recipe = declaration(catalog, recipeId);
        if (!route.matches(recipe)) throw new Rejected(Rejection.STALE_CATALOG);
        if (sourceSlot < 0 || sourceSlot >= recipe.inputs().size() || recipe.inputs().get(sourceSlot).isEmptySlot())
            throw new Rejected(Rejection.INVALID_CHOICE);
        Map<Integer, List<String>> choices = new LinkedHashMap<>(route.choices());
        List<String> source = route.choices().get(sourceSlot);
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            if (recipe.inputs().get(slot).equals(recipe.inputs().get(sourceSlot))) {
                if (source == null) choices.remove(slot);
                else choices.put(slot, source);
            }
        }
        return replace(new WorkbenchRoute(recipeId, route.signature(), route.enabled(), choices, route.patterns()));
    }

    public WorkbenchSnapshot resetRoute(long revision, String recipeId) {
        check(revision);
        WorkbenchRoute old = imported(recipeId);
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>(snapshot.routes());
        routes.put(recipeId, new WorkbenchRoute(recipeId, old.signature(), true, Collections.emptyMap()));
        Map<String, String> preferences = new LinkedHashMap<>(snapshot.preferences());
        preferences.values().removeIf(recipeId::equals);
        return publish(routes, preferences);
    }

    /**
     * Adds a server-verified concrete 3x3 pattern to an imported route.
     */
    public WorkbenchSnapshot addPattern(long revision, RecipeCatalog catalog, VerifiedWorkbenchPattern pattern) {
        check(revision);
        if (pattern == null) throw new Rejected(Rejection.INVALID_PATTERN);
        WorkbenchRoute route = imported(pattern.recipeId());
        RecipeDeclaration recipe = declaration(catalog, pattern.recipeId());
        if (!route.matches(recipe) || !pattern.declarationSignature().equals(recipe.signature())
                || !pattern.catalogGeneration().equals(catalog.generation())
                || !pattern.output().equals(recipe.output()) || pattern.outputCount() != recipe.outputCount()) {
            throw new Rejected(Rejection.STALE_CATALOG);
        }
        Map<UUID, VerifiedWorkbenchPattern> patterns = new LinkedHashMap<>(route.patterns());
        VerifiedWorkbenchPattern previous = patterns.get(pattern.id());
        if (previous != null && !previous.equals(pattern)) throw new Rejected(Rejection.INVALID_PATTERN);
        if (previous != null) return snapshot;
        if (route.patternCount() >= WorkbenchSnapshot.MAX_PATTERNS_PER_ROUTE)
            throw new Rejected(Rejection.PATTERN_QUOTA_EXCEEDED);
        patterns.put(pattern.id(), pattern);
        try {
            return replace(new WorkbenchRoute(route.recipeId(), route.signature(), route.enabled(), route.choices(), patterns));
        } catch (IllegalArgumentException | ArithmeticException error) {
            throw new Rejected(Rejection.PATTERN_QUOTA_EXCEEDED);
        }
    }

    public WorkbenchSnapshot removePattern(long revision, String recipeId, UUID patternId) {
        check(revision);
        if (patternId == null) throw new Rejected(Rejection.PATTERN_NOT_FOUND);
        WorkbenchRoute route = imported(recipeId);
        if (!route.patterns().containsKey(patternId)) throw new Rejected(Rejection.PATTERN_NOT_FOUND);
        Map<UUID, VerifiedWorkbenchPattern> patterns = new LinkedHashMap<>(route.patterns());
        patterns.remove(patternId);
        return replace(new WorkbenchRoute(route.recipeId(), route.signature(), route.enabled(), route.choices(), patterns));
    }

    /**
     * Returns only patterns valid for the current declaration and catalog generation.
     */
    public List<VerifiedWorkbenchPattern> validPatterns(RecipeCatalog catalog, String recipeId) {
        WorkbenchRoute route = snapshot.routes().get(recipeId);
        RecipeDeclaration recipe = catalog.byId(recipeId);
        if (route == null || recipe == null || !route.matches(recipe)) return Collections.emptyList();
        List<VerifiedWorkbenchPattern> result = new ArrayList<>();
        for (VerifiedWorkbenchPattern pattern : route.patterns().values()) {
            if (pattern.declarationSignature().equals(recipe.signature()) && pattern.catalogGeneration().equals(catalog.generation()))
                result.add(pattern);
        }
        return Collections.unmodifiableList(result);
    }

    public WorkbenchSnapshot remove(long revision, Iterable<String> recipeIds) {
        check(revision);
        Set<String> removed = new HashSet<>();
        recipeIds.forEach(removed::add);
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>(snapshot.routes());
        removed.forEach(routes::remove);
        Map<String, String> preferences = new LinkedHashMap<>(snapshot.preferences());
        preferences.values().removeIf(removed::contains);
        return publish(routes, preferences);
    }

    private WorkbenchSnapshot replace(WorkbenchRoute route) {
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>(snapshot.routes());
        routes.put(route.recipeId(), route);
        return publish(routes, snapshot.preferences());
    }

    private WorkbenchSnapshot publish(Map<String, WorkbenchRoute> routes, Map<String, String> preferences) {
        if (snapshot.sameContents(routes, preferences)) return snapshot;
        WorkbenchSnapshot next = new WorkbenchSnapshot(snapshot.id(), Math.addExact(snapshot.revision(), 1), routes, preferences);
        snapshot = next;
        return next;
    }

    private void check(long revision) {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Workbench mutation must run on its owner thread");
        if (revision != snapshot.revision()) throw new Rejected(Rejection.STALE_REVISION);
    }

    private WorkbenchRoute imported(String id) {
        WorkbenchRoute route = snapshot.routes().get(id);
        if (route == null) throw new Rejected(Rejection.NOT_IMPORTED);
        return route;
    }

    private static RecipeDeclaration declaration(RecipeCatalog catalog, String id) {
        RecipeDeclaration recipe = catalog.byId(id);
        if (recipe == null) throw new Rejected(Rejection.UNKNOWN_RECIPE);
        return recipe;
    }
}
