package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.util.QIOHashing;
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import mekanism.qioprocessing.common.workbench.WorkbenchRoute;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sparse, frequency-local workbench route and ingredient preferences.
 */

/**
 * QIO 处理模块中的 QIOWorkbenchConfiguration 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchConfiguration {

    /** Layout persisted with a player pattern; shapeless occurrences have no physical slot. */
    public enum PatternLayout {
        SHAPED,
        SHAPELESS,
        ORDERED_UNKNOWN
    }

    /** v4 stores encoded grids as sparse references into one frequency-local palette. */
    private static final int SCHEMA_VERSION = 5;
    private static final int MAX_PRODUCT_OVERRIDES = 65_536;
    private static final int MAX_RECIPE_OVERRIDES = 65_536;
    private static final int MAX_RECIPES_PER_PRODUCT = 65_536;
    private static final int MAX_INGREDIENTS_PER_RECIPE = 9;
    // Candidate domains are not the bounded (64-option) flexible groups stored in a plan.
    // All declared choices remain selectable and pageable after the old enumerator retires.
    private static final int MAX_CANDIDATES_PER_INGREDIENT = 65_536;
    private static final int MAX_RECIPE_ID_LENGTH = 256;
    private static final int HASH_LENGTH = 64;
    private static final int MAX_ENCODED_PATTERNS = 65_536;

    @Nullable
    private WorkbenchSnapshot runtimeBook;
    @Nullable
    private RecipeCatalog runtimeCatalog;

    /** A detached, non-persistable view of the sole authoritative frequency book. */
    public static QIOWorkbenchConfiguration runtimeView(WorkbenchSnapshot book, RecipeCatalog catalog) {
        QIOWorkbenchConfiguration view = new QIOWorkbenchConfiguration(book.id());
        view.runtimeBook = Objects.requireNonNull(book);
        view.runtimeCatalog = Objects.requireNonNull(catalog);
        view.revision = book.revision();
        view.patternRevision = book.revision();
        view.catalogGenerationId = catalog.generation();
        for (WorkbenchRoute route : book.routes().values()) {
            RecipeDeclaration recipe = catalog.byId(route.recipeId());
            if (!route.matches(recipe) || !route.validChoices(recipe)) continue;
            String product = sha256(Stack.fromRecipeKey(recipe.output(), recipe.outputCount()).getResource().toString());
            view.productOrders.computeIfAbsent(product, ignored -> new ArrayList<>()).add(recipe.id());
        }
        for (List<String> order : view.productOrders.values()) {
            if (order.isEmpty()) continue;
            String preferred = book.preferred(catalog.byId(order.get(0)).output());
            if (preferred != null && order.remove(preferred)) order.add(0, preferred);
        }
        return view;
    }

    @Nullable
    public WorkbenchSnapshot getRuntimeBook() {
        return runtimeBook;
    }

    @Nullable
    public RecipeCatalog getRuntimeCatalog() {
        return runtimeCatalog;
    }

    private final UUID configUUID;
    private UUID originUUID;
    private long revision;
    private long patternRevision;
    /** Catalog generation the encoded patterns were captured against. */
    private String catalogGenerationId = sha256("catalog-unbound");
    private final Map<String, List<String>> productOrders = new LinkedHashMap<>();
    private final Map<String, RecipeOverride> recipeOverrides = new LinkedHashMap<>();
    /** Only these recipes are exposed to the QIO planner for this frequency. */
    private final Map<String, EncodedPattern> encodedPatterns = new LinkedHashMap<>();
    /** Patterns captured against an older catalog generation, retained for manual recovery. */
    private final Map<String, EncodedPattern> recoveryPatterns = new LinkedHashMap<>();
    @Nullable
    private ImportStamp lastImport;
    @Nullable
    private Runnable generationChangeListener;

    public QIOWorkbenchConfiguration() {
        this(UUID.randomUUID());
    }

    QIOWorkbenchConfiguration(UUID configUUID) {
        this.configUUID = Objects.requireNonNull(configUUID, "configUUID");
        originUUID = configUUID;
    }

    @Nonnull
    public UUID getConfigUUID() {
        return configUUID;
    }

    @Nonnull
    public UUID getOriginUUID() {
        return originUUID;
    }

    public long getRevision() {
        return revision;
    }

    public long getPatternRevision() {
        return patternRevision;
    }

    /** Creates an isolated configuration snapshot that is safe to read on a planning worker. */
    @Nonnull
    public QIOWorkbenchConfiguration copy() {
        QIOWorkbenchConfiguration copy = new QIOWorkbenchConfiguration(configUUID);
        copy.originUUID = originUUID;
        copy.runtimeBook = runtimeBook;
        copy.runtimeCatalog = runtimeCatalog;
        copy.revision = revision;
        copy.patternRevision = patternRevision;
        copy.catalogGenerationId = catalogGenerationId;
        productOrders.forEach((key, value) ->
                copy.productOrders.put(key, new ArrayList<>(value)));
        recipeOverrides.forEach((key, value) ->
                copy.recipeOverrides.put(key, value.copy()));
        encodedPatterns.forEach((key, value) ->
                copy.encodedPatterns.put(key, value.copy()));
        recoveryPatterns.forEach((key, value) ->
                copy.recoveryPatterns.put(key, value.copy()));
        if (lastImport != null) {
            copy.lastImport = new ImportStamp(lastImport.sourceConfigUUID,
                    lastImport.sourceRevision, lastImport.sourceDigest);
        }
        return copy;
    }

    @Nonnull
    public String getCatalogGenerationId() {
        return catalogGenerationId;
    }

    public int getProductOverrideCount() {
        return productOrders.size();
    }

    public int getRecipeOverrideCount() {
        return recipeOverrides.size();
    }

    public int getIngredientOverrideCount() {
        int count = 0;
        for (RecipeOverride override : recipeOverrides.values()) {
            count += override.ingredients.size();
        }
        return count;
    }

    /** Number of manually encoded workbench patterns in this frequency. */
    public int getEncodedPatternCount() {
        return encodedPatterns.size();
    }

    public int getRecoveryPatternCount() {
        return recoveryPatterns.size();
    }

    public void bindGenerationChangeListener(@Nullable Runnable listener) {
        generationChangeListener = listener;
    }

    @Nonnull
    public List<EncodedPattern> getEncodedPatterns() {
        List<EncodedPattern> result = new ArrayList<>(encodedPatterns.size());
        encodedPatterns.values().forEach(pattern -> result.add(pattern.copy()));
        result.sort(Comparator.comparing(pattern -> pattern.getRecipeId().toString()));
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public EncodedPattern getEncodedPattern(@Nonnull String recipeId) {
        return encodedPatterns.get(checkedRecipeId(recipeId));
    }

    /** Adds or replaces one server-validated pattern. */
    public boolean putEncodedPattern(@Nonnull EncodedPattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        String recipeId = checkedRecipeId(pattern.getRecipeId().toString());
        EncodedPattern previous = encodedPatterns.get(recipeId);
        if (previous != null) {
            if (previous.sameRecipeContent(pattern)) return false;
            pattern = pattern.withUUID(previous.patternUUID);
        }
        if (previous == null && encodedPatterns.size() >= MAX_ENCODED_PATTERNS) {
            throw new IllegalStateException("QIO workbench pattern limit reached");
        }
        encodedPatterns.put(recipeId, pattern.copy());
        // Recipe signatures and candidate identities are tied to the encoded recipe. Any
        // replacement must discard preferences that no longer describe the new pattern.
        recipeOverrides.remove(recipeId);
        productOrders.values().forEach(order -> order.remove(recipeId));
        productOrders.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        incrementPatternRevision();
        incrementRevision();
        return true;
    }

    /** Successful read-time migration preserves UUIDs, product order and sparse preferences. */
    public boolean migrateVerified(EncodedPattern expected, QIOVerifiedWorkbenchPattern verified) {
        return migrateVerified(expected, verified, Collections.emptyMap());
    }

    public boolean migrateVerified(EncodedPattern expected, QIOVerifiedWorkbenchPattern verified,
                                   Map<Integer, Map<String, List<String>>> aliases) {
        String id = expected.getRecipeId().toString();
        EncodedPattern current = encodedPatterns.get(id);
        if (current == null || !current.equals(expected)) return false;
        EncodedPattern migrated = EncodedPattern.fromVerified(verified.withId(current.patternUUID));
        if (current.equals(migrated)) return false;
        RecipeOverride previous = recipeOverrides.get(id);
        if (previous != null && previous.signature.equals(current.recipeSignature)) {
            RecipeOverride rebound = new RecipeOverride(migrated.recipeSignature);
            rebound.enabled = previous.enabled;
            previous.ingredients.forEach((slot, ingredient) -> {
                Map<String, List<String>> mapping = aliases.getOrDefault(slot, Collections.emptyMap());
                IngredientOverride replacement = new IngredientOverride();
                for (String candidate : ingredient.order) {
                    for (String mapped : mapping.getOrDefault(candidate, Collections.singletonList(candidate))) {
                        if (!replacement.order.contains(mapped)) replacement.order.add(mapped);
                    }
                }
                for (String candidate : ingredient.disabled) {
                    replacement.disabled.addAll(mapping.getOrDefault(candidate, Collections.singletonList(candidate)));
                }
                if (replacement.order.size() > MAX_CANDIDATES_PER_INGREDIENT ||
                        !replacement.disabled.isEmpty() && replacement.disabled.size() >= replacement.order.size()) {
                    throw new IllegalArgumentException("Cannot preserve workbench candidate preferences at slot " + slot);
                }
                rebound.ingredients.put(slot, replacement);
            });
            recipeOverrides.put(id, rebound);
        }
        encodedPatterns.put(id, migrated);
        incrementPatternRevision();
        incrementRevision();
        if (generationChangeListener != null) generationChangeListener.run();
        return true;
    }

    /**
     * Binds this configuration to a newly published catalog. Stale active patterns are moved to
     * the recovery set atomically so they can be inspected or restored by an explicit player
     * action instead of making planning unavailable indefinitely.
     */
    public boolean bindCatalogGeneration(@Nonnull String generationId) {
        String checked = checkedHash(generationId, "catalogGenerationId");
        if (checked.equals(catalogGenerationId)) return false;
        // A catalog generation is a directory identity, not proof that every recipe changed.
        // The old implementation moved every active pattern to recovery (and cleared every
        // product order) whenever one recipe or Forge integration changed.  The published
        // immutable index performs per-pattern signature validation below, so keep the active
        // configuration intact and quarantine only the entries it actually rejects.
        catalogGenerationId = checked;
        if (generationChangeListener != null) {
            generationChangeListener.run();
        }
        return false;
    }

    /**
     * Moves invalid active patterns to the recovery set in one revision. The IDs are supplied by
     * the immutable catalog, so this method never has to inspect Forge objects itself.
     */
    public int quarantineEncodedPatterns(@Nonnull Collection<String> recipeIds) {
        Objects.requireNonNull(recipeIds, "recipeIds");
        if (recipeIds.isEmpty()) return 0;
        List<String> checked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String recipeId : recipeIds) {
            String id = checkedRecipeId(recipeId);
            if (seen.add(id) && encodedPatterns.containsKey(id)) {
                checked.add(id);
            }
        }
        if (checked.isEmpty()) return 0;
        checked.sort(String::compareTo);
        boolean changed = false;
        for (String id : checked) {
            EncodedPattern pattern = encodedPatterns.remove(id);
            if (pattern == null) continue;
            recoveryPatterns.putIfAbsent(id, pattern.copy());
            recipeOverrides.remove(id);
            changed = true;
        }
        if (!changed) return 0;
        productOrders.values().forEach(order -> order.removeIf(checked::contains));
        productOrders.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        incrementPatternRevision();
        incrementRevision();
        return checked.size();
    }

    /** Restores all recoverable patterns for callers that do not have a catalog available. */
    public int restoreRecoveryPatterns() {
        return restoreRecoveryPatterns(pattern -> true);
    }

    /**
     * Restores only recovery patterns accepted by the current immutable catalog. Rejected entries
     * remain available for inspection or a later explicit recovery after the catalog changes.
     */
    public int restoreRecoveryPatterns(@Nonnull Predicate<EncodedPattern> validator) {
        Objects.requireNonNull(validator, "validator");
        if (recoveryPatterns.isEmpty()) return 0;
        int capacity = MAX_ENCODED_PATTERNS - encodedPatterns.size();
        if (capacity <= 0) return 0;
        int restored = 0;
        List<String> ids = new ArrayList<>(recoveryPatterns.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            if (restored >= capacity) break;
            if (!encodedPatterns.containsKey(id)) {
                EncodedPattern candidate = recoveryPatterns.get(id);
                boolean accepted;
                try {
                    accepted = validator.test(candidate);
                } catch (RuntimeException ignored) {
                    accepted = false;
                }
                if (accepted) {
                    encodedPatterns.put(id, recoveryPatterns.remove(id).copy());
                    restored++;
                }
            }
        }
        if (restored > 0) {
            incrementPatternRevision();
            incrementRevision();
        }
        return restored;
    }

    /**
     * Adds server-discovered patterns without replacing any existing recipe identity. The
     * entire batch consumes one configuration revision and preserves all existing route and
     * ingredient preferences.
     *
     * @return Number of newly inserted recipe patterns.
     */
    public int putEncodedPatternsIfAbsent(@Nonnull Collection<EncodedPattern> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        Map<String, EncodedPattern> checked = new LinkedHashMap<>();
        for (EncodedPattern pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            String recipeId = checkedRecipeId(pattern.getRecipeId().toString());
            EncodedPattern duplicate = checked.putIfAbsent(recipeId, pattern);
            if (duplicate != null && !duplicate.sameRecipeContent(pattern)) {
                throw new IllegalArgumentException(
                        "Conflicting workbench patterns in one batch: " + recipeId);
            }
        }
        List<Map.Entry<String, EncodedPattern>> additions = new ArrayList<>();
        checked.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            EncodedPattern existing = encodedPatterns.get(entry.getKey());
            if (existing == null) {
                additions.add(entry);
            }
            // Identical entries are already present. Changed entries are deliberately skipped:
            // a bulk target import must never discard the player's existing route preferences.
        });
        if (additions.isEmpty()) return 0;
        if (additions.size() > MAX_ENCODED_PATTERNS - encodedPatterns.size()) {
            throw new IllegalStateException("QIO workbench pattern limit reached");
        }
        additions.forEach(entry -> encodedPatterns.put(entry.getKey(),
                entry.getValue().copy()));
        incrementPatternRevision();
        incrementRevision();
        return additions.size();
    }

    /**
     * Atomically replaces one explicitly selected root and adds every absent dependency.
     * A {@code null} root gives batch-import semantics where existing identities are never
     * replaced. Existing dependency preferences are always preserved.
     */
    public boolean applyEncodedClosure(@Nullable EncodedPattern replacingRoot,
                                       @Nonnull Collection<EncodedPattern> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        Map<String, EncodedPattern> checked = new LinkedHashMap<>();
        for (EncodedPattern pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            String recipeId = checkedRecipeId(pattern.getRecipeId().toString());
            EncodedPattern duplicate = checked.putIfAbsent(recipeId, pattern);
            if (duplicate != null && !duplicate.sameRecipeContent(pattern)) {
                throw new IllegalArgumentException(
                        "Conflicting workbench patterns in one closure: " + recipeId);
            }
        }
        String rootId = null;
        if (replacingRoot != null) {
            rootId = checkedRecipeId(replacingRoot.getRecipeId().toString());
            EncodedPattern discoveredRoot = checked.get(rootId);
            if (discoveredRoot != null && !discoveredRoot.sameRecipeContent(replacingRoot)) {
                throw new IllegalArgumentException(
                        "Workbench closure root conflicts with discovered pattern: " + rootId);
            }
            checked.put(rootId, replacingRoot);
        }

        int additions = 0;
        for (String recipeId : checked.keySet()) {
            if (!encodedPatterns.containsKey(recipeId)) additions++;
        }
        if (additions > MAX_ENCODED_PATTERNS - encodedPatterns.size()) {
            throw new IllegalStateException("QIO workbench pattern limit reached");
        }

        boolean changed = false;
        if (rootId != null) {
            EncodedPattern root = checked.remove(rootId);
            EncodedPattern previous = encodedPatterns.get(rootId);
            if (previous == null || !previous.sameRecipeContent(root)) {
                if (previous != null) root = root.withUUID(previous.patternUUID);
                encodedPatterns.put(rootId, root.copy());
                recipeOverrides.remove(rootId);
                for (List<String> order : productOrders.values()) order.remove(rootId);
                productOrders.entrySet().removeIf(entry -> entry.getValue().isEmpty());
                changed = true;
            }
        }
        List<Map.Entry<String, EncodedPattern>> ordered = new ArrayList<>(checked.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, EncodedPattern> entry : ordered) {
            if (!encodedPatterns.containsKey(entry.getKey())) {
                encodedPatterns.put(entry.getKey(), entry.getValue().copy());
                changed = true;
            }
        }
        if (changed) {
            incrementPatternRevision();
            incrementRevision();
        }
        return changed;
    }

    public boolean removeEncodedPattern(@Nonnull String recipeId) {
        String checked = checkedRecipeId(recipeId);
        boolean removedPattern = encodedPatterns.remove(checked) != null;
        boolean changed = removedPattern;
        changed |= recipeOverrides.remove(checked) != null;
        for (List<String> order : productOrders.values()) {
            changed |= order.remove(checked);
        }
        productOrders.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (changed) {
            if (removedPattern) incrementPatternRevision();
            incrementRevision();
        }
        return changed;
    }

    /** Removes every manually encoded route for one exact output as one revision. */
    public boolean removeEncodedProduct(@Nonnull String productKey,
                                        @Nonnull PortableResourceDescriptor output) {
        String checkedProduct = checkedHash(productKey, "productKey");
        Objects.requireNonNull(output, "output");
        List<String> removedRecipes = new ArrayList<>();
        encodedPatterns.forEach((recipeId, pattern) -> {
            if (pattern.getOutput().equals(output)) removedRecipes.add(recipeId);
        });
        if (removedRecipes.isEmpty()) return false;
        removedRecipes.forEach(encodedPatterns::remove);
        productOrders.remove(checkedProduct);
        for (String recipeId : removedRecipes) {
            recipeOverrides.remove(recipeId);
            for (List<String> order : productOrders.values()) {
                order.remove(recipeId);
            }
        }
        productOrders.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        incrementPatternRevision();
        incrementRevision();
        return true;
    }

    /** Removes several exact output groups atomically and consumes one revision. */
    public boolean removeEncodedProducts(
            @Nonnull Map<String, PortableResourceDescriptor> products) {
        Objects.requireNonNull(products, "products");
        if (products.isEmpty()) return false;
        Map<String, PortableResourceDescriptor> checked = new LinkedHashMap<>();
        products.forEach((productKey, output) -> checked.put(
                checkedHash(productKey, "productKey"), Objects.requireNonNull(output,
                        "output")));
        return applyAtomicBatch(working -> checked.forEach((productKey, output) -> {
            if (!working.removeEncodedProduct(productKey, output)) {
                throw new IllegalArgumentException("Unknown workbench product");
            }
        }));
    }

    /** Removes several encoded routes atomically and consumes one pattern revision. */
    public boolean removeEncodedPatterns(@Nonnull Map<String, String> signatures) {
        Objects.requireNonNull(signatures, "signatures");
        if (signatures.isEmpty()) return false;
        Map<String, String> checked = new LinkedHashMap<>();
        signatures.forEach((recipeId, signature) -> checked.put(
                checkedRecipeId(recipeId), checkedHash(signature, "recipeSignature")));
        return applyAtomicBatch(working -> checked.forEach((recipeId, signature) -> {
            EncodedPattern existing = working.encodedPatterns.get(recipeId);
            if (existing == null || !existing.getRecipeSignature().equals(signature)) {
                throw new IllegalArgumentException("Unknown or changed workbench pattern");
            }
            working.removeEncodedPattern(recipeId);
        }));
    }

    public boolean isRecipeEnabled(@Nonnull String recipeId,
                                   @Nonnull String recipeSignature) {
        if (runtimeBook != null) {
            WorkbenchRoute route = runtimeBook.routes().get(recipeId);
            return route != null && route.enabled() && route.signature().equals(recipeSignature) &&
                    route.validChoices(runtimeCatalog.byId(recipeId));
        }
        RecipeOverride override = recipeOverrides.get(checkedRecipeId(recipeId));
        return override == null || !override.signature.equals(checkedHash(recipeSignature,
                "recipeSignature")) || override.enabled;
    }

    @Nonnull
    public List<String> orderedRecipes(@Nonnull String productKey,
                                       @Nonnull List<String> defaults) {
        return mergeOrder(productOrders.get(checkedHash(productKey, "productKey")),
                checkedRecipeIds(defaults));
    }

    public long recipePriority(@Nonnull String productKey,
                               @Nonnull List<String> defaults, @Nonnull String recipeId) {
        List<String> order = orderedRecipes(productKey, defaults);
        int index = order.indexOf(checkedRecipeId(recipeId));
        return index < 0 ? Long.MIN_VALUE : Long.MAX_VALUE - index;
    }

    @Nonnull
    public List<String> orderedCandidates(@Nonnull String recipeId,
                                          @Nonnull String recipeSignature, int slot, @Nonnull List<String> defaults) {
        List<String> checkedDefaults = checkedCandidateIds(defaults);
        RecipeOverride recipe = currentRecipeOverride(recipeId, recipeSignature);
        IngredientOverride ingredient = recipe == null ? null : recipe.ingredients.get(
                checkedSlot(slot));
        return mergeOrder(ingredient == null ? null : ingredient.order, checkedDefaults);
    }

    @Nonnull
    public List<String> orderedEnabledCandidates(@Nonnull String recipeId,
                                                 @Nonnull String recipeSignature, int slot, @Nonnull List<String> defaults) {
        List<String> checkedDefaults = checkedCandidateIds(defaults);
        RecipeOverride recipe = currentRecipeOverride(recipeId, recipeSignature);
        IngredientOverride ingredient = recipe == null ? null : recipe.ingredients.get(
                checkedSlot(slot));
        List<String> ordered = orderedCandidates(recipeId, recipeSignature, slot,
                checkedDefaults);
        if (ingredient == null || ingredient.disabled.isEmpty()) {
            return ordered;
        }
        List<String> enabled = new ArrayList<>(ordered.size());
        for (String candidate : ordered) {
            if (!ingredient.disabled.contains(candidate)) {
                enabled.add(candidate);
            }
        }
        return Collections.unmodifiableList(enabled);
    }

    public boolean isCandidateEnabled(@Nonnull String recipeId,
                                      @Nonnull String recipeSignature, int slot, @Nonnull String candidateId) {
        RecipeOverride recipe = currentRecipeOverride(recipeId, recipeSignature);
        IngredientOverride ingredient = recipe == null ? null : recipe.ingredients.get(
                checkedSlot(slot));
        return ingredient == null || !ingredient.disabled.contains(
                checkedHash(candidateId, "candidateId"));
    }

    public boolean setRecipeEnabled(@Nonnull String recipeId,
                                    @Nonnull String recipeSignature, boolean enabled) {
        String checkedRecipe = checkedRecipeId(recipeId);
        String checkedSignature = checkedHash(recipeSignature, "recipeSignature");
        RecipeOverride override = mutableRecipe(checkedRecipe, checkedSignature);
        if (override.enabled == enabled) {
            removeDefaultRecipe(checkedRecipe, override);
            return false;
        }
        override.enabled = enabled;
        removeDefaultRecipe(checkedRecipe, override);
        incrementRevision();
        return true;
    }

    /** Applies one enabled state to several routes atomically in one revision. */
    public boolean setRecipesEnabled(@Nonnull Map<String, String> signatures,
                                     boolean enabled) {
        Objects.requireNonNull(signatures, "signatures");
        if (signatures.isEmpty()) return false;
        Map<String, String> checked = new LinkedHashMap<>();
        signatures.forEach((recipeId, signature) -> checked.put(
                checkedRecipeId(recipeId), checkedHash(signature, "recipeSignature")));
        return applyAtomicBatch(working -> checked.forEach((recipeId, signature) ->
                working.setRecipeEnabled(recipeId, signature, enabled)));
    }

    public boolean moveRecipe(@Nonnull String productKey,
                              @Nonnull List<String> defaults, @Nonnull String recipeId, int direction,
                              boolean edge) {
        String checkedProduct = checkedHash(productKey, "productKey");
        List<String> checkedDefaults = checkedRecipeIds(defaults);
        String checkedRecipe = checkedRecipeId(recipeId);
        List<String> order = new ArrayList<>(mergeOrder(productOrders.get(checkedProduct),
                checkedDefaults));
        int from = order.indexOf(checkedRecipe);
        if (from < 0) {
            throw new IllegalArgumentException("Unknown workbench recipe " + checkedRecipe);
        }
        int to = edge ? direction < 0 ? 0 : order.size() - 1 :
                Math.max(0, Math.min(order.size() - 1, from + Integer.signum(direction)));
        if (from == to) {
            return false;
        }
        order.remove(from);
        order.add(to, checkedRecipe);
        putOrder(productOrders, checkedProduct, order, checkedDefaults);
        incrementRevision();
        return true;
    }

    /** Moves selected routes as stable blocks while preserving their relative order. */
    public boolean moveRecipes(@Nonnull String productKey,
                               @Nonnull List<String> defaults, @Nonnull Collection<String> recipeIds,
                               int direction, boolean edge) {
        String checkedProduct = checkedHash(productKey, "productKey");
        List<String> checkedDefaults = checkedRecipeIds(defaults);
        Set<String> selected = new LinkedHashSet<>(checkedRecipeIds(recipeIds));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No workbench recipes selected");
        }
        List<String> order = new ArrayList<>(mergeOrder(productOrders.get(checkedProduct),
                checkedDefaults));
        if (!order.containsAll(selected)) {
            throw new IllegalArgumentException("Unknown workbench recipe in batch");
        }
        List<String> moved = new ArrayList<>(order);
        if (edge) {
            moved.clear();
            if (direction < 0) {
                order.stream().filter(selected::contains).forEach(moved::add);
                order.stream().filter(recipe -> !selected.contains(recipe)).forEach(moved::add);
            } else {
                order.stream().filter(recipe -> !selected.contains(recipe)).forEach(moved::add);
                order.stream().filter(selected::contains).forEach(moved::add);
            }
        } else if (direction < 0) {
            for (int index = 1; index < moved.size(); index++) {
                if (selected.contains(moved.get(index)) &&
                        !selected.contains(moved.get(index - 1))) {
                    Collections.swap(moved, index, index - 1);
                }
            }
        } else {
            for (int index = moved.size() - 2; index >= 0; index--) {
                if (selected.contains(moved.get(index)) &&
                        !selected.contains(moved.get(index + 1))) {
                    Collections.swap(moved, index, index + 1);
                }
            }
        }
        if (moved.equals(order)) return false;
        putOrder(productOrders, checkedProduct, moved, checkedDefaults);
        incrementRevision();
        return true;
    }

    public boolean setCandidateEnabled(@Nonnull String recipeId,
                                       @Nonnull String recipeSignature, int slot, @Nonnull List<String> defaults,
                                       @Nonnull String candidateId, boolean enabled) {
        String checkedRecipe = checkedRecipeId(recipeId);
        String checkedSignature = checkedHash(recipeSignature, "recipeSignature");
        int checkedSlot = checkedSlot(slot);
        List<String> checkedDefaults = checkedCandidateIds(defaults);
        String checkedCandidate = checkedHash(candidateId, "candidateId");
        if (!checkedDefaults.contains(checkedCandidate)) {
            throw new IllegalArgumentException("Unknown workbench ingredient candidate");
        }
        RecipeOverride recipe = mutableRecipe(checkedRecipe, checkedSignature);
        IngredientOverride ingredient = recipe.ingredients.computeIfAbsent(checkedSlot,
                ignored -> new IngredientOverride());
        boolean currentlyEnabled = !ingredient.disabled.contains(checkedCandidate);
        if (currentlyEnabled == enabled) {
            cleanupIngredient(checkedRecipe, checkedSlot, recipe, ingredient, checkedDefaults);
            return false;
        }
        if (!enabled) {
            int enabledCount = 0;
            for (String candidate : checkedDefaults) {
                if (!ingredient.disabled.contains(candidate)) {
                    enabledCount++;
                }
            }
            if (enabledCount <= 1) {
                throw new LastCandidateException();
            }
            ingredient.disabled.add(checkedCandidate);
        } else {
            ingredient.disabled.remove(checkedCandidate);
        }
        if (!ingredient.disabled.isEmpty() && ingredient.order.isEmpty()) {
            ingredient.order.addAll(checkedDefaults);
        }
        cleanupIngredient(checkedRecipe, checkedSlot, recipe, ingredient, checkedDefaults);
        incrementRevision();
        return true;
    }

    public boolean moveCandidate(@Nonnull String recipeId,
                                 @Nonnull String recipeSignature, int slot, @Nonnull List<String> defaults,
                                 @Nonnull String candidateId, int direction, boolean edge) {
        String checkedRecipe = checkedRecipeId(recipeId);
        String checkedSignature = checkedHash(recipeSignature, "recipeSignature");
        int checkedSlot = checkedSlot(slot);
        List<String> checkedDefaults = checkedCandidateIds(defaults);
        String checkedCandidate = checkedHash(candidateId, "candidateId");
        RecipeOverride recipe = mutableRecipe(checkedRecipe, checkedSignature);
        IngredientOverride ingredient = recipe.ingredients.computeIfAbsent(checkedSlot,
                ignored -> new IngredientOverride());
        List<String> order = new ArrayList<>(mergeOrder(ingredient.order, checkedDefaults));
        int from = order.indexOf(checkedCandidate);
        if (from < 0) {
            throw new IllegalArgumentException("Unknown workbench ingredient candidate");
        }
        int to = edge ? direction < 0 ? 0 : order.size() - 1 :
                Math.max(0, Math.min(order.size() - 1, from + Integer.signum(direction)));
        return moveCandidateToIndexChecked(checkedRecipe, checkedSignature, checkedSlot,
                checkedDefaults, checkedCandidate, to);
    }

    public boolean moveCandidateToIndex(@Nonnull String recipeId,
                                        @Nonnull String recipeSignature, int slot, @Nonnull List<String> defaults,
                                        @Nonnull String candidateId, int targetIndex) {
        String checkedRecipe = checkedRecipeId(recipeId);
        String checkedSignature = checkedHash(recipeSignature, "recipeSignature");
        int checkedSlot = checkedSlot(slot);
        List<String> checkedDefaults = checkedCandidateIds(defaults);
        String checkedCandidate = checkedHash(candidateId, "candidateId");
        if (targetIndex < 0 || targetIndex >= checkedDefaults.size()) {
            throw new IllegalArgumentException("Workbench candidate target index is invalid");
        }
        return moveCandidateToIndexChecked(checkedRecipe, checkedSignature, checkedSlot,
                checkedDefaults, checkedCandidate, targetIndex);
    }

    private boolean moveCandidateToIndexChecked(String checkedRecipe, String checkedSignature,
                                                int checkedSlot, List<String> checkedDefaults, String checkedCandidate,
                                                int targetIndex) {
        RecipeOverride recipe = mutableRecipe(checkedRecipe, checkedSignature);
        IngredientOverride ingredient = recipe.ingredients.computeIfAbsent(checkedSlot,
                ignored -> new IngredientOverride());
        List<String> order = new ArrayList<>(mergeOrder(ingredient.order, checkedDefaults));
        int from = order.indexOf(checkedCandidate);
        if (from < 0) {
            throw new IllegalArgumentException("Unknown workbench ingredient candidate");
        }
        if (targetIndex < 0 || targetIndex >= order.size()) {
            throw new IllegalArgumentException("Workbench candidate target index is invalid");
        }
        if (from == targetIndex) {
            cleanupIngredient(checkedRecipe, checkedSlot, recipe, ingredient,
                    checkedDefaults);
            return false;
        }
        order.remove(from);
        order.add(targetIndex, checkedCandidate);
        ingredient.order.clear();
        if (!order.equals(checkedDefaults)) {
            ingredient.order.addAll(order);
        }
        cleanupIngredient(checkedRecipe, checkedSlot, recipe, ingredient, checkedDefaults);
        incrementRevision();
        return true;
    }

    /** Copies one ingredient's order and disabled set to equivalent slots in one revision. */
    public boolean synchronizeEquivalentCandidates(@Nonnull String recipeId,
                                                   @Nonnull String recipeSignature, int sourceSlot,
                                                   @Nonnull Map<Integer, List<String>> equivalentDefaults) {
        String checkedRecipe = checkedRecipeId(recipeId);
        String checkedSignature = checkedHash(recipeSignature, "recipeSignature");
        int checkedSourceSlot = checkedSlot(sourceSlot);
        Objects.requireNonNull(equivalentDefaults, "equivalentDefaults");
        List<String> sourceDefaults = checkedCandidateIds(
                Objects.requireNonNull(equivalentDefaults.get(checkedSourceSlot),
                        "source candidate defaults"));
        Set<String> sourceSet = new HashSet<>(sourceDefaults);
        Map<Integer, List<String>> checkedTargets = new LinkedHashMap<>();
        equivalentDefaults.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int targetSlot = checkedSlot(entry.getKey());
            List<String> targetDefaults = checkedCandidateIds(entry.getValue());
            if (targetDefaults.size() != sourceDefaults.size() ||
                    !sourceSet.equals(new HashSet<>(targetDefaults))) {
                throw new IllegalArgumentException(
                        "Equivalent workbench ingredients have different candidates");
            }
            checkedTargets.put(targetSlot, targetDefaults);
        });
        if (checkedTargets.size() < 2) {
            return false;
        }
        List<String> sourceOrder = orderedCandidates(checkedRecipe, checkedSignature,
                checkedSourceSlot, sourceDefaults);
        Set<String> sourceDisabled = new LinkedHashSet<>();
        for (String candidate : sourceOrder) {
            if (!isCandidateEnabled(checkedRecipe, checkedSignature, checkedSourceSlot,
                    candidate)) {
                sourceDisabled.add(candidate);
            }
        }
        RecipeOverride recipe = mutableRecipe(checkedRecipe, checkedSignature);
        boolean changed = false;
        for (Map.Entry<Integer, List<String>> entry : checkedTargets.entrySet()) {
            int targetSlot = entry.getKey();
            List<String> targetDefaults = entry.getValue();
            IngredientOverride existing = recipe.ingredients.get(targetSlot);
            List<String> existingOrder = mergeOrder(existing == null ? null : existing.order,
                    targetDefaults);
            Set<String> existingDisabled = existing == null ? Collections.emptySet() :
                    new LinkedHashSet<>(existing.disabled);
            if (existingOrder.equals(sourceOrder) && existingDisabled.equals(sourceDisabled)) {
                continue;
            }
            changed = true;
            IngredientOverride replacement = new IngredientOverride();
            replacement.disabled.addAll(sourceDisabled);
            if (!sourceOrder.equals(targetDefaults) || !sourceDisabled.isEmpty()) {
                replacement.order.addAll(sourceOrder);
            }
            if (replacement.isDefault()) {
                recipe.ingredients.remove(targetSlot);
            } else {
                recipe.ingredients.put(targetSlot, replacement);
            }
        }
        removeDefaultRecipe(checkedRecipe, recipe);
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    public boolean resetRecipe(@Nonnull String recipeId) {
        if (recipeOverrides.remove(checkedRecipeId(recipeId)) == null) {
            return false;
        }
        incrementRevision();
        return true;
    }

    public boolean resetProduct(@Nonnull String productKey,
                                @Nonnull Collection<String> recipeIds) {
        String checkedProduct = checkedHash(productKey, "productKey");
        boolean changed = productOrders.remove(checkedProduct) != null;
        for (String recipeId : checkedRecipeIds(recipeIds)) {
            changed |= recipeOverrides.remove(recipeId) != null;
        }
        if (changed) {
            incrementRevision();
        }
        return changed;
    }

    public boolean resetAll() {
        if (productOrders.isEmpty() && recipeOverrides.isEmpty() && encodedPatterns.isEmpty() &&
                recoveryPatterns.isEmpty()) {
            return false;
        }
        boolean hadPatterns = !encodedPatterns.isEmpty();
        productOrders.clear();
        recipeOverrides.clear();
        encodedPatterns.clear();
        recoveryPatterns.clear();
        if (hadPatterns) incrementPatternRevision();
        incrementRevision();
        return true;
    }

    public boolean importedFrom(@Nonnull UUID sourceConfigUUID, long sourceRevision,
                                @Nonnull String sourceDigest) {
        return lastImport != null && lastImport.sourceConfigUUID.equals(sourceConfigUUID) &&
                lastImport.sourceRevision == sourceRevision &&
                lastImport.sourceDigest.equals(checkedHash(sourceDigest, "sourceDigest"));
    }

    public boolean replaceFrom(@Nonnull QIOWorkbenchConfiguration source) {
        Objects.requireNonNull(source, "source");
        String digest = source.contentDigest();
        if (source == this || importedFrom(source.configUUID, source.revision, digest)) {
            return false;
        }
        boolean patternsChanged = !encodedPatterns.equals(source.encodedPatterns);
        productOrders.clear();
        source.productOrders.forEach((key, value) ->
                productOrders.put(key, new ArrayList<>(value)));
        recipeOverrides.clear();
        source.recipeOverrides.forEach((key, value) ->
                recipeOverrides.put(key, value.copy()));
        encodedPatterns.clear();
        source.encodedPatterns.forEach((key, value) ->
                encodedPatterns.put(key, value.copy()));
        recoveryPatterns.clear();
        source.recoveryPatterns.forEach((key, value) ->
                recoveryPatterns.put(key, value.copy()));
        catalogGenerationId = source.catalogGenerationId;
        originUUID = source.originUUID;
        lastImport = new ImportStamp(source.configUUID, source.revision, digest);
        if (patternsChanged) incrementPatternRevision();
        incrementRevision();
        return true;
    }

    @Nonnull
    public String contentDigest() {
        return sha256(writeContent().toString());
    }

    @Nonnull
    public NBTTagCompound write() {
        if (runtimeBook != null) throw new IllegalStateException("Runtime Workbench views must not be persisted");
        NBTTagCompound data = writeContent();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "configUUID", configUUID);
        QIOProcessingNbt.writeUUID(data, "originUUID", originUUID);
        data.setLong("revision", revision);
        data.setLong("patternRevision", patternRevision);
        data.setString("catalogGenerationId", catalogGenerationId);
        data.setBoolean("hasLastImport", lastImport != null);
        if (lastImport != null) {
            data.setTag("lastImport", lastImport.write());
        }
        return data;
    }

    private NBTTagCompound writeContent() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("catalogGenerationId", catalogGenerationId);
        NBTTagList storedProducts = new NBTTagList();
        productOrders.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = new NBTTagCompound();
            stored.setString("productKey", entry.getKey());
            stored.setTag("recipeOrder", writeStrings(entry.getValue()));
            storedProducts.appendTag(stored);
        });
        data.setTag("productOrders", storedProducts);
        NBTTagList storedRecipes = new NBTTagList();
        recipeOverrides.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getValue().write();
            stored.setString("recipeId", entry.getKey());
            storedRecipes.appendTag(stored);
        });
        data.setTag("recipeOverrides", storedRecipes);
        // Build one deterministic palette for the whole frequency. Large packs commonly reuse
        // the same stacks across hundreds of encoded patterns; storing each stack per pattern
        // was the dominant source of frequency NBT growth.
        Map<String, Integer> paletteIds = new LinkedHashMap<>();
        List<Stack> palette = new ArrayList<>();
        List<EncodedPattern> allPatterns = new ArrayList<>(encodedPatterns.values());
        allPatterns.addAll(recoveryPatterns.values());
        allPatterns.sort(Comparator.comparing(pattern -> pattern.getRecipeId().toString()));
        allPatterns.forEach(pattern -> {
            for (Stack stack : pattern.grid) {
                if (stack.isEmpty()) continue;
                String key = paletteKey(stack);
                if (!paletteIds.containsKey(key)) {
                    paletteIds.put(key, palette.size());
                    palette.add(stack);
                }
            }
        });
        NBTTagList storedPalette = new NBTTagList();
        palette.forEach(stack -> storedPalette.appendTag(
                stack.write()));
        data.setTag("patternPalette", storedPalette);
        NBTTagList storedPatterns = new NBTTagList();
        encodedPatterns.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getValue().write(paletteIds);
            stored.setString("recipeId", entry.getKey());
            storedPatterns.appendTag(stored);
        });
        data.setTag("encodedPatterns", storedPatterns);
        NBTTagList storedRecovery = new NBTTagList();
        recoveryPatterns.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getValue().write(paletteIds);
            stored.setString("recipeId", entry.getKey());
            storedRecovery.appendTag(stored);
        });
        data.setTag("recoveryPatterns", storedRecovery);
        return data;
    }

    private static String paletteKey(Stack stack) {
        return stack.getKey();
    }

    @Nonnull
    public static QIOWorkbenchConfiguration read(@Nonnull NBTTagCompound data)
            throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        try {
            if ((data.getInteger("schema") != SCHEMA_VERSION && data.getInteger("schema") != 4) ||
                    !data.hasKey("configUUID", NBT.TAG_STRING) ||
                    !data.hasKey("originUUID", NBT.TAG_STRING) ||
                    !data.hasKey("revision", NBT.TAG_LONG) ||
                    !data.hasKey("patternRevision", NBT.TAG_LONG) ||
                    !data.hasKey("catalogGenerationId", NBT.TAG_STRING) ||
                    !data.hasKey("patternPalette", NBT.TAG_LIST) ||
                    !data.hasKey("productOrders", NBT.TAG_LIST) ||
                    !data.hasKey("recipeOverrides", NBT.TAG_LIST) ||
                    !data.hasKey("encodedPatterns", NBT.TAG_LIST) ||
                    !data.hasKey("recoveryPatterns", NBT.TAG_LIST) ||
                    !data.hasKey("hasLastImport", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException(
                        "QIO workbench configuration is missing current-schema fields");
            }
            QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration(
                    QIOProcessingNbt.readUUID(data, "configUUID"));
            configuration.originUUID = QIOProcessingNbt.readUUID(data, "originUUID");
            configuration.revision = QIOProcessingNbt.requireNonNegative(
                    data.getLong("revision"), "workbenchConfigurationRevision");
            configuration.patternRevision = QIOProcessingNbt.requireNonNegative(
                    data.getLong("patternRevision"), "workbenchPatternRevision");
            configuration.catalogGenerationId = checkedHash(
                    data.getString("catalogGenerationId"), "catalogGenerationId");
            NBTTagList storedPalette = requireList(data, "patternPalette", NBT.TAG_COMPOUND);
            if (storedPalette.tagCount() > MAX_ENCODED_PATTERNS * MAX_INGREDIENTS_PER_RECIPE) {
                throw new QIOProcessingDataException("Workbench pattern palette is too large");
            }
            List<Stack> palette = new ArrayList<>(storedPalette.tagCount());
            for (int index = 0; index < storedPalette.tagCount(); index++) {
                Stack stack = data.getInteger("schema") == 4 ? Stack.capture(QIORecipeStackUtils.readForRecipeSelection(
                        storedPalette.getCompoundTagAt(index)), 1) : Stack.read(storedPalette.getCompoundTagAt(index));
                if (stack.isEmpty()) throw new QIOProcessingDataException(
                        "Workbench pattern palette contains an empty stack");
                palette.add(stack);
            }
            NBTTagList storedProducts = requireList(data, "productOrders",
                    NBT.TAG_COMPOUND);
            if (storedProducts.tagCount() > MAX_PRODUCT_OVERRIDES) {
                throw new QIOProcessingDataException("Too many QIO workbench product overrides");
            }
            for (int index = 0; index < storedProducts.tagCount(); index++) {
                NBTTagCompound stored = storedProducts.getCompoundTagAt(index);
                if (!stored.hasKey("productKey", NBT.TAG_STRING) ||
                        !stored.hasKey("recipeOrder", NBT.TAG_LIST)) {
                    throw new QIOProcessingDataException("Incomplete workbench product override");
                }
                String productKey = checkedHash(stored.getString("productKey"), "productKey");
                List<String> order = readStrings(requireList(stored, "recipeOrder",
                        NBT.TAG_STRING), MAX_RECIPES_PER_PRODUCT, false);
                if (order.isEmpty() || configuration.productOrders.put(productKey, order) != null) {
                    throw new QIOProcessingDataException("Duplicate or empty workbench product override");
                }
            }
            NBTTagList storedRecipes = requireList(data, "recipeOverrides",
                    NBT.TAG_COMPOUND);
            if (storedRecipes.tagCount() > MAX_RECIPE_OVERRIDES) {
                throw new QIOProcessingDataException("Too many QIO workbench recipe overrides");
            }
            for (int index = 0; index < storedRecipes.tagCount(); index++) {
                NBTTagCompound stored = storedRecipes.getCompoundTagAt(index);
                if (!stored.hasKey("recipeId", NBT.TAG_STRING)) {
                    throw new QIOProcessingDataException("Workbench recipe override has no ID");
                }
                String recipeId = checkedRecipeId(stored.getString("recipeId"));
                RecipeOverride override = RecipeOverride.read(stored);
                if (override.isDefault() ||
                        configuration.recipeOverrides.put(recipeId, override) != null) {
                    throw new QIOProcessingDataException("Duplicate or empty workbench recipe override");
                }
            }
            NBTTagList storedPatterns = requireList(data, "encodedPatterns", NBT.TAG_COMPOUND);
            if (storedPatterns.tagCount() > MAX_ENCODED_PATTERNS) {
                throw new QIOProcessingDataException("Too many QIO workbench encoded patterns");
            }
            for (int index = 0; index < storedPatterns.tagCount(); index++) {
                NBTTagCompound stored = storedPatterns.getCompoundTagAt(index);
                if (!stored.hasKey("recipeId", NBT.TAG_STRING)) {
                    throw new QIOProcessingDataException("Workbench encoded pattern has no ID");
                }
                String recipeId = checkedRecipeId(stored.getString("recipeId"));
                EncodedPattern pattern = EncodedPattern.read(stored, recipeId, palette);
                if (configuration.encodedPatterns.put(recipeId, pattern) != null) {
                    throw new QIOProcessingDataException("Duplicate workbench encoded pattern");
                }
            }
            NBTTagList storedRecovery = requireList(data, "recoveryPatterns", NBT.TAG_COMPOUND);
            if (storedRecovery.tagCount() > MAX_ENCODED_PATTERNS) {
                throw new QIOProcessingDataException("Too many QIO workbench recovery patterns");
            }
            for (int index = 0; index < storedRecovery.tagCount(); index++) {
                NBTTagCompound stored = storedRecovery.getCompoundTagAt(index);
                if (!stored.hasKey("recipeId", NBT.TAG_STRING)) {
                    throw new QIOProcessingDataException("Workbench recovery pattern has no ID");
                }
                String recipeId = checkedRecipeId(stored.getString("recipeId"));
                EncodedPattern pattern = EncodedPattern.read(stored, recipeId, palette);
                if (configuration.recoveryPatterns.put(recipeId, pattern) != null ||
                        configuration.encodedPatterns.containsKey(recipeId)) {
                    throw new QIOProcessingDataException("Duplicate workbench recovery pattern");
                }
            }
            boolean hasLastImport = data.getBoolean("hasLastImport");
            if (hasLastImport != data.hasKey("lastImport") || hasLastImport &&
                    !data.hasKey("lastImport", NBT.TAG_COMPOUND)) {
                throw new QIOProcessingDataException("Workbench import marker is incomplete");
            }
            if (hasLastImport) {
                configuration.lastImport = ImportStamp.read(data.getCompoundTag("lastImport"));
            }
            return configuration;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO workbench configuration", e);
        }
    }

    @Nullable
    private RecipeOverride currentRecipeOverride(String recipeId, String signature) {
        RecipeOverride override = recipeOverrides.get(checkedRecipeId(recipeId));
        return override != null && override.signature.equals(
                checkedHash(signature, "recipeSignature")) ? override : null;
    }

    private RecipeOverride mutableRecipe(String recipeId, String signature) {
        RecipeOverride existing = recipeOverrides.get(recipeId);
        if (existing != null && existing.signature.equals(signature)) {
            return existing;
        }
        if (existing == null && recipeOverrides.size() >= MAX_RECIPE_OVERRIDES) {
            throw new IllegalStateException("QIO workbench recipe override limit reached");
        }
        RecipeOverride replacement = new RecipeOverride(signature);
        recipeOverrides.put(recipeId, replacement);
        return replacement;
    }

    private void cleanupIngredient(String recipeId, int slot, RecipeOverride recipe,
                                   IngredientOverride ingredient, List<String> defaults) {
        ingredient.disabled.retainAll(new HashSet<>(defaults));
        if (!ingredient.order.isEmpty()) {
            List<String> merged = mergeOrder(ingredient.order, defaults);
            ingredient.order.clear();
            if (!merged.equals(defaults) || !ingredient.disabled.isEmpty()) {
                ingredient.order.addAll(merged);
            }
        }
        if (ingredient.isDefault()) {
            recipe.ingredients.remove(slot);
        }
        removeDefaultRecipe(recipeId, recipe);
    }

    private void removeDefaultRecipe(String recipeId, RecipeOverride recipe) {
        if (recipe.isDefault()) {
            recipeOverrides.remove(recipeId);
        }
    }

    private boolean applyAtomicBatch(Consumer<QIOWorkbenchConfiguration> mutation) {
        QIOWorkbenchConfiguration working = copy();
        mutation.accept(working);
        if (working.revision == revision) return false;
        boolean patternsChanged = working.patternRevision != patternRevision;
        productOrders.clear();
        working.productOrders.forEach((key, value) ->
                productOrders.put(key, new ArrayList<>(value)));
        recipeOverrides.clear();
        working.recipeOverrides.forEach((key, value) ->
                recipeOverrides.put(key, value.copy()));
        encodedPatterns.clear();
        working.encodedPatterns.forEach((key, value) ->
                encodedPatterns.put(key, value.copy()));
        recoveryPatterns.clear();
        working.recoveryPatterns.forEach((key, value) ->
                recoveryPatterns.put(key, value.copy()));
        catalogGenerationId = working.catalogGenerationId;
        if (patternsChanged) incrementPatternRevision();
        incrementRevision();
        return true;
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO workbench configuration revision exhausted");
        }
        revision++;
    }

    private void incrementPatternRevision() {
        if (patternRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO workbench pattern revision exhausted");
        }
        patternRevision++;
    }

    private static <K> void putOrder(Map<K, List<String>> target, K key, List<String> order,
                                     List<String> defaults) {
        if (order.equals(defaults)) {
            target.remove(key);
        } else {
            target.put(key, new ArrayList<>(order));
        }
    }

    private static List<String> mergeOrder(@Nullable List<String> stored,
                                           List<String> defaults) {
        if (stored == null || stored.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(defaults));
        }
        Set<String> allowed = new LinkedHashSet<>(defaults);
        List<String> merged = new ArrayList<>(defaults.size());
        for (String value : stored) {
            if (allowed.remove(value)) {
                merged.add(value);
            }
        }
        for (String value : defaults) {
            if (allowed.remove(value)) {
                merged.add(value);
            }
        }
        return Collections.unmodifiableList(merged);
    }

    private static List<String> checkedRecipeIds(Collection<String> recipeIds) {
        Objects.requireNonNull(recipeIds, "recipeIds");
        if (recipeIds.size() > MAX_RECIPES_PER_PRODUCT) {
            throw new IllegalArgumentException("Too many workbench recipes for one product");
        }
        List<String> checked = new ArrayList<>(recipeIds.size());
        Set<String> unique = new HashSet<>();
        for (String recipeId : recipeIds) {
            String value = checkedRecipeId(recipeId);
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate workbench recipe ID");
            }
            checked.add(value);
        }
        return Collections.unmodifiableList(checked);
    }

    private static List<String> checkedCandidateIds(Collection<String> candidateIds) {
        Objects.requireNonNull(candidateIds, "candidateIds");
        if (candidateIds.isEmpty() || candidateIds.size() > MAX_CANDIDATES_PER_INGREDIENT) {
            throw new IllegalArgumentException("Workbench ingredient candidate count is invalid");
        }
        List<String> checked = new ArrayList<>(candidateIds.size());
        Set<String> unique = new HashSet<>();
        for (String candidateId : candidateIds) {
            String value = checkedHash(candidateId, "candidateId");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate workbench ingredient candidate");
            }
            checked.add(value);
        }
        return Collections.unmodifiableList(checked);
    }

    private static String checkedRecipeId(String recipeId) {
        String checked = Objects.requireNonNull(recipeId, "recipeId").trim();
        if (checked.isEmpty() || checked.length() > MAX_RECIPE_ID_LENGTH) {
            throw new IllegalArgumentException("Workbench recipe ID has an invalid length");
        }
        return checked;
    }

    private static String checkedHash(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim().toLowerCase(
                java.util.Locale.ROOT);
        if (checked.length() != HASH_LENGTH ||
                !checked.chars().allMatch(character -> character >= '0' && character <= '9' ||
                        character >= 'a' && character <= 'f')) {
            throw new IllegalArgumentException(name + " is not a SHA-256 identifier");
        }
        return checked;
    }

    private static int checkedSlot(int slot) {
        if (slot < 0 || slot >= MAX_INGREDIENTS_PER_RECIPE) {
            throw new IllegalArgumentException("Workbench ingredient slot is invalid");
        }
        return slot;
    }

    private static NBTTagList writeStrings(Collection<String> values) {
        NBTTagList list = new NBTTagList();
        values.forEach(value -> list.appendTag(new NBTTagString(value)));
        return list;
    }

    private static List<String> readStrings(NBTTagList values, int maximum,
                                            boolean hashes) throws QIOProcessingDataException {
        if (values.tagCount() > maximum) {
            throw new QIOProcessingDataException("QIO workbench string list is too large");
        }
        List<String> result = new ArrayList<>(values.tagCount());
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < values.tagCount(); index++) {
            String value = hashes ? checkedHash(values.getStringTagAt(index), "candidateId") :
                    checkedRecipeId(values.getStringTagAt(index));
            if (!unique.add(value)) {
                throw new QIOProcessingDataException("Duplicate QIO workbench list entry");
            }
            result.add(value);
        }
        return result;
    }

    private static NBTTagList requireList(NBTTagCompound data, String key,
                                          int elementType) throws QIOProcessingDataException {
        if (!data.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException("Missing QIO workbench list " + key);
        }
        NBTTagList list = (NBTTagList) data.getTag(key);
        if (!list.isEmpty() && list.getTagType() != elementType) {
            throw new QIOProcessingDataException("QIO workbench list " + key +
                    " has the wrong element type");
        }
        return list;
    }

    private static String sha256(String value) {
        return QIOHashing.sha256(value);
    }

    public static final class LastCandidateException extends IllegalStateException {

        public LastCandidateException() {
            super("At least one workbench ingredient candidate must remain enabled");
        }
    }

    private static final class RecipeOverride {

        private final String signature;
        private boolean enabled = true;
        private final Map<Integer, IngredientOverride> ingredients = new LinkedHashMap<>();

        private RecipeOverride(String signature) {
            this.signature = checkedHash(signature, "recipeSignature");
        }

        private boolean isDefault() {
            return enabled && ingredients.isEmpty();
        }

        private RecipeOverride copy() {
            RecipeOverride copy = new RecipeOverride(signature);
            copy.enabled = enabled;
            ingredients.forEach((slot, ingredient) ->
                    copy.ingredients.put(slot, ingredient.copy()));
            return copy;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("signature", signature);
            data.setBoolean("enabled", enabled);
            NBTTagList storedIngredients = new NBTTagList();
            ingredients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                NBTTagCompound stored = entry.getValue().write();
                stored.setInteger("slot", entry.getKey());
                storedIngredients.appendTag(stored);
            });
            data.setTag("ingredients", storedIngredients);
            return data;
        }

        private static RecipeOverride read(NBTTagCompound data)
                throws QIOProcessingDataException {
            if (!data.hasKey("signature", NBT.TAG_STRING) ||
                    !data.hasKey("enabled", NBT.TAG_BYTE) ||
                    !data.hasKey("ingredients", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete workbench recipe override");
            }
            RecipeOverride override = new RecipeOverride(data.getString("signature"));
            override.enabled = data.getBoolean("enabled");
            NBTTagList storedIngredients = requireList(data, "ingredients",
                    NBT.TAG_COMPOUND);
            if (storedIngredients.tagCount() > MAX_INGREDIENTS_PER_RECIPE) {
                throw new QIOProcessingDataException("Too many workbench ingredient overrides");
            }
            for (int index = 0; index < storedIngredients.tagCount(); index++) {
                NBTTagCompound stored = storedIngredients.getCompoundTagAt(index);
                if (!stored.hasKey("slot", NBT.TAG_INT)) {
                    throw new QIOProcessingDataException("Workbench ingredient override has no slot");
                }
                int slot = checkedSlot(stored.getInteger("slot"));
                IngredientOverride ingredient = IngredientOverride.read(stored);
                if (ingredient.isDefault() || override.ingredients.put(slot, ingredient) != null) {
                    throw new QIOProcessingDataException(
                            "Duplicate or empty workbench ingredient override");
                }
            }
            return override;
        }
    }

    private static final class IngredientOverride {

        private final List<String> order = new ArrayList<>();
        private final Set<String> disabled = new LinkedHashSet<>();

        private boolean isDefault() {
            return order.isEmpty() && disabled.isEmpty();
        }

        private IngredientOverride copy() {
            IngredientOverride copy = new IngredientOverride();
            copy.order.addAll(order);
            copy.disabled.addAll(disabled);
            return copy;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("candidateOrder", writeStrings(order));
            List<String> sortedDisabled = new ArrayList<>(disabled);
            Collections.sort(sortedDisabled);
            data.setTag("disabledCandidates", writeStrings(sortedDisabled));
            return data;
        }

        private static IngredientOverride read(NBTTagCompound data)
                throws QIOProcessingDataException {
            if (!data.hasKey("candidateOrder", NBT.TAG_LIST) ||
                    !data.hasKey("disabledCandidates", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete workbench ingredient override");
            }
            IngredientOverride override = new IngredientOverride();
            override.order.addAll(readStrings(requireList(data, "candidateOrder",
                    NBT.TAG_STRING), MAX_CANDIDATES_PER_INGREDIENT, true));
            override.disabled.addAll(readStrings(requireList(data, "disabledCandidates",
                    NBT.TAG_STRING), MAX_CANDIDATES_PER_INGREDIENT, true));
            if (!override.disabled.isEmpty() && (override.order.isEmpty() ||
                    !override.order.containsAll(override.disabled) ||
                    override.disabled.size() >= override.order.size())) {
                throw new QIOProcessingDataException(
                        "Workbench ingredient override disables every candidate or has no full order");
            }
            return override;
        }
    }

    private static final class ImportStamp {

        private final UUID sourceConfigUUID;
        private final long sourceRevision;
        private final String sourceDigest;

        private ImportStamp(UUID sourceConfigUUID, long sourceRevision, String sourceDigest) {
            this.sourceConfigUUID = Objects.requireNonNull(sourceConfigUUID,
                    "sourceConfigUUID");
            if (sourceRevision < 0) {
                throw new IllegalArgumentException("Source workbench revision is negative");
            }
            this.sourceRevision = sourceRevision;
            this.sourceDigest = checkedHash(sourceDigest, "sourceDigest");
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(data, "sourceConfigUUID", sourceConfigUUID);
            data.setLong("sourceRevision", sourceRevision);
            data.setString("sourceDigest", sourceDigest);
            return data;
        }

        private static ImportStamp read(NBTTagCompound data)
                throws QIOProcessingDataException {
            if (!data.hasKey("sourceConfigUUID", NBT.TAG_STRING) ||
                    !data.hasKey("sourceRevision", NBT.TAG_LONG) ||
                    !data.hasKey("sourceDigest", NBT.TAG_STRING)) {
                throw new QIOProcessingDataException("Incomplete workbench import marker");
            }
            return new ImportStamp(QIOProcessingNbt.readUUID(data, "sourceConfigUUID"),
                    QIOProcessingNbt.requireNonNegative(data.getLong("sourceRevision"),
                            "sourceWorkbenchRevision"), data.getString("sourceDigest"));
        }
    }

    /** Immutable, frequency-local input for one manually encoded workbench recipe. */
    public static final class EncodedPattern {
        private static final int SCHEMA_VERSION = 3;
        private final UUID patternUUID;
        private final ResourceLocation recipeId;
        private final String recipeSignature;
        private final PortableResourceDescriptor output;
        private final long outputAmount;
        private final PatternLayout layout;
        private final List<Stack> grid;
        @Nullable
        private final QIOVerifiedWorkbenchPattern verified;

        public EncodedPattern(@Nonnull UUID patternUUID, @Nonnull ResourceLocation recipeId,
                              @Nonnull String recipeSignature, @Nonnull PortableResourceDescriptor output,
                              long outputAmount, @Nonnull List<ItemStack> grid) {
            this(patternUUID, recipeId, recipeSignature, output, outputAmount, PatternLayout.ORDERED_UNKNOWN, grid);
        }

        public EncodedPattern(@Nonnull UUID patternUUID, @Nonnull ResourceLocation recipeId,
                              @Nonnull String recipeSignature, @Nonnull PortableResourceDescriptor output,
                              long outputAmount, @Nonnull PatternLayout layout, @Nonnull List<ItemStack> grid) {
            this(patternUUID, recipeId, recipeSignature, output, outputAmount, layout, freeze(grid, layout), null);
        }

        private EncodedPattern(UUID patternUUID, ResourceLocation recipeId, String recipeSignature,
                               PortableResourceDescriptor output, long outputAmount, PatternLayout layout,
                               List<Stack> grid, @Nullable QIOVerifiedWorkbenchPattern verified) {
            this.patternUUID = Objects.requireNonNull(patternUUID, "patternUUID");
            this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
            this.recipeSignature = checkedHash(recipeSignature, "recipeSignature");
            this.output = Objects.requireNonNull(output, "output");
            this.layout = Objects.requireNonNull(layout, "layout");
            if (outputAmount <= 0 || grid.size() != 9 || grid.stream().allMatch(Stack::isEmpty)) {
                throw new IllegalArgumentException("Invalid workbench pattern grid/output");
            }
            this.outputAmount = outputAmount;
            this.grid = Collections.unmodifiableList(new ArrayList<>(grid));
            this.verified = verified;
        }

        public static EncodedPattern fromVerified(QIOVerifiedWorkbenchPattern pattern) {
            return fromVerified(pattern, pattern.getDeclarationSignature());
        }

        /** Temporary binding to a legacy planning snapshot; the verified record keeps the new declaration signature. */
        public static EncodedPattern fromVerified(QIOVerifiedWorkbenchPattern pattern, String planningSignature) {
            return new EncodedPattern(pattern.getId(), pattern.getRecipeId(), pattern.getDeclarationSignature(),
                    pattern.getOutput().getResource(), pattern.getOutput().getAmount(),
                    PatternLayout.valueOf(pattern.getLayout().name()), pattern.getGrid(), pattern).bindPlanner(planningSignature);
        }

        private EncodedPattern bindPlanner(String signature) {
            return recipeSignature.equals(signature) ? this : new EncodedPattern(patternUUID, recipeId, signature,
                    output, outputAmount, layout, grid, verified);
        }

        /** Pure declaration adapter: this is an encoding candidate, not a validation credential. */
        public static EncodedPattern fromFrozen(UUID id, ResourceLocation recipe, String signature,
                                                PortableResourceDescriptor output, long amount, PatternLayout layout, List<Stack> grid) {
            List<Stack> packed = new ArrayList<>(9);
            for (Stack stack : grid) if (layout != PatternLayout.SHAPELESS || !stack.isEmpty()) packed.add(stack);
            while (packed.size() < 9) packed.add(Stack.EMPTY);
            return new EncodedPattern(id, recipe, signature, output, amount, layout, packed, null);
        }

        private static List<Stack> freeze(List<ItemStack> source, PatternLayout layout) {
            if (source == null || source.size() != 9) throw new IllegalArgumentException("Expected nine slots");
            List<Stack> frozen = new ArrayList<>(9);
            for (ItemStack item : source) {
                Stack stack = Stack.capture(item, 1);
                if (layout != PatternLayout.SHAPELESS || !stack.isEmpty()) frozen.add(stack);
            }
            while (frozen.size() < 9) frozen.add(Stack.EMPTY);
            return frozen;
        }

        @Nonnull
        public UUID getPatternUUID() {
            return patternUUID;
        }

        @Nonnull
        public ResourceLocation getRecipeId() {
            return recipeId;
        }

        @Nonnull
        public String getRecipeSignature() {
            return recipeSignature;
        }

        @Nonnull
        public PortableResourceDescriptor getOutput() {
            return output;
        }

        public long getOutputAmount() {
            return outputAmount;
        }

        @Nonnull
        public PatternLayout getLayout() {
            return layout;
        }

        @Nullable
        public QIOVerifiedWorkbenchPattern getVerified() {
            return verified;
        }

        @Nonnull
        public List<Stack> getFrozenGrid() {
            return grid;
        }

        /** Forge/UI boundary only; copying the pattern itself never reconstructs capabilities. */
        @Nonnull
        public List<ItemStack> getGrid() {
            List<ItemStack> stacks = new ArrayList<>(9);
            for (Stack stack : grid) stacks.add(stack.thaw());
            return Collections.unmodifiableList(stacks);
        }

        private EncodedPattern copy() {
            return this;
        }

        private EncodedPattern withUUID(UUID uuid) {
            return verified == null ? new EncodedPattern(uuid, recipeId, recipeSignature, output, outputAmount, layout, grid, null) :
                    fromVerified(verified.withId(uuid), recipeSignature);
        }

        public EncodedPattern withLayout(@Nonnull PatternLayout nextLayout) {
            if (layout == nextLayout) return this;
            if (verified != null) throw new IllegalArgumentException("Cannot reinterpret a validated physical grid");
            List<Stack> reordered = new ArrayList<>();
            for (Stack stack : grid)
                if (nextLayout != PatternLayout.SHAPELESS || !stack.isEmpty()) reordered.add(stack);
            while (reordered.size() < 9) reordered.add(Stack.EMPTY);
            return new EncodedPattern(patternUUID, recipeId, recipeSignature, output, outputAmount,
                    Objects.requireNonNull(nextLayout, "nextLayout"), reordered, null);
        }

        private boolean sameRecipeContent(EncodedPattern other) {
            if (!recipeId.equals(other.recipeId) || layout != other.layout || !recipeSignature.equals(other.recipeSignature) ||
                    !output.equals(other.output) || outputAmount != other.outputAmount) return false;
            if (!Objects.equals(verified == null ? null : verified.getContentSignature(),
                    other.verified == null ? null : other.verified.getContentSignature())) return false;
            for (int slot = 0; slot < 9; slot++)
                if (!grid.get(slot).getKey().equals(other.grid.get(slot).getKey())) return false;
            return true;
        }

        private NBTTagCompound write(Map<String, Integer> paletteIds) {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("patternSchema", SCHEMA_VERSION);
            QIOProcessingNbt.writeUUID(data, "patternUUID", patternUUID);
            data.setString("recipeSignature", recipeSignature);
            data.setString("layout", layout.name());
            data.setTag("output", output.write());
            data.setLong("outputAmount", outputAmount);
            if (verified != null) {
                data.setTag("verified", verified.write());
                data.setBoolean("plannerBinding", !recipeSignature.equals(verified.getDeclarationSignature()));
            }
            NBTTagList entries = new NBTTagList();
            for (int slot = 0; slot < 9; slot++) {
                Stack stack = grid.get(slot);
                if (stack.isEmpty()) continue;
                Integer index = paletteIds.get(paletteKey(stack));
                if (index == null) throw new IllegalStateException("Pattern stack absent from palette");
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger(layout == PatternLayout.SHAPELESS ? "occurrence" : "slot", slot);
                entry.setInteger("paletteIndex", index);
                entries.appendTag(entry);
            }
            data.setTag("entries", entries);
            return data;
        }

        private static EncodedPattern read(NBTTagCompound data, String recipeId, List<Stack> palette)
                throws QIOProcessingDataException {
            try {
                int schema = data.getInteger("patternSchema");
                if ((schema != SCHEMA_VERSION && schema != 2) || !data.hasKey("entries", NBT.TAG_LIST)) {
                    throw new IllegalArgumentException("Unsupported workbench pattern");
                }
                PatternLayout layout = PatternLayout.valueOf(data.getString("layout"));
                NBTTagList entries = requireList(data, "entries", NBT.TAG_COMPOUND);
                if (entries.tagCount() < 1 || entries.tagCount() > 9)
                    throw new IllegalArgumentException("Invalid entries");
                List<Stack> grid = new ArrayList<>(Collections.nCopies(9, Stack.EMPTY));
                for (int index = 0; index < entries.tagCount(); index++) {
                    NBTTagCompound entry = entries.getCompoundTagAt(index);
                    String position = layout == PatternLayout.SHAPELESS ? "occurrence" : "slot";
                    if (!entry.hasKey(position, NBT.TAG_INT) || !entry.hasKey("paletteIndex", NBT.TAG_INT)) {
                        throw new IllegalArgumentException("Missing pattern position");
                    }
                    int slot = entry.getInteger(position);
                    int paletteIndex = entry.getInteger("paletteIndex");
                    if (slot < 0 || slot >= 9 || !grid.get(slot).isEmpty() || paletteIndex < 0 || paletteIndex >= palette.size()) {
                        throw new IllegalArgumentException("Invalid pattern position");
                    }
                    grid.set(slot, palette.get(paletteIndex));
                }
                if (layout == PatternLayout.SHAPELESS) for (int slot = 0; slot < entries.tagCount(); slot++) {
                    if (grid.get(slot).isEmpty()) throw new IllegalArgumentException("Noncontiguous occurrences");
                }
                UUID uuid = QIOProcessingNbt.readUUID(data, "patternUUID");
                EncodedPattern raw = new EncodedPattern(uuid, new ResourceLocation(recipeId), data.getString("recipeSignature"),
                        PortableResourceDescriptor.read(data.getCompoundTag("output")), data.getLong("outputAmount"), layout, grid, null);
                if (!data.hasKey("verified", NBT.TAG_COMPOUND)) return raw;
                QIOVerifiedWorkbenchPattern verified = QIOVerifiedWorkbenchPattern.read(data.getCompoundTag("verified"));
                EncodedPattern exact = fromVerified(verified, data.getBoolean("plannerBinding") ? raw.recipeSignature : verified.getDeclarationSignature());
                if (!uuid.equals(exact.patternUUID) || !raw.sameRecipeContent(new EncodedPattern(exact.patternUUID,
                        exact.recipeId, exact.recipeSignature, exact.output, exact.outputAmount, exact.layout, exact.grid, null))) {
                    throw new IllegalArgumentException("Verified payload differs from encoded pattern");
                }
                return exact;
            } catch (RuntimeException error) {
                throw new QIOProcessingDataException("Invalid workbench encoded pattern", error);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EncodedPattern pattern && patternUUID.equals(pattern.patternUUID) && sameRecipeContent(pattern);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(patternUUID, recipeId, recipeSignature, output, outputAmount, layout,
                    verified == null ? null : verified.getContentSignature());
            for (Stack stack : grid) result = 31 * result + stack.getKey().hashCode();
            return result;
        }
    }
}
