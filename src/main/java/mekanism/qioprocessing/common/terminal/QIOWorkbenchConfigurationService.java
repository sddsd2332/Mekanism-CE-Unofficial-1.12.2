package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration.LastCandidateException;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.RecipeTarget;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog.CandidateDefinition;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog.IngredientDefinition;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog.RecipeDefinition;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Candidate;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Ingredient;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Recipe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

/** Server-authoritative workbench configuration browsing and mutation. */
/**
 * QIO 处理模块中的 QIOWorkbenchConfigurationService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOWorkbenchConfigurationService {

    public static final int MAX_QUERY_LENGTH = 128;

    public enum MutationStatus {
        APPLIED,
        UNCHANGED,
        REVISION_CONFLICT,
        CATALOG_CHANGED,
        INVALID_TARGET,
        INVALID_PATTERN,
        READ_ONLY,
        LAST_CANDIDATE,
        UNAVAILABLE
    }

    private QIOWorkbenchConfigurationService() {
    }

    @Nonnull
    /** 打开工作台配置编辑上下文。 */
    public static Context open(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID playerUUID) {
        return open(session, network, currentAccessRevision, playerUUID, null);
    }

    @Nonnull
    /** 按目标版本打开工作台配置编辑上下文。 */
    public static Context open(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID playerUUID, @Nullable World world) {
        validateSession(session, network, currentAccessRevision, playerUUID);
        QIORecipeCatalogService.State catalog = QIORecipeCatalogService.INSTANCE.getState(
              network.getWorkbenchConfiguration());
        UUID owner = network.getLastKnownFrequencyIdentity().getOwnerUUID();
        return new Context(network, catalog, owner != null && owner.equals(playerUUID), world);
    }

    @Nonnull
    /** 查询工作台配置可用产品分页。 */
    public static QIOWorkbenchConfigurationSnapshot products(@Nonnull Context context,
          int requestedOffset, int requestedPageSize, @Nonnull String query) {
        String checkedQuery = normalizeQuery(query);
        List<Product> matches = new ArrayList<>();
        QIOWorkbenchConfiguration configuration = context.configuration();
        QIOWorkbenchRecipeCatalog.Snapshot catalog = context.catalog.getSnapshot();
        for (PortableResourceDescriptor output : catalog.getOrderedOutputs()) {
            List<ResourceLocation> recipes = catalog.getRecipeIds(output);
            if (recipes.isEmpty() || !matchesProduct(catalog, output, recipes, checkedQuery)) {
                continue;
            }
            int enabled = 0;
            for (ResourceLocation recipeId : recipes) {
                RecipeDefinition definition = catalog.getRecipeDefinition(recipeId);
                if (definition != null && configuration.isRecipeEnabled(recipeId.toString(),
                      definition.getSignature())) {
                    enabled++;
                }
            }
            matches.add(new Product(QIOWorkbenchRecipeCatalog.productKey(output), output,
                  recipes.size(), enabled));
        }
        return snapshot(context, PageKind.PRODUCTS, requestedOffset, requestedPageSize,
              matches.size(), checkedQuery, "", "", "", -1, matches,
              Collections.emptyList(), Collections.emptyList());
    }

    @Nonnull
    /** 查询产品对应的配方分页。 */
    public static QIOWorkbenchConfigurationSnapshot recipes(@Nonnull Context context,
          @Nonnull String productKey, int requestedOffset, int requestedPageSize) {
        String checkedProduct = checkedHash(productKey);
        QIOWorkbenchRecipeCatalog.Snapshot catalog = context.catalog.getSnapshot();
        PortableResourceDescriptor output = findOutput(catalog, checkedProduct);
        if (output == null) {
            throw new IllegalArgumentException("Unknown workbench product");
        }
        List<ResourceLocation> defaultIds = catalog.getRecipeIds(output);
        List<String> defaults = strings(defaultIds);
        QIOWorkbenchConfiguration configuration = context.configuration();
        List<String> ordered = configuration.orderedRecipes(checkedProduct, defaults);
        List<Recipe> values = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ResourceLocation recipeId = parseRecipe(ordered.get(index));
            RecipeDefinition definition = catalog.getRecipeDefinition(recipeId);
            if (definition == null || !definition.getOutput().equals(output)) {
                continue;
            }
            values.add(recipeSnapshot(configuration, catalog, definition, index));
        }
        return snapshot(context, PageKind.RECIPES, requestedOffset, requestedPageSize,
              values.size(), "", checkedProduct, "", "", -1,
              Collections.emptyList(), values, Collections.emptyList());
    }

    @Nonnull
    /** 查询指定配方输入槽位的候选资源。 */
    public static QIOWorkbenchConfigurationSnapshot candidates(@Nonnull Context context,
          @Nonnull String productKey, @Nonnull String recipeId,
          @Nonnull String recipeSignature, int ingredientSlot, int requestedOffset,
          int requestedPageSize) {
        String checkedProduct = checkedHash(productKey);
        RecipeDefinition definition = requireRecipe(context.catalog.getSnapshot(),
              checkedProduct, recipeId, recipeSignature);
        if (ingredientSlot < 0 || ingredientSlot >= definition.getIngredients().size()) {
            throw new IllegalArgumentException("Unknown workbench ingredient slot");
        }
        IngredientDefinition ingredient = definition.getIngredients().get(ingredientSlot);
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Workbench ingredient slot is empty");
        }
        QIOWorkbenchConfiguration configuration = context.configuration();
        QIOWorkbenchRecipeCatalog.Snapshot catalog = context.catalog.getSnapshot();
        List<String> defaults = catalog.getCandidateIds(definition, ingredientSlot);
        List<String> ordered = configuration.orderedCandidates(definition.getRecipeId().toString(),
              definition.getSignature(), ingredientSlot, defaults);
        Map<String, CandidateDefinition> byId = candidatesById(catalog, ingredient);
        List<Candidate> values = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            CandidateDefinition candidate = byId.get(ordered.get(index));
            if (candidate != null) {
                values.add(new Candidate(candidate.getCandidateId(), candidate.getDisplayStack(),
                      candidate.getResource(), candidate.getAmount(),
                      candidate.isVirtualFluid(), configuration.isCandidateEnabled(
                            definition.getRecipeId().toString(), definition.getSignature(),
                            ingredientSlot, candidate.getCandidateId()), index));
            }
        }
        return snapshot(context, PageKind.CANDIDATES, requestedOffset, requestedPageSize,
              values.size(), "", checkedProduct, definition.getRecipeId().toString(),
              definition.getSignature(), ingredientSlot, Collections.emptyList(),
              Collections.emptyList(), values);
    }

    @Nonnull
    /** 应用工作台配置变更并检查版本冲突。 */
    public static MutationStatus mutate(@Nonnull Context context,
          long expectedConfigurationRevision, long expectedCatalogRevision,
          @Nonnull QIOWorkbenchConfigurationMutation mutation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mutation, "mutation");
        if (!context.editable) {
            return MutationStatus.READ_ONLY;
        }
        if (!QIORecipeCatalogService.INSTANCE.isReady()) {
            return MutationStatus.UNAVAILABLE;
        }
        QIOWorkbenchConfiguration configuration = context.configuration();
        if (configuration.getRevision() != expectedConfigurationRevision) {
            return MutationStatus.REVISION_CONFLICT;
        }
        if (context.catalog.getRevision() != expectedCatalogRevision) {
            return MutationStatus.CATALOG_CHANGED;
        }
        long before = configuration.getRevision();
        try {
            apply(context, configuration, mutation);
        } catch (LastCandidateException e) {
            return MutationStatus.LAST_CANDIDATE;
        } catch (PatternValidationException e) {
            return MutationStatus.INVALID_PATTERN;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return MutationStatus.INVALID_TARGET;
        }
        context.network.markWorkbenchConfigurationChanged(before);
        return configuration.getRevision() == before ? MutationStatus.UNCHANGED :
              MutationStatus.APPLIED;
    }

    private static final class PatternValidationException extends IllegalArgumentException {
        private PatternValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static void apply(Context context,
          QIOWorkbenchConfiguration configuration,
          QIOWorkbenchConfigurationMutation mutation) {
        QIOWorkbenchRecipeCatalog.Snapshot catalog = context.catalog.getSnapshot();
        if (mutation.getAction() == Action.ENCODE_PATTERN) {
            if (context.world == null) {
                throw new PatternValidationException(
                      "Workbench pattern encoding requires a server world", null);
            }
            try {
                QIOWorkbenchConfiguration.EncodedPattern pattern =
                      QIOWorkbenchRecipeCatalog.resolveEncodedPattern(context.world,
                            mutation.getGrid());
                if (configuration.putEncodedPattern(pattern)) {
                    preferEncodedCandidates(context.world, configuration, pattern);
                }
            } catch (IllegalArgumentException e) {
                throw new PatternValidationException("Invalid workbench pattern", e);
            }
            return;
        }
        if (mutation.getAction() == Action.ENCODE_TARGETS) {
            if (context.world == null) {
                throw new PatternValidationException(
                      "Workbench batch encoding requires a server world", null);
            }
            try {
                List<QIOWorkbenchConfiguration.EncodedPattern> patterns =
                      QIOWorkbenchRecipeCatalog.resolveEncodedTargets(context.world,
                            mutation.getTargets());
                if (patterns.isEmpty()) {
                    throw new IllegalArgumentException(
                          "No stable workbench recipes match the requested targets");
                }
                configuration.putEncodedPatternsIfAbsent(patterns);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new PatternValidationException("Invalid workbench batch targets", e);
            }
            return;
        }
        if (mutation.getAction() == Action.DELETE_PATTERN) {
            QIOWorkbenchConfiguration.EncodedPattern existing =
                  configuration.getEncodedPattern(mutation.getRecipeId());
            if (existing == null || !existing.getRecipeSignature().equals(
                  mutation.getRecipeSignature())) {
                throw new IllegalArgumentException("Unknown or changed workbench pattern");
            }
            configuration.removeEncodedPattern(mutation.getRecipeId());
            return;
        }
        if (mutation.getAction() == Action.DELETE_PRODUCT) {
            PortableResourceDescriptor output = findOutput(catalog,
                  mutation.getProductKey());
            if (output == null || !configuration.removeEncodedProduct(
                  mutation.getProductKey(), output)) {
                throw new IllegalArgumentException("Unknown workbench product");
            }
            return;
        }
        if (mutation.getAction() == Action.BATCH_DELETE_PRODUCTS) {
            Map<String, PortableResourceDescriptor> products = new LinkedHashMap<>();
            for (String productKey : mutation.getProductKeys()) {
                PortableResourceDescriptor output = findOutput(catalog, productKey);
                if (output == null) {
                    throw new IllegalArgumentException("Unknown workbench product");
                }
                products.put(productKey, output);
            }
            configuration.removeEncodedProducts(products);
            return;
        }
        if (mutation.getAction() == Action.RESET_ALL) {
            configuration.resetAll();
            return;
        }
        PortableResourceDescriptor output = findOutput(catalog, mutation.getProductKey());
        if (output == null) {
            throw new IllegalArgumentException("Unknown workbench product");
        }
        List<String> defaultRecipes = strings(catalog.getRecipeIds(output));
        if (mutation.getAction() == Action.RESET_PRODUCT) {
            configuration.resetProduct(mutation.getProductKey(), defaultRecipes);
            return;
        }
        if (mutation.getAction() == Action.BATCH_SET_RECIPES_ENABLED ||
            mutation.getAction() == Action.BATCH_MOVE_RECIPES ||
            mutation.getAction() == Action.BATCH_DELETE_PATTERNS) {
            Map<String, String> signatures = new LinkedHashMap<>();
            List<String> recipeIds = new ArrayList<>();
            for (RecipeTarget target : mutation.getRecipeTargets()) {
                RecipeDefinition targetDefinition = requireRecipe(catalog,
                      mutation.getProductKey(), target.getRecipeId(),
                      target.getRecipeSignature());
                String targetRecipeId = targetDefinition.getRecipeId().toString();
                signatures.put(targetRecipeId, targetDefinition.getSignature());
                recipeIds.add(targetRecipeId);
            }
            switch (mutation.getAction()) {
                case BATCH_SET_RECIPES_ENABLED -> configuration.setRecipesEnabled(
                      signatures, mutation.isEnabled());
                case BATCH_MOVE_RECIPES -> configuration.moveRecipes(
                      mutation.getProductKey(), defaultRecipes, recipeIds,
                      mutation.getDirection(), mutation.getTargetIndex() != -1);
                case BATCH_DELETE_PATTERNS -> configuration.removeEncodedPatterns(
                      signatures);
                default -> throw new IllegalArgumentException(
                      "Unsupported workbench batch mutation");
            }
            return;
        }
        RecipeDefinition definition = requireRecipe(catalog, mutation.getProductKey(),
              mutation.getRecipeId(), mutation.getRecipeSignature());
        String recipeId = definition.getRecipeId().toString();
        String signature = definition.getSignature();
        switch (mutation.getAction()) {
            case TOGGLE_RECIPE -> configuration.setRecipeEnabled(recipeId, signature,
                  !configuration.isRecipeEnabled(recipeId, signature));
            case MOVE_RECIPE -> configuration.moveRecipe(mutation.getProductKey(),
                  defaultRecipes, recipeId, mutation.getDirection(), false);
            case MOVE_RECIPE_TO_TOP -> configuration.moveRecipe(mutation.getProductKey(),
                  defaultRecipes, recipeId, -1, true);
            case MOVE_RECIPE_TO_BOTTOM -> configuration.moveRecipe(mutation.getProductKey(),
                  defaultRecipes, recipeId, 1, true);
            case RESET_RECIPE -> configuration.resetRecipe(recipeId);
            case TOGGLE_CANDIDATE, MOVE_CANDIDATE, MOVE_CANDIDATE_TO_TOP,
                 MOVE_CANDIDATE_TO_BOTTOM, MOVE_CANDIDATE_TO_INDEX,
                 SYNC_EQUIVALENT_CANDIDATES -> applyCandidate(configuration, catalog,
                       definition, mutation);
            default -> throw new IllegalArgumentException("Unsupported workbench mutation");
        }
    }

    static void preferEncodedCandidates(World world,
          QIOWorkbenchConfiguration configuration,
          QIOWorkbenchConfiguration.EncodedPattern pattern) {
        QIOWorkbenchRecipeCatalog.Snapshot encoded = QIOWorkbenchRecipeCatalog.capturePatterns(
              world, Collections.singletonList(pattern), ignored -> 0);
        RecipeDefinition definition = encoded.getRecipeDefinition(pattern.getRecipeId());
        if (definition == null) return;
        Set<Integer> assignedIngredients = new HashSet<>();
        List<ItemStack> grid = pattern.getGrid();
        for (int gridSlot = 0; gridSlot < grid.size(); gridSlot++) {
            ItemStack selected = grid.get(gridSlot);
            if (selected.isEmpty()) continue;
            int ingredientSlot = matchingIngredient(encoded, definition, selected, gridSlot,
                  assignedIngredients);
            if (ingredientSlot < 0) continue;
            IngredientDefinition ingredient = definition.getIngredients().get(ingredientSlot);
            for (CandidateDefinition candidate : encoded.getCandidates(ingredient)) {
                ItemStack display = candidate.getDisplayStack();
                if (ItemStack.areItemsEqual(selected, display) &&
                    ItemStack.areItemStackTagsEqual(selected, display)) {
                    try {
                        configuration.moveCandidate(pattern.getRecipeId().toString(),
                              pattern.getRecipeSignature(), ingredientSlot,
                              encoded.getCandidateIds(definition, ingredientSlot),
                              candidate.getCandidateId(), -1, true);
                    } catch (IllegalArgumentException | IllegalStateException ignored) {
                        // The validated pattern remains usable even if a sparse preference
                        // override cannot be allocated at its configured limit.
                    }
                    assignedIngredients.add(ingredientSlot);
                    break;
                }
            }
        }
    }

    private static int matchingIngredient(QIOWorkbenchRecipeCatalog.Snapshot catalog,
          RecipeDefinition definition, ItemStack selected,
          int preferredSlot, Set<Integer> assigned) {
        if (preferredSlot >= 0 && preferredSlot < definition.getIngredients().size() &&
            !assigned.contains(preferredSlot) && matches(catalog,
                  definition.getIngredients().get(preferredSlot), selected)) {
            return preferredSlot;
        }
        for (IngredientDefinition ingredient : definition.getIngredients()) {
            if (!assigned.contains(ingredient.getSlot()) &&
                matches(catalog, ingredient, selected)) {
                return ingredient.getSlot();
            }
        }
        return -1;
    }

    private static boolean matches(QIOWorkbenchRecipeCatalog.Snapshot catalog,
          IngredientDefinition ingredient, ItemStack selected) {
        for (CandidateDefinition candidate : catalog.getCandidates(ingredient)) {
            ItemStack display = candidate.getDisplayStack();
            if (ItemStack.areItemsEqual(selected, display) &&
                ItemStack.areItemStackTagsEqual(selected, display)) return true;
        }
        return false;
    }

    private static void applyCandidate(QIOWorkbenchConfiguration configuration,
          QIOWorkbenchRecipeCatalog.Snapshot catalog, RecipeDefinition definition,
          QIOWorkbenchConfigurationMutation mutation) {
        int slot = mutation.getIngredientSlot();
        if (slot < 0 || slot >= definition.getIngredients().size() ||
            definition.getIngredients().get(slot).isEmpty()) {
            throw new IllegalArgumentException("Unknown workbench ingredient");
        }
        List<String> defaults = catalog.getCandidateIds(definition, slot);
        String recipeId = definition.getRecipeId().toString();
        String signature = definition.getSignature();
        switch (mutation.getAction()) {
            case TOGGLE_CANDIDATE -> configuration.setCandidateEnabled(recipeId, signature,
                  slot, defaults, mutation.getCandidateId(), !configuration.isCandidateEnabled(
                        recipeId, signature, slot, mutation.getCandidateId()));
            case MOVE_CANDIDATE -> configuration.moveCandidate(recipeId, signature, slot,
                  defaults, mutation.getCandidateId(), mutation.getDirection(), false);
            case MOVE_CANDIDATE_TO_TOP -> configuration.moveCandidate(recipeId, signature,
                  slot, defaults, mutation.getCandidateId(), -1, true);
            case MOVE_CANDIDATE_TO_BOTTOM -> configuration.moveCandidate(recipeId, signature,
                  slot, defaults, mutation.getCandidateId(), 1, true);
            case MOVE_CANDIDATE_TO_INDEX -> configuration.moveCandidateToIndex(recipeId,
                  signature, slot, defaults, mutation.getCandidateId(),
                  mutation.getTargetIndex());
            case SYNC_EQUIVALENT_CANDIDATES -> configuration.synchronizeEquivalentCandidates(
                  recipeId, signature, slot,
                  equivalentCandidateDefaults(catalog, definition, slot));
            default -> throw new IllegalArgumentException("Unsupported candidate mutation");
        }
    }

    private static Map<Integer, List<String>> equivalentCandidateDefaults(
          QIOWorkbenchRecipeCatalog.Snapshot catalog, RecipeDefinition definition,
          int sourceSlot) {
        IngredientDefinition source = definition.getIngredients().get(sourceSlot);
        Map<Integer, List<String>> equivalent = new LinkedHashMap<>();
        for (IngredientDefinition ingredient : definition.getIngredients()) {
            if (!ingredient.isEmpty() &&
                source.getMatcherId() == ingredient.getMatcherId()) {
                equivalent.put(ingredient.getSlot(), catalog.getCandidateIds(definition,
                      ingredient.getSlot()));
            }
        }
        return equivalent;
    }

    private static Recipe recipeSnapshot(QIOWorkbenchConfiguration configuration,
          QIOWorkbenchRecipeCatalog.Snapshot catalog, RecipeDefinition definition, int order) {
        List<Ingredient> ingredients = new ArrayList<>(9);
        for (IngredientDefinition ingredient : definition.getIngredients()) {
            if (ingredient.isEmpty()) {
                ingredients.add(new Ingredient(ingredient.getSlot(), 0, 0, ItemStack.EMPTY,
                      null, 0));
                continue;
            }
            List<String> defaults = catalog.getCandidateIds(definition, ingredient.getSlot());
            List<String> enabled = configuration.orderedEnabledCandidates(
                  definition.getRecipeId().toString(), definition.getSignature(),
                  ingredient.getSlot(), defaults);
            Map<String, CandidateDefinition> byId = candidatesById(catalog, ingredient);
            CandidateDefinition representative = enabled.isEmpty() ? null :
                  byId.get(enabled.get(0));
            if (representative == null) {
                throw new IllegalStateException("Workbench ingredient has no representative");
            }
            ingredients.add(new Ingredient(ingredient.getSlot(), defaults.size(),
                  enabled.size(), representative.getDisplayStack(),
                  representative.isVirtualFluid() ? representative.getResource() : null,
                  representative.isVirtualFluid() ? representative.getAmount() : 0));
        }
        return new Recipe(definition.getRecipeId().toString(), definition.getSignature(),
              definition.getOutput(), definition.getOutputAmount(),
              configuration.isRecipeEnabled(definition.getRecipeId().toString(),
                    definition.getSignature()), order, definition.isShaped(),
              definition.getWidth(), definition.getHeight(), ingredients);
    }

    private static QIOWorkbenchConfigurationSnapshot snapshot(Context context,
          PageKind kind, int requestedOffset, int requestedPageSize, int totalSize,
          String query, String productKey, String recipeId, String recipeSignature,
          int ingredientSlot, List<Product> products, List<Recipe> recipes,
          List<Candidate> candidates) {
        int pageSize = checkedPageSize(requestedPageSize);
        int offset = Math.max(0, Math.min(requestedOffset, totalSize));
        int end = Math.min(totalSize, offset + pageSize);
        return new QIOWorkbenchConfigurationSnapshot(kind,
              context.configuration().getConfigUUID(),
              context.configuration().getOriginUUID(),
              context.configuration().getRevision(), context.catalog.getRevision(),
              context.editable, offset, totalSize, query, productKey, recipeId,
              recipeSignature, ingredientSlot,
              kind == PageKind.PRODUCTS ? products.subList(offset, end) :
                    Collections.emptyList(),
              kind == PageKind.RECIPES ? recipes.subList(offset, end) :
                    Collections.emptyList(),
              kind == PageKind.CANDIDATES ? candidates.subList(offset, end) :
                    Collections.emptyList());
    }

    @Nullable
    private static PortableResourceDescriptor findOutput(
          QIOWorkbenchRecipeCatalog.Snapshot catalog, String productKey) {
        String checked = checkedHash(productKey);
        for (PortableResourceDescriptor output : catalog.getOrderedOutputs()) {
            if (QIOWorkbenchRecipeCatalog.productKey(output).equals(checked)) {
                return output;
            }
        }
        return null;
    }

    private static RecipeDefinition requireRecipe(
          QIOWorkbenchRecipeCatalog.Snapshot catalog, String productKey,
          String recipeId, String signature) {
        PortableResourceDescriptor output = findOutput(catalog, productKey);
        RecipeDefinition definition = catalog.getRecipeDefinition(parseRecipe(recipeId));
        if (output == null || definition == null || !definition.getOutput().equals(output) ||
            !definition.getSignature().equals(checkedHash(signature))) {
            throw new IllegalArgumentException("Unknown or changed workbench recipe");
        }
        return definition;
    }

    private static Map<String, CandidateDefinition> candidatesById(
          QIOWorkbenchRecipeCatalog.Snapshot catalog, IngredientDefinition ingredient) {
        Map<String, CandidateDefinition> result = new LinkedHashMap<>();
        for (CandidateDefinition candidate : catalog.getCandidates(ingredient)) {
            result.put(candidate.getCandidateId(), candidate);
        }
        return result;
    }

    private static boolean matchesProduct(QIOWorkbenchRecipeCatalog.Snapshot catalog,
          PortableResourceDescriptor output, List<ResourceLocation> recipes, String query) {
        if (query.isEmpty() || output.getRegistryName().toLowerCase(Locale.ROOT)
              .contains(query)) {
            return true;
        }
        ItemStack stack = output.resolveItem();
        if (!stack.isEmpty() && stack.getDisplayName().toLowerCase(Locale.ROOT)
              .contains(query)) {
            return true;
        }
        for (ResourceLocation recipe : recipes) {
            if (recipe.toString().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> strings(List<ResourceLocation> ids) {
        List<String> result = new ArrayList<>(ids.size());
        ids.forEach(id -> result.add(id.toString()));
        return result;
    }

    private static ResourceLocation parseRecipe(String recipeId) {
        String checked = Objects.requireNonNull(recipeId, "recipeId").trim();
        if (checked.isEmpty() || checked.length() > 256) {
            throw new IllegalArgumentException("Invalid workbench recipe ID");
        }
        return new ResourceLocation(checked);
    }

    private static String normalizeQuery(String query) {
        String checked = Objects.requireNonNull(query, "query").trim();
        if (checked.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Workbench query is too long");
        }
        return checked.toLowerCase(Locale.ROOT);
    }

    private static String checkedHash(String value) {
        String checked = Objects.requireNonNull(value, "hash").trim().toLowerCase(Locale.ROOT);
        if (checked.length() != 64 ||
            !checked.chars().allMatch(character -> character >= '0' && character <= '9' ||
                  character >= 'a' && character <= 'f')) {
            throw new IllegalArgumentException("Invalid workbench identifier");
        }
        return checked;
    }

    private static int checkedPageSize(int requestedPageSize) {
        if (requestedPageSize <= 0) {
            throw new IllegalArgumentException("Workbench page size must be positive");
        }
        return Math.min(requestedPageSize, QIOWorkbenchConfigurationSnapshot.MAX_PAGE_SIZE);
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision, UUID playerUUID) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(playerUUID, "playerUUID");
        if (!session.isOpen() || session.getTerminalType() !=
            QIOProcessingTerminalType.MANAGEMENT ||
            !session.getPlayerUUID().equals(playerUUID) ||
            session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Invalid QIO workbench management session");
        }
        if (currentAccessRevision < 0 || session.getAccessRevision() !=
            currentAccessRevision) {
            throw new IllegalStateException("QIO workbench access changed");
        }
    }

    public static final class Context {

        private final QIOProcessingNetworkData network;
        private final QIORecipeCatalogService.State catalog;
        private final boolean editable;
        @Nullable private final World world;

        private Context(QIOProcessingNetworkData network,
              QIORecipeCatalogService.State catalog, boolean editable, @Nullable World world) {
            this.network = network;
            this.catalog = catalog;
            this.editable = editable;
            this.world = world;
        }

        @Nonnull public QIOProcessingNetworkData getNetwork() { return network; }
        public long getCatalogRevision() { return catalog.getRevision(); }
        public boolean isEditable() { return editable; }
        private QIOWorkbenchConfiguration configuration() {
            return network.getWorkbenchConfiguration();
        }
    }
}
