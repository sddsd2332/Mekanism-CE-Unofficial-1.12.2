package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;

import java.util.*;

/**
 * A selected declaration plus the concrete patterns explicitly verified on the server.
 */
public final class WorkbenchRoute {
    private final String recipeId, signature;
    private final boolean enabled;
    private final Map<Integer, List<String>> choices;
    private final Map<UUID, VerifiedWorkbenchPattern> patterns;
    private final List<VerifiedWorkbenchPattern> patternIndex;

    WorkbenchRoute(String recipeId, String signature, boolean enabled, Map<Integer, List<String>> choices) {
        this(recipeId, signature, enabled, choices, Collections.emptyMap());
    }

    WorkbenchRoute(String recipeId, String signature, boolean enabled, Map<Integer, List<String>> choices,
                   Map<UUID, VerifiedWorkbenchPattern> patterns) {
        this.recipeId = Objects.requireNonNull(recipeId);
        this.signature = Objects.requireNonNull(signature);
        this.enabled = enabled;
        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        choices.forEach((slot, values) -> copy.put(slot, Collections.unmodifiableList(new ArrayList<>(values))));
        this.choices = Collections.unmodifiableMap(copy);
        if (patterns.size() > WorkbenchSnapshot.MAX_PATTERNS_PER_ROUTE)
            throw new IllegalArgumentException("Pattern route quota exceeded");
        Map<UUID, VerifiedWorkbenchPattern> patternCopy = new LinkedHashMap<>();
        patterns.forEach((id, pattern) -> {
            if (!id.equals(pattern.id()) || !recipeId.equals(pattern.recipeId()) || !signature.equals(pattern.declarationSignature())) {
                throw new IllegalArgumentException("Pattern does not belong to route");
            }
            patternCopy.put(id, pattern);
        });
        this.patterns = Collections.unmodifiableMap(patternCopy);
        this.patternIndex = Collections.unmodifiableList(new ArrayList<>(patternCopy.values()));
    }

    static WorkbenchRoute of(RecipeDeclaration recipe) {
        return new WorkbenchRoute(recipe.id(), recipe.signature(), true, Collections.emptyMap());
    }

    public String recipeId() {
        return recipeId;
    }

    public String signature() {
        return signature;
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<Integer, List<String>> choices() {
        return choices;
    }

    public Map<UUID, VerifiedWorkbenchPattern> patterns() {
        return patterns;
    }

    public int patternCount() {
        return patternIndex.size();
    }

    public List<VerifiedWorkbenchPattern> patternPage(int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("Invalid pattern page");
        int start = Math.min(offset, patternIndex.size());
        return patternIndex.subList(start, start + Math.min(limit, patternIndex.size() - start));
    }

    public boolean hasPatterns() {
        return !patterns.isEmpty();
    }

    public List<VerifiedWorkbenchPattern> validPatterns(RecipeDeclaration recipe, String catalogGeneration) {
        if (!matches(recipe)) return Collections.emptyList();
        List<VerifiedWorkbenchPattern> result = new ArrayList<>();
        for (VerifiedWorkbenchPattern pattern : patterns.values()) {
            if (pattern.declarationSignature().equals(recipe.signature()) && pattern.catalogGeneration().equals(catalogGeneration))
                result.add(pattern);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean matches(RecipeDeclaration recipe) {
        return recipe != null && recipeId.equals(recipe.id()) && signature.equals(recipe.signature());
    }

    public boolean validChoices(RecipeDeclaration recipe) {
        if (!matches(recipe)) return false;
        for (Map.Entry<Integer, List<String>> choice : choices.entrySet()) {
            int slot = choice.getKey();
            if (slot < 0 || slot >= recipe.inputs().size() || recipe.inputs().get(slot).isEmptySlot() || choice.getValue().isEmpty())
                return false;
            java.util.Set<String> available = new java.util.HashSet<>(), selected = new java.util.HashSet<>();
            for (RecipeItemKey key : recipe.inputs().get(slot).candidates()) available.add(key.fingerprint());
            for (String key : choice.getValue()) if (!available.contains(key) || !selected.add(key)) return false;
        }
        return true;
    }

    /**
     * A changed declaration gets default choices until explicitly reimported; old overrides never leak across signatures.
     */
    public List<RecipeItemKey> candidates(RecipeDeclaration recipe, int slot) {
        List<RecipeItemKey> defaults = recipe.inputs().get(slot).candidates();
        List<String> selected = matches(recipe) ? choices.get(slot) : null;
        if (selected == null) return defaults;
        Map<String, RecipeItemKey> byFingerprint = new HashMap<>();
        for (RecipeItemKey item : defaults) byFingerprint.put(item.fingerprint(), item);
        List<RecipeItemKey> result = new ArrayList<>(selected.size());
        for (String fingerprint : selected) {
            RecipeItemKey item = byFingerprint.get(fingerprint);
            if (item == null) throw new IllegalStateException("Invalid stored choice for " + recipeId);
            result.add(item);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorkbenchRoute)) return false;
        WorkbenchRoute route = (WorkbenchRoute) other;
        return enabled == route.enabled && recipeId.equals(route.recipeId) && signature.equals(route.signature)
                && choices.equals(route.choices) && patterns.equals(route.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, signature, enabled, choices, patterns);
    }
}

