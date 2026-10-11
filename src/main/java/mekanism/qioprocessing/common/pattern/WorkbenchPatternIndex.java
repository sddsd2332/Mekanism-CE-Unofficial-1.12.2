package mekanism.qioprocessing.common.pattern;

import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.workbench.WorkbenchPatternPolicy;
import mekanism.qioprocessing.common.workbench.WorkbenchRoute;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;

import java.util.*;

/**
 * Immutable planner input built from one workbench revision. It contains only patterns
 * whose declaration and catalog generation still match; no World or live IRecipe is retained.
 */
public final class WorkbenchPatternIndex {
    private final String generation;
    private final long revision;
    private final Map<String, List<VerifiedWorkbenchPattern>> byRecipe;
    private final Map<mekanism.qioprocessing.common.recipe.RecipeItemKey, List<VerifiedWorkbenchPattern>> byOutput;

    private WorkbenchPatternIndex(String generation, long revision, Map<String, List<VerifiedWorkbenchPattern>> byRecipe) {
        this.generation = generation;
        this.revision = revision;
        Map<String, List<VerifiedWorkbenchPattern>> recipes = new LinkedHashMap<>();
        Map<mekanism.qioprocessing.common.recipe.RecipeItemKey, List<VerifiedWorkbenchPattern>> outputs = new LinkedHashMap<>();
        byRecipe.forEach((recipe, patterns) -> {
            List<VerifiedWorkbenchPattern> copy = Collections.unmodifiableList(new ArrayList<>(patterns));
            recipes.put(recipe, copy);
            for (VerifiedWorkbenchPattern pattern : copy)
                outputs.computeIfAbsent(pattern.output(), ignored -> new ArrayList<>()).add(pattern);
        });
        Map<mekanism.qioprocessing.common.recipe.RecipeItemKey, List<VerifiedWorkbenchPattern>> frozenOutputs = new LinkedHashMap<>();
        outputs.forEach((key, value) -> frozenOutputs.put(key, Collections.unmodifiableList(new ArrayList<>(value))));
        this.byRecipe = Collections.unmodifiableMap(recipes);
        this.byOutput = Collections.unmodifiableMap(frozenOutputs);
    }

    public static WorkbenchPatternIndex build(WorkbenchSnapshot snapshot, RecipeCatalog catalog) {
        if (snapshot == null || catalog == null) throw new NullPointerException("snapshot/catalog");
        Map<String, List<VerifiedWorkbenchPattern>> index = new LinkedHashMap<>();
        // The book's route order is the priority shown and edited by the workbench GUI.
        for (WorkbenchRoute route : snapshot.routes().values()) {
            RecipeDeclaration recipe = catalog.byId(route.recipeId());
            if (recipe == null || !route.enabled() || !route.validChoices(recipe)) continue;
            List<VerifiedWorkbenchPattern> patterns = new ArrayList<>();
            for (VerifiedWorkbenchPattern pattern : route.validPatterns(recipe, catalog.generation())) {
                if (WorkbenchPatternPolicy.admits(route, recipe, pattern.grid())) patterns.add(pattern);
            }
            if (!patterns.isEmpty()) index.put(recipe.id(), patterns);
        }
        return new WorkbenchPatternIndex(catalog.generation(), snapshot.revision(), index);
    }

    /**
     * Builds an immutable index for planner tests and non-world snapshot adapters.
     */
    public static WorkbenchPatternIndex of(String generation, long revision,
                                           Map<String, List<VerifiedWorkbenchPattern>> patterns) {
        if (generation == null || revision < 0 || patterns == null)
            throw new IllegalArgumentException("Invalid pattern index");
        return new WorkbenchPatternIndex(generation, revision, patterns);
    }

    public String generation() {
        return generation;
    }

    public long revision() {
        return revision;
    }

    public List<VerifiedWorkbenchPattern> patterns(String recipeId) {
        return byRecipe.getOrDefault(recipeId, Collections.emptyList());
    }

    public boolean has(String recipeId) {
        return !patterns(recipeId).isEmpty();
    }

    public List<VerifiedWorkbenchPattern> patternsForOutput(mekanism.qioprocessing.common.recipe.RecipeItemKey output) {
        return byOutput.getOrDefault(output, Collections.emptyList());
    }

    public Map<String, List<VerifiedWorkbenchPattern>> byRecipe() {
        return byRecipe;
    }
}
