package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.RecipeTarget;
import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import mekanism.qioprocessing.common.pattern.WorkbenchPatternValidator;
import mekanism.qioprocessing.common.pattern.WorkbenchValidationResult;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.recipe.*;
import mekanism.qioprocessing.common.workbench.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.io.IOException;
import java.util.*;

/**
 * Projects the new recipe/workbench core into the legacy GUI page wire model.
 * All methods run on the server thread and never read the legacy configuration.
 */
final class QIOWorkbenchCorePageService {
    private QIOWorkbenchCorePageService() {
    }

    static boolean available() {
        return WorkbenchBookRuntime.isOpen() &&
                RecipeDirectoryService.INSTANCE.state() == RecipeDirectoryService.State.READY;
    }

    static QIOWorkbenchConfigurationSnapshot products(World world, UUID frequency,
                                                      int offset, int pageSize, String query) throws IOException {
        WorkbenchSnapshot book = load(world, frequency);
        RecipeCatalog catalog = catalog();
        List<ProductRow> rows = products(catalog, book, query);
        int start = pageStart(offset, rows.size());
        int end = Math.min(rows.size(), start + pageLimit(pageSize));
        List<QIOWorkbenchConfigurationSnapshot.Product> page = new ArrayList<>();
        for (ProductRow row : rows.subList(start, end)) {
            page.add(new QIOWorkbenchConfigurationSnapshot.Product(row.key, row.output,
                    row.recipes.size(), row.enabled));
        }
        return snapshot(QIOWorkbenchConfigurationSnapshot.PageKind.PRODUCTS, frequency,
                book, offsetValue(offset, rows.size()), rows.size(), query, "", "", "", -1,
                page, Collections.emptyList(), Collections.emptyList());
    }

    static QIOWorkbenchConfigurationSnapshot recipes(World world, UUID frequency,
                                                     String productKey, int offset, int pageSize) throws IOException {
        WorkbenchSnapshot book = load(world, frequency);
        RecipeCatalog catalog = catalog();
        List<RecipeDeclaration> declarations = productRecipes(catalog, book, productKey);
        int start = pageStart(offset, declarations.size());
        int end = Math.min(declarations.size(), start + pageLimit(pageSize));
        List<QIOWorkbenchConfigurationSnapshot.Recipe> page = new ArrayList<>();
        for (int index = start; index < end; index++) {
            RecipeDeclaration recipe = declarations.get(index);
            page.add(recipePage(recipe, book, catalog, index));
        }
        return snapshot(QIOWorkbenchConfigurationSnapshot.PageKind.RECIPES, frequency,
                book, offsetValue(offset, declarations.size()), declarations.size(), "",
                productKey, "", "", -1, Collections.emptyList(), page,
                Collections.emptyList());
    }

    static QIOWorkbenchConfigurationSnapshot candidates(World world, UUID frequency,
                                                        String productKey, String recipeId, String recipeSignature, int slot,
                                                        int offset, int pageSize) throws IOException {
        WorkbenchSnapshot book = load(world, frequency);
        RecipeCatalog catalog = catalog();
        RecipeDeclaration recipe = requireRecipe(catalog, productKey, recipeId, recipeSignature);
        if (!book.routes().containsKey(recipe.id())) {
            throw new IllegalArgumentException("Workbench recipe is not imported");
        }
        if (slot < 0 || slot >= recipe.inputs().size() || recipe.inputs().get(slot).isEmptySlot()) {
            throw new IllegalArgumentException("Unknown workbench ingredient slot");
        }
        WorkbenchRoute route = book.routes().get(recipe.id());
        List<RecipeItemKey> defaults = concreteCandidates(recipe.inputs().get(slot));
        List<RecipeItemKey> choices = configurationCandidates(recipe, route, slot, defaults);
        Set<RecipeItemKey> enabled = new LinkedHashSet<>(choices);
        Set<RecipeItemKey> ordered = new LinkedHashSet<>(choices);
        ordered.addAll(defaults);
        List<QIOWorkbenchConfigurationSnapshot.Candidate> values = new ArrayList<>();
        for (RecipeItemKey key : ordered) {
            PortableResourceDescriptor resource = item(key);
            values.add(new QIOWorkbenchConfigurationSnapshot.Candidate(key.fingerprint(),
                    key.toStack(1), resource, 1, false, enabled.contains(key), values.size()));
        }
        int start = pageStart(offset, values.size());
        int end = Math.min(values.size(), start + pageLimit(pageSize));
        return snapshot(QIOWorkbenchConfigurationSnapshot.PageKind.CANDIDATES, frequency,
                book, offsetValue(offset, values.size()), values.size(), "", productKey,
                recipe.id(), recipe.signature(), slot, Collections.emptyList(),
                Collections.emptyList(), values.subList(start, end));
    }

    static QIOWorkbenchConfigurationService.MutationStatus mutate(
            QIOWorkbenchConfigurationService.Context context, long expectedRevision,
            long expectedCatalogRevision, QIOWorkbenchConfigurationMutation mutation) {
        try {
            World world = context.world();
            UUID frequency = context.frequencyUUID();
            WorkbenchSnapshot current = load(world, frequency);
            if (current.revision() != expectedRevision) {
                return QIOWorkbenchConfigurationService.MutationStatus.REVISION_CONFLICT;
            }
            if (catalogRevision(catalog()) != expectedCatalogRevision) {
                return QIOWorkbenchConfigurationService.MutationStatus.CATALOG_CHANGED;
            }
            RecipeCatalog catalog = catalog();
            if (mutation.getAction() == Action.ENCODE_TARGETS) {
                // This mutation is sent only for recursion NONE. Recursive imports use the
                // closure-preview protocol, which carries mode and cycle-filter settings.
                WorkbenchImport.Preview preview = WorkbenchCoreFacade.prepareImport(world, frequency,
                        mutation.getTargets(), mekanism.qioprocessing.common.recipe.RecipeDiscovery.Mode.DIRECT);
                if (preview.discovery().recipes().isEmpty())
                    return QIOWorkbenchConfigurationService.MutationStatus.INVALID_TARGET;
                if (preview.additions().isEmpty()) return QIOWorkbenchConfigurationService.MutationStatus.UNCHANGED;
                WorkbenchCoreFacade.commitImport(world, frequency, preview);
                return QIOWorkbenchConfigurationService.MutationStatus.APPLIED;
            }
            WorkbenchRecipeBook book = new WorkbenchRecipeBook(current);
            WorkbenchSnapshot next = apply(context.player(), world, book, catalog, mutation, current);
            if (next != current) WorkbenchBookRuntime.save(world, next);
            return next == current ? QIOWorkbenchConfigurationService.MutationStatus.UNCHANGED :
                    QIOWorkbenchConfigurationService.MutationStatus.APPLIED;
        } catch (WorkbenchRecipeBook.Rejected rejected) {
            return rejected.reason == WorkbenchRecipeBook.Rejection.STALE_REVISION ?
                    QIOWorkbenchConfigurationService.MutationStatus.REVISION_CONFLICT :
                    rejected.reason == WorkbenchRecipeBook.Rejection.STALE_CATALOG ?
                            QIOWorkbenchConfigurationService.MutationStatus.CATALOG_CHANGED :
                            QIOWorkbenchConfigurationService.MutationStatus.INVALID_TARGET;
        } catch (PatternMutationException invalidPattern) {
            return QIOWorkbenchConfigurationService.MutationStatus.INVALID_PATTERN;
        } catch (IllegalArgumentException | IllegalStateException | IOException error) {
            return QIOWorkbenchConfigurationService.MutationStatus.INVALID_TARGET;
        }
    }

    private static WorkbenchSnapshot apply(net.minecraft.entity.player.EntityPlayer player, World world,
                                           WorkbenchRecipeBook book, RecipeCatalog catalog,
                                           QIOWorkbenchConfigurationMutation mutation, WorkbenchSnapshot original) throws IOException {
        Action action = mutation.getAction();
        long revision = original.revision();
        if (action == Action.ENCODE_PATTERN) {
            return addPattern(player, world, book, catalog, original, mutation.getGrid(),
                    mutation.getProductKey(), mutation.getRecipeId(), mutation.getRecipeSignature());
        }
        switch (action) {
            case RESET_ALL -> {
                return book.clearAll(revision);
            }
            case DELETE_PRODUCT, BATCH_DELETE_PRODUCTS -> {
                Set<String> remove = new LinkedHashSet<>();
                List<String> productKeys = action == Action.DELETE_PRODUCT ?
                        Collections.singletonList(mutation.getProductKey()) : mutation.getProductKeys();
                for (RecipeDeclaration recipe : catalog.recipes()) {
                    if (productKeys.contains(productKey(recipe))) remove.add(recipe.id());
                }
                return book.remove(revision, remove);
            }
            case RESET_PRODUCT -> {
                WorkbenchSnapshot next = original;
                for (RecipeDeclaration recipe : productRecipes(catalog, original,
                        mutation.getProductKey())) {
                    WorkbenchRecipeBook currentBook = new WorkbenchRecipeBook(next);
                    next = currentBook.resetRoute(next.revision(), recipe.id());
                }
                return next;
            }
            case RESET_RECIPE -> {
                return book.resetRoute(revision, mutation.getRecipeId());
            }
            case TOGGLE_RECIPE -> {
                WorkbenchRoute route = requireRoute(original, mutation.getRecipeId());
                return book.setEnabled(revision, mutation.getRecipeId(), !route.enabled());
            }
            case TOGGLE_CANDIDATE, MOVE_CANDIDATE, MOVE_CANDIDATE_TO_TOP,
                 MOVE_CANDIDATE_TO_BOTTOM, MOVE_CANDIDATE_TO_INDEX,
                 SYNC_EQUIVALENT_CANDIDATES -> {
                return applyCandidate(book, catalog, original, mutation);
            }
            case BATCH_SET_RECIPES_ENABLED -> {
                WorkbenchSnapshot next = original;
                for (RecipeTarget target : mutation.getRecipeTargets()) {
                    WorkbenchRoute route = requireRoute(next, target.getRecipeId());
                    WorkbenchRecipeBook currentBook = new WorkbenchRecipeBook(next);
                    next = currentBook.setEnabled(next.revision(), target.getRecipeId(), mutation.isEnabled());
                }
                return next;
            }
            case DELETE_PATTERN -> {
                RecipeDeclaration recipe = requireRecipeById(catalog, mutation.getRecipeId(),
                        mutation.getRecipeSignature());
                requireRoute(original, recipe.id());
                return book.remove(revision, Collections.singletonList(recipe.id()));
            }
            case BATCH_DELETE_PATTERNS -> {
                Set<String> remove = new LinkedHashSet<>();
                for (RecipeTarget target : mutation.getRecipeTargets()) {
                    RecipeDeclaration recipe = requireRecipe(catalog, mutation.getProductKey(),
                            target.getRecipeId(), target.getRecipeSignature());
                    requireRoute(original, recipe.id());
                    remove.add(recipe.id());
                }
                return book.remove(revision, remove);
            }
            case MOVE_RECIPE, MOVE_RECIPE_TO_TOP, MOVE_RECIPE_TO_BOTTOM -> {
                RecipeDeclaration recipe = requireRecipe(catalog, mutation.getProductKey(),
                        mutation.getRecipeId(), mutation.getRecipeSignature());
                List<String> siblings = recipeSiblings(catalog, original, mutation.getProductKey());
                boolean edge = action != Action.MOVE_RECIPE;
                int direction = action == Action.MOVE_RECIPE_TO_TOP ? -1 :
                        action == Action.MOVE_RECIPE_TO_BOTTOM ? 1 : mutation.getDirection();
                return book.moveRecipe(revision, siblings, recipe.id(), direction, edge);
            }
            case BATCH_MOVE_RECIPES -> {
                List<String> siblings = recipeSiblings(catalog, original, mutation.getProductKey());
                List<String> selected = new ArrayList<>();
                for (RecipeTarget target : mutation.getRecipeTargets()) {
                    RecipeDeclaration recipe = requireRecipe(catalog, mutation.getProductKey(),
                            target.getRecipeId(), target.getRecipeSignature());
                    requireRoute(original, recipe.id());
                    selected.add(recipe.id());
                }
                return book.moveRecipes(revision, siblings, selected, mutation.getDirection(),
                        mutation.getTargetIndex() != -1);
            }
            case RESTORE_RECOVERY ->
                    throw new IllegalArgumentException("Recovery is not available in the new Workbench core");
            default -> throw new IllegalArgumentException("Unsupported Workbench mutation: " + action);
        }
    }

    private static WorkbenchSnapshot addPattern(net.minecraft.entity.player.EntityPlayer player, World world, WorkbenchRecipeBook book, RecipeCatalog catalog,
                                                WorkbenchSnapshot snapshot, List<ItemStack> grid) {
        return addPattern(player, world, book, catalog, snapshot, grid, null, null, null);
    }

    private static WorkbenchSnapshot addPattern(net.minecraft.entity.player.EntityPlayer player, World world, WorkbenchRecipeBook book, RecipeCatalog catalog,
                                                WorkbenchSnapshot snapshot, List<ItemStack> grid, String productKey, String recipeId,
                                                String recipeSignature) {
        if (recipeId != null && !recipeId.isEmpty()) {
            RecipeDeclaration recipe = requireRecipe(catalog, productKey, recipeId, recipeSignature);
            WorkbenchValidationResult result = WorkbenchPatternValidator.validate(player, world, recipe,
                    catalog.generation(), grid, UUID.randomUUID());
            if (!result.valid()) throw new PatternMutationException(result.status().name());
            return importValidatedPattern(book, catalog, snapshot, result.pattern());
        }
        VerifiedWorkbenchPattern match = null;
        // A blank frequency has no imported routes to search. Match the concrete grid against
        // the published declarations just as recursive root validation does, then import only
        // that one validated declaration. Do not discover producers of its ingredients.
        for (RecipeDeclaration recipe : catalog.recipes()) {
            WorkbenchValidationResult result = WorkbenchPatternValidator.validate(player, world, recipe,
                    catalog.generation(), grid, UUID.randomUUID());
            if (!result.valid()) continue;
            if (match != null)
                throw new PatternMutationException("Pattern matches multiple recipes; select a recipe explicitly");
            match = result.pattern();
        }
        if (match == null) throw new PatternMutationException("Pattern does not match a catalog recipe");
        return importValidatedPattern(book, catalog, snapshot, match);
    }

    /**
     * The caller persists once, after both declaration import and pattern validation succeed.
     */
    private static WorkbenchSnapshot importValidatedPattern(WorkbenchRecipeBook book, RecipeCatalog catalog,
                                                            WorkbenchSnapshot snapshot, VerifiedWorkbenchPattern pattern) {
        RecipeDeclaration recipe = catalog.byId(pattern.recipeId());
        WorkbenchRoute existing = snapshot.routes().get(recipe.id());
        if (existing == null || !existing.matches(recipe)) {
            mekanism.qioprocessing.common.recipe.RecipeDiscovery.Selection onlyRoot =
                    new mekanism.qioprocessing.common.recipe.RecipeDiscovery.Selection() {
                        @Override
                        public boolean enabled(RecipeDeclaration candidate) {
                            return recipe.id().equals(candidate.id());
                        }
                    };
            snapshot = book.commit(catalog, WorkbenchImport.prepare(snapshot, catalog,
                    Collections.singletonList(recipe.output()),
                    mekanism.qioprocessing.common.recipe.RecipeDiscovery.Mode.DIRECT, onlyRoot));
        }
        WorkbenchRoute route = snapshot.routes().get(recipe.id());
        if (route.patterns().values().stream().anyMatch(value ->
                value.contentSignature().equals(pattern.contentSignature()))) return snapshot;
        return book.addPattern(snapshot.revision(), catalog, pattern);
    }

    private static final class PatternMutationException extends IllegalArgumentException {
        private PatternMutationException(String message) {
            super(message);
        }
    }

    private static WorkbenchSnapshot applyCandidate(WorkbenchRecipeBook book, RecipeCatalog catalog,
                                                    WorkbenchSnapshot snapshot, QIOWorkbenchConfigurationMutation mutation) {
        RecipeDeclaration recipe = catalog.byId(mutation.getRecipeId());
        if (recipe == null || !recipe.signature().equals(mutation.getRecipeSignature())) {
            throw new IllegalArgumentException("Stale Workbench recipe");
        }
        WorkbenchRoute route = requireRoute(snapshot, recipe.id());
        int inputSlot = mutation.getIngredientSlot();
        if (inputSlot < 0 || inputSlot >= recipe.inputs().size() || recipe.inputs().get(inputSlot).isEmptySlot()) {
            throw new IllegalArgumentException("Unknown workbench ingredient slot");
        }
        List<RecipeItemKey> defaults = concreteCandidates(recipe.inputs().get(inputSlot));
        List<String> selected = new ArrayList<>();
        for (RecipeItemKey key : configurationCandidates(recipe, route, inputSlot, defaults))
            selected.add(key.fingerprint());
        if (mutation.getAction() == Action.SYNC_EQUIVALENT_CANDIDATES) {
            String source = selected.isEmpty() ? null : selected.get(0);
            if (source == null) throw new IllegalArgumentException("No selected candidate");
            WorkbenchSnapshot next = snapshot;
            for (int slot = 0; slot < recipe.inputs().size(); slot++) {
                if (recipe.inputs().get(slot).equals(recipe.inputs().get(mutation.getIngredientSlot()))) {
                    next = new WorkbenchRecipeBook(next).setChoices(next.revision(), catalog,
                            recipe.id(), slot, new ArrayList<>(selected));
                }
            }
            return next;
        }
        String candidate = mutation.getCandidateId();
        if (!selected.contains(candidate)) {
            if (!defaults.stream()
                    .anyMatch(key -> key.fingerprint().equals(candidate)))
                throw new IllegalArgumentException("Unknown candidate");
            selected.add(candidate);
        } else if (mutation.getAction() == Action.TOGGLE_CANDIDATE) {
            if (selected.size() <= 1) throw new IllegalArgumentException("Cannot disable the last candidate");
            selected.remove(candidate);
        } else if (mutation.getAction() == Action.MOVE_CANDIDATE ||
                mutation.getAction() == Action.MOVE_CANDIDATE_TO_TOP ||
                mutation.getAction() == Action.MOVE_CANDIDATE_TO_BOTTOM ||
                mutation.getAction() == Action.MOVE_CANDIDATE_TO_INDEX) {
            selected.remove(candidate);
            int index = mutation.getAction() == Action.MOVE_CANDIDATE_TO_TOP ? 0 :
                    mutation.getAction() == Action.MOVE_CANDIDATE_TO_BOTTOM ? selected.size() :
                            mutation.getAction() == Action.MOVE_CANDIDATE_TO_INDEX ?
                                    Math.max(0, Math.min(mutation.getTargetIndex(), selected.size())) :
                                    Math.max(0, Math.min(selected.size() + mutation.getDirection(), selected.size()));
            selected.add(index, candidate);
        }
        return book.setChoices(snapshot.revision(), catalog, recipe.id(), mutation.getIngredientSlot(), selected);
    }

    private static WorkbenchRoute requireRoute(WorkbenchSnapshot snapshot, String recipeId) {
        WorkbenchRoute route = snapshot.routes().get(recipeId);
        if (route == null) throw new IllegalArgumentException("Workbench recipe is not imported");
        return route;
    }

    /**
     * Wildcards remain in the catalog for matching/discovery, never as selectable item stacks.
     */
    private static List<RecipeItemKey> concreteCandidates(RecipeIngredient ingredient) {
        List<RecipeItemKey> result = new ArrayList<>();
        for (RecipeItemKey key : ingredient.candidates()) {
            if (key.metadata() != OreDictionary.WILDCARD_VALUE) result.add(key);
        }
        return result;
    }

    private static List<RecipeItemKey> configurationCandidates(RecipeDeclaration recipe, WorkbenchRoute route,
                                                               int slot, List<RecipeItemKey> defaults) {
        if (route == null || !route.matches(recipe) || !route.choices().containsKey(slot)) return defaults;
        Set<RecipeItemKey> result = new LinkedHashSet<>();
        for (RecipeItemKey selected : route.candidates(recipe, slot)) {
            if (selected.metadata() != OreDictionary.WILDCARD_VALUE) {
                result.add(selected);
                continue;
            }
            // Older books may have explicitly selected a wildcard. Resolve it against the frozen
            // declaration without enumerating items or changing the stored book on a page read.
            NBTTagCompound expected = selected.displayNBT();
            expected.removeTag("Damage");
            for (RecipeItemKey concrete : defaults) {
                if (!selected.itemId().equals(concrete.itemId())) continue;
                NBTTagCompound actual = concrete.displayNBT();
                actual.removeTag("Damage");
                if (expected.equals(actual)) result.add(concrete);
            }
        }
        return new ArrayList<>(result);
    }

    private static QIOWorkbenchConfigurationSnapshot.Recipe recipePage(RecipeDeclaration recipe,
                                                                       WorkbenchSnapshot book, RecipeCatalog catalog, int order) {
        List<QIOWorkbenchConfigurationSnapshot.Ingredient> ingredients = new ArrayList<>();
        WorkbenchRoute route = book.routes().get(recipe.id());
        List<RecipeIngredient> slots = new ArrayList<>();
        List<Integer> inputIndexes = new ArrayList<>();
        for (int i = 0; i < 9; i++) slots.add(RecipeIngredient.EMPTY);
        for (int i = 0; i < 9; i++) inputIndexes.add(-1);
        if (recipe.layout() == RecipeDeclaration.Layout.SHAPED) {
            for (int row = 0; row < recipe.height(); row++)
                for (int column = 0; column < recipe.width(); column++) {
                    int source = row * recipe.width() + column;
                    slots.set(row * 3 + column, recipe.inputs().get(source));
                    inputIndexes.set(row * 3 + column, source);
                }
        } else for (int i = 0; i < recipe.inputs().size() && i < 9; i++) {
            slots.set(i, recipe.inputs().get(i));
            inputIndexes.set(i, i);
        }
        for (int slot = 0; slot < 9; slot++) {
            RecipeIngredient ingredient = slots.get(slot);
            if (ingredient.isEmptySlot()) {
                ingredients.add(new QIOWorkbenchConfigurationSnapshot.Ingredient(slot, 0, 0,
                        ItemStack.EMPTY, null, 0));
                continue;
            }
            int inputIndex = inputIndexes.get(slot);
            List<RecipeItemKey> defaults = concreteCandidates(ingredient);
            List<RecipeItemKey> selected = configurationCandidates(recipe, route, inputIndex, defaults);
            ItemStack representative = selected.isEmpty() ? ItemStack.EMPTY : selected.get(0).toStack(1);
            ingredients.add(new QIOWorkbenchConfigurationSnapshot.Ingredient(slot,
                    selected.isEmpty() ? 0 : defaults.size(), selected.size(), representative, null, 0));
        }
        return new QIOWorkbenchConfigurationSnapshot.Recipe(recipe.id(), recipe.signature(),
                item(recipe.output()), recipe.outputCount(), book.enabled(recipe), order,
                recipe.layout() == RecipeDeclaration.Layout.SHAPED, recipe.width(), recipe.height(), ingredients);
    }

    private static List<ProductRow> products(RecipeCatalog catalog, WorkbenchSnapshot book, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, ProductRow> rows = new LinkedHashMap<>();
        for (RecipeDeclaration recipe : importedRecipes(catalog, book)) {
            if (recipe.output().itemId().toLowerCase(java.util.Locale.ROOT).contains(normalized) ||
                    recipe.id().toLowerCase(java.util.Locale.ROOT).contains(normalized)) {
                String key = productKey(recipe);
                ProductRow row = rows.computeIfAbsent(key, ignored -> new ProductRow(key, item(recipe.output())));
                row.recipes.add(recipe);
                if (book.enabled(recipe)) row.enabled++;
            }
        }
        List<ProductRow> result = new ArrayList<>(rows.values());
        result.sort(Comparator.comparing(row -> row.key));
        return result;
    }

    private static List<RecipeDeclaration> productRecipes(RecipeCatalog catalog,
                                                          WorkbenchSnapshot book, String productKey) {
        List<RecipeDeclaration> result = new ArrayList<>();
        for (RecipeDeclaration recipe : importedRecipes(catalog, book)) {
            if (productKey(recipe).equals(productKey)) result.add(recipe);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Unknown or unimported workbench product");
        return result;
    }

    private static List<RecipeDeclaration> importedRecipes(RecipeCatalog catalog,
                                                           WorkbenchSnapshot book) {
        List<RecipeDeclaration> result = new ArrayList<>();
        for (String recipeId : book.routes().keySet()) {
            RecipeDeclaration recipe = catalog.byId(recipeId);
            if (recipe != null) result.add(recipe);
        }
        return result;
    }

    private static List<String> recipeSiblings(RecipeCatalog catalog, WorkbenchSnapshot book,
                                               String productKey) {
        List<String> result = new ArrayList<>();
        for (String recipeId : book.routes().keySet()) {
            RecipeDeclaration recipe = catalog.byId(recipeId);
            if (recipe != null && productKey(recipe).equals(productKey)) result.add(recipeId);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Unknown or unimported workbench product");
        return result;
    }

    private static RecipeDeclaration requireRecipe(RecipeCatalog catalog, String productKey,
                                                   String recipeId, String signature) {
        RecipeDeclaration recipe = catalog.byId(recipeId);
        if (recipe == null || !productKey(recipe).equals(productKey) || !recipe.signature().equals(signature)) {
            throw new IllegalArgumentException("Unknown or changed workbench recipe");
        }
        return recipe;
    }

    private static RecipeDeclaration requireRecipeById(RecipeCatalog catalog, String recipeId,
                                                       String signature) {
        RecipeDeclaration recipe = catalog.byId(recipeId);
        if (recipe == null || !recipe.signature().equals(signature)) {
            throw new IllegalArgumentException("Unknown or changed workbench recipe");
        }
        return recipe;
    }

    private static QIOWorkbenchConfigurationSnapshot snapshot(QIOWorkbenchConfigurationSnapshot.PageKind kind,
                                                              UUID frequency, WorkbenchSnapshot book, int offset, int total, String query, String product,
                                                              String recipe, String signature, int slot, List<QIOWorkbenchConfigurationSnapshot.Product> products,
                                                              List<QIOWorkbenchConfigurationSnapshot.Recipe> recipes, List<QIOWorkbenchConfigurationSnapshot.Candidate> candidates) {
        return new QIOWorkbenchConfigurationSnapshot(kind, frequency, frequency, book.revision(),
                catalogRevision(catalog()), true, 0, offset, total, query, product, recipe, signature,
                slot, products, recipes, candidates);
    }

    private static WorkbenchSnapshot load(World world, UUID frequency) throws IOException {
        return WorkbenchBookRuntime.loadOrBlank(world, frequency);
    }

    static RecipeCatalog currentCatalog() {
        RecipeDirectoryService service = RecipeDirectoryService.INSTANCE;
        if (service.state() != RecipeDirectoryService.State.READY)
            throw new IllegalStateException("QIO recipe directory is not ready");
        return service.catalog();
    }

    private static RecipeCatalog catalog() {
        return currentCatalog();
    }

    private static PortableResourceDescriptor item(RecipeItemKey key) {
        return PortableResourceDescriptor.item(key.toStack(1));
    }

    private static String productKey(RecipeDeclaration recipe) {
        return QIOWorkbenchRecipeCatalog.productKey(item(recipe.output()));
    }

    private static int catalogRevision(RecipeCatalog catalog) {
        long value = Long.parseUnsignedLong(catalog.generation().substring(0, 16), 16) & Long.MAX_VALUE;
        return (int) (value == 0 ? 1 : value % Integer.MAX_VALUE);
    }

    static int currentCatalogRevision() {
        return catalogRevision(catalog());
    }

    private static int pageLimit(int value) {
        if (value <= 0) throw new IllegalArgumentException("Invalid page size");
        return Math.min(value, QIOWorkbenchConfigurationSnapshot.MAX_PAGE_SIZE);
    }

    private static int pageStart(int offset, int total) {
        if (offset < 0) throw new IllegalArgumentException("Invalid page offset");
        return Math.min(offset, total);
    }

    private static int offsetValue(int requested, int total) {
        return pageStart(requested, total);
    }

    private static final class ProductRow {
        private final String key;
        private final PortableResourceDescriptor output;
        private final List<RecipeDeclaration> recipes = new ArrayList<>();
        private int enabled;

        private ProductRow(String key, PortableResourceDescriptor output) {
            this.key = key;
            this.output = output;
        }
    }
}
