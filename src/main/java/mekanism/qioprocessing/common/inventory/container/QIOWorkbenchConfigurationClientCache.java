package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationData.Status;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationRequest.Operation;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyPreview;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosurePreview;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded sparse workbench pages plus copy and editor state for the active terminal. */
public final class QIOWorkbenchConfigurationClientCache {

    private static final int MAX_CACHED_PAGES = 8;
    public static final int MAX_BATCH_TARGETS =
          QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS;

    private final Map<Integer, QIOWorkbenchConfigurationSnapshot> productPages =
          pageCache();
    private final Map<Integer, QIOWorkbenchConfigurationSnapshot> recipePages =
          pageCache();

    @Nullable private UUID expectedProductRequest;
    @Nullable private UUID expectedRecipeRequest;
    @Nullable private UUID expectedCandidateRequest;
    @Nullable private UUID expectedMutationRequest;
    @Nullable private UUID expectedCopyRequest;
    @Nullable private UUID expectedClosureRequest;
    @Nullable private QIOWorkbenchConfigurationSnapshot productPage;
    @Nullable private QIOWorkbenchConfigurationSnapshot recipePage;
    @Nullable private QIOWorkbenchConfigurationSnapshot candidatePage;
    @Nullable private QIOWorkbenchCopyPreview copyPreview;
    @Nullable private QIOWorkbenchClosurePreview closurePreview;
    private Status productStatus = Status.UNAVAILABLE;
    private Status recipeStatus = Status.UNAVAILABLE;
    private Status candidateStatus = Status.UNAVAILABLE;
    private Status mutationStatus = Status.UNAVAILABLE;
    private Status copyStatus = Status.UNAVAILABLE;
    private Status closureStatus = Status.UNAVAILABLE;
    private long productGeneration;
    private long recipeGeneration;
    private long candidateGeneration;
    private long mutationGeneration;
    private long copyGeneration;
    private long closureGeneration;
    private long productDirectoryGeneration;
    private long recipeDirectoryGeneration;
    private final List<ItemStack> encodingGrid = new ArrayList<>(9);
    private final List<ItemStack> batchTargets = new ArrayList<>();

    public QIOWorkbenchConfigurationClientCache() {
        for (int slot = 0; slot < 9; slot++) {
            encodingGrid.add(ItemStack.EMPTY);
        }
    }

    public void expectPage(@Nonnull PageKind kind, @Nonnull UUID requestId) {
        switch (kind) {
            case PRODUCTS -> expectedProductRequest = requestId;
            case RECIPES -> expectedRecipeRequest = requestId;
            case CANDIDATES -> expectedCandidateRequest = requestId;
        }
    }

    public void expectMutation(@Nonnull UUID requestId) {
        expectedMutationRequest = requestId;
    }

    public void expectCopy(@Nonnull UUID requestId) {
        expectedCopyRequest = requestId;
    }

    public void expectClosure(@Nonnull UUID requestId) {
        expectedClosureRequest = requestId;
    }

    public boolean applyPage(@Nonnull UUID requestId, @Nonnull Status status,
          @Nonnull QIOWorkbenchConfigurationSnapshot snapshot) {
        if (status != Status.OK) {
            return false;
        }
        return switch (snapshot.getPageKind()) {
            case PRODUCTS -> {
                if (!requestId.equals(expectedProductRequest)) yield false;
                expectedProductRequest = null;
                boolean changedDirectory = productPage == null ||
                      !sameProductDirectory(productPage, snapshot);
                if (changedDirectory) {
                    productPages.clear();
                    productDirectoryGeneration = next(productDirectoryGeneration);
                    if (recipePage != null || expectedRecipeRequest != null) {
                        clearRecipes();
                        recipeGeneration = next(recipeGeneration);
                    }
                }
                productStatus = status;
                productPage = snapshot;
                productPages.put(snapshot.getOffset(), snapshot);
                productGeneration = next(productGeneration);
                yield true;
            }
            case RECIPES -> {
                if (!requestId.equals(expectedRecipeRequest)) yield false;
                expectedRecipeRequest = null;
                boolean changedDirectory = recipePage == null ||
                      !sameRecipeDirectory(recipePage, snapshot);
                if (changedDirectory) {
                    recipePages.clear();
                    recipeDirectoryGeneration = next(recipeDirectoryGeneration);
                    if (candidatePage != null || expectedCandidateRequest != null) {
                        clearCandidates();
                        candidateGeneration = next(candidateGeneration);
                    }
                }
                recipeStatus = status;
                recipePage = snapshot;
                recipePages.put(snapshot.getOffset(), snapshot);
                recipeGeneration = next(recipeGeneration);
                yield true;
            }
            case CANDIDATES -> {
                if (!requestId.equals(expectedCandidateRequest)) yield false;
                expectedCandidateRequest = null;
                candidateStatus = status;
                candidatePage = snapshot;
                candidateGeneration = next(candidateGeneration);
                yield true;
            }
        };
    }

    public boolean applyUnavailable(@Nonnull Operation operation, @Nonnull UUID requestId,
          @Nonnull Status status) {
        return switch (operation) {
            case PRODUCTS -> {
                if (!requestId.equals(expectedProductRequest)) yield false;
                expectedProductRequest = null;
                productPages.clear();
                productPage = null;
                productStatus = status;
                productDirectoryGeneration = next(productDirectoryGeneration);
                clearRecipes();
                productGeneration = next(productGeneration);
                yield true;
            }
            case RECIPES -> {
                if (!requestId.equals(expectedRecipeRequest)) yield false;
                expectedRecipeRequest = null;
                recipePages.clear();
                recipePage = null;
                recipeStatus = status;
                recipeDirectoryGeneration = next(recipeDirectoryGeneration);
                clearCandidates();
                recipeGeneration = next(recipeGeneration);
                yield true;
            }
            case CANDIDATES -> {
                if (!requestId.equals(expectedCandidateRequest)) yield false;
                expectedCandidateRequest = null;
                candidatePage = null;
                candidateStatus = status;
                candidateGeneration = next(candidateGeneration);
                yield true;
            }
            default -> false;
        };
    }

    public boolean applyMutation(@Nonnull UUID requestId, @Nonnull Status status) {
        if (!requestId.equals(expectedMutationRequest)) {
            return false;
        }
        expectedMutationRequest = null;
        mutationStatus = status;
        if (status == Status.APPLIED || status == Status.REVISION_CONFLICT ||
            status == Status.CATALOG_CHANGED) {
            clearPages();
        }
        mutationGeneration = next(mutationGeneration);
        return true;
    }

    public boolean applyCopy(@Nonnull UUID requestId, @Nonnull Status status,
          @Nullable QIOWorkbenchCopyPreview preview) {
        if (!requestId.equals(expectedCopyRequest) ||
            status == Status.READY != (preview != null)) {
            return false;
        }
        expectedCopyRequest = null;
        copyStatus = status;
        copyPreview = preview;
        if (status == Status.APPLIED) {
            clearPages();
        }
        copyGeneration = next(copyGeneration);
        return true;
    }

    public boolean applyClosure(@Nonnull UUID requestId, @Nonnull Status status,
          @Nullable QIOWorkbenchClosurePreview preview) {
        if (!requestId.equals(expectedClosureRequest) ||
            (status == Status.READY) != (preview != null)) {
            return false;
        }
        expectedClosureRequest = null;
        closureStatus = status;
        closurePreview = preview;
        if (status == Status.APPLIED) clearPages();
        closureGeneration = next(closureGeneration);
        return true;
    }

    public void clearProducts() {
        expectedProductRequest = null;
        productPages.clear();
        productPage = null;
        productStatus = Status.UNAVAILABLE;
        productDirectoryGeneration = next(productDirectoryGeneration);
        clearRecipes();
    }

    public void clearRecipes() {
        expectedRecipeRequest = null;
        recipePages.clear();
        recipePage = null;
        recipeStatus = Status.UNAVAILABLE;
        recipeDirectoryGeneration = next(recipeDirectoryGeneration);
        clearCandidates();
    }

    public void clearCandidates() {
        expectedCandidateRequest = null;
        candidatePage = null;
        candidateStatus = Status.UNAVAILABLE;
    }

    public void clearPages() {
        clearProducts();
    }

    public void clearCopyPreview() {
        expectedCopyRequest = null;
        copyPreview = null;
        copyStatus = Status.UNAVAILABLE;
    }

    public void clearClosurePreview() {
        expectedClosureRequest = null;
        closurePreview = null;
        closureStatus = Status.UNAVAILABLE;
    }

    public void clear() {
        clearPages();
        expectedMutationRequest = null;
        mutationStatus = Status.UNAVAILABLE;
        clearCopyPreview();
        clearClosurePreview();
        clearEncodingGrid();
        clearBatchTargets();
    }

    @Nonnull
    public ItemStack getEncodingSlot(int slot) {
        if (slot < 0 || slot >= 9) return ItemStack.EMPTY;
        return encodingGrid.get(slot).copy();
    }

    public void setEncodingSlot(int slot, @Nullable ItemStack stack) {
        if (slot < 0 || slot >= 9) {
            throw new IllegalArgumentException("Workbench encoding slot is outside the 3x3 grid");
        }
        ItemStack copy = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        encodingGrid.set(slot, copy);
    }

    public void setEncodingGrid(@Nonnull List<ItemStack> grid) {
        if (grid.size() != 9) {
            throw new IllegalArgumentException("Workbench encoding grid must contain nine slots");
        }
        for (int slot = 0; slot < 9; slot++) {
            setEncodingSlot(slot, grid.get(slot));
        }
    }

    @Nonnull
    public List<ItemStack> getEncodingGrid() {
        List<ItemStack> copy = new ArrayList<>(9);
        encodingGrid.forEach(stack -> copy.add(stack.copy()));
        return Collections.unmodifiableList(copy);
    }

    public boolean hasEncodingInput() {
        return encodingGrid.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public void clearEncodingGrid() {
        for (int slot = 0; slot < 9; slot++) encodingGrid.set(slot, ItemStack.EMPTY);
    }

    public boolean addBatchTarget(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || batchTargets.size() >= MAX_BATCH_TARGETS) {
            return false;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        PortableResourceDescriptor descriptor;
        try {
            descriptor = PortableResourceDescriptor.item(copy);
        } catch (RuntimeException ignored) {
            return false;
        }
        for (ItemStack existing : batchTargets) {
            if (PortableResourceDescriptor.item(existing).equals(descriptor)) {
                return false;
            }
        }
        batchTargets.add(copy);
        return true;
    }

    public int addBatchTargets(@Nonnull Iterable<ItemStack> stacks) {
        int added = 0;
        for (ItemStack stack : stacks) {
            if (batchTargets.size() >= MAX_BATCH_TARGETS) break;
            if (addBatchTarget(stack)) added++;
        }
        return added;
    }

    @Nonnull
    public ItemStack getBatchTarget(int index) {
        return index < 0 || index >= batchTargets.size() ? ItemStack.EMPTY :
              batchTargets.get(index).copy();
    }

    @Nonnull
    public List<ItemStack> getBatchTargets() {
        List<ItemStack> copy = new ArrayList<>(batchTargets.size());
        batchTargets.forEach(stack -> copy.add(stack.copy()));
        return Collections.unmodifiableList(copy);
    }

    public boolean removeBatchTarget(int index) {
        if (index < 0 || index >= batchTargets.size()) return false;
        batchTargets.remove(index);
        return true;
    }

    public int getBatchTargetCount() {
        return batchTargets.size();
    }

    public boolean hasBatchTargets() {
        return !batchTargets.isEmpty();
    }

    public void clearBatchTargets() {
        batchTargets.clear();
    }

    @Nullable public QIOWorkbenchConfigurationSnapshot getProductPage() { return productPage; }
    @Nullable public QIOWorkbenchConfigurationSnapshot getRecipePage() { return recipePage; }
    @Nullable public QIOWorkbenchConfigurationSnapshot getCandidatePage() { return candidatePage; }
    @Nullable public QIOWorkbenchCopyPreview getCopyPreview() { return copyPreview; }
    @Nullable public QIOWorkbenchClosurePreview getClosurePreview() { return closurePreview; }
    @Nonnull public Status getProductStatus() { return productStatus; }
    @Nonnull public Status getRecipeStatus() { return recipeStatus; }
    @Nonnull public Status getCandidateStatus() { return candidateStatus; }
    @Nonnull public Status getMutationStatus() { return mutationStatus; }
    @Nonnull public Status getCopyStatus() { return copyStatus; }
    @Nonnull public Status getClosureStatus() { return closureStatus; }
    public long getProductGeneration() { return productGeneration; }
    public long getRecipeGeneration() { return recipeGeneration; }
    public long getCandidateGeneration() { return candidateGeneration; }
    public long getMutationGeneration() { return mutationGeneration; }
    public long getCopyGeneration() { return copyGeneration; }
    public long getClosureGeneration() { return closureGeneration; }
    public long getProductDirectoryGeneration() { return productDirectoryGeneration; }
    public long getRecipeDirectoryGeneration() { return recipeDirectoryGeneration; }

    public int getProductTotalSize() {
        return productPage == null ? 0 : productPage.getTotalSize();
    }

    public int getRecipeTotalSize() {
        return recipePage == null ? 0 : recipePage.getTotalSize();
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Product getProduct(int index) {
        if (index < 0 || index >= getProductTotalSize()) return null;
        for (QIOWorkbenchConfigurationSnapshot page :
              new ArrayList<>(productPages.values())) {
            int local = index - page.getOffset();
            if (local >= 0 && local < page.getProducts().size()) {
                productPages.get(page.getOffset());
                return page.getProducts().get(local);
            }
        }
        return null;
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Recipe getRecipe(int index) {
        if (index < 0 || index >= getRecipeTotalSize()) return null;
        for (QIOWorkbenchConfigurationSnapshot page :
              new ArrayList<>(recipePages.values())) {
            int local = index - page.getOffset();
            if (local >= 0 && local < page.getRecipes().size()) {
                recipePages.get(page.getOffset());
                return page.getRecipes().get(local);
            }
        }
        return null;
    }

    public boolean isProductPageLoaded(int offset) {
        return productPages.containsKey(offset);
    }

    public boolean isRecipePageLoaded(int offset) {
        return recipePages.containsKey(offset);
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Product findProduct(String productKey) {
        for (QIOWorkbenchConfigurationSnapshot page :
              new ArrayList<>(productPages.values())) {
            for (QIOWorkbenchConfigurationSnapshot.Product product : page.getProducts()) {
                if (product.getProductKey().equals(productKey)) {
                    productPages.get(page.getOffset());
                    return product;
                }
            }
        }
        return null;
    }

    public int findProductIndex(String productKey) {
        for (QIOWorkbenchConfigurationSnapshot page :
              new ArrayList<>(productPages.values())) {
            for (int index = 0; index < page.getProducts().size(); index++) {
                if (page.getProducts().get(index).getProductKey().equals(productKey)) {
                    productPages.get(page.getOffset());
                    return page.getOffset() + index;
                }
            }
        }
        return -1;
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Recipe findRecipe(String recipeId) {
        for (QIOWorkbenchConfigurationSnapshot page :
              new ArrayList<>(recipePages.values())) {
            for (QIOWorkbenchConfigurationSnapshot.Recipe recipe : page.getRecipes()) {
                if (recipe.getRecipeId().equals(recipeId)) {
                    recipePages.get(page.getOffset());
                    return recipe;
                }
            }
        }
        return null;
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Product getFirstLoadedProduct() {
        QIOWorkbenchConfigurationSnapshot first = firstPage(productPages);
        return first == null || first.getProducts().isEmpty() ? null :
              first.getProducts().get(0);
    }

    @Nullable
    public QIOWorkbenchConfigurationSnapshot.Recipe getFirstLoadedRecipe() {
        QIOWorkbenchConfigurationSnapshot first = firstPage(recipePages);
        return first == null || first.getRecipes().isEmpty() ? null :
              first.getRecipes().get(0);
    }

    private static boolean sameProductDirectory(QIOWorkbenchConfigurationSnapshot first,
          QIOWorkbenchConfigurationSnapshot second) {
        return sameSource(first, second) && first.getTotalSize() == second.getTotalSize() &&
              first.getQuery().equals(second.getQuery());
    }

    private static boolean sameRecipeDirectory(QIOWorkbenchConfigurationSnapshot first,
          QIOWorkbenchConfigurationSnapshot second) {
        return sameSource(first, second) && first.getTotalSize() == second.getTotalSize() &&
              first.getProductKey().equals(second.getProductKey());
    }

    private static boolean sameSource(QIOWorkbenchConfigurationSnapshot first,
          QIOWorkbenchConfigurationSnapshot second) {
        return first.getConfigUUID().equals(second.getConfigUUID()) &&
              first.getConfigurationRevision() == second.getConfigurationRevision() &&
              first.getCatalogRevision() == second.getCatalogRevision();
    }

    private static Map<Integer, QIOWorkbenchConfigurationSnapshot> pageCache() {
        return new LinkedHashMap<>(8, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(
                  Map.Entry<Integer, QIOWorkbenchConfigurationSnapshot> eldest) {
                return size() > MAX_CACHED_PAGES;
            }
        };
    }

    @Nullable
    private static QIOWorkbenchConfigurationSnapshot firstPage(
          Map<Integer, QIOWorkbenchConfigurationSnapshot> pages) {
        QIOWorkbenchConfigurationSnapshot first = null;
        for (QIOWorkbenchConfigurationSnapshot page : pages.values()) {
            if (first == null || page.getOffset() < first.getOffset()) first = page;
        }
        if (first != null) pages.get(first.getOffset());
        return first;
    }

    private static long next(long value) {
        return value == Long.MAX_VALUE ? 0 : value + 1;
    }
}
