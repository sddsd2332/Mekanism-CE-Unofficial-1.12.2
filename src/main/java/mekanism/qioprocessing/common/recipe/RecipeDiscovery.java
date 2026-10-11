package mekanism.qioprocessing.common.recipe;

import java.util.*;

/**
 * Iterative declaration closure. It neither encodes patterns nor solves an executable crafting plan.
 */
public final class RecipeDiscovery {
    public enum Mode {DIRECT, PREFERRED, ALL}

    /**
     * Pure selection over declarations; implementations used by workers must be immutable.
     */
    public interface Selection {
        /**
         * Producer preference for the output family (item + metadata), shared by its NBT variants.
         */
        default String preferred(RecipeItemKey output) {
            return null;
        }

        default boolean enabled(RecipeDeclaration recipe) {
            return true;
        }

        default List<RecipeItemKey> candidates(RecipeDeclaration recipe, int slot) {
            return recipe.inputs().get(slot).candidates();
        }
    }

    private RecipeDiscovery() {
    }

    /**
     * Preferences map an output family to a recipe ID. A stale preference falls back to registry-name order.
     * PREFERRED explores one producer per input domain; ALL explores every declared alternative.
     * Every recipe is visited once, so cycles terminate without a depth/recipe/combination limit.
     */
    public static Result discover(RecipeCatalog catalog, RecipeItemKey target, Mode mode, Map<String, String> preferences) {
        return discover(catalog, Collections.singletonList(target), mode, new Selection() {
            @Override
            public String preferred(RecipeItemKey output) {
                return preferences.get(output.familyKey());
            }
        });
    }

    public static Result discover(RecipeCatalog catalog, Iterable<RecipeItemKey> targets, Mode mode, Selection selection) {
        return discover(catalog, targets, mode, selection, false);
    }

    public static Result discover(RecipeCatalog catalog, Iterable<RecipeItemKey> targets, Mode mode,
                                  Selection selection, boolean skipCycles) {
        Deque<Pending> pending = new ArrayDeque<>();
        Set<RecipeItemKey> scheduled = new HashSet<>();
        Set<String> expandedFamilies = new HashSet<>(), leafFamilies = new HashSet<>();
        Map<List<RecipeItemKey>, RecipeItemKey> preferredInputs = new IdentityHashMap<>();
        Map<String, RecipeDeclaration> selected = new LinkedHashMap<>();
        Set<RecipeItemKey> leaves = new LinkedHashSet<>();
        Set<String> incomplete = new LinkedHashSet<>();
        for (RecipeItemKey target : targets) enqueue(pending, scheduled, target, Collections.emptyList());
        int cycles = 0, skippedCycles = 0;
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Recipe discovery interrupted");
            Pending next = pending.removeFirst();
            RecipeItemKey output = next.key;
            // outputsFor groups NBT variants. Expanding the same family again would scan the
            // entire producer list per variant, turning a bulk selection into quadratic work.
            if (!expandedFamilies.add(output.familyKey())) {
                if (leafFamilies.contains(output.familyKey())) leaves.add(output);
                continue;
            }
            List<RecipeDeclaration> candidates = catalog.outputsFor(output);
            List<RecipeDeclaration> allowed = new ArrayList<>();
            for (RecipeDeclaration candidate : candidates) if (selection.enabled(candidate)) allowed.add(candidate);
            candidates = allowed;
            if (candidates.isEmpty()) {
                leafFamilies.add(output.familyKey());
                leaves.add(output);
                continue;
            }
            if (mode == Mode.PREFERRED)
                candidates = Collections.singletonList(preferred(candidates, selection.preferred(output)));
            for (RecipeDeclaration recipe : candidates) {
                if (Thread.currentThread().isInterrupted())
                    throw new java.util.concurrent.CancellationException("Recipe discovery interrupted");
                if (selected.putIfAbsent(recipe.id(), recipe) != null) continue;
                if (!recipe.hasDeclaredCandidates()) incomplete.add(recipe.id());
                if (mode == Mode.DIRECT) continue;
                List<String> path = new ArrayList<>(next.path);
                path.add(output.familyKey());
                for (int slot = 0; slot < recipe.inputs().size(); slot++) {
                    RecipeIngredient input = recipe.inputs().get(slot);
                    if (input.isEmptySlot()) continue;
                    List<RecipeItemKey> choices = selection.candidates(recipe, slot);
                    if (choices.isEmpty()) {
                        incomplete.add(recipe.id());
                        continue;
                    }
                    if (mode == Mode.ALL) {
                        for (RecipeItemKey candidate : choices) {
                            if (path.contains(candidate.familyKey())) {
                                cycles++;
                                if (skipCycles) {
                                    skippedCycles++;
                                    continue;
                                }
                            }
                            enqueue(pending, scheduled, candidate, path);
                        }
                    } else {
                        RecipeItemKey choice = preferredInputs.get(choices);
                        if (choice == null) {
                            choice = choices.get(0);
                            for (RecipeItemKey candidate : choices) {
                                boolean producer = false;
                                for (RecipeDeclaration route : catalog.outputsFor(candidate)) {
                                    if (selection.enabled(route)) {
                                        producer = true;
                                        break;
                                    }
                                }
                                if (producer) {
                                    choice = candidate;
                                    break;
                                }
                            }
                            preferredInputs.put(choices, choice);
                        }
                        if (path.contains(choice.familyKey())) {
                            cycles++;
                            if (skipCycles) {
                                skippedCycles++;
                                continue;
                            }
                        }
                        enqueue(pending, scheduled, choice, path);
                    }
                }
            }
        }
        return new Result(catalog.generation(), selected, leaves, incomplete, cycles, skippedCycles);
    }

    private static void enqueue(Deque<Pending> pending, Set<RecipeItemKey> scheduled,
                                RecipeItemKey key, List<String> path) {
        if (scheduled.add(key)) pending.addLast(new Pending(key, path));
    }

    private static RecipeDeclaration preferred(List<RecipeDeclaration> candidates, String id) {
        if (id != null) for (RecipeDeclaration candidate : candidates) if (candidate.id().equals(id)) return candidate;
        return candidates.get(0);
    }

    public static final class Result {
        private final String generation;
        private final List<RecipeDeclaration> recipes;
        private final Set<RecipeItemKey> leaves;
        private final Set<String> incomplete;
        private final int cycles;
        private final int skippedCycles;

        private Result(String generation, Map<String, RecipeDeclaration> recipes, Set<RecipeItemKey> leaves,
                       Set<String> incomplete, int cycles, int skippedCycles) {
            this.generation = generation;
            this.recipes = Collections.unmodifiableList(new ArrayList<>(recipes.values()));
            this.leaves = Collections.unmodifiableSet(leaves);
            this.incomplete = Collections.unmodifiableSet(incomplete);
            this.cycles = cycles;
            this.skippedCycles = skippedCycles;
        }

        public String generation() {
            return generation;
        }

        public List<RecipeDeclaration> recipes() {
            return recipes;
        }

        public Set<RecipeItemKey> leaves() {
            return leaves;
        }

        public Set<String> incompleteRecipes() {
            return incomplete;
        }

        public int cycleCount() {
            return cycles;
        }

        public int skippedCyclicRecipeCount() {
            return skippedCycles;
        }
    }

    private static final class Pending {
        private final RecipeItemKey key;
        private final List<String> path;

        private Pending(RecipeItemKey key, List<String> path) {
            this.key = key;
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
        }
    }
}
