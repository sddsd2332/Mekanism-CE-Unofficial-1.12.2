package mekanism.qioprocessing.common.recipe;

import net.minecraftforge.oredict.OreDictionary;

import java.util.*;

/**
 * Immutable, process-local declaration directory. All query methods are safe for background readers.
 */
public final class RecipeCatalog {
    private final Map<String, RecipeDeclaration> byId;
    private final Map<RecipeItemKey, List<RecipeDeclaration>> exactOutputs;
    private final Map<String, List<RecipeDeclaration>> outputFamilies;
    private final Map<String, List<RecipeDeclaration>> inputFamilies;
    private final Map<String, List<RecipeDeclaration>> outputsByItem;
    private final Map<String, List<RecipeDeclaration>> inputsByItem;
    private final List<RecipeDiagnostic> diagnostics;
    private final Map<RecipeDiagnostic.Reason, Integer> diagnosticCounts;
    private final String generation;
    private final int scanned, uniqueIngredients, uniqueItems;
    private final long elapsedNanos, estimatedBytes;

    // Package-private ownership transfer: Scanner creates these collections locally and never reuses them.
    RecipeCatalog(Map<String, RecipeDeclaration> declarations, List<RecipeDiagnostic> diagnostics,
                  int scanned, int uniqueIngredients, int uniqueItems, long estimatedBytes, long started) {
        byId = Collections.unmodifiableMap(declarations);
        this.diagnostics = Collections.unmodifiableList(diagnostics);
        this.scanned = scanned;
        this.uniqueIngredients = uniqueIngredients;
        this.uniqueItems = uniqueItems;
        Map<RecipeItemKey, List<RecipeDeclaration>> exact = new HashMap<>();
        Map<String, List<RecipeDeclaration>> outputs = new HashMap<>();
        Map<String, List<RecipeDeclaration>> inputs = new HashMap<>();
        Map<String, List<RecipeDeclaration>> outputItems = new HashMap<>();
        Map<String, List<RecipeDeclaration>> inputItems = new HashMap<>();
        RecipeFingerprint digest = new RecipeFingerprint().add("qio-declarations-v1");
        for (RecipeDeclaration recipe : declarations.values()) {
            digest.add(recipe.signature());
            add(exact, recipe.output(), recipe);
            add(outputs, recipe.output().familyKey(), recipe);
            add(outputItems, recipe.output().itemId(), recipe);
            Set<String> families = new LinkedHashSet<>();
            Set<String> items = new LinkedHashSet<>();
            for (RecipeIngredient ingredient : recipe.inputs()) {
                for (RecipeItemKey key : ingredient.candidates()) {
                    families.add(key.familyKey());
                    items.add(key.itemId());
                }
            }
            for (String family : families) add(inputs, family, recipe);
            for (String item : items) add(inputItems, item, recipe);
        }
        exactOutputs = freeze(exact);
        outputFamilies = freeze(outputs);
        inputFamilies = freeze(inputs);
        outputsByItem = freeze(outputItems);
        inputsByItem = freeze(inputItems);
        generation = digest.finish();
        EnumMap<RecipeDiagnostic.Reason, Integer> counts = new EnumMap<>(RecipeDiagnostic.Reason.class);
        for (RecipeDiagnostic diagnostic : diagnostics) counts.merge(diagnostic.reason, 1, Integer::sum);
        diagnosticCounts = Collections.unmodifiableMap(counts);
        this.estimatedBytes = estimatedBytes + estimate(exact) + estimate(outputs) + estimate(inputs)
                + estimate(outputItems) + estimate(inputItems) + 128L * diagnostics.size();
        elapsedNanos = System.nanoTime() - started;
    }

    private static <K> void add(Map<K, List<RecipeDeclaration>> map, K key, RecipeDeclaration recipe) {
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
    }

    private static <K> Map<K, List<RecipeDeclaration>> freeze(Map<K, List<RecipeDeclaration>> map) {
        map.replaceAll((key, value) -> Collections.unmodifiableList(value));
        return Collections.unmodifiableMap(map);
    }

    private static long estimate(Map<?, List<RecipeDeclaration>> map) {
        long bytes = 64L + 128L * map.size();
        for (List<RecipeDeclaration> list : map.values()) bytes += 8L * list.size();
        return bytes;
    }

    public static RecipeCatalog empty() {
        return new RecipeCatalog(new LinkedHashMap<>(), new ArrayList<>(), 0, 0, 0, 0, System.nanoTime());
    }

    public RecipeDeclaration byId(String id) {
        return byId.get(id);
    }

    public Collection<RecipeDeclaration> recipes() {
        return byId.values();
    }

    public List<RecipeDeclaration> exactOutput(RecipeItemKey output) {
        return exactOutputs.getOrDefault(output, Collections.emptyList());
    }

    /**
     * Display/discovery candidates: includes all tag/capability variants and wildcard metadata.
     */
    public List<RecipeDeclaration> outputsFor(RecipeItemKey selection) {
        return families(outputFamilies, outputsByItem, selection);
    }

    /**
     * Product-family query used by the two-pane workbench editor.
     */
    public List<RecipeDeclaration> outputsForFamily(String family) {
        return outputFamilies.getOrDefault(family, Collections.emptyList());
    }

    /**
     * Declaration-based input lookup, NOT proof of Ingredient.apply() for arbitrary custom predicates.
     */
    public List<RecipeDeclaration> usesOf(RecipeItemKey selection) {
        return families(inputFamilies, inputsByItem, selection);
    }

    private static List<RecipeDeclaration> families(Map<String, List<RecipeDeclaration>> families,
                                                    Map<String, List<RecipeDeclaration>> items, RecipeItemKey selection) {
        if (selection.metadata() == OreDictionary.WILDCARD_VALUE) {
            return items.getOrDefault(selection.itemId(), Collections.emptyList());
        }
        List<RecipeDeclaration> concrete = families.getOrDefault(selection.familyKey(), Collections.emptyList());
        List<RecipeDeclaration> wildcard = families.getOrDefault(
                RecipeItemKey.familyKey(selection.itemId(), OreDictionary.WILDCARD_VALUE), Collections.emptyList());
        if (wildcard.isEmpty()) return concrete;
        if (concrete.isEmpty()) return wildcard;
        Set<RecipeDeclaration> union = new LinkedHashSet<>(concrete);
        union.addAll(wildcard);
        List<RecipeDeclaration> result = new ArrayList<>(union);
        result.sort(java.util.Comparator.comparing(RecipeDeclaration::id));
        return Collections.unmodifiableList(result);
    }

    public String generation() {
        return generation;
    }

    public int scanned() {
        return scanned;
    }

    public int uniqueIngredients() {
        return uniqueIngredients;
    }

    public int uniqueItems() {
        return uniqueItems;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * Approximate retained declaration/index cost, not a heap measurement or a memory limit.
     */
    public long estimatedBytes() {
        return estimatedBytes;
    }

    public List<RecipeDiagnostic> diagnostics() {
        return diagnostics;
    }

    public Map<RecipeDiagnostic.Reason, Integer> diagnosticCounts() {
        return diagnosticCounts;
    }
}

