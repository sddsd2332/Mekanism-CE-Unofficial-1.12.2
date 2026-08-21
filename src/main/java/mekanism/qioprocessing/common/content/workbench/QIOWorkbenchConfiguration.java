package mekanism.qioprocessing.common.content.workbench;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Sparse, frequency-local workbench route and ingredient preferences. */
/**
 * QIO 处理模块中的 QIOWorkbenchConfiguration 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchConfiguration {

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_PRODUCT_OVERRIDES = 65_536;
    private static final int MAX_RECIPE_OVERRIDES = 65_536;
    private static final int MAX_RECIPES_PER_PRODUCT = 65_536;
    private static final int MAX_INGREDIENTS_PER_RECIPE = 9;
    private static final int MAX_CANDIDATES_PER_INGREDIENT = 64;
    private static final int MAX_RECIPE_ID_LENGTH = 256;
    private static final int HASH_LENGTH = 64;
    private static final int MAX_ENCODED_PATTERNS = 65_536;

    private final UUID configUUID;
    private UUID originUUID;
    private long revision;
    private long patternRevision;
    private final Map<String, List<String>> productOrders = new LinkedHashMap<>();
    private final Map<String, RecipeOverride> recipeOverrides = new LinkedHashMap<>();
    /** Only these recipes are exposed to the QIO planner for this frequency. */
    private final Map<String, EncodedPattern> encodedPatterns = new LinkedHashMap<>();
    @Nullable private ImportStamp lastImport;

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
        copy.revision = revision;
        copy.patternRevision = patternRevision;
        productOrders.forEach((key, value) ->
              copy.productOrders.put(key, new ArrayList<>(value)));
        recipeOverrides.forEach((key, value) ->
              copy.recipeOverrides.put(key, value.copy()));
        encodedPatterns.forEach((key, value) ->
              copy.encodedPatterns.put(key, value.copy()));
        if (lastImport != null) {
            copy.lastImport = new ImportStamp(lastImport.sourceConfigUUID,
                  lastImport.sourceRevision, lastImport.sourceDigest);
        }
        return copy;
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
        if (productOrders.isEmpty() && recipeOverrides.isEmpty() && encodedPatterns.isEmpty()) {
            return false;
        }
        boolean hadPatterns = !encodedPatterns.isEmpty();
        productOrders.clear();
        recipeOverrides.clear();
        encodedPatterns.clear();
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
        NBTTagCompound data = writeContent();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "configUUID", configUUID);
        QIOProcessingNbt.writeUUID(data, "originUUID", originUUID);
        data.setLong("revision", revision);
        data.setLong("patternRevision", patternRevision);
        data.setBoolean("hasLastImport", lastImport != null);
        if (lastImport != null) {
            data.setTag("lastImport", lastImport.write());
        }
        return data;
    }

    private NBTTagCompound writeContent() {
        NBTTagCompound data = new NBTTagCompound();
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
        NBTTagList storedPatterns = new NBTTagList();
        encodedPatterns.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound stored = entry.getValue().write();
            stored.setString("recipeId", entry.getKey());
            storedPatterns.appendTag(stored);
        });
        data.setTag("encodedPatterns", storedPatterns);
        return data;
    }

    @Nonnull
    public static QIOWorkbenchConfiguration read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("configUUID", NBT.TAG_STRING) ||
                !data.hasKey("originUUID", NBT.TAG_STRING) ||
                !data.hasKey("revision", NBT.TAG_LONG) ||
                !data.hasKey("patternRevision", NBT.TAG_LONG) ||
                !data.hasKey("productOrders", NBT.TAG_LIST) ||
                !data.hasKey("recipeOverrides", NBT.TAG_LIST) ||
                !data.hasKey("encodedPatterns", NBT.TAG_LIST) ||
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
                EncodedPattern pattern = EncodedPattern.read(stored, recipeId);
                if (configuration.encodedPatterns.put(recipeId, pattern) != null) {
                    throw new QIOProcessingDataException("Duplicate workbench encoded pattern");
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

        private static final int SCHEMA_VERSION = 1;
        private final UUID patternUUID;
        private final ResourceLocation recipeId;
        private final String recipeSignature;
        private final PortableResourceDescriptor output;
        private final long outputAmount;
        private final List<ItemStack> grid;

        public EncodedPattern(@Nonnull UUID patternUUID, @Nonnull ResourceLocation recipeId,
              @Nonnull String recipeSignature, @Nonnull PortableResourceDescriptor output,
              long outputAmount, @Nonnull List<ItemStack> grid) {
            this.patternUUID = Objects.requireNonNull(patternUUID, "patternUUID");
            this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
            this.recipeSignature = checkedHash(recipeSignature, "recipeSignature");
            this.output = Objects.requireNonNull(output, "output");
            if (outputAmount <= 0) {
                throw new IllegalArgumentException("Workbench encoded output amount must be positive");
            }
            this.outputAmount = outputAmount;
            if (grid == null || grid.size() != 9) {
                throw new IllegalArgumentException("Workbench encoded pattern must contain nine slots");
            }
            List<ItemStack> copy = new ArrayList<>(9);
            boolean hasInput = false;
            for (ItemStack stack : grid) {
                ItemStack checked = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
                if (!checked.isEmpty()) {
                    checked.setCount(1);
                    hasInput = true;
                }
                copy.add(checked);
            }
            if (!hasInput) {
                throw new IllegalArgumentException("Workbench encoded pattern has no inputs");
            }
            this.grid = Collections.unmodifiableList(copy);
        }

        @Nonnull public UUID getPatternUUID() { return patternUUID; }
        @Nonnull public ResourceLocation getRecipeId() { return recipeId; }
        @Nonnull public String getRecipeSignature() { return recipeSignature; }
        @Nonnull public PortableResourceDescriptor getOutput() { return output; }
        public long getOutputAmount() { return outputAmount; }
        @Nonnull public List<ItemStack> getGrid() {
            List<ItemStack> copy = new ArrayList<>(grid.size());
            grid.forEach(stack -> copy.add(stack.copy()));
            return Collections.unmodifiableList(copy);
        }

        private EncodedPattern copy() {
            return new EncodedPattern(patternUUID, recipeId, recipeSignature, output,
                  outputAmount, grid);
        }

        private EncodedPattern withUUID(UUID uuid) {
            return new EncodedPattern(uuid, recipeId, recipeSignature, output, outputAmount, grid);
        }

        private boolean sameRecipeContent(EncodedPattern pattern) {
            if (!recipeId.equals(pattern.recipeId) ||
                !recipeSignature.equals(pattern.recipeSignature) || !output.equals(pattern.output) ||
                outputAmount != pattern.outputAmount || grid.size() != pattern.grid.size()) return false;
            for (int index = 0; index < grid.size(); index++) {
                if (!ItemStack.areItemStacksEqual(grid.get(index), pattern.grid.get(index))) return false;
            }
            return true;
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("patternSchema", SCHEMA_VERSION);
            QIOProcessingNbt.writeUUID(data, "patternUUID", patternUUID);
            data.setString("recipeSignature", recipeSignature);
            data.setTag("output", output.write());
            data.setLong("outputAmount", outputAmount);
            NBTTagList storedGrid = new NBTTagList();
            grid.forEach(stack -> storedGrid.appendTag(stack.isEmpty() ? new NBTTagCompound() :
                  stack.writeToNBT(new NBTTagCompound())));
            data.setTag("grid", storedGrid);
            return data;
        }

        private static EncodedPattern read(NBTTagCompound data, String recipeId)
              throws QIOProcessingDataException {
            if (data.getInteger("patternSchema") != SCHEMA_VERSION ||
                !data.hasKey("patternUUID", NBT.TAG_STRING) ||
                !data.hasKey("recipeSignature", NBT.TAG_STRING) ||
                !data.hasKey("output", NBT.TAG_COMPOUND) ||
                !data.hasKey("outputAmount", NBT.TAG_LONG) ||
                !data.hasKey("grid", NBT.TAG_LIST)) {
                throw new QIOProcessingDataException("Incomplete workbench encoded pattern");
            }
            NBTTagList storedGrid = requireList(data, "grid", NBT.TAG_COMPOUND);
            if (storedGrid.tagCount() != 9) {
                throw new QIOProcessingDataException("Workbench encoded pattern grid is not 3x3");
            }
            List<ItemStack> grid = new ArrayList<>(9);
            for (int index = 0; index < storedGrid.tagCount(); index++) {
                NBTTagCompound slot = storedGrid.getCompoundTagAt(index);
                grid.add(slot.isEmpty() ? ItemStack.EMPTY : new ItemStack(slot));
            }
            try {
                return new EncodedPattern(QIOProcessingNbt.readUUID(data, "patternUUID"),
                      new ResourceLocation(recipeId), data.getString("recipeSignature"),
                      PortableResourceDescriptor.read(data.getCompoundTag("output")),
                      data.getLong("outputAmount"), grid);
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid workbench encoded pattern", e);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof EncodedPattern pattern)) return false;
            return patternUUID.equals(pattern.patternUUID) && sameRecipeContent(pattern);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternUUID, recipeId, recipeSignature, output, outputAmount,
                  grid.toString());
        }
    }
}
