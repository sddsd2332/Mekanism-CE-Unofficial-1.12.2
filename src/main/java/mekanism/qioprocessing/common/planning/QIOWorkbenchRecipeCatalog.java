package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.recipe.RecipeCatalog;
import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import mekanism.qioprocessing.common.util.QIOHashing;
import mekanism.qioprocessing.common.workbench.WorkbenchRoute;
import mekanism.qioprocessing.common.workbench.WorkbenchSnapshot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Projects validated patterns into detached candidates and verifies selected concrete variants on demand.
 */

/**
 * QIO 处理模块中的 QIOWorkbenchRecipeCatalog 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchRecipeCatalog {

    public static final ResourceLocation PROVIDER_ID =
            new ResourceLocation(MekanismQIOProcessing.MODID, "workbench");
    public static final int DEFAULT_PATTERN_CACHE_SIZE = 4_096;
    public static final int MAX_BATCH_TARGETS =
            QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS;

    @FunctionalInterface
    public interface PriorityResolver {

        long priority(@Nonnull ResourceLocation recipeId);
    }

    /** Semantic class of one frozen ingredient matcher. */
    public enum MatcherKind {
        EMPTY,
        EXACT,
        ORE,
        WILDCARD,
        ANY_OF
    }

    /**
     * Describes which positions are semantically part of a frozen recipe.  The physical
     * 3x3 inventory is still materialized at the Forge boundary, but the catalog and cache do
     * not assign meaning to empty positions in a shapeless recipe.
     */
    public enum RecipeLayout {
        SHAPED,
        SHAPELESS,
        ORDERED_UNKNOWN
    }

    private QIOWorkbenchRecipeCatalog() {
    }

    @Nonnull
    public static String productKey(@Nonnull PortableResourceDescriptor output) {
        return sha256(Objects.requireNonNull(output, "output").toString());
    }

    /** Builds a catalog from patterns explicitly encoded in one frequency. */
    @Nonnull
    static Snapshot emptyPublished(String generationId) {
        CatalogSymbols symbols = CatalogSymbols.merge(Collections.emptyList());
        CatalogGeneration generation = new CatalogGeneration(symbols.itemTypes, symbols.ingredientVariants,
                new RecipeSkeletonTable(Collections.emptyList()), generationId, symbols.generationToken);
        return new Snapshot(Collections.emptyMap(), Collections.emptyList(), DEFAULT_PATTERN_CACHE_SIZE, generation);
    }

    /**
     * Projects concrete verified patterns which the old compiler cannot represent (full capability
     * state, shifted physical layouts, or implementations absent from its index). Existing flexible
     * compiled recipes remain unchanged. This bridge performs no Forge callbacks or global scan.
     */
    static Snapshot projectVerified(Snapshot source, Collection<QIOWorkbenchConfiguration.EncodedPattern> patterns,
                                    @Nullable QIOWorkbenchDeclarations declarations, int dimension, long epoch) {
        Map<ResourceLocation, QIOWorkbenchConfiguration.EncodedPattern> exact = new LinkedHashMap<>();
        for (QIOWorkbenchConfiguration.EncodedPattern pattern : patterns) {
            if (pattern.getVerified() == null) continue;
            exact.put(pattern.getRecipeId(), pattern);
        }
        if (exact.isEmpty()) return source;
        SymbolShard shard = new SymbolShard();
        shard.matchers.put(MatcherKey.EMPTY, MatcherSeed.empty());
        Map<ResourceLocation, List<MatcherSeed>> seeds = new LinkedHashMap<>();
        Map<ResourceLocation, List<IngredientChoice>> grids = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, QIOWorkbenchConfiguration.EncodedPattern> entry : exact.entrySet()) {
            mekanism.qioprocessing.common.content.workbench.QIOVerifiedWorkbenchPattern pattern = entry.getValue().getVerified();
            FrozenItemType output = new FrozenItemType(pattern.getOutput());
            shard.itemTypes.putIfAbsent(output.descriptor, output);
            List<MatcherSeed> slots = new ArrayList<>(9);
            List<IngredientChoice> grid = new ArrayList<>(9);
            boolean[] virtual = pattern.getVirtualSlots();
            QIOWorkbenchDeclarations.Recipe declaration = declarations == null ? null : declarations.get(entry.getKey());
            List<List<QIOWorkbenchDeclarations.Choice>> aligned = declaration == null ? null : declaration.slotsForGrid(pattern.getGrid());
            boolean substitute = declaration != null && declaration.allowsAutomaticDiscovery() &&
                    declaration.getSignature().equals(pattern.getDeclarationSignature()) &&
                    aligned != null;
            for (int slot = 0; slot < 9; slot++) {
                QIOWorkbenchDeclarations.Stack stack = pattern.getGrid().get(slot);
                IngredientChoice choice = stack.isEmpty() ? IngredientChoice.empty() :
                        new IngredientChoice(stack, pattern.getConsumedResource(slot), pattern.getConsumedAmount(slot), virtual[slot]);
                grid.add(choice);
                Map<String, IngredientChoice> alternatives = new LinkedHashMap<>();
                if (substitute) for (QIOWorkbenchDeclarations.Choice candidate : aligned.get(slot)) {
                    IngredientChoice alternative = candidate.getStack().isEmpty() ? IngredientChoice.empty() :
                            new IngredientChoice(candidate.getStack(), candidate.getResource(), candidate.getAmount(), candidate.isVirtualFluid());
                    alternatives.putIfAbsent(alternative.candidateId(), alternative);
                }
                alternatives.putIfAbsent(choice.candidateId(), choice);
                List<IngredientChoice> choices = new ArrayList<>(alternatives.values());
                // The encoded physical input is the default. Explicit configuration order
                // still overrides it, and stock ranking can choose another admitted input.
                choices.sort(Comparator.comparingInt((IngredientChoice candidate) ->
                                candidate.candidateId().equals(choice.candidateId()) ? 0 : 1)
                        .thenComparing(IngredientChoice::sortKey));
                MatcherSeed seed = choices.size() == 1 && choices.get(0).isEmpty() ? MatcherSeed.empty() : new MatcherSeed(
                        new MatcherKey(choices.size() == 1 ? MatcherKind.EXACT : MatcherKind.ANY_OF, "declaration", choices), choices);
                slots.add(seed);
                shard.matchers.putIfAbsent(seed.key, seed);
                for (IngredientChoice alternative : choices)
                    if (!alternative.isEmpty()) {
                        shard.itemTypes.putIfAbsent(alternative.itemType.descriptor, alternative.itemType);
                    }
            }
            seeds.put(entry.getKey(), slots);
            grids.put(entry.getKey(), grid);
        }
        CatalogSymbols symbols = CatalogSymbols.merge(Collections.singletonList(shard));
        // Numeric tables are local indexes only; persisted v4 routes contain full candidate digests.
        int matcherOffset = source.generation.ingredientVariants.size();
        List<ItemTypeDefinition> items = new ArrayList<>(source.generation.itemTypes.entries);
        Map<PortableResourceDescriptor, Integer> itemIds = new LinkedHashMap<>(source.generation.itemTypes.ids);
        for (ItemTypeDefinition item : symbols.itemTypes.entries) {
            FrozenItemType frozen = shard.itemTypes.get(item.descriptor);
            int id = items.size();
            items.add(new ItemTypeDefinition(id, frozen));
            itemIds.put(item.descriptor, id);
        }
        ItemTypeTable itemTable = new ItemTypeTable(items, itemIds);
        List<MatcherDefinition> matchers = new ArrayList<>(source.generation.ingredientVariants.matchers);
        for (MatcherDefinition matcher : symbols.ingredientVariants.matchers) {
            List<IngredientChoice> choices = matcher.choices;
            MatcherKey key = matcher.isEmpty() ? MatcherKey.EMPTY : new MatcherKey(matcher.kind, "declaration", choices);
            matchers.add(new MatcherDefinition(matchers.size(), key, choices, itemTable));
        }
        IngredientVariantTable variants = new IngredientVariantTable(matchers);
        Object token = new Object();
        Map<ResourceLocation, CompiledRecipe> recipes = new LinkedHashMap<>();
        source.recipes.forEach((id, compiled) -> recipes.put(id, new CompiledRecipe(compiled, compiled.definition.withToken(token))));
        for (Map.Entry<ResourceLocation, QIOWorkbenchConfiguration.EncodedPattern> entry : exact.entrySet()) {
            mekanism.qioprocessing.common.content.workbench.QIOVerifiedWorkbenchPattern pattern = entry.getValue().getVerified();
            List<IngredientDefinition> ingredients = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                int id = symbols.matcherId(seeds.get(entry.getKey()).get(slot).key);
                ingredients.add(new IngredientDefinition(slot, id == 0 ? 0 : matcherOffset + id, token));
            }
            RecipeDefinition definition = new RecipeDefinition(entry.getKey(), pattern.getOutput().getResource(),
                    itemTable.getId(pattern.getOutput().getResource()), pattern.getOutput().getAmount(),
                    RecipeLayout.valueOf(pattern.getLayout().name()), 3, 3, entry.getValue().getRecipeSignature(), ingredients, token);
            CompiledRecipe compiled = new CompiledRecipe(definition, variants, grids.get(entry.getKey()), pattern.getOutputs(),
                    pattern.getContentSignature(), QIOWorkbenchStateInputs.slots(pattern.getGrid(), pattern.getRemainders(), pattern.getVirtualSlots()));
            compiled.stateSequence = QIOWorkbenchStateInputs.changesState(pattern.getGrid(), pattern.getRemainders(), pattern.getVirtualSlots());
            boolean[] virtualStateSlots = pattern.getVirtualSlots();
            for (int slot = 0; slot < 9; slot++) {
                QIOWorkbenchDeclarations.Stack remainder = pattern.getRemainders().get(slot);
                if (virtualStateSlots[slot] || remainder.isEmpty()) continue;
                // A returned item may become another declared ingredient (for example two
                // alternating container forms). It also requires sequential material flow.
                for (IngredientChoice choice : compiled.candidates.get(slot)) {
                    if (!choice.isEmpty() && !choice.isVirtualFluid() &&
                            QIOWorkbenchStateInputs.sameItem(choice.resource(), remainder.getResource())) {
                        compiled.stateSlots[slot] = true;
                        if (!pattern.getGrid().get(slot).isEmpty() &&
                                !pattern.getGrid().get(slot).getResource().equals(remainder.getResource()))
                            compiled.stateSequence = true;
                        break;
                    }
                }
            }
            QIOPlanningRoute.Builder route = QIOPlanningRoute.builder(ProviderKind.WORKBENCH, PROVIDER_ID,
                    entry.getKey().toString(), entry.getKey().toString()).variantId("v3." + pattern.getContentSignature());
            pattern.getInputs().forEach(route::input);
            pattern.getOutputs().forEach(route::output);
            compiled.dynamicDimension = dimension;
            compiled.dynamicEpoch = epoch;
            compiled.dynamicServer = QIOWorkbenchValidationService.INSTANCE.getServerEpoch();
            compiled.trustedExactRoute = compiled.createTrustedExactRoute();
            // Retain v3 exact route IDs for already persisted plans.
            compiled.legacyExactExecution = QIOWorkbenchRecipePattern.frozen(route.build(), entry.getKey(),
                    pattern.getGrid(), pattern.getVirtualSlots());
            recipes.put(entry.getKey(), compiled);
        }
        CatalogGeneration generation = new CatalogGeneration(itemTable, variants, new RecipeSkeletonTable(recipes.values()),
                sha256(source.generation.generationId + "|" + compiledCatalogSignature(recipes.values())), token);
        List<String> diagnostics = new ArrayList<>(source.diagnostics);
        diagnostics.removeIf(message -> exact.keySet().stream().anyMatch(id ->
                message.equals("Encoded workbench recipe changed: " + id) ||
                        message.equals("Encoded workbench recipe is no longer registered: " + id)));
        return new Snapshot(recipes, diagnostics, DEFAULT_PATTERN_CACHE_SIZE, generation);
    }

    /**
     * Pure projection of the frequency book. Declarations and persisted physical grids are
     * candidates, not credentials in this server/dimension. The solver's existing continuation
     * validates its selected grid; execution resolves the same variant through that boundary.
     */
    static Snapshot projectBook(WorkbenchSnapshot book, RecipeCatalog catalog, int dimension, long epoch) {
        List<BookSeed> seeds = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (WorkbenchRoute route : book.routes().values()) {
            RecipeDeclaration declaration = catalog.byId(route.recipeId());
            if (!route.enabled()) continue;
            if (!route.matches(declaration) || !route.validChoices(declaration)) {
                diagnostics.add(route.recipeId() + ": STALE_WORKBENCH_DECLARATION");
                continue;
            }
            if (declaration.hasDeclaredCandidates()) {
                List<List<IngredientChoice>> slots = new ArrayList<>(9);
                for (int slot = 0; slot < 9; slot++) slots.add(Collections.singletonList(IngredientChoice.empty()));
                for (int logical = 0; logical < declaration.inputs().size(); logical++) {
                    if (declaration.inputs().get(logical).isEmptySlot()) continue;
                    int physical = declaration.layout() == RecipeDeclaration.Layout.SHAPED ?
                            logical / declaration.width() * 3 + logical % declaration.width() : logical;
                    List<IngredientChoice> choices = new ArrayList<>();
                    for (RecipeItemKey key : route.candidates(declaration, logical)) choices.add(bookChoice(key));
                    slots.set(physical, choices);
                }
                seeds.add(new BookSeed(declaration, slots, null));
            }
            for (VerifiedWorkbenchPattern pattern : route.validPatterns(declaration, catalog.generation())) {
                if (!mekanism.qioprocessing.common.workbench.WorkbenchPatternPolicy.admits(route, declaration, pattern.grid()))
                    continue;
                List<List<IngredientChoice>> slots = new ArrayList<>(9);
                for (RecipeItemKey key : pattern.grid()) slots.add(Collections.singletonList(bookChoice(key)));
                seeds.add(new BookSeed(declaration, slots, pattern));
            }
        }
        SymbolShard shard = new SymbolShard();
        for (BookSeed seed : seeds) {
            shard.itemTypes.putIfAbsent(seed.output.getResource(), new FrozenItemType(seed.output));
            for (MatcherSeed slot : seed.slots) {
                shard.matchers.putIfAbsent(slot.key, slot);
                for (IngredientChoice choice : slot.choices)
                    if (!choice.isEmpty())
                        shard.itemTypes.putIfAbsent(choice.resource(), choice.itemType);
            }
        }
        CatalogSymbols symbols = CatalogSymbols.merge(Collections.singletonList(shard));
        Map<ResourceLocation, CompiledRecipe> recipes = new LinkedHashMap<>();
        List<CompiledRecipe> all = new ArrayList<>();
        for (BookSeed seed : seeds) {
            List<IngredientDefinition> ingredients = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++)
                ingredients.add(new IngredientDefinition(slot,
                        symbols.matcherId(seed.slots.get(slot).key), symbols.generationToken));
            ResourceLocation id = new ResourceLocation(seed.declaration.id());
            RecipeDefinition definition = new RecipeDefinition(id, seed.output.getResource(),
                    symbols.itemTypes.getId(seed.output.getResource()), seed.output.getAmount(), seed.layout,
                    3, 3, seed.declaration.signature(), ingredients, symbols.generationToken);
            CompiledRecipe compiled = new CompiledRecipe(definition, symbols.ingredientVariants,
                    seed.grid, seed.outputs, seed.signature, seed.stateSlots);
            compiled.baselineTrusted = false;
            compiled.stateSequence = seed.stateSequence;
            compiled.dynamicDimension = dimension;
            compiled.dynamicEpoch = epoch;
            compiled.dynamicServer = QIOWorkbenchValidationService.INSTANCE.getServerEpoch();
            CompiledRecipe first = recipes.putIfAbsent(id, compiled);
            if (first != null) first.alternatives.add(compiled);
            all.add(compiled);
        }
        CatalogGeneration generation = new CatalogGeneration(symbols.itemTypes, symbols.ingredientVariants,
                new RecipeSkeletonTable(all), sha256("book|" + book.id() + '|' + book.revision() + '|' +
                catalog.generation() + '|' + dimension + '|' + epoch + '|' + compiledCatalogSignature(all)), symbols.generationToken);
        return new Snapshot(recipes, diagnostics, DEFAULT_PATTERN_CACHE_SIZE, generation);
    }

    private static IngredientChoice bookChoice(@Nullable RecipeItemKey key) {
        QIOWorkbenchDeclarations.Stack stack = QIOWorkbenchDeclarations.Stack.fromRecipeKey(key, 1);
        return stack.isEmpty() ? IngredientChoice.empty() : new IngredientChoice(stack, stack.getResource(), 1, false);
    }

    private static final class BookSeed {
        final RecipeDeclaration declaration;
        final QIOWorkbenchDeclarations.Stack output;
        final List<MatcherSeed> slots = new ArrayList<>(9);
        final List<IngredientChoice> grid = new ArrayList<>(9);
        final Map<PortableResourceDescriptor, Long> outputs = new LinkedHashMap<>();
        final RecipeLayout layout;
        final String signature;
        final boolean[] stateSlots;
        final boolean stateSequence;

        BookSeed(RecipeDeclaration declaration, List<List<IngredientChoice>> choices,
                 @Nullable VerifiedWorkbenchPattern pattern) {
            this.declaration = declaration;
            output = QIOWorkbenchDeclarations.Stack.fromRecipeKey(declaration.output(), declaration.outputCount());
            outputs.put(output.getResource(), (long) output.getAmount());
            layout = declaration.layout() == RecipeDeclaration.Layout.SHAPED ? RecipeLayout.SHAPED :
                    declaration.layout() == RecipeDeclaration.Layout.SHAPELESS ? RecipeLayout.SHAPELESS : RecipeLayout.ORDERED_UNKNOWN;
            StringBuilder content = new StringBuilder(declaration.signature());
            for (List<IngredientChoice> slot : choices) {
                MatcherSeed matcher = slot.size() == 1 && slot.get(0).isEmpty() ? MatcherSeed.empty() :
                        new MatcherSeed(new MatcherKey(slot.size() == 1 ? MatcherKind.EXACT : MatcherKind.ANY_OF, "book", slot), slot);
                slots.add(matcher);
                grid.add(slot.get(0));
                content.append('|').append(matcher.key.canonical);
            }
            List<QIOWorkbenchDeclarations.Stack> remainders = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                QIOWorkbenchDeclarations.Stack remainder = QIOWorkbenchDeclarations.Stack.fromRecipeKey(
                        pattern == null ? null : pattern.remainders().get(slot), 1);
                remainders.add(remainder);
                if (!remainder.isEmpty())
                    outputs.merge(remainder.getResource(), (long) remainder.getAmount(), Math::addExact);
            }
            stateSlots = QIOWorkbenchStateInputs.slots(frozenGrid(grid), remainders, new boolean[9]);
            stateSequence = QIOWorkbenchStateInputs.changesState(frozenGrid(grid), remainders, new boolean[9]);
            signature = sha256(content.append('|').append(pattern == null ? "declaration" : pattern.contentSignature()).toString());
        }
    }

    private static List<CompiledRecipe> family(CompiledRecipe first) {
        if (first == null) return Collections.emptyList();
        List<CompiledRecipe> values = new ArrayList<>(1 + first.alternatives.size());
        values.add(first);
        values.addAll(first.alternatives);
        return values;
    }

    private static List<CompiledRecipe> allRecipes(Map<ResourceLocation, CompiledRecipe> recipes) {
        List<CompiledRecipe> result = new ArrayList<>();
        for (CompiledRecipe first : recipes.values()) result.addAll(family(first));
        return result;
    }

    static Snapshot withDiagnostics(Snapshot source, List<String> diagnostics) {
        if (diagnostics.isEmpty()) return source;
        List<String> combined = new ArrayList<>(source.diagnostics);
        combined.addAll(diagnostics);
        return new Snapshot(source.recipes, combined, DEFAULT_PATTERN_CACHE_SIZE, source.generation);
    }

    private static final class MatcherSeed {

        private static final MatcherSeed EMPTY = new MatcherSeed(MatcherKey.EMPTY,
                Collections.singletonList(IngredientChoice.empty()));
        private final MatcherKey key;
        private final List<IngredientChoice> choices;

        private MatcherSeed(MatcherKey key, List<IngredientChoice> choices) {
            this.key = Objects.requireNonNull(key, "key");
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        }

        private static MatcherSeed empty() {
            return EMPTY;
        }
    }

    private static final class MatcherKey implements Comparable<MatcherKey> {

        private static final MatcherKey EMPTY = new MatcherKey();
        private final MatcherKind kind;
        private final String canonical;
        private final int hashCode;

        private MatcherKey() {
            kind = MatcherKind.EMPTY;
            canonical = "EMPTY";
            hashCode = canonical.hashCode();
        }

        private MatcherKey(MatcherKind kind, String sourceSignature,
                           List<IngredientChoice> choices) {
            this.kind = Objects.requireNonNull(kind, "kind");
            StringBuilder value = new StringBuilder(kind.name()).append('|')
                    .append(Objects.requireNonNull(sourceSignature, "sourceSignature"))
                    .append('|');
            for (IngredientChoice choice : choices) {
                value.append(choice.sortKey()).append(';');
            }
            canonical = value.toString();
            hashCode = canonical.hashCode();
        }

        @Override
        public int compareTo(MatcherKey other) {
            return canonical.compareTo(Objects.requireNonNull(other, "other").canonical);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MatcherKey key && canonical.equals(key.canonical);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class FrozenItemType {

        private final PortableResourceDescriptor descriptor;
        private final int runtimeItemId;
        private final NBTTagCompound serializedPrototype;

        /** Pure frozen projection: no registry or capability reconstruction on a planning worker. */
        private FrozenItemType(QIOWorkbenchDeclarations.Stack stack) {
            descriptor = stack.getResource();
            runtimeItemId = -1;
            serializedPrototype = stack.write().getCompoundTag("item");
        }

    }

    /** One concrete item/metadata/ordinary-NBT identity in a catalog generation. */
    public static final class ItemTypeDefinition {

        private final int itemTypeId;
        private final int runtimeItemId;
        private final PortableResourceDescriptor descriptor;
        private final NBTTagCompound serializedPrototype;

        private ItemTypeDefinition(int itemTypeId, FrozenItemType frozen) {
            this.itemTypeId = itemTypeId;
            runtimeItemId = frozen.runtimeItemId;
            descriptor = frozen.descriptor;
            serializedPrototype = frozen.serializedPrototype.copy();
        }

        public int getItemTypeId() {
            return itemTypeId;
        }

        public int getRuntimeItemId() {
            return runtimeItemId;
        }

        @Nonnull
        public PortableResourceDescriptor getDescriptor() {
            return descriptor;
        }

        @Nonnull
        public ItemStack getDisplayStack() {
            return new ItemStack(serializedPrototype.copy());
        }
    }

    /** Immutable numeric lookup for every concrete item identity used by this generation. */
    public static final class ItemTypeTable {

        private final List<ItemTypeDefinition> entries;
        private final Map<PortableResourceDescriptor, Integer> ids;

        private ItemTypeTable(List<ItemTypeDefinition> entries,
                              Map<PortableResourceDescriptor, Integer> ids) {
            this(entries, ids, false);
        }

        private ItemTypeTable(List<ItemTypeDefinition> entries,
                              Map<PortableResourceDescriptor, Integer> ids, boolean owned) {
            this.entries = Collections.unmodifiableList(owned ? entries :
                    new ArrayList<>(entries));
            this.ids = Collections.unmodifiableMap(owned ? ids :
                    new LinkedHashMap<>(ids));
        }

        public int size() {
            return entries.size();
        }

        @Nonnull
        public ItemTypeDefinition get(int itemTypeId) {
            if (itemTypeId < 0 || itemTypeId >= entries.size()) {
                throw new IllegalArgumentException("Unknown QIO item type ID: " + itemTypeId);
            }
            return entries.get(itemTypeId);
        }

        public int getId(@Nonnull PortableResourceDescriptor descriptor) {
            Integer id = ids.get(Objects.requireNonNull(descriptor, "descriptor"));
            if (id == null) {
                throw new IllegalArgumentException("Item type is not part of this catalog generation");
            }
            return id;
        }
    }

    /** One shared ingredient matcher and its selectable concrete variants. */
    public static final class MatcherDefinition {

        private final int matcherId;
        private final MatcherKind kind;
        private final List<IngredientChoice> choices;
        private final List<CandidateDefinition> candidates;
        private final List<String> candidateIds;

        private MatcherDefinition(int matcherId, MatcherKey key,
                                  List<IngredientChoice> choices, ItemTypeTable itemTypes) {
            this.matcherId = matcherId;
            kind = key.kind;
            this.choices = choices;
            List<CandidateDefinition> definitions = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (IngredientChoice choice : choices) {
                if (!choice.isEmpty()) {
                    CandidateDefinition definition = choice.definition(itemTypes);
                    definitions.add(definition);
                    ids.add(definition.getCandidateId());
                }
            }
            candidates = Collections.unmodifiableList(definitions);
            candidateIds = Collections.unmodifiableList(ids);
        }

        public int getMatcherId() {
            return matcherId;
        }

        @Nonnull
        public MatcherKind getKind() {
            return kind;
        }

        public boolean isEmpty() {
            return matcherId == IngredientVariantTable.EMPTY_MATCHER_ID;
        }

        @Nonnull
        public List<CandidateDefinition> getCandidates() {
            return candidates;
        }

        @Nonnull
        public List<String> getCandidateIds() {
            return candidateIds;
        }
    }

    /** Immutable MatcherId to variant-set table shared by every recipe in one generation. */
    public static final class IngredientVariantTable {

        public static final int EMPTY_MATCHER_ID = 0;
        private final List<MatcherDefinition> matchers;

        private IngredientVariantTable(List<MatcherDefinition> matchers) {
            this(matchers, false);
        }

        private IngredientVariantTable(List<MatcherDefinition> matchers, boolean owned) {
            this.matchers = Collections.unmodifiableList(owned ? matchers :
                    new ArrayList<>(matchers));
        }

        public int size() {
            return matchers.size();
        }

        @Nonnull
        public MatcherDefinition get(int matcherId) {
            if (matcherId < 0 || matcherId >= matchers.size()) {
                throw new IllegalArgumentException("Unknown QIO ingredient matcher ID: " + matcherId);
            }
            return matchers.get(matcherId);
        }

        @Nonnull
        private List<IngredientChoice> choices(int matcherId) {
            return get(matcherId).choices;
        }
    }

    /** Immutable numeric recipe-skeleton directory for one catalog generation. */
    public static final class RecipeSkeletonTable {

        private final List<RecipeDefinition> recipes;
        private final Map<ResourceLocation, RecipeDefinition> recipesById;

        private RecipeSkeletonTable(Collection<CompiledRecipe> compiled) {
            List<RecipeDefinition> ordered = new ArrayList<>(compiled.size());
            Map<ResourceLocation, RecipeDefinition> byId = new LinkedHashMap<>();
            for (CompiledRecipe recipe : compiled) {
                ordered.add(recipe.definition);
                byId.put(recipe.definition.getRecipeId(), recipe.definition);
            }
            recipes = Collections.unmodifiableList(ordered);
            recipesById = Collections.unmodifiableMap(byId);
        }

        public int size() {
            return recipes.size();
        }

        @Nonnull
        public List<RecipeDefinition> getRecipes() {
            return recipes;
        }

        @Nullable
        public RecipeDefinition get(@Nonnull ResourceLocation recipeId) {
            return recipesById.get(Objects.requireNonNull(recipeId, "recipeId"));
        }
    }

    /** Atomically published set of all three numeric tables. */
    public static final class CatalogGeneration {

        private final ItemTypeTable itemTypes;
        private final IngredientVariantTable ingredientVariants;
        private final RecipeSkeletonTable recipeSkeletons;
        private final String generationId;
        private final Object generationToken;

        private CatalogGeneration(ItemTypeTable itemTypes,
                                  IngredientVariantTable ingredientVariants,
                                  RecipeSkeletonTable recipeSkeletons, String generationId,
                                  Object generationToken) {
            this.itemTypes = itemTypes;
            this.ingredientVariants = ingredientVariants;
            this.recipeSkeletons = recipeSkeletons;
            this.generationId = generationId;
            this.generationToken = generationToken;
        }

        @Nonnull
        public ItemTypeTable getItemTypes() {
            return itemTypes;
        }

        @Nonnull
        public IngredientVariantTable getIngredientVariants() {
            return ingredientVariants;
        }

        @Nonnull
        public RecipeSkeletonTable getRecipeSkeletons() {
            return recipeSkeletons;
        }

        @Nonnull
        public String getGenerationId() {
            return generationId;
        }
    }

    /** Pure-data projection used by the background symbol-table builder. */
    private static final class SymbolShard {

        private final Map<PortableResourceDescriptor, FrozenItemType> itemTypes =
                new LinkedHashMap<>();
        private final Map<MatcherKey, MatcherSeed> matchers = new LinkedHashMap<>();

    }

    private static final class CatalogSymbols {

        private final ItemTypeTable itemTypes;
        private final IngredientVariantTable ingredientVariants;
        private final Map<MatcherKey, Integer> matcherIds;
        private final Object generationToken = new Object();

        private CatalogSymbols(ItemTypeTable itemTypes,
                               IngredientVariantTable ingredientVariants,
                               Map<MatcherKey, Integer> matcherIds) {
            this.itemTypes = itemTypes;
            this.ingredientVariants = ingredientVariants;
            this.matcherIds = matcherIds;
        }

        private static CatalogSymbols merge(Collection<SymbolShard> shards) {
            Map<PortableResourceDescriptor, FrozenItemType> frozenItems = new TreeMap<>();
            Map<MatcherKey, MatcherSeed> frozenMatchers = new TreeMap<>();
            frozenMatchers.put(MatcherKey.EMPTY, MatcherSeed.empty());
            for (SymbolShard shard : shards) {
                shard.itemTypes.forEach(frozenItems::putIfAbsent);
                shard.matchers.forEach(frozenMatchers::putIfAbsent);
            }
            List<ItemTypeDefinition> itemDefinitions = new ArrayList<>(frozenItems.size());
            Map<PortableResourceDescriptor, Integer> itemIds = new LinkedHashMap<>();
            for (FrozenItemType item : frozenItems.values()) {
                int id = itemDefinitions.size();
                itemDefinitions.add(new ItemTypeDefinition(id, item));
                itemIds.put(item.descriptor, id);
            }
            ItemTypeTable itemTypes = new ItemTypeTable(itemDefinitions, itemIds);

            List<MatcherDefinition> matcherDefinitions = new ArrayList<>(frozenMatchers.size());
            Map<MatcherKey, Integer> matcherIds = new HashMap<>();
            MatcherSeed empty = frozenMatchers.remove(MatcherKey.EMPTY);
            matcherDefinitions.add(new MatcherDefinition(
                    IngredientVariantTable.EMPTY_MATCHER_ID, MatcherKey.EMPTY,
                    empty == null ? MatcherSeed.empty().choices : empty.choices, itemTypes));
            matcherIds.put(MatcherKey.EMPTY, IngredientVariantTable.EMPTY_MATCHER_ID);
            for (Map.Entry<MatcherKey, MatcherSeed> entry : frozenMatchers.entrySet()) {
                int id = matcherDefinitions.size();
                matcherDefinitions.add(new MatcherDefinition(id, entry.getKey(),
                        entry.getValue().choices, itemTypes));
                matcherIds.put(entry.getKey(), id);
            }
            return new CatalogSymbols(itemTypes,
                    new IngredientVariantTable(matcherDefinitions),
                    Collections.unmodifiableMap(matcherIds));
        }

        private int matcherId(MatcherKey key) {
            Integer matcherId = matcherIds.get(key);
            if (matcherId == null) {
                throw new IllegalStateException("Recipe matcher belongs to another catalog generation");
            }
            return matcherId;
        }

        private CatalogGeneration generation(Collection<CompiledRecipe> recipes) {
            RecipeSkeletonTable skeletons = new RecipeSkeletonTable(recipes);
            return new CatalogGeneration(itemTypes, ingredientVariants, skeletons,
                    generationSignature(recipes), generationToken);
        }

        private String generationSignature(Collection<CompiledRecipe> recipes) {
            StringBuilder canonical = new StringBuilder();
            for (ItemTypeDefinition itemType : itemTypes.entries) {
                canonical.append("I|").append(itemType.itemTypeId).append('|')
                        .append(itemType.descriptor).append('\n');
            }
            for (MatcherDefinition matcher : ingredientVariants.matchers) {
                canonical.append("M|").append(matcher.matcherId).append('|')
                        .append(matcher.kind).append('|');
                for (CandidateDefinition candidate : matcher.candidates) {
                    canonical.append(candidate.candidateId).append('@')
                            .append(candidate.itemTypeId).append(';');
                }
                canonical.append('\n');
            }
            canonical.append("R|").append(compiledCatalogSignature(recipes));
            return sha256(canonical.toString());
        }
    }

    public static final class Snapshot {

        private final Map<ResourceLocation, CompiledRecipe> recipes;
        private final Map<PortableResourceDescriptor, List<CompiledRecipe>> recipesByOutput;
        private final List<ResourceLocation> recipeIds;
        private final Set<ResourceLocation> recipeIdSet;
        private final Set<PortableResourceDescriptor> declaredOutputs;
        private final List<String> diagnostics;
        private final String structuralSignature;
        private final CatalogGeneration generation;
        private final QIOWeightedCache<String, QIOWorkbenchRecipePattern> patterns;
        private final long retainedBytes;

        private Snapshot(Map<ResourceLocation, CompiledRecipe> recipes, List<String> diagnostics,
                         int maximumCachedPatterns, CatalogGeneration generation) {
            this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            this.recipeIds = Collections.unmodifiableList(new ArrayList<>(recipes.keySet()));
            this.recipeIdSet = Collections.unmodifiableSet(new LinkedHashSet<>(recipes.keySet()));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
            this.generation = Objects.requireNonNull(generation, "generation");
            Map<PortableResourceDescriptor, List<CompiledRecipe>> outputIndex = new LinkedHashMap<>();
            for (CompiledRecipe recipe : allRecipes(recipes)) {
                outputIndex.computeIfAbsent(recipe.definition.getOutput(),
                                ignored -> new ArrayList<>())
                        .add(recipe);
            }
            List<PortableResourceDescriptor> outputs = new ArrayList<>(outputIndex.keySet());
            Collections.sort(outputs);
            Map<PortableResourceDescriptor, List<CompiledRecipe>> orderedIndex =
                    new LinkedHashMap<>();
            for (PortableResourceDescriptor output : outputs) {
                orderedIndex.put(output, Collections.unmodifiableList(outputIndex.get(output)));
            }
            recipesByOutput = Collections.unmodifiableMap(orderedIndex);
            declaredOutputs = Collections.unmodifiableSet(new LinkedHashSet<>(outputs));
            structuralSignature = sha256(compiledCatalogSignature(allRecipes(recipes)) + '|' +
                    generation.getGenerationId());
            patterns = new QIOWeightedCache<>(maximumCachedPatterns, 32 * QIOWorkbenchMemory.MIB,
                    (key, value) -> QIOWorkbenchMemory.text(key) + value.getRetainedBytes());
            // Reserve the entire mutable execution cache so later insertions cannot make the
            // outer snapshot LRU exceed its recorded weight. Shared data is overcounted safely.
            long bytes = 32 * QIOWorkbenchMemory.MIB + 1024;
            for (ItemTypeDefinition item : generation.itemTypes.entries) {
                bytes += 192 + QIOWorkbenchMemory.resource(item.descriptor) + QIOWorkbenchMemory.nbt(item.serializedPrototype);
            }
            for (MatcherDefinition matcher : generation.ingredientVariants.matchers) {
                bytes += 256;
                for (IngredientChoice choice : matcher.choices) {
                    bytes += 512 + choice.frozenStack.getRetainedBytes() + QIOWorkbenchMemory.resource(choice.resource) +
                            QIOWorkbenchMemory.text(choice.sortKey) + (choice.itemType == null ? 0 :
                            QIOWorkbenchMemory.nbt(choice.itemType.serializedPrototype));
                }
            }
            for (CompiledRecipe recipe : allRecipes(recipes)) {
                bytes += 2048 + QIOWorkbenchMemory.text(recipe.structuralSignature) +
                        QIOWorkbenchMemory.resources(recipe.baselineOutputs) +
                        (recipe.legacyExactExecution == null ? 0 : recipe.legacyExactExecution.getRetainedBytes()) +
                        (recipe.trustedExactRoute == null ? 0 : recipe.trustedExactRoute.verification().getRetainedBytes());
            }
            for (String diagnostic : diagnostics) bytes += QIOWorkbenchMemory.text(diagnostic);
            retainedBytes = bytes;
        }

        long getRetainedBytes() {
            return retainedBytes;
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
                @Nonnull PortableResourceDescriptor output,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Set<PortableResourceDescriptor> producibleResources,
                @Nonnull PriorityResolver priorityResolver) {
            Objects.requireNonNull(priorityResolver, "priorityResolver");
            Map<ResourceLocation, Long> priorities = new LinkedHashMap<>();
            List<CompiledRecipe> matching = recipesByOutput.get(output);
            if (matching != null) {
                for (CompiledRecipe recipe : matching) {
                    priorities.put(recipe.definition.getRecipeId(),
                            priorityResolver.priority(recipe.definition.getRecipeId()));
                }
            }
            return materialize(output, availableResources, producibleResources, priorities);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
                @Nonnull PortableResourceDescriptor output,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Set<PortableResourceDescriptor> producibleResources,
                @Nonnull Map<ResourceLocation, Long> effectivePriorities) {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(availableResources, "availableResources");
            Objects.requireNonNull(producibleResources, "producibleResources");
            Objects.requireNonNull(effectivePriorities, "effectivePriorities");
            return materialize(output, availableResources, producibleResources,
                    effectivePriorities, Integer.MAX_VALUE, null);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
                @Nonnull PortableResourceDescriptor output,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Set<PortableResourceDescriptor> producibleResources,
                @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes) {
            return materialize(output, availableResources, producibleResources,
                    effectivePriorities, maximumRoutes, null);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
                @Nonnull PortableResourceDescriptor output,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Set<PortableResourceDescriptor> producibleResources,
                @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes,
                @Nullable QIOWorkbenchConfiguration configuration) {
            return materialize(output, availableResources, producibleResources,
                    effectivePriorities, maximumRoutes, configuration, () -> false);
        }

        @Nonnull
        List<QIOPlanningRoute> materialize(
                @Nonnull PortableResourceDescriptor output,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Set<PortableResourceDescriptor> producibleResources,
                @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes,
                @Nullable QIOWorkbenchConfiguration configuration,
                @Nonnull BooleanSupplier cancelled) {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(availableResources, "availableResources");
            Objects.requireNonNull(producibleResources, "producibleResources");
            Objects.requireNonNull(effectivePriorities, "effectivePriorities");
            Objects.requireNonNull(cancelled, "cancelled");
            if (maximumRoutes <= 0) {
                throw new IllegalArgumentException("maximumRoutes must be positive");
            }
            List<CompiledRecipe> matching = recipesByOutput.get(output);
            if (matching == null || matching.isEmpty()) {
                return Collections.emptyList();
            }
            List<CompiledRecipe> orderedMatching = new ArrayList<>();
            for (CompiledRecipe recipe : matching) {
                if (effectivePriorities.containsKey(recipe.definition.getRecipeId()) && (configuration == null ||
                        configuration.isRecipeEnabled(recipe.definition.getRecipeId().toString(), recipe.definition.getSignature()))) {
                    orderedMatching.add(recipe);
                }
            }
            orderedMatching.sort(Comparator.comparingLong((CompiledRecipe recipe) ->
                            effectivePriorities.get(recipe.definition.getRecipeId())).reversed()
                    .thenComparing(recipe -> recipe.definition.getRecipeId().toString()));
            List<QIOPlanningRoute> result = new ArrayList<>();
            Set<String> selectedIds = new HashSet<>();
            for (CompiledRecipe recipe : orderedMatching) {
                CompiledRecipe.checkpoint(cancelled);
                if (result.size() >= maximumRoutes) {
                    break;
                }
                Long priority = effectivePriorities.get(recipe.definition.getRecipeId());

                List<List<IngredientChoice>> effective = recipe.effectiveCandidates(configuration);
                if (effective == null) continue;
                // The exact template is a representative, never the whole recipe family.
                // Closure uses its domain's possible inputs; solve binds a concrete grid to
                // current stock and preferences. No Cartesian search during graph compilation.
                CompiledRecipe.BoundDomain domain = recipe.new BoundDomain(effective, producibleResources);
                QIOPlanningRoute selected = domain.representative(availableResources, cancelled, output);
                if (selected == null) {
                    recipe.executionDiagnostic = "NO_VALID_CANDIDATE: " + recipe.definition.recipeId;
                    continue;
                }
                QIOPlanningRoute route = selected.withPriority(priority).withVariantPriority(Long.MAX_VALUE);
                // Declarations precede stored exact grids. Keep their broader candidate
                // domain when both select the same physical route.
                if (selectedIds.add(route.getStableId())) result.add(route);
            }
            result.sort(Comparator.comparingLong(QIOPlanningRoute::getRoutePriority)
                    .reversed().thenComparing(QIOPlanningRoute::getLogicalId)
                    .thenComparing(Comparator.comparingLong(
                            QIOPlanningRoute::getVariantPriority).reversed())
                    .thenComparing(QIOPlanningRoute::getStableId));
            return Collections.unmodifiableList(result);
        }

        @Nullable
        public QIOWorkbenchRecipePattern getPattern(
                @Nonnull String stableRouteId) {
            String checked = Objects.requireNonNull(stableRouteId, "stableRouteId");
            QIOWorkbenchRecipePattern cached = cachedPattern(checked);
            if (cached != null) {
                return cached.validatedExecution();
            }
            ParsedRouteId parsed = parseStableRouteId(checked);
            if (parsed == null) {
                return null;
            }
            for (CompiledRecipe recipe : family(recipes.get(parsed.recipeId))) {
                QIOWorkbenchRecipePattern pattern = getPattern(recipe, parsed);
                if (pattern != null) return pattern;
            }
            return null;
        }

        private QIOWorkbenchRecipePattern getPattern(CompiledRecipe recipe, ParsedRouteId parsed) {
            QIOWorkbenchRecipePattern pattern;
            if (recipe.legacyExactExecution != null && parsed.variantId.equals(recipe.legacyExactExecution.getVariantId())) {
                pattern = recipe.legacyExactExecution;
            } else {
                List<IngredientChoice> grid = recipe.decodeVariant(parsed.variantId);
                if (grid == null) return null;
                QIOWorkbenchVariant variant = recipe.variantKnowledge(grid, parsed.variantId, CompiledRecipe.exactInputs(grid));
                QIOPlanningRoute route = variant.route(0, 0);
                if (route == null) return null;
                pattern = QIOWorkbenchRecipePattern.frozen(route, recipe.definition.recipeId, frozenGrid(grid), virtualFluidSlots(grid));
            }
            cachePattern(pattern.getRoute().getStableId(), pattern);
            return pattern.validatedExecution();
        }

        public boolean isPatternValidationPending(String stableRouteId) {
            QIOWorkbenchRecipePattern cached = cachedPattern(stableRouteId);
            if (cached != null && cached.getRoute().verification() != null &&
                    cached.getRoute().verification().executionPending()) return true;
            ParsedRouteId parsed = parseStableRouteId(stableRouteId);
            return parsed != null && isRecipeValidationPending(parsed.recipeId.toString());
        }

        public boolean isRecipeValidationPending(String recipeId) {
            synchronized (patterns) {
                for (QIOWorkbenchRecipePattern pattern : patterns.values()) {
                    if (pattern.getRecipeId().toString().equals(recipeId) && pattern.getRoute().verification() != null &&
                            pattern.getRoute().verification().executionPending()) return true;
                }
            }
            return false;
        }

        /** Rebind a persisted route only through exact material/physical-output equivalence. */
        @Nullable
        public QIOWorkbenchRecipePattern recoverPersistedPattern(String stableRouteId,
                                                                 Map<PortableResourceDescriptor, Long> inputs, Map<PortableResourceDescriptor, Long> outputs,
                                                                 QIOWorkbenchConfiguration configuration) {
            ParsedRouteId parsed = parseStableRouteId(stableRouteId);
            if (parsed == null) return null;
            if (parsed.variantId.startsWith("v4.")) {
                for (CompiledRecipe recipe : family(recipes.get(parsed.recipeId))) {
                    QIOWorkbenchRecipePattern recovered = recoverPersistedPattern(recipe, parsed, inputs, outputs, configuration);
                    if (recovered != null) return recovered;
                }
                return null;
            }
            QIOWorkbenchRecipePattern resolved = resolveExecutablePattern(parsed.recipeId.toString(), inputs, outputs, configuration, null, inputs);
            return resolved != null && resolved.getExactInputs().equals(inputs) ? resolved : null;
        }

        private QIOWorkbenchRecipePattern recoverPersistedPattern(CompiledRecipe recipe, ParsedRouteId parsed,
                                                                  Map<PortableResourceDescriptor, Long> inputs, Map<PortableResourceDescriptor, Long> outputs,
                                                                  QIOWorkbenchConfiguration configuration) {
            if (!configuration.isRecipeEnabled(parsed.recipeId.toString(), recipe.definition.getSignature()))
                return null;
            List<List<IngredientChoice>> enabled = recipe.effectiveCandidates(configuration);
            if (enabled == null) return null;
            try {
                List<IngredientChoice> grid = recipe.decodeVariant(parsed.variantId, recipe.stateCandidates(enabled, inputs, () -> false));
                if (grid == null || !inputs.equals(CompiledRecipe.exactInputs(grid))) return null;
                List<List<IngredientChoice>> fixed = new ArrayList<>(9);
                for (IngredientChoice choice : grid) fixed.add(Collections.singletonList(choice));
                return resolveExecution(recipe, fixed, 0, emptyGrid(), new LinkedHashMap<>(inputs), outputs,
                        new int[]{0, 0}, inputs, null);
            } catch (QIOPlanningLimitException limit) {
                recipe.executionDiagnostic = limit.getMessage();
                return null;
            }
        }

        @Nullable
        public QIOWorkbenchRecipePattern recoverPersistedPattern(mekanism.qioprocessing.common.content.plan.QIOPlanStep step,
                                                                 Map<PortableResourceDescriptor, Long> inputs, QIOWorkbenchConfiguration configuration) {
            return resolveExecutablePattern(step.getRouteId(), inputs, step.getGuaranteedOutputs(), configuration, step, inputs);
        }

        public boolean matchesPlan(mekanism.qioprocessing.common.content.plan.QIOPlanStep step, QIOWorkbenchRecipePattern pattern) {
            if (!step.getRouteId().equals(pattern.getRecipeId().toString()) ||
                    !step.getGuaranteedOutputs().equals(pattern.getExpectedOutputs())) return false;
            if (step.getCandidateInputs().isEmpty()) return step.getExactInputs().equals(pattern.getExactInputs());
            for (CompiledRecipe recipe : family(recipes.get(pattern.getRecipeId()))) {
                if (matchesPlan(recipe, step, pattern)) return true;
            }
            return false;
        }

        private boolean matchesPlan(CompiledRecipe recipe, mekanism.qioprocessing.common.content.plan.QIOPlanStep step,
                                    QIOWorkbenchRecipePattern pattern) {
            List<IngredientChoice> grid;
            try {
                grid = recipe.decodeVariant(pattern.getVariantId(), recipe.stateCandidates(recipe.candidates, pattern.getExactInputs(), () -> false));
            } catch (QIOPlanningLimitException limit) {
                recipe.executionDiagnostic = limit.getMessage();
                return false;
            }
            if (grid == null && recipe.legacyExactExecution != null &&
                    recipe.legacyExactExecution.getVariantId().equals(pattern.getVariantId()))
                grid = recipe.baselineGrid;
            return grid != null && matchesPlanContract(step, grid);
        }

        /** Resolves an exact workbench variant from the resources owned by one job. */
        @Nullable
        public QIOWorkbenchRecipePattern resolveExecutablePattern(
                @Nonnull String recipeId,
                @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
                @Nonnull Map<PortableResourceDescriptor, Long> expectedOutputs,
                @Nonnull QIOWorkbenchConfiguration configuration) {
            return resolveExecutablePattern(recipeId, availableResources, expectedOutputs, configuration, null, null);
        }

        @Nullable
        public QIOWorkbenchRecipePattern resolveExecutablePattern(
                mekanism.qioprocessing.common.content.plan.QIOPlanStep step,
                Map<PortableResourceDescriptor, Long> availableResources, QIOWorkbenchConfiguration configuration) {
            return resolveExecutablePattern(step.getRouteId(), availableResources, step.getGuaranteedOutputs(), configuration, step, null);
        }

        @Nullable
        private QIOWorkbenchRecipePattern resolveExecutablePattern(String recipeId,
                                                                   Map<PortableResourceDescriptor, Long> availableResources,
                                                                   Map<PortableResourceDescriptor, Long> expectedOutputs, QIOWorkbenchConfiguration configuration,
                                                                   @Nullable mekanism.qioprocessing.common.content.plan.QIOPlanStep contract,
                                                                   @Nullable Map<PortableResourceDescriptor, Long> exactContract) {
            ResourceLocation id;
            try {
                id = new ResourceLocation(Objects.requireNonNull(recipeId, "recipeId"));
            } catch (RuntimeException e) {
                return null;
            }
            for (CompiledRecipe recipe : family(recipes.get(id))) {
                QIOWorkbenchRecipePattern result = resolveExecutablePattern(recipe, availableResources, expectedOutputs,
                        configuration, contract, exactContract);
                if (result != null) return result;
            }
            return null;
        }

        private QIOWorkbenchRecipePattern resolveExecutablePattern(CompiledRecipe recipe,
                                                                   Map<PortableResourceDescriptor, Long> availableResources,
                                                                   Map<PortableResourceDescriptor, Long> expectedOutputs, QIOWorkbenchConfiguration configuration,
                                                                   @Nullable mekanism.qioprocessing.common.content.plan.QIOPlanStep contract,
                                                                   @Nullable Map<PortableResourceDescriptor, Long> exactContract) {
            if (!configuration.isRecipeEnabled(recipe.definition.getRecipeId().toString(),
                    recipe.definition.getSignature())) {
                return null;
            }
            List<List<IngredientChoice>> choices = recipe.effectiveCandidates(configuration);
            if (choices == null) return null;
            // Bind the saved physical grid first. A known exact contract must not search all
            // declaration combinations again on every machine dispatch.
            if (contract != null && contract.getCandidateInputs().isEmpty()) {
                QIOWorkbenchRecipePattern exact = getPattern(contract.getStableRouteId());
                if (exact != null && matchesPlan(contract, exact) &&
                        (exactContract == null || exactContract.equals(exact.getExactInputs()))) {
                    try {
                        List<IngredientChoice> grid = recipe.decodeVariant(exact.getVariantId(),
                                recipe.stateCandidates(choices, exact.getExactInputs(), () -> false));
                        if (grid != null && CompiledRecipe.hasInputs(availableResources, exact.getExactInputs()))
                            return exact;
                    } catch (QIOPlanningLimitException limit) {
                        recipe.executionDiagnostic = limit.getMessage();
                        return null;
                    }
                }
            }
            try {
                choices = recipe.stateCandidates(choices, availableResources, () -> false);
            } catch (QIOPlanningLimitException limit) {
                recipe.executionDiagnostic = limit.getMessage();
                return null;
            }
            if (contract != null && contract.getVariantId().startsWith("v4.")) {
                List<IngredientChoice> original;
                try {
                    original = recipe.decodeVariant(contract.getVariantId(), recipe.stateCandidates(
                            recipe.candidates, contract.getExactInputs(), () -> false));
                } catch (QIOPlanningLimitException limit) {
                    recipe.executionDiagnostic = limit.getMessage();
                    return null;
                }
                if (original == null) return null;
                List<List<IngredientChoice>> constrained = new ArrayList<>(9);
                for (int slot = 0; slot < 9; slot++) {
                    Set<String> allowed = new LinkedHashSet<>();
                    allowed.add(original.get(slot).candidateId());
                    for (mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup group : contract.getCandidateInputs()) {
                        if (group.getSlots().contains(slot))
                            group.getOptions().forEach(option -> allowed.add(option.getCandidateId()));
                    }
                    List<IngredientChoice> options = new ArrayList<>();
                    for (IngredientChoice choice : choices.get(slot))
                        if (allowed.contains(choice.candidateId())) options.add(choice);
                    if (options.isEmpty()) return null;
                    constrained.add(options);
                }
                choices = constrained;
            }
            if (contract != null) {
                List<List<IngredientChoice>> constrained = new ArrayList<>(9);
                for (int slot = 0; slot < 9; slot++) {
                    List<IngredientChoice> options = new ArrayList<>();
                    for (IngredientChoice choice : choices.get(slot))
                        if (allowsSlot(contract, slot, choice)) options.add(choice);
                    if (options.isEmpty()) return null;
                    constrained.add(options);
                }
                choices = constrained;
            }
            recipe.executionDiagnostic = "";
            int[] visited = {0, 0};
            return resolveExecution(recipe, choices, 0, emptyGrid(), new LinkedHashMap<>(availableResources),
                    expectedOutputs, visited, exactContract, contract);
        }

        private QIOWorkbenchRecipePattern resolveExecution(CompiledRecipe recipe, List<List<IngredientChoice>> choices,
                                                           int slot, List<IngredientChoice> grid, Map<PortableResourceDescriptor, Long> remaining,
                                                           Map<PortableResourceDescriptor, Long> outputs, int[] visited,
                                                           @Nullable Map<PortableResourceDescriptor, Long> exactContract,
                                                           @Nullable mekanism.qioprocessing.common.content.plan.QIOPlanStep contract) {
            if (++visited[0] > 65_536) {
                recipe.executionDiagnostic = "EXECUTION_CANDIDATE_LIMIT: " + recipe.definition.recipeId;
                return null;
            }
            if (slot == 9) {
                if (contract != null && !matchesPlanContract(contract, grid)) return null;
                if (exactContract != null && !exactContract.equals(CompiledRecipe.exactInputs(grid))) return null;
                QIOWorkbenchVariant variant = recipe.variantKnowledge(grid, recipe.variantId(grid), CompiledRecipe.exactInputs(grid));
                QIOPlanningRoute route = variant.route(0, 0);
                if (route == null) return null;
                QIOWorkbenchRecipePattern pattern = QIOWorkbenchRecipePattern.frozen(route, recipe.definition.recipeId, frozenGrid(grid), virtualFluidSlots(grid));
                cachePattern(route.getStableId(), pattern);
                QIOWorkbenchRecipePattern executable = variant.execution();
                if (variant.executionPending()) visited[1]++;
                return executable != null && outputs.equals(executable.getExpectedOutputs()) ? executable : null;
            }
            for (IngredientChoice choice : choices.get(slot)) {
                long previous = choice.isEmpty() ? 0 : remaining.getOrDefault(choice.resource(), 0L);
                if (!choice.isEmpty() && previous < choice.amount()) continue;
                grid.set(slot, choice);
                if (!choice.isEmpty()) remaining.put(choice.resource(), previous - choice.amount());
                QIOWorkbenchRecipePattern result = resolveExecution(recipe, choices, slot + 1, grid, remaining, outputs, visited, exactContract, contract);
                if (!choice.isEmpty()) remaining.put(choice.resource(), previous);
                if (result != null || visited[0] > 65_536 || visited[1] >= 128) return result;
            }
            return null;
        }

        private static boolean allowsSlot(mekanism.qioprocessing.common.content.plan.QIOPlanStep contract,
                                          int slot, IngredientChoice choice) {
            for (mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup group : contract.getCandidateInputs()) {
                if (!group.getSlots().contains(slot)) continue;
                if (choice.isEmpty()) return false;
                boolean legacy = contract.getVariantId().startsWith("v2.");
                String oldId = legacy ? sha256(choice.isVirtualFluid() ?
                        "F|" + choice.resource() + '|' + choice.amount() + '|' + choice.frozenStack.getResource().withoutCapabilities() :
                        "I|" + choice.resource().withoutCapabilities()) : choice.candidateId();
                for (mekanism.qioprocessing.common.content.plan.QIOCandidateOption option : group.getOptions()) {
                    if ((option.getCandidateId().equals(choice.candidateId()) || legacy && option.getCandidateId().equals(oldId)) &&
                            option.getResource().equals(choice.resource()) && option.getAmountPerUnit() == choice.amount() &&
                            option.isVirtualFluid() == choice.isVirtualFluid()) return true;
                }
                return false;
            }
            return choice.isEmpty() || contract.getFixedInputs().getOrDefault(choice.resource(), 0L) >= choice.amount();
        }

        /** Verify saved candidate groups without interpreting obsolete numeric variant IDs. */
        private static boolean matchesPlanContract(mekanism.qioprocessing.common.content.plan.QIOPlanStep contract,
                                                   List<IngredientChoice> grid) {
            boolean[] grouped = new boolean[9];
            for (mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup group : contract.getCandidateInputs()) {
                for (int slot : group.getSlots()) {
                    if (grouped[slot]) return false;
                    grouped[slot] = true;
                    IngredientChoice choice = grid.get(slot);
                    if (choice.isEmpty()) return false;
                    if (!allowsSlot(contract, slot, choice)) return false;
                }
            }
            Map<PortableResourceDescriptor, Long> fixed = new LinkedHashMap<>();
            for (int slot = 0; slot < 9; slot++) {
                IngredientChoice choice = grid.get(slot);
                if (!grouped[slot] && !choice.isEmpty())
                    fixed.merge(choice.resource(), choice.amount(), Math::addExact);
            }
            return fixed.equals(contract.getFixedInputs());
        }

        /** Number of exact patterns currently retained by the bounded cache. */
        int getCachedPatternCount() {
            synchronized (patterns) {
                return patterns.size();
            }
        }

        @Nonnull
        public List<ResourceLocation> getRecipeIds() {
            return recipeIds;
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getDeclaredOutputs() {
            return declaredOutputs;
        }

        @Nonnull
        public List<PortableResourceDescriptor> getOrderedOutputs() {
            return Collections.unmodifiableList(new ArrayList<>(recipesByOutput.keySet()));
        }

        @Nonnull
        public List<ResourceLocation> getRecipeIds(
                @Nonnull PortableResourceDescriptor output) {
            List<CompiledRecipe> matching = recipesByOutput.get(
                    Objects.requireNonNull(output, "output"));
            if (matching == null || matching.isEmpty()) {
                return Collections.emptyList();
            }
            List<ResourceLocation> result = new ArrayList<>(matching.size());
            matching.forEach(recipe -> {
                if (!result.contains(recipe.definition.getRecipeId())) result.add(recipe.definition.getRecipeId());
            });
            return Collections.unmodifiableList(result);
        }

        @Nullable
        public RecipeDefinition getRecipeDefinition(@Nonnull ResourceLocation recipeId) {
            CompiledRecipe recipe = recipes.get(Objects.requireNonNull(recipeId, "recipeId"));
            return recipe == null ? null : recipe.definition;
        }

        @Nonnull
        public CatalogGeneration getGeneration() {
            return generation;
        }

        @Nonnull
        public MatcherDefinition getMatcher(@Nonnull IngredientDefinition ingredient) {
            IngredientDefinition checked = Objects.requireNonNull(ingredient, "ingredient");
            if (checked.generationToken != generation.generationToken) {
                throw new IllegalArgumentException(
                        "Ingredient matcher belongs to another catalog generation");
            }
            return generation.ingredientVariants.get(checked.getMatcherId());
        }

        @Nonnull
        public List<CandidateDefinition> getCandidates(
                @Nonnull IngredientDefinition ingredient) {
            return getMatcher(ingredient).getCandidates();
        }

        @Nonnull
        public List<String> getCandidateIds(@Nonnull RecipeDefinition recipe, int slot) {
            Objects.requireNonNull(recipe, "recipe");
            if (recipe.generationToken != generation.generationToken) {
                throw new IllegalArgumentException(
                        "Recipe skeleton belongs to another catalog generation");
            }
            if (slot < 0 || slot >= recipe.getIngredients().size()) {
                return Collections.emptyList();
            }
            return getMatcher(recipe.getIngredients().get(slot)).getCandidateIds();
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getDeclaredOutputs(
                @Nonnull Set<ResourceLocation> enabledRecipes) {
            Objects.requireNonNull(enabledRecipes, "enabledRecipes");
            Set<PortableResourceDescriptor> result = new LinkedHashSet<>();
            for (Map.Entry<PortableResourceDescriptor, List<CompiledRecipe>> entry :
                    recipesByOutput.entrySet()) {
                for (CompiledRecipe recipe : entry.getValue()) {
                    if (enabledRecipes.contains(recipe.definition.getRecipeId())) {
                        result.add(entry.getKey());
                        break;
                    }
                }
            }
            return Collections.unmodifiableSet(result);
        }

        public boolean containsRecipe(@Nonnull ResourceLocation recipeId) {
            return recipeIdSet.contains(Objects.requireNonNull(recipeId, "recipeId"));
        }

        public boolean containsRecipe(@Nonnull String recipeId) {
            try {
                return containsRecipe(new ResourceLocation(Objects.requireNonNull(recipeId,
                        "recipeId")));
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        @Nonnull
        public List<String> getDiagnostics() {
            List<String> values = new ArrayList<>(diagnostics);
            for (CompiledRecipe recipe : allRecipes(recipes))
                if (!recipe.executionDiagnostic.isEmpty()) values.add(recipe.executionDiagnostic);
            return Collections.unmodifiableList(values);
        }

        @Nonnull
        public String getStructuralSignature() {
            return structuralSignature;
        }

        public long getValidationRevision() {
            long count = 0;
            for (CompiledRecipe recipe : allRecipes(recipes)) count += recipe.validationRevision();
            return count;
        }

        private QIOWorkbenchRecipePattern cachedPattern(String stableRouteId) {
            synchronized (patterns) {
                patterns.refresh(stableRouteId);
                return patterns.get(stableRouteId);
            }
        }

        private void cachePattern(String stableRouteId, QIOWorkbenchRecipePattern pattern) {
            synchronized (patterns) {
                patterns.put(stableRouteId, pattern);
            }
        }
    }

    /** Immutable logical recipe data used by the management terminal. */
    public static final class RecipeDefinition {

        private final ResourceLocation recipeId;
        private final PortableResourceDescriptor output;
        private final int outputItemTypeId;
        private final int outputAmount;
        private final RecipeLayout layout;
        private final boolean shaped;
        private final int width;
        private final int height;
        private final String signature;
        private final List<IngredientDefinition> ingredients;
        private final Map<Integer, Integer> condensedInputs;
        private final Object generationToken;

        private RecipeDefinition(ResourceLocation recipeId,
                                 PortableResourceDescriptor output, int outputItemTypeId, int outputAmount,
                                 RecipeLayout layout,
                                 int width, int height, String signature,
                                 List<IngredientDefinition> ingredients, Object generationToken) {
            this.recipeId = recipeId;
            this.output = output;
            this.outputItemTypeId = outputItemTypeId;
            this.outputAmount = outputAmount;
            this.layout = Objects.requireNonNull(layout, "layout");
            // An unknown/manual recipe has an exact physical grid. Only SHAPELESS has no
            // dimensions in the UI protocol; publishing 3x3 there invalidates the whole page.
            this.shaped = layout != RecipeLayout.SHAPELESS;
            this.width = shaped ? width : 0;
            this.height = shaped ? height : 0;
            this.signature = signature;
            this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
            this.generationToken = generationToken;
            Map<Integer, Integer> condensed = new LinkedHashMap<>();
            for (IngredientDefinition ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    condensed.merge(ingredient.getMatcherId(), 1, Math::addExact);
                }
            }
            condensedInputs = Collections.unmodifiableMap(condensed);
        }

        @Nonnull
        public ResourceLocation getRecipeId() {
            return recipeId;
        }

        @Nonnull
        public PortableResourceDescriptor getOutput() {
            return output;
        }

        public int getOutputItemTypeId() {
            return outputItemTypeId;
        }

        public int getOutputAmount() {
            return outputAmount;
        }

        @Nonnull
        public RecipeLayout getLayout() {
            return layout;
        }

        public boolean isShaped() {
            return shaped;
        }

        public boolean isShapeless() {
            return layout == RecipeLayout.SHAPELESS;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        @Nonnull
        public String getSignature() {
            return signature;
        }

        @Nonnull
        public List<IngredientDefinition> getIngredients() {
            return ingredients;
        }

        /** Sparse logical entries; shapeless entries are occurrence-ordered and have no slot identity. */
        @Nonnull
        public List<IngredientDefinition> getLogicalIngredients() {
            List<IngredientDefinition> result = new ArrayList<>();
            for (IngredientDefinition ingredient : ingredients) {
                if (!ingredient.isEmpty()) result.add(ingredient);
            }
            return Collections.unmodifiableList(result);
        }

        @Nonnull
        public Map<Integer, Integer> getCondensedInputs() {
            return condensedInputs;
        }

        private RecipeDefinition withToken(Object token) {
            List<IngredientDefinition> rebound = new ArrayList<>(ingredients.size());
            for (IngredientDefinition ingredient : ingredients) {
                rebound.add(new IngredientDefinition(ingredient.slot, ingredient.matcherId, token));
            }
            return new RecipeDefinition(recipeId, output, outputItemTypeId, outputAmount, layout,
                    width, height, signature, rebound, token);
        }
    }

    public static final class IngredientDefinition {

        private final int slot;
        private final int matcherId;
        private final Object generationToken;

        private IngredientDefinition(int slot, int matcherId, Object generationToken) {
            this.slot = slot;
            this.matcherId = matcherId;
            this.generationToken = generationToken;
        }

        public int getSlot() {
            return slot;
        }

        public int getMatcherId() {
            return matcherId;
        }

        public boolean isEmpty() {
            return matcherId == IngredientVariantTable.EMPTY_MATCHER_ID;
        }
    }

    public static final class CandidateDefinition {

        private final String candidateId;
        private final int itemTypeId;
        private final ItemTypeDefinition itemType;
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;

        private CandidateDefinition(String candidateId, ItemTypeDefinition itemType,
                                    PortableResourceDescriptor resource, long amount, boolean virtualFluid) {
            this.candidateId = candidateId;
            this.itemType = itemType;
            itemTypeId = itemType.getItemTypeId();
            this.resource = resource;
            this.amount = amount;
            this.virtualFluid = virtualFluid;
        }

        @Nonnull
        public String getCandidateId() {
            return candidateId;
        }

        public int getItemTypeId() {
            return itemTypeId;
        }

        @Nonnull
        public ItemStack getDisplayStack() {
            return itemType.getDisplayStack();
        }

        @Nonnull
        public PortableResourceDescriptor getResource() {
            return resource;
        }

        public long getAmount() {
            return amount;
        }

        public boolean isVirtualFluid() {
            return virtualFluid;
        }
    }

    private static final class CompiledRecipe {
        @Nullable
        private QIOWorkbenchRecipePattern legacyExactExecution;
        @Nullable
        private QIOPlanningRoute trustedExactRoute;
        private final List<CompiledRecipe> alternatives = new ArrayList<>();
        private boolean baselineTrusted = true;
        private Object knowledgeOwner = new Object();
        private int dynamicDimension;
        private long dynamicEpoch;
        private long dynamicServer;
        private final java.util.concurrent.atomic.AtomicLong knowledgeRevision;

        private final RecipeDefinition definition;
        private final RecipeLayout layout;
        private final int[] matcherIds;
        private final IngredientVariantTable ingredientVariants;
        private final List<List<IngredientChoice>> candidates;
        private final List<IngredientChoice> baselineGrid;
        private final Map<PortableResourceDescriptor, Long> baselineOutputs;
        private final boolean[] stateSlots;
        private boolean stateSequence;
        private volatile String executionDiagnostic = "";

        private long validationRevision() {
            return knowledgeRevision.get();
        }

        private final String structuralSignature;

        private CompiledRecipe(RecipeDefinition definition,
                               IngredientVariantTable ingredientVariants,
                               List<IngredientChoice> baselineGrid,
                               Map<PortableResourceDescriptor, Long> baselineOutputs,
                               String structuralSignature, boolean[] stateSlots) {
            knowledgeRevision = new java.util.concurrent.atomic.AtomicLong();
            this.definition = Objects.requireNonNull(definition, "definition");
            this.stateSlots = stateSlots.clone();
            layout = definition.getLayout();
            this.ingredientVariants = Objects.requireNonNull(ingredientVariants,
                    "ingredientVariants");
            matcherIds = new int[definition.getIngredients().size()];
            for (int slot = 0; slot < matcherIds.length; slot++) {
                matcherIds[slot] = definition.getIngredients().get(slot).getMatcherId();
            }
            candidates = Collections.unmodifiableList(new MatcherGrid(ingredientVariants,
                    matcherIds));
            List<IngredientChoice> sharedBaseline = new ArrayList<>(baselineGrid.size());
            for (int slot = 0; slot < baselineGrid.size(); slot++) {
                IngredientChoice original = baselineGrid.get(slot);
                IngredientChoice shared = null;
                for (IngredientChoice choice : candidates.get(slot)) {
                    if (choice.candidateId().equals(original.candidateId())) {
                        shared = choice;
                        break;
                    }
                }
                if (shared == null) {
                    throw new IllegalStateException(
                            "Baseline candidate is absent from its shared matcher");
                }
                sharedBaseline.add(shared);
            }
            this.baselineGrid = Collections.unmodifiableList(sharedBaseline);
            this.baselineOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(
                    baselineOutputs));
            this.structuralSignature = Objects.requireNonNull(structuralSignature,
                    "structuralSignature");
        }

        private CompiledRecipe(CompiledRecipe source, RecipeDefinition definition) {
            this.definition = definition;
            layout = source.layout;
            matcherIds = source.matcherIds;
            ingredientVariants = source.ingredientVariants;
            candidates = source.candidates;
            baselineGrid = source.baselineGrid;
            baselineOutputs = source.baselineOutputs;
            stateSlots = source.stateSlots;
            stateSequence = source.stateSequence;
            structuralSignature = source.structuralSignature;
            legacyExactExecution = source.legacyExactExecution;
            trustedExactRoute = source.trustedExactRoute;
            knowledgeOwner = source.knowledgeOwner;
            knowledgeRevision = source.knowledgeRevision;
            alternatives.addAll(source.alternatives);
            baselineTrusted = source.baselineTrusted;
            dynamicDimension = source.dynamicDimension;
            dynamicEpoch = source.dynamicEpoch;
            dynamicServer = source.dynamicServer;
        }

        private static final class MatcherGrid extends AbstractList<List<IngredientChoice>> {

            private final IngredientVariantTable variants;
            private final int[] matcherIds;

            private MatcherGrid(IngredientVariantTable variants, int[] matcherIds) {
                this.variants = variants;
                this.matcherIds = matcherIds;
            }

            @Override
            public List<IngredientChoice> get(int index) {
                return variants.choices(matcherIds[index]);
            }

            @Override
            public int size() {
                return matcherIds.length;
            }
        }

        /** Only returned-item slots admit stock states; the complete grid is still untrusted. */
        private List<List<IngredientChoice>> stateCandidates(List<List<IngredientChoice>> enabled,
                                                             Map<PortableResourceDescriptor, Long> available, BooleanSupplier cancelled) {
            if (!QIOWorkbenchStateInputs.any(stateSlots)) return enabled;
            Set<String> itemIds = new HashSet<>();
            for (int slot = 0; slot < 9; slot++)
                if (stateSlots[slot]) {
                    for (IngredientChoice choice : enabled.get(slot))
                        if (!choice.isEmpty() && !choice.isVirtualFluid()) {
                            itemIds.add(choice.resource().getDescriptor().getPersistedItemId());
                        }
                }
            Map<String, List<PortableResourceDescriptor>> states = new HashMap<>();
            for (Map.Entry<PortableResourceDescriptor, Long> entry : available.entrySet()) {
                checkpoint(cancelled);
                if (entry.getValue() <= 0) continue;
                String id = entry.getKey().getDescriptor().getPersistedItemId();
                if (!id.isEmpty() && itemIds.contains(id))
                    states.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry.getKey());
            }
            states.values().forEach(Collections::sort);
            List<List<IngredientChoice>> result = new ArrayList<>(enabled);
            long bytes = 0;
            int count = 0;
            for (int slot = 0; slot < 9; slot++)
                if (stateSlots[slot]) {
                    Set<PortableResourceDescriptor> declared = new HashSet<>();
                    for (IngredientChoice choice : candidates.get(slot))
                        if (!choice.isEmpty()) declared.add(choice.resource());
                    Set<PortableResourceDescriptor> added = new HashSet<>();
                    List<IngredientChoice> options = new ArrayList<>();
                    for (IngredientChoice basis : enabled.get(slot)) {
                        if (basis.isEmpty() || basis.isVirtualFluid()) continue;
                        for (PortableResourceDescriptor resource : states.getOrDefault(
                                basis.resource().getDescriptor().getPersistedItemId(), Collections.emptyList())) {
                            checkpoint(cancelled);
                            // Declared choices retain explicit enable/disable preferences. Only
                            // unlisted states inherit admission from an enabled item identity.
                            if (declared.contains(resource) || !added.add(resource)) continue;
                            if (++count > 4096)
                                throw new QIOPlanningLimitException("WORKBENCH_STATE_CANDIDATE_COUNT_LIMIT");
                            QIOWorkbenchDeclarations.Stack frozen;
                            try {
                                frozen = QIOWorkbenchDeclarations.Stack.fromResource(resource);
                            } catch (QIOWorkbenchMemory.LimitException limit) {
                                throw new QIOPlanningLimitException(limit.getMessage());
                            }
                            bytes += 1024 + 3 * frozen.getRetainedBytes();
                            if (bytes > QIOWorkbenchMemory.TASK_LIMIT)
                                throw new QIOPlanningLimitException("WORKBENCH_STATE_CANDIDATE_MEMORY_LIMIT");
                            options.add(new IngredientChoice(frozen, resource, 1, false));
                        }
                    }
                    // Prefer an existing changed state to starting another declared fresh tool.
                    options.addAll(enabled.get(slot));
                    result.set(slot, Collections.unmodifiableList(options));
                }
            return Collections.unmodifiableList(result);
        }

        private static boolean hasInputs(Map<PortableResourceDescriptor, Long> available,
                                         Map<PortableResourceDescriptor, Long> inputs) {
            for (Map.Entry<PortableResourceDescriptor, Long> entry : inputs.entrySet()) {
                if (available.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
            }
            return true;
        }

        /** Request-local preference view; constructing a closure never expands a Cartesian product. */
        private final class BoundDomain implements QIOWorkbenchCandidateDomain {
            private final List<List<IngredientChoice>> choices;
            private final Set<PortableResourceDescriptor> producible;
            private final Set<PortableResourceDescriptor> inputs;

            private BoundDomain(List<List<IngredientChoice>> choices, Set<PortableResourceDescriptor> producible) {
                this.choices = choices;
                this.producible = producible;
                Set<PortableResourceDescriptor> resources = new LinkedHashSet<>();
                for (List<IngredientChoice> slot : choices)
                    for (IngredientChoice choice : slot) {
                        if (!choice.isEmpty()) resources.add(choice.resource());
                    }
                inputs = Collections.unmodifiableSet(resources);
            }

            @Override
            public Set<PortableResourceDescriptor> inputs() {
                return inputs;
            }

            private QIOPlanningRoute representative(Map<PortableResourceDescriptor, Long> available,
                                                    BooleanSupplier cancelled, PortableResourceDescriptor output) {
                checkpoint(cancelled);
                checkEpoch();
                QIOPlanningRoute exact = trustedExactRoute;
                if (exact != null && matchesBaseline(choices) && exact.getGuaranteedOutputAmount(output) > 0) {
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_EXACT_ROUTE_HITS, 1);
                    return exact.withWorkbenchDomain(this);
                }
                return select(available, 1, cancelled, Collections.emptySet(), output);
            }

            private void checkEpoch() {
                if (dynamicEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch() ||
                        !QIOWorkbenchValidationService.INSTANCE.isCurrentServer(dynamicServer)) {
                    throw new QIOPlanningVersionException();
                }
            }

            @Override
            public boolean hasAlternatives() {
                if (QIOWorkbenchStateInputs.any(stateSlots)) return true;
                for (List<IngredientChoice> slot : choices) if (slot.size() > 1) return true;
                return false;
            }

            @Override
            public QIOPlanningRoute select(Map<PortableResourceDescriptor, Long> available, long operations,
                                           BooleanSupplier cancelled) {
                QIOPlanningRoute selected = select(available, operations, cancelled, Collections.emptySet());
                if (selected == null)
                    throw new QIOPlanningLimitException("Workbench candidate domain exhausted: " + definition.recipeId);
                return selected;
            }

            @Override
            public QIOPlanningRoute select(Map<PortableResourceDescriptor, Long> available, long operations,
                                           BooleanSupplier cancelled, Set<String> excluded, @Nullable PortableResourceDescriptor requiredOutput) {
                try (QIOPlanningMetrics.Scope timing = QIOPlanningMetrics.current().measure(QIOPlanningMetrics.Phase.CANDIDATE)) {
                    return selectNow(available, operations, cancelled, excluded, requiredOutput);
                }
            }

            private QIOPlanningRoute selectNow(Map<PortableResourceDescriptor, Long> available, long operations,
                                               BooleanSupplier cancelled, Set<String> excluded, @Nullable PortableResourceDescriptor requiredOutput) {
                checkpoint(cancelled);
                checkEpoch();
                if (operations <= 0) throw new IllegalArgumentException("Nonpositive workbench batch");
                List<List<IngredientChoice>> currentChoices = stateCandidates(choices, available, cancelled);
                if (stateSequence) operations = 1;
                QIOPlanningRoute route = search(currentChoices, available, operations, cancelled, true, excluded, requiredOutput);
                if (route == null && operations != 1)
                    route = search(currentChoices, available, 1, cancelled, true, excluded, requiredOutput);
                if (route == null)
                    route = search(currentChoices, available, operations, cancelled, false, excluded, requiredOutput);
                if (route == null) return null;
                if (route.verification().resolved() && route.verification().valid()) {
                    GroupedRouteKey key = new GroupedRouteKey(this, route.getVariantId(), knowledgeRevision.get());
                    QIOPlanningRoute cached = searchCache().groupedRoutes.get(key);
                    QIOPlanningMetrics.current().add(cached == null ? QIOPlanningMetrics.Metric.GROUPED_ROUTE_CACHE_MISSES :
                            QIOPlanningMetrics.Metric.GROUPED_ROUTE_CACHE_HITS, 1);
                    if (cached != null) route = cached;
                    else {
                        List<IngredientChoice> grid = decodeVariant(route.getVariantId(), currentChoices);
                        if (grid != null) {
                            route = route.withCandidateGroups(validatedGroup(grid, route));
                            // A concurrent validation changes the set of admitted substitutes.
                            if (key.revision == knowledgeRevision.get()) searchCache().groupedRoutes.put(key, route);
                        }
                    }
                }
                return route.withWorkbenchDomain(this);
            }

            private QIOPlanningRoute search(List<List<IngredientChoice>> currentChoices,
                                            Map<PortableResourceDescriptor, Long> available, long operations, BooleanSupplier cancelled,
                                            boolean stockedOnly, Set<String> excluded, @Nullable PortableResourceDescriptor requiredOutput) {
                int[] visits = new int[2];
                try {
                    SearchCursor cursor = searchCursor(currentChoices, available, operations, stockedOnly, excluded, requiredOutput);
                    QIOPlanningRoute selected = cursor.select(excluded, requiredOutput, cancelled, visits);
                    searchCache().cursors.put(cursor.key, cursor);
                    return selected;
                } finally {
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.CANDIDATE_VISITS, visits[0]);
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.CANDIDATE_GRIDS, visits[1]);
                }
            }

            @Override
            public Collection<QIOWorkbenchVariant> validationBatch(QIOPlanningRoute selected,
                                                                   Map<PortableResourceDescriptor, Long> available, long operations, BooleanSupplier cancelled,
                                                                   Set<String> excluded, @Nullable PortableResourceDescriptor requiredOutput) {
                checkpoint(cancelled);
                checkEpoch();
                List<QIOWorkbenchVariant> pending = new ArrayList<>();
                pending.add(selected.verification());
                // State-changing tools depend on their preceding remainder; do not speculate
                // across those states. Ordinary grids use a small ordered lookahead window.
                if (stateSequence || QIOWorkbenchStateInputs.any(stateSlots)) return pending;
                // A forced candidate has no alternative to look ahead for: speculating would only
                // validate grids the plan can never adopt, while still paying for the cursor walk.
                if (!selected.hasAlternatives()) return pending;
                int[] visits = new int[2];
                try {
                    SearchCursor cursor = searchCursor(choices, available, operations, true, excluded, requiredOutput);
                    QIOPlanningRoute first = cursor.select(excluded, requiredOutput, cancelled, visits);
                    if (first == null && operations != 1) {
                        cursor = searchCursor(choices, available, 1, true, excluded, requiredOutput);
                        first = cursor.select(excluded, requiredOutput, cancelled, visits);
                    }
                    if (first == null) {
                        cursor = searchCursor(choices, available, operations, false, excluded, requiredOutput);
                        first = cursor.select(excluded, requiredOutput, cancelled, visits);
                    }
                    if (first != null && first.getStableId().equals(selected.getStableId())) {
                        cursor.prefetch(pending, excluded, requiredOutput, cancelled, visits);
                    }
                    searchCache().cursors.put(cursor.key, cursor);
                } finally {
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.CANDIDATE_VISITS, visits[0]);
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.CANDIDATE_GRIDS, visits[1]);
                }
                return pending;
            }

            private SearchCursor searchCursor(List<List<IngredientChoice>> slots,
                                              Map<PortableResourceDescriptor, Long> stock, long operations, boolean stockedOnly,
                                              Set<String> excluded, @Nullable PortableResourceDescriptor output) {
                SearchKey key = new SearchKey(slots, stock, operations, stockedOnly, output);
                SearchCursor cursor = searchCache().cursors.get(key);
                // Added exclusions only remove candidates. Removing any exclusion (e.g. a
                // sibling rollback) can restore an earlier candidate, so restart that search.
                if (cursor == null || !excluded.containsAll(cursor.excluded)) cursor = new SearchCursor(key, slots);
                cursor.excluded = new HashSet<>(excluded);
                return cursor;
            }

            private final class SearchKey {
                final BoundDomain domain = BoundDomain.this;
                final Map<PortableResourceDescriptor, Long> stock = new HashMap<>();
                final long operations;
                final boolean stockedOnly;
                @Nullable
                final PortableResourceDescriptor output;

                private SearchKey(List<List<IngredientChoice>> slots, Map<PortableResourceDescriptor, Long> available,
                                  long operations, boolean stockedOnly, @Nullable PortableResourceDescriptor output) {
                    for (List<IngredientChoice> slot : slots)
                        for (IngredientChoice choice : slot) {
                            if (!choice.isEmpty()) {
                                long amount = available.getOrDefault(choice.resource(), 0L);
                                if (amount > 0) stock.put(choice.resource(), amount);
                            }
                        }
                    this.operations = operations;
                    this.stockedOnly = stockedOnly;
                    this.output = output;
                }

                @Override
                public int hashCode() {
                    return Objects.hash(System.identityHashCode(domain), stock, operations, stockedOnly, output);
                }

                @Override
                public boolean equals(Object other) {
                    if (!(other instanceof SearchKey)) return false;
                    SearchKey key = (SearchKey) other;
                    return domain == key.domain && operations == key.operations && stockedOnly == key.stockedOnly &&
                            Objects.equals(output, key.output) && stock.equals(key.stock);
                }
            }

            /** Owns both DFS progress and the small lookahead window, only within one task. */
            private final class SearchCursor {
                final SearchKey key;
                final List<List<IngredientChoice>> slots;
                final List<IngredientChoice> grid = emptyGrid();
                final List<List<IngredientChoice>> ordered = new ArrayList<>(Collections.nCopies(9, null));
                final Map<PortableResourceDescriptor, Long> remaining;
                final int[] next = new int[9];
                final long[] previous = new long[9];
                final List<QIOPlanningRoute> ready = new ArrayList<>();
                Set<String> excluded = Collections.emptySet();
                List<List<IngredientChoice>> probes;
                boolean alternativesPrepared;
                int probe, slot;
                final long baseBytes;

                private SearchCursor(SearchKey key, List<List<IngredientChoice>> slots) {
                    this.key = key;
                    this.slots = slots;
                    remaining = new HashMap<>(key.stock);
                    long bytes = 4096 + 2 * QIOWorkbenchMemory.resources(key.stock);
                    for (List<IngredientChoice> choices : slots)
                        for (IngredientChoice choice : choices) {
                            bytes += 96 + choice.frozenStack.getRetainedBytes();
                        }
                    baseBytes = bytes;
                }

                long retainedBytes() {
                    long bytes = baseBytes + 1024L * ready.size();
                    for (String id : excluded) bytes += 64 + QIOWorkbenchMemory.text(id);
                    return bytes;
                }

                @Nullable
                QIOPlanningRoute select(Set<String> excluded, @Nullable PortableResourceDescriptor output,
                                        BooleanSupplier cancelled, int[] visits) {
                    while (true) {
                        checkpoint(cancelled);
                        if (ready.isEmpty()) {
                            QIOPlanningRoute route = advance(excluded, output, cancelled, visits, Integer.MAX_VALUE, null);
                            if (route == null) return null;
                            ready.add(route);
                        }
                        QIOPlanningRoute first = ready.get(0).verification().route(0, 0);
                        if (admitted(first, excluded, output)) {
                            ready.set(0, first);
                            return first;
                        }
                        ready.remove(0);
                    }
                }

                void prefetch(List<QIOWorkbenchVariant> pending, Set<String> excluded,
                              @Nullable PortableResourceDescriptor output, BooleanSupplier cancelled, int[] visits) {
                    PrefetchBudget lookahead = new PrefetchBudget(pending);
                    int stopAt = Math.min(65_536, visits[0] + 512);
                    for (int index = 1; index < 16 && pending.size() < 16; index++) {
                        checkpoint(cancelled);
                        if (index >= ready.size()) {
                            QIOPlanningRoute route;
                            try {
                                route = advance(excluded, output, cancelled, visits, stopAt, lookahead);
                            } catch (QIOPlanningLimitException limit) {
                                if (!limit.isSearchLimit()) throw limit;
                                break; // Optional lookahead must not fail the selected route.
                            }
                            if (route == null) break;
                            ready.add(route);
                        }
                        QIOWorkbenchVariant value = ready.get(index).verification();
                        if (!value.resolved() && !pending.contains(value)) {
                            if (!lookahead.retain(value)) break;
                            pending.add(value);
                        }
                    }
                }

                @Nullable
                QIOPlanningRoute advance(Set<String> excluded, @Nullable PortableResourceDescriptor output,
                                         BooleanSupplier cancelled, int[] visits, int stopAt, @Nullable PrefetchBudget lookahead) {
                    if (probes == null) {
                        if (key.stockedOnly && !canSupply(slots, 0, key.stock, key.operations)) {
                            QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_STOCK_PRUNES, 1);
                            probes = Collections.emptyList();
                            slot = -1;
                            return null;
                        }
                        probes = new ArrayList<>();
                        List<IngredientChoice> preferred = preferredGrid(slots, key.stock, key.operations, key.stockedOnly);
                        if (preferred != null) {
                            probes.add(preferred);
                        }
                    }
                    while (true) {
                        while (probe < probes.size()) {
                            checkpoint(cancelled);
                            if (visits[0] >= stopAt) return null;
                            QIOPlanningRoute route;
                            if (probe == 0) {
                                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_DIRECT_GRID_ATTEMPTS, 1);
                                route = resolveGrid(probes.get(probe), excluded, output, lookahead);
                            } else
                                route = probeGrid(probes.get(probe), key.stock, key.operations, key.stockedOnly, excluded, output, lookahead);
                            // A declined optional candidate is still the next probe on resume.
                            if (lookahead != null && lookahead.blocked) return null;
                            probe++;
                            visits[0]++;
                            if (route != null) return route;
                        }
                        if (!alternativesPrepared && !probes.isEmpty()) {
                            alternativesPrepared = true;
                            collectSimpleAlternatives(slots, probes.get(0), probes, cancelled);
                            continue;
                        }
                        return enumerate(this, visits, cancelled, excluded, output, stopAt, lookahead);
                    }
                }
            }

            private boolean admitted(@Nullable QIOPlanningRoute route, Set<String> excluded,
                                     @Nullable PortableResourceDescriptor output) {
                return route != null && !excluded.contains(route.getStableId()) &&
                        (output == null || !route.verification().resolved() || route.getGuaranteedOutputAmount(output) > 0) &&
                        route.getGuaranteedOutputAmount(definition.output) >= definition.outputAmount;
            }

            /** Optimistic capacity bound; reject only grids that cannot fit the remaining stock. */
            private boolean canSupply(List<List<IngredientChoice>> slots, int from,
                                      Map<PortableResourceDescriptor, Long> remaining, long operations) {
                Map<PortableResourceDescriptor, Long> minimumCost = new HashMap<>();
                Map<PortableResourceDescriptor, Long> mandatory = new HashMap<>();
                int needed = 0;
                for (int slot = from; slot < 9; slot++) {
                    List<IngredientChoice> choices = slots.get(slot);
                    if (choices.size() == 1 && choices.get(0).isEmpty()) continue;
                    needed++;
                    PortableResourceDescriptor only = null;
                    boolean distinct = false, affordable = false;
                    long minimum = Long.MAX_VALUE;
                    for (IngredientChoice choice : choices) {
                        if (choice.isEmpty()) continue;
                        long cost = multiplyBounded(choice.amount(), operations);
                        if (remaining.getOrDefault(choice.resource(), 0L) < cost) continue;
                        affordable = true;
                        minimumCost.merge(choice.resource(), cost, Math::min);
                        if (only == null) only = choice.resource();
                        else if (!only.equals(choice.resource())) distinct = true;
                        minimum = Math.min(minimum, cost);
                    }
                    if (!affordable) return false;
                    if (!distinct) mandatory.merge(only, minimum, QIOWorkbenchRecipeCatalog::saturatedAdd);
                }
                for (Map.Entry<PortableResourceDescriptor, Long> entry : mandatory.entrySet()) {
                    if (remaining.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
                }
                long capacity = 0;
                for (Map.Entry<PortableResourceDescriptor, Long> entry : minimumCost.entrySet()) {
                    capacity += Math.min(9, remaining.getOrDefault(entry.getKey(), 0L) / entry.getValue());
                    if (capacity >= needed) return true;
                }
                return needed == 0;
            }

            /** Small ordered probes precede the full iterator; they never replace its coverage. */
            private void collectSimpleAlternatives(List<List<IngredientChoice>> slots,
                                                   List<IngredientChoice> preferred, List<List<IngredientChoice>> probes, BooleanSupplier cancelled) {
                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_ALTERNATIVE_PREPARATIONS, 1);
                int attempts = 0;
                // Equivalent physical slots often need to change together (e.g. all oak to birch).
                // Use candidate identity sets, not matcher IDs or item IDs, to preserve slot policy.
                List<Set<String>> identities = new ArrayList<>(9);
                for (List<IngredientChoice> choices : slots) {
                    Set<String> ids = new LinkedHashSet<>();
                    for (IngredientChoice choice : choices) ids.add(choice.candidateId());
                    identities.add(ids);
                }
                boolean[] grouped = new boolean[9];
                for (int pivot = 0; pivot < 9; pivot++) {
                    if (grouped[pivot] || slots.get(pivot).size() < 2) continue;
                    List<Integer> group = new ArrayList<>();
                    for (int slot = pivot; slot < 9; slot++)
                        if (identities.get(pivot).equals(identities.get(slot))) {
                            group.add(slot);
                            grouped[slot] = true;
                        }
                    if (group.size() < 2) continue;
                    for (IngredientChoice choice : slots.get(pivot)) {
                        checkpoint(cancelled);
                        if (choice.candidateId().equals(preferred.get(pivot).candidateId())) continue;
                        if (++attempts > 64) return;
                        List<IngredientChoice> grid = new ArrayList<>(preferred);
                        for (int slot : group)
                            for (IngredientChoice option : slots.get(slot))
                                if (option.candidateId().equals(choice.candidateId())) {
                                    grid.set(slot, option);
                                    break;
                                }
                        probes.add(grid);
                    }
                }
                for (int slot = 0; slot < 9; slot++)
                    for (IngredientChoice choice : slots.get(slot)) {
                        checkpoint(cancelled);
                        if (choice.candidateId().equals(preferred.get(slot).candidateId())) continue;
                        if (++attempts > 64) return;
                        List<IngredientChoice> grid = new ArrayList<>(preferred);
                        grid.set(slot, choice);
                        probes.add(grid);
                    }
            }

            @Nullable
            private QIOPlanningRoute probeGrid(List<IngredientChoice> grid,
                                               Map<PortableResourceDescriptor, Long> stock, long operations, boolean stockedOnly,
                                               Set<String> excluded, @Nullable PortableResourceDescriptor output, @Nullable PrefetchBudget lookahead) {
                int[] taskWork = QIOPlanningExecutor.taskLocal(QIOWorkbenchCandidateDomain.class, () -> new int[1]);
                if (++taskWork[0] > 1_048_576)
                    throw QIOPlanningLimitException.search("QIO cumulative candidate work budget reached");
                if (stockedOnly) {
                    Map<PortableResourceDescriptor, Long> left = new HashMap<>(stock);
                    for (IngredientChoice choice : grid)
                        if (!choice.isEmpty()) {
                            long cost = multiplyBounded(choice.amount(), operations);
                            long present = left.getOrDefault(choice.resource(), 0L);
                            if (present < cost) return null;
                            left.put(choice.resource(), present - cost);
                        }
                }
                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_HEURISTIC_GRID_ATTEMPTS, 1);
                return resolveGrid(grid, excluded, output, lookahead);
            }

            @Nullable
            private List<IngredientChoice> preferredGrid(List<List<IngredientChoice>> slots,
                                                         Map<PortableResourceDescriptor, Long> available, long operations, boolean stockedOnly) {
                Map<PortableResourceDescriptor, Long> remaining = new HashMap<>(available);
                List<IngredientChoice> grid = new ArrayList<>(9);
                for (List<IngredientChoice> slot : slots) {
                    IngredientChoice best = null;
                    int bestRank = Integer.MAX_VALUE;
                    for (IngredientChoice choice : slot) {
                        long present = choice.isEmpty() ? 0 : remaining.getOrDefault(choice.resource(), 0L);
                        if (stockedOnly && !choice.isEmpty() && present < multiplyBounded(choice.amount(), operations))
                            continue;
                        int value = rank(choice, remaining, operations);
                        if (value < bestRank) {
                            best = choice;
                            bestRank = value;
                        }
                    }
                    if (best == null) return null;
                    grid.add(best);
                    if (!best.isEmpty()) {
                        long present = remaining.getOrDefault(best.resource(), 0L);
                        remaining.put(best.resource(), Math.max(0, present - Math.min(present, multiplyBounded(best.amount(), operations))));
                    }
                }
                return grid;
            }

            @Nullable
            private QIOPlanningRoute resolveGrid(List<IngredientChoice> grid, Set<String> excluded,
                                                 @Nullable PortableResourceDescriptor requiredOutput, @Nullable PrefetchBudget lookahead) {
                String id = variantId(grid);
                QIOWorkbenchVariant variant;
                QIOPlanningRoute route;
                if (trustedExactRoute != null && id.equals(trustedExactRoute.getVariantId())) {
                    route = trustedExactRoute;
                    variant = route.verification();
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_EXACT_ROUTE_HITS, 1);
                } else {
                    variant = variantKnowledge(grid, id, exactInputs(grid), lookahead);
                    if (variant == null) return null;
                    route = variant.route(0, 0);
                }
                return route != null && !excluded.contains(route.getStableId()) &&
                        (requiredOutput == null || !variant.resolved() || route.getGuaranteedOutputAmount(requiredOutput) > 0) &&
                        route.getGuaranteedOutputAmount(definition.output) >= definition.outputAmount ? route : null;
            }

            private List<mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup> validatedGroup(
                    List<IngredientChoice> grid, QIOPlanningRoute selected) {
                List<mekanism.qioprocessing.common.content.plan.QIOCandidateOption> best = Collections.emptyList();
                int pivot = -1;
                for (int slot = 0; slot < 9; slot++) {
                    IngredientChoice original = grid.get(slot);
                    if (original.isEmpty() || choices.get(slot).size() <= 1) continue;
                    List<mekanism.qioprocessing.common.content.plan.QIOCandidateOption> options = new ArrayList<>();
                    // One varying physical slot per group. Multiple independent groups would
                    // incorrectly assert unverified cross-products of otherwise valid grids.
                    for (IngredientChoice choice : choices.get(slot)) {
                        if (choice.isEmpty() || options.size() == 64) continue;
                        grid.set(slot, choice);
                        QIOWorkbenchVariant known = knownVariant(variantId(grid));
                        QIOPlanningRoute route = known == null || !known.resolved() || !known.valid() ? null : known.route(0, 0);
                        if (route != null && selected.getGuaranteedOutputs().equals(route.getGuaranteedOutputs())) {
                            options.add(new mekanism.qioprocessing.common.content.plan.QIOCandidateOption(
                                    choice.candidateId(), choice.resource(), choice.amount(), choice.isVirtualFluid()));
                        }
                    }
                    grid.set(slot, original);
                    if (options.size() > best.size() && options.stream().anyMatch(option -> option.getCandidateId().equals(original.candidateId()))) {
                        best = options;
                        pivot = slot;
                    }
                }
                if (best.size() < 2) return Collections.emptyList();
                IngredientChoice selectedInput = grid.get(pivot);
                return Collections.singletonList(new mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup(
                        Collections.singletonList(pivot), best, Collections.singletonMap(selectedInput.resource(), selectedInput.amount())));
            }

            private QIOPlanningRoute enumerate(SearchCursor cursor, int[] visits, BooleanSupplier cancelled,
                                               Set<String> excluded, @Nullable PortableResourceDescriptor requiredOutput, int stopAt,
                                               @Nullable PrefetchBudget lookahead) {
                List<List<IngredientChoice>> currentChoices = cursor.slots;
                List<IngredientChoice> grid = cursor.grid;
                List<List<IngredientChoice>> ordered = cursor.ordered;
                Map<PortableResourceDescriptor, Long> remaining = cursor.remaining;
                int[] next = cursor.next;
                long[] previous = cursor.previous;
                long operations = cursor.key.operations;
                boolean stockedOnly = cursor.key.stockedOnly;
                int[] taskWork = QIOPlanningExecutor.taskLocal(QIOWorkbenchCandidateDomain.class, () -> new int[1]);
                while (cursor.slot >= 0) {
                    checkpoint(cancelled);
                    if (visits[0] >= stopAt) return null;
                    if (++taskWork[0] > 1_048_576)
                        throw QIOPlanningLimitException.search("QIO cumulative candidate work budget reached");
                    if (++visits[0] > 65_536)
                        throw QIOPlanningLimitException.search("Workbench candidate search budget reached: " + definition.recipeId);
                    int slot = cursor.slot;
                    if (slot == 9) {
                        visits[1]++;
                        QIOPlanningRoute result = resolveGrid(grid, excluded, requiredOutput, lookahead);
                        // Keep the leaf and its consumed stock intact when optional admission
                        // fails. A later mandatory selection must retry this exact candidate.
                        if (lookahead != null && lookahead.blocked) return null;
                        // Advance before yielding the route; resuming must inspect its sibling,
                        // not revisit the leaf that was just returned or prefetched.
                        cursor.slot--;
                        if (!grid.get(cursor.slot).isEmpty())
                            remaining.put(grid.get(cursor.slot).resource(), previous[cursor.slot]);
                        if (result != null) return result;
                        continue;
                    } else {
                        if (ordered.get(slot) == null) {
                            List<IngredientChoice> options = new ArrayList<>();
                            if (!stockedOnly || canSupply(currentChoices, slot, remaining, operations)) {
                                for (IngredientChoice choice : currentChoices.get(slot)) {
                                    if (!stockedOnly || choice.isEmpty() || remaining.getOrDefault(choice.resource(), 0L) >=
                                            multiplyBounded(choice.amount(), operations)) options.add(choice);
                                }
                                options.sort(Comparator.comparingInt(choice -> rank(choice, remaining, operations)));
                            } else
                                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.WORKBENCH_STOCK_PRUNES, 1);
                            ordered.set(slot, options);
                            next[slot] = 0;
                        }
                        if (next[slot] < ordered.get(slot).size()) {
                            IngredientChoice choice = ordered.get(slot).get(next[slot]++);
                            grid.set(slot, choice);
                            previous[slot] = choice.isEmpty() ? 0 : remaining.getOrDefault(choice.resource(), 0L);
                            if (!choice.isEmpty()) remaining.put(choice.resource(), Math.max(0, previous[slot] -
                                    Math.min(previous[slot], multiplyBounded(choice.amount(), operations))));
                            cursor.slot++;
                            if (cursor.slot < 9) ordered.set(cursor.slot, null);
                            continue;
                        }
                    }
                    // Undo precisely the selected slot before advancing its sibling.
                    cursor.slot--;
                    if (cursor.slot >= 0 && !grid.get(cursor.slot).isEmpty())
                        remaining.put(grid.get(cursor.slot).resource(), previous[cursor.slot]);
                }
                return null;
            }

            private int rank(IngredientChoice choice, Map<PortableResourceDescriptor, Long> remaining, long operations) {
                if (choice.isEmpty()) return 0;
                long stock = remaining.getOrDefault(choice.resource(), 0L);
                if (stock >= multiplyBounded(choice.amount(), operations)) return 0;
                if (stock >= choice.amount()) return 1;
                return producible.contains(choice.resource()) ? 2 : 3;
            }

            private long multiplyBounded(long amount, long operations) {
                return amount > Long.MAX_VALUE / operations ? Long.MAX_VALUE : amount * operations;
            }

            private CandidateSearchCache searchCache() {
                return QIOPlanningExecutor.taskLocal(CandidateSearchCache.class, CandidateSearchCache::new);
            }
        }

        @Nullable
        private List<List<IngredientChoice>> effectiveCandidates(
                @Nullable QIOWorkbenchConfiguration configuration) {
            if (configuration == null) {
                return candidates;
            }
            List<List<IngredientChoice>> effective = new ArrayList<>(candidates.size());
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<IngredientChoice> defaults = candidates.get(slot);
                if (defaults.size() == 1 && defaults.get(0).isEmpty()) {
                    effective.add(defaults);
                    continue;
                }
                List<String> defaultIds = new ArrayList<>(defaults.size());
                Map<String, IngredientChoice> byId = new LinkedHashMap<>();
                for (IngredientChoice candidate : defaults) {
                    defaultIds.add(candidate.candidateId());
                    byId.put(candidate.candidateId(), candidate);
                }
                List<String> enabled = configuration.orderedEnabledCandidates(
                        definition.getRecipeId().toString(), definition.getSignature(), slot,
                        defaultIds);
                if (enabled.isEmpty()) {
                    return null;
                }
                List<IngredientChoice> ordered = new ArrayList<>(enabled.size());
                for (String candidateId : enabled) {
                    IngredientChoice candidate = byId.get(candidateId);
                    if (candidate != null) ordered.add(candidate);
                }
                if (ordered.isEmpty()) {
                    return null;
                }
                effective.add(Collections.unmodifiableList(ordered));
            }
            return Collections.unmodifiableList(effective);
        }

        private static Map<PortableResourceDescriptor, Long> exactInputs(
                List<IngredientChoice> grid) {
            Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
            for (IngredientChoice input : grid) {
                if (!input.isEmpty()) {
                    inputs.merge(input.resource(), input.amount(), Math::addExact);
                }
            }
            return Collections.unmodifiableMap(inputs);
        }

        private QIOWorkbenchVariant knownVariant(String id) {
            if (trustedExactRoute != null && trustedExactRoute.getVariantId().equals(id))
                return trustedExactRoute.verification();
            Map<String, QIOWorkbenchVariant> pinned = QIOPlanningExecutor.taskLocal(this, LinkedHashMap::new);
            QIOWorkbenchVariant value = pinned.get(id);
            return value == null ? QIOWorkbenchKnowledge.get(knowledgeOwner, id) : value;
        }

        private QIOWorkbenchVariant variantKnowledge(List<IngredientChoice> grid, String variantId,
                                                     Map<PortableResourceDescriptor, Long> inputs) {
            return variantKnowledge(grid, variantId, inputs, null);
        }

        @Nullable
        private QIOWorkbenchVariant variantKnowledge(List<IngredientChoice> grid, String variantId,
                                                     Map<PortableResourceDescriptor, Long> inputs, @Nullable PrefetchBudget lookahead) {
            if (trustedExactRoute != null && trustedExactRoute.getVariantId().equals(variantId))
                return trustedExactRoute.verification();
            // The LRU owns reusable knowledge; the admitted task pins what it actually visits.
            // Eviction cannot turn that task's already rejected candidate into an unknown one.
            Map<String, QIOWorkbenchVariant> pinned = QIOPlanningExecutor.taskLocal(this, LinkedHashMap::new);
            QIOWorkbenchVariant result = pinned.get(variantId);
            if (result != null) {
                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.PINNED_KNOWLEDGE_HITS, 1);
                if (lookahead != null && !lookahead.retain(result)) return null;
                return result;
            }
            synchronized (knowledgeOwner) {
                result = QIOWorkbenchKnowledge.get(knowledgeOwner, variantId);
                if (result == null) {
                    boolean baseline = true;
                    for (int slot = 0; slot < 9; slot++) {
                        if (!grid.get(slot).candidateId().equals(baselineGrid.get(slot).candidateId()))
                            baseline = false;
                    }
                    Object retainedOwner = knowledgeOwner;
                    java.util.concurrent.atomic.AtomicLong retainedRevision = knowledgeRevision;
                    result = new QIOWorkbenchVariant(definition.recipeId, variantId, frozenGrid(grid), virtualFluidSlots(grid),
                            inputs, definition.output, definition.outputAmount, baselineOutputs, dynamicDimension, dynamicEpoch,
                            baseline && baselineTrusted ? baselineOutputs : null, stateSequence, () -> {
                        QIOWorkbenchKnowledge.refresh(retainedOwner, variantId);
                        retainedRevision.incrementAndGet();
                    });
                    QIOWorkbenchKnowledge.put(knowledgeOwner, variantId, result);
                }
            }
            if (lookahead == null) result.retain(QIOWorkbenchKnowledge.taskBudget());
            else if (!lookahead.retain(result)) return null;
            pinned.put(variantId, result);
            return result;
        }

        /** Optional candidates must fit alongside every result in the validation handoff. */
        private static final class PrefetchBudget {
            private final List<QIOWorkbenchVariant> pending;
            private final QIOWorkbenchKnowledge.TaskBudget budget = QIOWorkbenchKnowledge.taskBudget();
            private boolean blocked;

            private PrefetchBudget(List<QIOWorkbenchVariant> pending) {
                this.pending = pending;
            }

            private boolean retain(QIOWorkbenchVariant value) {
                boolean alreadyPending = pending.contains(value);
                long retained = 0, reserved = 0;
                for (QIOWorkbenchVariant candidate : pending) {
                    long candidateRetained = candidate.getRetainedBytes();
                    retained += candidateRetained;
                    reserved += Math.max(0, candidate.getMaximumRetainedBytes() - candidateRetained);
                }
                if (!alreadyPending && !value.resolved()) {
                    long valueRetained = value.getRetainedBytes();
                    if (retained + valueRetained > QIOWorkbenchMemory.MIB) {
                        blocked = true;
                        return false;
                    }
                    reserved += Math.max(0, value.getMaximumRetainedBytes() - valueRetained);
                }
                // History is not an upper bound for an unknown result. Optional speculation
                // must not admit a batch that can exhaust the task when its results arrive.
                // tryRetain refuses without pinning the candidate or poisoning the task.
                blocked = !value.tryRetain(budget, reserved);
                return !blocked;
            }
        }

        /**
         * Tests whether the already validated concrete grid is still admitted by the current
         * candidate policy. This is deliberately linear in the nine slots; it must not call the
         * recursive candidate selector.
         */
        private boolean matchesBaseline(List<List<IngredientChoice>> effective) {
            if (effective.size() != baselineGrid.size()) return false;
            for (int slot = 0; slot < baselineGrid.size(); slot++) {
                String expected = baselineGrid.get(slot).candidateId();
                boolean admitted = false;
                for (IngredientChoice choice : effective.get(slot)) {
                    if (expected.equals(choice.candidateId())) {
                        admitted = true;
                        break;
                    }
                }
                if (!admitted) return false;
            }
            return true;
        }

        /**
         * Builds the trusted exact route once while publishing the snapshot. Going through a
         * trusted variant keeps stateful workbench recipes marked sequential without inserting a
         * new entry into the task knowledge cache every time the route is materialized.
         */
        @Nullable
        private QIOPlanningRoute createTrustedExactRoute() {
            QIOWorkbenchVariant variant = new QIOWorkbenchVariant(definition.recipeId,
                    variantId(baselineGrid), frozenGrid(baselineGrid), virtualFluidSlots(baselineGrid),
                    exactInputs(baselineGrid), definition.output, definition.outputAmount,
                    baselineOutputs, dynamicDimension, dynamicEpoch, baselineOutputs,
                    stateSequence, () -> {
            });
            return variant.route(0, Long.MAX_VALUE);
        }

        @Nullable
        private List<IngredientChoice> decodeVariant(String encodedVariant) {
            return decodeVariant(encodedVariant, candidates);
        }

        @Nullable
        private List<IngredientChoice> decodeVariant(String encodedVariant, List<List<IngredientChoice>> currentChoices) {
            String[] parts = encodedVariant.split("\\.", -1);
            String layoutCode = layout.name().toLowerCase(java.util.Locale.ROOT);
            if (parts.length < 2 || !layoutCode.equals(parts[1])) return null;
            List<IngredientChoice> grid = emptyGrid();
            if ("v4".equals(parts[0]) && parts.length == 11) {
                for (int slot = 0; slot < 9; slot++) {
                    IngredientChoice match = null;
                    for (IngredientChoice choice : currentChoices.get(slot)) {
                        if (variantToken(choice).equals(parts[slot + 2])) {
                            match = choice;
                            break;
                        }
                    }
                    if (match == null) return null;
                    grid.set(slot, match);
                }
                return grid;
            }
            // Numeric v2 variants belong to the removed catalog. Persisted operations rebind
            // through their exact saved input/output contract, never through a new candidate index.
            return null;
        }

        private static String variantToken(IngredientChoice choice) {
            return choice.variantToken;
        }

        private String variantId(List<IngredientChoice> grid) {
            StringBuilder encoded = new StringBuilder("v4.")
                    .append(layout.name().toLowerCase(java.util.Locale.ROOT));
            for (IngredientChoice choice : grid) encoded.append('.').append(variantToken(choice));
            return encoded.toString();
        }

        private static void checkpoint(BooleanSupplier cancelled) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException(
                        "QIO workbench materialization cancelled");
            }
        }

    }

    /** Shared by a task's domains; eviction only loses an optimization, never knowledge. */
    private static final class CandidateSearchCache {
        final QIOWeightedCache<Object, CompiledRecipe.BoundDomain.SearchCursor> cursors = new QIOWeightedCache<>(
                128, 8 * QIOWorkbenchMemory.MIB, (key, cursor) -> cursor.retainedBytes());
        // One budget across every domain in an admitted task, not one cache per recipe/frequency.
        final QIOWeightedCache<GroupedRouteKey, QIOPlanningRoute> groupedRoutes = new QIOWeightedCache<>(
                1024, 4 * QIOWorkbenchMemory.MIB, (key, route) -> groupedRouteBytes(key, route));
    }

    private static final class GroupedRouteKey {
        final Object domain;
        final String variant;
        final long revision;

        GroupedRouteKey(Object domain, String variant, long revision) {
            this.domain = domain;
            this.variant = variant;
            this.revision = revision;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(domain), variant, revision);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GroupedRouteKey key && domain == key.domain && revision == key.revision && variant.equals(key.variant);
        }
    }

    private static long groupedRouteBytes(GroupedRouteKey key, QIOPlanningRoute route) {
        long bytes = 1024 + QIOWorkbenchMemory.text(key.variant) + QIOWorkbenchMemory.text(route.getSignature()) +
                QIOWorkbenchMemory.resources(route.getExactInputs()) + QIOWorkbenchMemory.resources(route.getGuaranteedOutputs()) +
                QIOWorkbenchMemory.resources(route.getConfigurationInputs()) + QIOWorkbenchMemory.resources(route.getOptionalOutputs());
        for (mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup group : route.getCandidateInputs()) {
            bytes += 256 + QIOWorkbenchMemory.text(group.getGroupId()) + 32L * group.getSlots().size() +
                    QIOWorkbenchMemory.resources(group.getPlannedInputs());
            for (mekanism.qioprocessing.common.content.plan.QIOCandidateOption option : group.getOptions()) {
                bytes += 128 + QIOWorkbenchMemory.text(option.getCandidateId()) + QIOWorkbenchMemory.resource(option.getResource());
            }
            if (bytes > 4 * QIOWorkbenchMemory.MIB) break;
        }
        return bytes;
    }

    private static final class ParsedRouteId {

        private final ResourceLocation recipeId;
        private final String variantId;

        private ParsedRouteId(ResourceLocation recipeId, String variantId) {
            this.recipeId = recipeId;
            this.variantId = variantId;
        }
    }

    /** Capability-neutral key used only while selecting a workbench recipe. */
    private static ParsedRouteId parseStableRouteId(String stableRouteId) {
        String prefix = PROVIDER_ID + "/";
        if (!stableRouteId.startsWith(prefix)) {
            return null;
        }
        String remainder = stableRouteId.substring(prefix.length());
        int variantSeparator = remainder.lastIndexOf('/');
        if (variantSeparator <= 0 || variantSeparator == remainder.length() - 1) {
            return null;
        }
        String logical = remainder.substring(0, variantSeparator);
        String variant = remainder.substring(variantSeparator + 1);
        for (int split = logical.indexOf('/'); split >= 0;
             split = logical.indexOf('/', split + 1)) {
            String left = logical.substring(0, split);
            String right = logical.substring(split + 1);
            if (!left.equals(right)) {
                continue;
            }
            try {
                return new ParsedRouteId(new ResourceLocation(left), variant);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<IngredientChoice> emptyGrid() {
        List<IngredientChoice> grid = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            grid.add(IngredientChoice.empty());
        }
        return grid;
    }

    private static List<QIOWorkbenchDeclarations.Stack> frozenGrid(List<IngredientChoice> grid) {
        List<QIOWorkbenchDeclarations.Stack> result = new ArrayList<>(9);
        for (IngredientChoice choice : grid) result.add(choice.frozenStack);
        return Collections.unmodifiableList(result);
    }

    private static boolean[] virtualFluidSlots(List<IngredientChoice> grid) {
        boolean[] slots = new boolean[grid.size()];
        for (int index = 0; index < grid.size(); index++) {
            slots[index] = grid.get(index).isVirtualFluid();
        }
        return slots;
    }

    private static final class IngredientChoice {

        private static final IngredientChoice EMPTY = new IngredientChoice(QIOWorkbenchDeclarations.Stack.EMPTY, null, 0, false);
        @Nullable
        private final FrozenItemType itemType;
        @Nullable
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;
        private final String sortKey;
        private final String candidateId;
        private final String variantToken;
        private final QIOWorkbenchDeclarations.Stack frozenStack;

        private IngredientChoice(QIOWorkbenchDeclarations.Stack stack,
                                 @Nullable PortableResourceDescriptor resource, long amount, boolean virtualFluid) {
            frozenStack = stack;
            itemType = stack.isEmpty() ? null : new FrozenItemType(stack);
            this.resource = resource;
            this.amount = amount;
            this.virtualFluid = virtualFluid;
            sortKey = resource == null ? "" : virtualFluid ? "F|" + resource + '|' + amount + '|' +
                    stack.getResource() : "I|" + resource;
            candidateId = resource == null ? "" : sha256(sortKey);
            if (resource == null) variantToken = "_";
            else {
                byte[] digest = new byte[32];
                for (int index = 0; index < digest.length; index++)
                    digest[index] = (byte)
                            ((Character.digit(candidateId.charAt(index * 2), 16) << 4) | Character.digit(candidateId.charAt(index * 2 + 1), 16));
                variantToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            }
        }

        private static IngredientChoice empty() {
            return EMPTY;
        }

        private boolean isEmpty() {
            return resource == null;
        }

        private PortableResourceDescriptor resource() {
            return Objects.requireNonNull(resource, "resource");
        }

        private long amount() {
            return amount;
        }

        private boolean isVirtualFluid() {
            return virtualFluid;
        }

        private String sortKey() {
            return sortKey;
        }

        private String candidateId() {
            return candidateId;
        }

        private CandidateDefinition definition(ItemTypeTable itemTypes) {
            FrozenItemType frozen = Objects.requireNonNull(itemType, "itemType");
            ItemTypeDefinition itemType = itemTypes.get(itemTypes.getId(frozen.descriptor));
            return new CandidateDefinition(candidateId, itemType,
                    Objects.requireNonNull(resource, "resource"), amount, virtualFluid);
        }
    }

    private static String compiledCatalogSignature(Collection<CompiledRecipe> recipes) {
        StringBuilder canonical = new StringBuilder();
        for (CompiledRecipe recipe : recipes) {
            canonical.append(recipe.structuralSignature).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String sha256(String value) {
        return QIOHashing.sha256(value);
    }

    private static String diagnostic(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() :
                message;
    }
}
