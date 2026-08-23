package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiConfirmationDialog;
import mekanism.client.gui.element.window.GuiConfirmationDialog.DialogType;
import mekanism.client.gui.element.window.GuiPlayerInventoryWindow;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.RecipeTarget;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationData.Status;
import mekanism.qioprocessing.common.network.PacketQIOWorkbenchConfigurationRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Candidate;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Ingredient;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Recipe;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyPreview;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosurePreview;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Compact frequency-local logical workbench configuration editor. */
/**
 * QIO 处理模块中的 GuiQIOWorkbenchConfigurationWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOWorkbenchConfigurationWindow extends GuiWindow {

    public static final int WIDTH = 320;
    private static final int HEIGHT = 254;
    private static final int PRODUCT_PAGE_SIZE = 48;
    private static final int RECIPE_PAGE_SIZE = 16;
    private static final int CANDIDATE_PAGE_SIZE = 64;
    private static final int SEARCH_DEBOUNCE_TICKS = 5;
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private static final int CLOSURE_TIMEOUT_TICKS = 1_200;
    private static final double UUID_SCROLL_PIXELS_PER_SECOND = 12D;
    private static final double UUID_SCROLL_EDGE_PAUSE_SECONDS = 0.5D;
    private static final int SELECTED = 0xA05A83B5;
    private static final int HOVERED = 0x604A5968;
    private static final int ENABLED = 0xFF57C78B;
    private static final int DISABLED = 0xFFC75656;

    private final QIOWorkbenchConfigurationContainer container;
    private final QIOProcessingTerminalContainerState terminalState;
    private final SessionKey openedSession;
    private final GuiTextField searchField;
    private final GuiTextField sourceUUIDField;
    private final ProductGrid productGrid;
    private final RecipeList recipeList;
    private final MekanismButton copyUUIDButton;
    private final MekanismButton importButton;
    private final MekanismButton resetProductButton;
    private final MekanismButton toggleRecipeButton;
    private final MekanismButton recipeUpButton;
    private final MekanismButton recipeDownButton;
    private final MekanismButton deletePatternButton;
    private final MekanismButton addPatternButton;
    private final MekanismButton batchPatternButton;
    private final MekanismButton restoreRecoveryButton;
    private final MekanismButton resetAllButton;

    @Nullable private String selectedProductKey;
    private final Set<String> selectedProductKeys = new LinkedHashSet<>();
    @Nullable private String selectedRecipeId;
    @Nullable private String selectedRecipeSignature;
    private final Map<String, String> selectedRecipes = new LinkedHashMap<>();
    private int selectedIngredientSlot = -1;
    @Nullable private String selectedCandidateId;
    private String query = "";
    private long searchChangedAtTick = -1;
    private long clientTick;
    @Nullable private Pending productPending;
    @Nullable private Pending recipePending;
    @Nullable private Pending candidatePending;
    @Nullable private Pending mutationPending;
    @Nullable private Pending copyPending;
    @Nullable private Pending closurePending;
    @Nullable private Action pendingMutationAction;
    @Nullable private Action pendingClosureAction;
    private long lastProductGeneration = Long.MIN_VALUE;
    private long lastRecipeGeneration = Long.MIN_VALUE;
    private long lastProductDirectoryGeneration = Long.MIN_VALUE;
    private long lastRecipeDirectoryGeneration = Long.MIN_VALUE;
    private long lastCandidateGeneration = Long.MIN_VALUE;
    private long lastMutationGeneration = Long.MIN_VALUE;
    private long lastCopyGeneration = Long.MIN_VALUE;
    private long lastClosureGeneration = Long.MIN_VALUE;
    @Nullable private String statusKey;
    @Nullable private GuiQIOWorkbenchPatternWindow patternWindow;
    @Nullable private GuiQIOWorkbenchBatchWindow batchWindow;
    @Nullable private GuiQIOWorkbenchCandidateWindow candidateWindow;
    @Nullable private GuiPlayerInventoryWindow inventoryWindow;
    @Nullable private EditorMode activeEditor;
    @Nullable private DeleteTarget deleteTarget;
    private int pendingProductSelectionIndex = -1;
    private int pendingProductFirstVisibleIndex = -1;
    private int pendingRecipeSelectionIndex = -1;
    private int pendingRecipeFirstVisibleIndex = -1;
    private int pendingCandidateSelectionIndex = -1;
    private boolean restoringMutationView;
    private boolean pinnedEditorRestoreChecked;
    private boolean pinnedCandidateRestoreChecked;
    private boolean closed;
    private int productDragAnchor = -1;
    private int productDragStartRow = -1;
    private int productDragStartColumn = -1;
    private boolean productDragBaseSelected;
    private Set<String> productDragSelectionBefore = Collections.emptySet();

    public GuiQIOWorkbenchConfigurationWindow(IGuiWrapper gui, int x, int y,
          QIOWorkbenchConfigurationContainer container,
          SelectedWindowData windowData) {
        super(gui, x, y, WIDTH, HEIGHT, windowData);
        this.container = Objects.requireNonNull(container, "container");
        terminalState = container.getTerminalState();
        openedSession = SessionKey.capture(terminalState);
        interactionStrategy = InteractionStrategy.ALL;

        copyUUIDButton = addChild(button(gui, 56, 18, 78,
              "gui.mekanismqioprocessing.workbench_copy_uuid",
              "gui.mekanismqioprocessing.workbench_copy_uuid_tooltip", this::copyUUID));
        sourceUUIDField = addChild(new GuiTextField(gui, relativeX + 140, relativeY + 19,
              108, 12).setMaxLength(36));
        importButton = addChild(button(gui, 250, 18, 64,
              "gui.mekanismqioprocessing.workbench_import",
              "gui.mekanismqioprocessing.workbench_import_tooltip",
              this::requestCopyPreview));
        searchField = addChild(new GuiTextField(gui, relativeX + 6, relativeY + 38,
              128, 12).setMaxLength(128).setResponder(this::searchChanged));

        productGrid = addChild(new ProductGrid(gui, relativeX + 6, relativeY + 63,
              128, 148));
        recipeList = addChild(new RecipeList(gui, relativeX + 140, relativeY + 63,
              174, 148));
        resetProductButton = addChild(button(gui, 70, 214, 64,
              "gui.mekanismqioprocessing.workbench_reset_product_short",
              "gui.mekanismqioprocessing.workbench_reset_product_tooltip",
              this::resetProduct));
        addPatternButton = addChild(button(gui, 6, 214, 62,
              "gui.mekanismqioprocessing.workbench_pattern_add",
              "gui.mekanismqioprocessing.workbench_pattern_add_tooltip",
              this::openPatternWindow));
        toggleRecipeButton = addChild(button(gui, 140, 214, 43,
              "gui.mekanismqioprocessing.config_route_toggle",
              "gui.mekanismqioprocessing.workbench_route_toggle_tooltip",
              this::toggleRecipe));
        recipeUpButton = addChild(button(gui, 185, 214, 31,
              "gui.mekanismqioprocessing.config_route_up",
              "gui.mekanismqioprocessing.workbench_route_up_tooltip",
              () -> moveRecipe(-1)));
        recipeDownButton = addChild(button(gui, 218, 214, 31,
              "gui.mekanismqioprocessing.config_route_down",
              "gui.mekanismqioprocessing.workbench_route_down_tooltip",
              () -> moveRecipe(1)));
        deletePatternButton = addChild(button(gui, 251, 214, 63,
              "gui.mekanismqioprocessing.workbench_pattern_delete",
              "gui.mekanismqioprocessing.workbench_pattern_delete_tooltip",
              this::deleteRecipe));
        resetAllButton = addChild(button(gui, 251, 232, 63,
              "gui.mekanismqioprocessing.config_reset_all",
              "gui.mekanismqioprocessing.workbench_reset_all_tooltip", this::resetAll));
        batchPatternButton = addChild(button(gui, 6, 232, 128,
              "gui.mekanismqioprocessing.workbench_batch_open",
              "gui.mekanismqioprocessing.workbench_batch_open_tooltip",
              this::openBatchWindow));
        restoreRecoveryButton = addChild(button(gui, 140, 232, 106,
              "gui.mekanismqioprocessing.workbench_recovery_restore",
              "gui.mekanismqioprocessing.workbench_recovery_restore_tooltip",
              this::restoreRecovery));

        cache().clear();
        if (openedSession != null) {
            requestProducts(0);
        }
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        SessionKey current = SessionKey.capture(terminalState);
        if (openedSession == null || !openedSession.equals(current)) {
            closeLater();
            return;
        }
        applyDebouncedSearch();
        acknowledge();
        synchronizePages();
        loadVisiblePages();
        restorePinnedEditorWindows();
        retryTimedOutRequests();
        updateButtons();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            closeEditorWindows();
            cache().clear();
            super.close();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_configuration_title"), 5);
        UUID configUUID = configUUID();
        drawUUID(configUUID == null ? "-" : configUUID.toString(), 6, 18, 48, 14);
        if (sourceUUIDField.isEmpty() && !sourceUUIDField.isFocused()) {
            drawScaledScrollingString(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.workbench_source_uuid"), 144, 22,
                  TextAlignment.LEFT, 0x707070, 100, 0, false, 0.72F,
                  getTimeOpened());
        }
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawScaledScrollingString(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.config_search"), 9, 41,
                  TextAlignment.LEFT, 0x707070, 122, 0, false, 0.72F,
                  getTimeOpened());
        }
        drawHeading("gui.mekanismqioprocessing.workbench_products", 6, 128);
        drawHeading("gui.mekanismqioprocessing.workbench_routes", 140, 174);
        if (statusKey != null) {
            drawScaledScrollingString(new TextComponentTranslation(statusKey), 140, 237,
                  TextAlignment.LEFT, 0xB04040, 107, 0, false, 0.72F,
                  getTimeOpened());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return productGrid.mouseScrolled(mouseX, mouseY, delta) ||
              recipeList.mouseScrolled(mouseX, mouseY, delta) ||
              super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        deleteTarget = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onFocusLost() {
        deleteTarget = null;
        super.onFocusLost();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == Keyboard.KEY_DELETE && deleteTarget != null) {
            deleteLastSelection();
            return true;
        }
        return false;
    }

    private void drawHeading(String key, int x, int width) {
        drawScaledScrollingString(new TextComponentTranslation(key), x, 54,
              TextAlignment.LEFT, 0x404040, width, 0, false, 0.76F, getTimeOpened());
    }

    private void drawUUID(String text, int x, int y, int width, int height) {
        int textWidth = getFont().getStringWidth(text);
        int drawX = relativeX + x;
        int drawY = relativeY + y;
        boolean scrolling = textWidth > width;
        float offset = 0;
        if (scrolling) {
            enableGuiScissor(drawX, drawY, drawX + width, drawY + height);
            offset = uuidScrollingOffset(textWidth, width);
        }
        float centeredY = drawY + (height - getFont().FONT_HEIGHT) / 2F;
        getFont().drawString(text, drawX - offset, centeredY, 0x404040, false);
        if (scrolling) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private float uuidScrollingOffset(double contentWidth, double areaWidth) {
        double overflow = contentWidth - areaWidth;
        if (overflow <= 0) return 0;
        double seconds = Math.max(0, GuiElement.getMillis() - getTimeOpened()) / 1_000D;
        double travel = overflow / UUID_SCROLL_PIXELS_PER_SECOND;
        double cycle = UUID_SCROLL_EDGE_PAUSE_SECONDS * 2 + travel * 2;
        double position = seconds % cycle;
        if (position < UUID_SCROLL_EDGE_PAUSE_SECONDS) return 0;
        position -= UUID_SCROLL_EDGE_PAUSE_SECONDS;
        if (position < travel) return (float) (position * UUID_SCROLL_PIXELS_PER_SECOND);
        if (position < travel + UUID_SCROLL_EDGE_PAUSE_SECONDS) return (float) overflow;
        position -= travel + UUID_SCROLL_EDGE_PAUSE_SECONDS;
        return (float) (overflow - position * UUID_SCROLL_PIXELS_PER_SECOND);
    }

    private void enableGuiScissor(int minX, int minY, int maxX, int maxY) {
        double scaleX = minecraft.displayWidth / (double) minecraft.currentScreen.width;
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = (int) Math.floor((getGuiLeft() + minX) * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight -
              (getGuiTop() + maxY) * scaleY);
        int scissorWidth = Math.max(0, (int) Math.ceil((maxX - minX) * scaleX));
        int scissorHeight = Math.max(0, (int) Math.ceil((maxY - minY) * scaleY));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private MekanismButton button(IGuiWrapper gui, int x, int y, int width,
          String key, String tooltipKey, Runnable action) {
        return new MekanismButton(gui, relativeX + x, relativeY + y, width, 14,
              new TextComponentTranslation(key), action, getOnHover(() ->
                    new TextComponentTranslation(tooltipKey)));
    }

    private void acknowledge() {
        QIOWorkbenchConfigurationClientCache cache = cache();
        if (productPending != null && productPending.generation !=
            cache.getProductGeneration()) productPending = null;
        if (recipePending != null && recipePending.generation !=
            cache.getRecipeGeneration()) recipePending = null;
        if (candidatePending != null && candidatePending.generation !=
            cache.getCandidateGeneration()) candidatePending = null;

        if (cache.getMutationGeneration() != lastMutationGeneration) {
            lastMutationGeneration = cache.getMutationGeneration();
            if (mutationPending != null) {
                mutationPending = null;
                Status mutationStatus = cache.getMutationStatus();
                Action completedAction = pendingMutationAction;
                statusKey = statusKey(mutationStatus, completedAction);
                if (pendingMutationAction == Action.ENCODE_PATTERN &&
                    (mutationStatus == Status.APPLIED || mutationStatus == Status.UNCHANGED)) {
                    cache.clearEncodingGrid();
                }
                if (pendingMutationAction == Action.ENCODE_TARGETS &&
                    (mutationStatus == Status.APPLIED || mutationStatus == Status.UNCHANGED)) {
                    cache.clearBatchTargets();
                }
                pendingMutationAction = null;
                if (mutationStatus == Status.APPLIED) {
                    if (completedAction == Action.DELETE_PRODUCT ||
                        completedAction == Action.BATCH_DELETE_PRODUCTS) {
                        clearMutationViewRestore();
                        clearProductSelection();
                        reloadProducts();
                    } else {
                        if (completedAction == Action.DELETE_PATTERN ||
                            completedAction == Action.BATCH_DELETE_PATTERNS) {
                            clearRecipeSelection();
                        }
                        restoringMutationView = pendingProductSelectionIndex >= 0 &&
                              selectedProductKey != null;
                        reloadProductsPreservingSelection();
                    }
                } else if (mutationStatus == Status.REVISION_CONFLICT ||
                           mutationStatus == Status.CATALOG_CHANGED) {
                    clearMutationViewRestore();
                    reloadProducts();
                } else {
                    clearMutationViewRestore();
                }
            }
        }
        if (cache.getCopyGeneration() != lastCopyGeneration) {
            lastCopyGeneration = cache.getCopyGeneration();
            if (copyPending != null) {
                copyPending = null;
                Status status = cache.getCopyStatus();
                statusKey = statusKey(status);
                QIOWorkbenchCopyPreview preview = cache.getCopyPreview();
                if (status == Status.READY && preview != null) {
                    showCopyConfirmation(preview);
                } else if (status == Status.APPLIED) {
                    reloadProducts();
                }
            }
        }
        if (cache.getClosureGeneration() != lastClosureGeneration) {
            lastClosureGeneration = cache.getClosureGeneration();
            if (closurePending != null) {
                closurePending = null;
                Status status = cache.getClosureStatus();
                statusKey = statusKey(status, pendingClosureAction);
                QIOWorkbenchClosurePreview preview = cache.getClosurePreview();
                if (status == Status.READY && preview != null) {
                    showClosureConfirmation(preview);
                } else {
                    if (status == Status.APPLIED || status == Status.UNCHANGED) {
                        completeClosureEditor();
                        reloadProducts();
                    }
                    pendingClosureAction = null;
                }
            }
        }
    }

    private void synchronizePages() {
        QIOWorkbenchConfigurationClientCache cache = cache();
        boolean productDirectoryChanged = cache.getProductDirectoryGeneration() !=
              lastProductDirectoryGeneration;
        if (productDirectoryChanged) {
            lastProductDirectoryGeneration = cache.getProductDirectoryGeneration();
        }
        if (productDirectoryChanged ||
            cache.getProductGeneration() != lastProductGeneration) {
            lastProductGeneration = cache.getProductGeneration();
            Product selected = selectedProductKey == null ? null :
                  cache.findProduct(selectedProductKey);
            if (cache.getProductPage() == null) {
                if (productPending == null) selectProduct(null, false);
            } else if (productDirectoryChanged || selectedProductKey == null) {
                if (selected == null && selectedProductKey != null) {
                    selectedProductKeys.remove(selectedProductKey);
                }
                selectProduct(selected == null ? cache.getFirstLoadedProduct() : selected,
                      false);
            }
            if (restoringMutationView && cache.getProductPage() != null) {
                if (selected != null && Objects.equals(selected.getProductKey(),
                      selectedProductKey)) {
                    productGrid.restoreFirstVisibleIndex(pendingProductFirstVisibleIndex);
                    pendingProductSelectionIndex = -1;
                    pendingProductFirstVisibleIndex = -1;
                    if (selectedRecipeId == null) clearMutationViewRestore();
                } else {
                    clearMutationViewRestore();
                }
            }
            if (selectedProductKey != null && cache.getRecipePage() == null &&
                recipePending == null) {
                requestRecipes(restoringMutationView && pendingRecipeSelectionIndex >= 0 ?
                      pageOffset(pendingRecipeSelectionIndex, RECIPE_PAGE_SIZE) :
                      recipeList.getReloadOffset());
            }
        }
        boolean recipeDirectoryChanged = cache.getRecipeDirectoryGeneration() !=
              lastRecipeDirectoryGeneration;
        if (recipeDirectoryChanged) {
            lastRecipeDirectoryGeneration = cache.getRecipeDirectoryGeneration();
        }
        if (recipeDirectoryChanged || cache.getRecipeGeneration() != lastRecipeGeneration) {
            lastRecipeGeneration = cache.getRecipeGeneration();
            Recipe selected = selectedRecipeId == null ? null :
                  cache.findRecipe(selectedRecipeId);
            if (cache.getRecipePage() == null || !Objects.equals(
                  cache.getRecipePage().getProductKey(), selectedProductKey)) {
                if (productPending == null && recipePending == null) selectRecipe(null);
            } else if (recipeDirectoryChanged || selectedRecipeId == null) {
                if (selected == null && selectedRecipeId != null) {
                    selectedRecipes.remove(selectedRecipeId);
                }
                selectRecipe(selected == null ? cache.getFirstLoadedRecipe() : selected);
            }
            if (restoringMutationView && cache.getRecipePage() != null) {
                if (selected != null && Objects.equals(selected.getRecipeId(),
                      selectedRecipeId)) {
                    recipeList.restoreScroll(pendingRecipeFirstVisibleIndex,
                          pendingRecipeSelectionIndex);
                }
                clearMutationViewRestore();
            }
        }
        if (selectedIngredientSlot >= 0 && cache.getCandidatePage() == null &&
            candidatePending == null) {
            Ingredient selected = selectedIngredient();
            if (selected != null && selected.getCandidateCount() > 1) {
                requestCandidates(0);
            }
        }
        if (cache.getCandidateGeneration() != lastCandidateGeneration) {
            lastCandidateGeneration = cache.getCandidateGeneration();
            QIOWorkbenchConfigurationSnapshot page = cache.getCandidatePage();
            boolean matchesSelection = page != null &&
                  Objects.equals(page.getRecipeId(), selectedRecipeId) &&
                  page.getIngredientSlot() == selectedIngredientSlot;
            if (!matchesSelection && (productPending != null || recipePending != null ||
                candidatePending != null)) return;
            List<Candidate> candidates = matchesSelection ? page.getCandidates() :
                  Collections.emptyList();
            Candidate selected = candidates.stream().filter(candidate ->
                  candidate.getCandidateId().equals(selectedCandidateId)).findFirst()
                  .orElse(candidates.isEmpty() ? null : candidates.get(0));
            selectedCandidateId = selected == null ? null : selected.getCandidateId();
        }
    }

    private void loadVisiblePages() {
        if (searchChangedAtTick < 0 && productPending == null) {
            int offset = productGrid.getMissingPageOffset(PRODUCT_PAGE_SIZE);
            if (offset >= 0) requestProducts(offset);
        }
        if (selectedProductKey != null && recipePending == null) {
            int offset = recipeList.getMissingPageOffset(RECIPE_PAGE_SIZE);
            if (offset >= 0) requestRecipes(offset);
        }
    }

    private void retryTimedOutRequests() {
        if (productPending != null && productPending.timedOut(clientTick)) {
            int offset = productPending.offset;
            productPending = null;
            requestProducts(offset);
        }
        if (recipePending != null && recipePending.timedOut(clientTick)) {
            int offset = recipePending.offset;
            recipePending = null;
            requestRecipes(offset);
        }
        if (candidatePending != null && candidatePending.timedOut(clientTick)) {
            int offset = candidatePending.offset;
            candidatePending = null;
            requestCandidates(offset);
        }
        if (mutationPending != null && mutationPending.timedOut(clientTick)) {
            mutationPending = null;
            pendingMutationAction = null;
            clearMutationViewRestore();
            statusKey = "gui.mekanismqioprocessing.workbench_status_timeout";
        }
        if (copyPending != null && copyPending.timedOut(clientTick)) {
            copyPending = null;
            statusKey = "gui.mekanismqioprocessing.workbench_status_timeout";
        }
        if (closurePending != null &&
            clientTick - closurePending.sentAtTick >= CLOSURE_TIMEOUT_TICKS) {
            closurePending = null;
            pendingClosureAction = null;
            statusKey = "gui.mekanismqioprocessing.workbench_status_timeout";
        }
    }

    private void selectProduct(@Nullable Product product, boolean request) {
        String key = product == null ? null : product.getProductKey();
        boolean changed = !Objects.equals(key, selectedProductKey);
        selectedProductKey = key;
        if (key != null) selectedProductKeys.add(key);
        if (key == null) selectedProductKeys.clear();
        if (changed) {
            selectedRecipeId = null;
            selectedRecipeSignature = null;
            selectedRecipes.clear();
            selectedIngredientSlot = -1;
            selectedCandidateId = null;
            cache().clearRecipes();
            recipePending = null;
            candidatePending = null;
            recipeList.resetScroll();
            if (candidateWindow != null) candidateWindow.recipeChanged();
        }
        if (request && key != null) {
            requestRecipes(0);
        }
    }

    private void selectOnlyProduct(@Nullable Product product, boolean request) {
        selectedProductKeys.clear();
        selectProduct(product, request);
    }

    private void refreshPrimaryProductFromSelection() {
        Product primary = selectedProductKey == null ? null :
              cache().findProduct(selectedProductKey);
        if (primary == null || !selectedProductKeys.contains(primary.getProductKey())) {
            primary = firstSelectedProduct();
        }
        String key = primary == null ? null : primary.getProductKey();
        if (!Objects.equals(key, selectedProductKey)) {
            selectedProductKey = key;
            selectedRecipeId = null;
            selectedRecipeSignature = null;
            selectedRecipes.clear();
            selectedIngredientSlot = -1;
            selectedCandidateId = null;
            cache().clearRecipes();
            cache().clearCandidates();
            recipePending = null;
            candidatePending = null;
            recipeList.resetScroll();
            if (candidateWindow != null) candidateWindow.recipeChanged();
        }
    }

    private void toggleProductSelection(@Nonnull Product product) {
        String key = product.getProductKey();
        if (selectedProductKeys.contains(key)) {
            selectedProductKeys.remove(key);
            if (Objects.equals(selectedProductKey, key)) {
                Product replacement = firstSelectedProduct();
                selectProduct(replacement, replacement != null);
            }
        } else {
            if (selectedProductKeys.size() >=
                QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS) return;
            selectedProductKeys.add(key);
            selectProduct(product, true);
        }
    }

    @Nullable
    private Product firstSelectedProduct() {
        for (String key : selectedProductKeys) {
            Product product = cache().findProduct(key);
            if (product != null) return product;
        }
        return null;
    }

    private void selectRecipe(@Nullable Recipe recipe) {
        String id = recipe == null ? null : recipe.getRecipeId();
        if (Objects.equals(id, selectedRecipeId)) {
            if (id != null) selectedRecipes.put(id, recipe.getSignature());
            return;
        }
        selectedRecipeId = id;
        selectedRecipeSignature = recipe == null ? null : recipe.getSignature();
        if (recipe != null) selectedRecipes.put(id, recipe.getSignature());
        selectedIngredientSlot = -1;
        selectedCandidateId = null;
        cache().clearCandidates();
        candidatePending = null;
        if (candidateWindow != null) {
            candidateWindow.recipeChanged();
            selectIngredient(preferredIngredient());
        }
    }

    private void selectOnlyRecipe(@Nullable Recipe recipe) {
        selectedRecipes.clear();
        selectRecipe(recipe);
    }

    private void toggleRecipeSelection(@Nonnull Recipe recipe) {
        String id = recipe.getRecipeId();
        if (selectedRecipes.containsKey(id)) {
            selectedRecipes.remove(id);
            if (Objects.equals(selectedRecipeId, id)) {
                Recipe replacement = firstSelectedRecipe();
                selectRecipe(replacement);
            }
        } else {
            if (selectedRecipes.size() >=
                QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS) return;
            selectedRecipes.put(id, recipe.getSignature());
            selectRecipe(recipe);
        }
    }

    @Nullable
    private Recipe firstSelectedRecipe() {
        for (String id : selectedRecipes.keySet()) {
            Recipe recipe = cache().findRecipe(id);
            if (recipe != null) return recipe;
        }
        return null;
    }

    private void clearProductSelection() {
        selectedProductKeys.clear();
        selectedProductKey = null;
        clearRecipeSelection();
    }

    private void clearRecipeSelection() {
        selectedRecipes.clear();
        selectedRecipeId = null;
        selectedRecipeSignature = null;
        selectedIngredientSlot = -1;
        selectedCandidateId = null;
        cache().clearRecipes();
        cache().clearCandidates();
        recipePending = null;
        candidatePending = null;
        if (candidateWindow != null) candidateWindow.recipeChanged();
    }

    void selectIngredient(@Nullable Ingredient ingredient) {
        int slot = ingredient == null ? -1 : ingredient.getSlot();
        if (slot == selectedIngredientSlot) return;
        selectedIngredientSlot = slot;
        selectedCandidateId = null;
        cache().clearCandidates();
        candidatePending = null;
        if (candidateWindow != null) candidateWindow.ingredientChanged();
        if (ingredient != null && ingredient.getCandidateCount() > 1) {
            requestCandidates(0);
        }
    }

    private void openRecipeConfigurationWindow() {
        if (selectedRecipe() == null) return;
        if (selectedIngredient() == null) selectIngredient(preferredIngredient());
        if (candidateWindow == null) {
            candidateWindow = new GuiQIOWorkbenchCandidateWindow(gui(), this);
            gui().addWindow(candidateWindow);
        } else {
            focusWindow(candidateWindow);
        }
    }

    private void closeCandidateWindow() {
        GuiQIOWorkbenchCandidateWindow window = candidateWindow;
        candidateWindow = null;
        if (window != null) window.close();
    }

    void candidateWindowClosed() {
        candidateWindow = null;
    }

    @Nullable
    private Ingredient preferredIngredient() {
        Recipe recipe = selectedRecipe();
        if (recipe == null) return null;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.getCandidateCount() > 1) return ingredient;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) return ingredient;
        }
        return null;
    }

    private void requestProducts(int offset) {
        if (productPending != null || !sessionValid()) return;
        int requestedOffset = Math.max(0, offset);
        UUID requestId = UUID.randomUUID();
        cache().expectPage(PageKind.PRODUCTS, requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.products(
                    container.getTerminalWindowId(),
                    terminalState, requestId, requestedOffset, PRODUCT_PAGE_SIZE, query));
        productPending = new Pending(cache().getProductGeneration(), clientTick,
              requestedOffset);
    }

    private void requestRecipes(int offset) {
        if (recipePending != null || selectedProductKey == null || !sessionValid()) return;
        int requestedOffset = Math.max(0, offset);
        UUID requestId = UUID.randomUUID();
        cache().expectPage(PageKind.RECIPES, requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.recipes(
                    container.getTerminalWindowId(),
                    terminalState, requestId, selectedProductKey, requestedOffset,
                    RECIPE_PAGE_SIZE));
        recipePending = new Pending(cache().getRecipeGeneration(), clientTick,
              requestedOffset);
    }

    private void requestCandidates(int offset) {
        if (candidatePending != null || selectedProductKey == null ||
            selectedRecipeId == null || selectedRecipeSignature == null ||
            selectedIngredientSlot < 0 || !sessionValid()) return;
        int requestedOffset = Math.max(0, offset);
        UUID requestId = UUID.randomUUID();
        cache().expectPage(PageKind.CANDIDATES, requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.candidates(
                    container.getTerminalWindowId(),
                    terminalState, requestId, selectedProductKey, selectedRecipeId,
                    selectedRecipeSignature, selectedIngredientSlot, requestedOffset,
                    CANDIDATE_PAGE_SIZE));
        candidatePending = new Pending(cache().getCandidateGeneration(), clientTick,
              requestedOffset);
    }

    private boolean sendMutation(QIOWorkbenchConfigurationMutation mutation) {
        QIOWorkbenchConfigurationSnapshot snapshot = identitySnapshot();
        if (snapshot == null || mutationPending != null || !snapshot.isEditable()) return false;
        UUID requestId = UUID.randomUUID();
        rememberPredictedMutationSelection(mutation);
        cache().expectMutation(requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.mutate(
                    container.getTerminalWindowId(),
                    terminalState, requestId, snapshot.getConfigurationRevision(),
                    snapshot.getCatalogRevision(), mutation));
        mutationPending = new Pending(cache().getMutationGeneration(), clientTick);
        pendingMutationAction = mutation.getAction();
        statusKey = null;
        return true;
    }

    private void rememberPredictedMutationSelection(
          QIOWorkbenchConfigurationMutation mutation) {
        clearMutationViewRestore();
        if (selectedProductKey != null) {
            pendingProductSelectionIndex = cache().findProductIndex(selectedProductKey);
            pendingProductFirstVisibleIndex = productGrid.getReloadOffset();
        }
        Recipe recipe = selectedRecipe();
        if (recipe != null) {
            pendingRecipeFirstVisibleIndex = recipeList.getReloadOffset();
            pendingRecipeSelectionIndex = switch (mutation.getAction()) {
                case MOVE_RECIPE -> clampIndex((long) recipe.getOrder() +
                      mutation.getDirection(), cache().getRecipeTotalSize());
                case MOVE_RECIPE_TO_TOP -> 0;
                case MOVE_RECIPE_TO_BOTTOM -> Math.max(0,
                      cache().getRecipeTotalSize() - 1);
                default -> recipe.getOrder();
            };
        }
        Candidate candidate = selectedCandidate();
        if (candidate != null) {
            int candidateCount = cache().getCandidatePage() == null ? 0 :
                  cache().getCandidatePage().getTotalSize();
            pendingCandidateSelectionIndex = switch (mutation.getAction()) {
                case MOVE_CANDIDATE -> clampIndex((long) candidate.getOrder() +
                      mutation.getDirection(), candidateCount);
                case MOVE_CANDIDATE_TO_TOP -> 0;
                case MOVE_CANDIDATE_TO_BOTTOM -> Math.max(0, candidateCount - 1);
                case MOVE_CANDIDATE_TO_INDEX -> clampIndex(mutation.getTargetIndex(),
                      candidateCount);
                default -> candidate.getOrder();
            };
        }
    }

    private void clearMutationViewRestore() {
        restoringMutationView = false;
        pendingProductSelectionIndex = -1;
        pendingProductFirstVisibleIndex = -1;
        pendingRecipeSelectionIndex = -1;
        pendingRecipeFirstVisibleIndex = -1;
        pendingCandidateSelectionIndex = -1;
    }

    private static int clampIndex(long index, int size) {
        return size <= 0 ? 0 : (int) Math.max(0, Math.min(size - 1L, index));
    }

    private static int pageOffset(int index, int pageSize) {
        return index < 0 ? 0 : index / pageSize * pageSize;
    }

    private boolean sendClosurePreview(QIOWorkbenchConfigurationMutation mutation,
          QIOWorkbenchClosureMode closureMode, boolean skipCyclicRecipes) {
        QIOWorkbenchConfigurationSnapshot snapshot = identitySnapshot();
        if (snapshot == null || closurePending != null || mutationPending != null ||
            !snapshot.isEditable() || closureMode == QIOWorkbenchClosureMode.NONE) {
            return false;
        }
        UUID requestId = UUID.randomUUID();
        cache().expectClosure(requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.closurePreview(
                    container.getTerminalWindowId(), terminalState, requestId,
                    snapshot.getConfigurationRevision(), snapshot.getCatalogRevision(),
                    mutation, closureMode, skipCyclicRecipes));
        closurePending = new Pending(cache().getClosureGeneration(), clientTick);
        pendingClosureAction = mutation.getAction();
        statusKey = "gui.mekanismqioprocessing.workbench_status_scanning";
        return true;
    }

    private void showClosureConfirmation(QIOWorkbenchClosurePreview preview) {
        String warning = preview.isTruncated() ? new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_closure_truncated").getFormattedText() : "";
        TextComponentTranslation message = new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_closure_confirm",
              preview.getRootRecipeCount(), preview.getNewProductCount(),
              preview.getNewRecipeCount(), preview.getExistingRecipeCount(),
              preview.getLeafMaterialCount(), preview.getCycleCount(),
              preview.getSkippedCyclicRecipeCount(), warning);
        GuiConfirmationDialog.show(gui(), message,
              () -> confirmClosure(preview.getConfirmationNonce()),
              preview.getCycleCount() > 0 ? DialogType.DANGER : DialogType.NORMAL);
    }

    private void confirmClosure(UUID confirmationNonce) {
        if (closurePending != null || pendingClosureAction == null || !editable()) return;
        UUID requestId = UUID.randomUUID();
        cache().expectClosure(requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.closureConfirm(
                    container.getTerminalWindowId(), terminalState, requestId,
                    confirmationNonce));
        closurePending = new Pending(cache().getClosureGeneration(), clientTick);
        statusKey = null;
    }

    private void completeClosureEditor() {
        if (pendingClosureAction == Action.ENCODE_PATTERN) {
            cache().clearEncodingGrid();
            if (patternWindow != null) patternWindow.close();
        } else if (pendingClosureAction == Action.ENCODE_TARGETS) {
            cache().clearBatchTargets();
            if (batchWindow != null) batchWindow.close();
        }
    }

    private void searchChanged(String value) {
        clearMutationViewRestore();
        query = value == null ? "" : value.trim();
        searchChangedAtTick = clientTick;
        selectedProductKey = null;
        selectedProductKeys.clear();
        selectedRecipeId = null;
        selectedRecipeSignature = null;
        selectedRecipes.clear();
        selectedIngredientSlot = -1;
        selectedCandidateId = null;
        cache().clearPages();
        productPending = null;
        recipePending = null;
        candidatePending = null;
        productGrid.resetScroll();
        recipeList.resetScroll();
        if (candidateWindow != null) candidateWindow.recipeChanged();
    }

    private void applyDebouncedSearch() {
        if (searchChangedAtTick >= 0 &&
            clientTick - searchChangedAtTick >= SEARCH_DEBOUNCE_TICKS) {
            searchChangedAtTick = -1;
            requestProducts(0);
        }
    }

    private void toggleRecipe() {
        Recipe recipe = selectedRecipe();
        if (recipe == null || selectedProductKey == null) return;
        List<RecipeTarget> targets = selectedRecipeTargets();
        if (targets.size() <= 1) {
            sendMutation(QIOWorkbenchConfigurationMutation.recipe(Action.TOGGLE_RECIPE,
                  selectedProductKey, recipe.getRecipeId(), recipe.getSignature()));
        } else {
            sendMutation(QIOWorkbenchConfigurationMutation.batchSetRecipesEnabled(
                  selectedProductKey, targets, !recipe.isEnabled()));
        }
    }

    private void moveRecipe(int direction) {
        Recipe recipe = selectedRecipe();
        if (recipe == null || selectedProductKey == null) return;
        List<RecipeTarget> targets = selectedRecipeTargets();
        if (targets.size() <= 1) {
            sendMutation(GuiScreen.isCtrlKeyDown() ?
                  QIOWorkbenchConfigurationMutation.recipe(direction < 0 ?
                        Action.MOVE_RECIPE_TO_TOP : Action.MOVE_RECIPE_TO_BOTTOM,
                        selectedProductKey, recipe.getRecipeId(), recipe.getSignature()) :
                  QIOWorkbenchConfigurationMutation.moveRecipe(selectedProductKey,
                        recipe.getRecipeId(), recipe.getSignature(), direction));
        } else {
            sendMutation(QIOWorkbenchConfigurationMutation.batchMoveRecipes(
                  selectedProductKey, targets, direction, GuiScreen.isCtrlKeyDown()));
        }
    }

    private void deleteRecipe() {
        Recipe recipe = selectedRecipe();
        if (recipe != null && selectedProductKey != null) {
            GuiConfirmationDialog.show(gui(), new TextComponentTranslation(
                  "gui.mekanismqioprocessing.workbench_pattern_delete_confirm",
                  recipe.getRecipeId()), () -> sendMutation(selectedRecipeTargets().size() <= 1 ?
                        QIOWorkbenchConfigurationMutation.deletePattern(recipe.getRecipeId(),
                              recipe.getSignature()) :
                        QIOWorkbenchConfigurationMutation.batchDeletePatterns(
                              selectedProductKey, selectedRecipeTargets())), DialogType.DANGER);
        }
    }

    private void deleteLastSelection() {
        if (!editable() || mutationPending != null || selectedProductKey == null) return;
        if (deleteTarget == DeleteTarget.RECIPE && selectedRecipe() != null &&
            selectedRecipeSignature != null) {
            List<RecipeTarget> targets = selectedRecipeTargets();
            sendMutation(targets.size() <= 1 ? QIOWorkbenchConfigurationMutation.deletePattern(
                  selectedRecipeId, selectedRecipeSignature) :
                  QIOWorkbenchConfigurationMutation.batchDeletePatterns(
                        selectedProductKey, targets));
        } else {
            List<String> products = selectedProductKeys.isEmpty() ?
                  Collections.singletonList(selectedProductKey) :
                  new ArrayList<>(selectedProductKeys);
            sendMutation(products.size() <= 1 ?
                  QIOWorkbenchConfigurationMutation.deleteProduct(selectedProductKey) :
                  QIOWorkbenchConfigurationMutation.batchDeleteProducts(products));
        }
    }

    @Nonnull
    private List<RecipeTarget> selectedRecipeTargets() {
        Map<String, String> targets = new LinkedHashMap<>(selectedRecipes);
        Recipe recipe = selectedRecipe();
        if (targets.isEmpty() && recipe != null) {
            targets.put(recipe.getRecipeId(), recipe.getSignature());
        }
        List<RecipeTarget> result = new ArrayList<>(targets.size());
        targets.forEach((id, signature) -> result.add(new RecipeTarget(id, signature)));
        return result;
    }

    private void openPatternWindow() {
        if (!editable() || identitySnapshot() == null || mutationPending != null ||
            closurePending != null) return;
        activeEditor = EditorMode.PATTERN;
        if (patternWindow == null) {
            patternWindow = new GuiQIOWorkbenchPatternWindow(gui(), cache(),
                  this::encodePattern, () -> editorFocused(EditorMode.PATTERN),
                  () -> editorClosed(EditorMode.PATTERN));
            ensureInventoryOpen(patternWindow);
            gui().addWindow(patternWindow);
        } else {
            ensureInventoryOpen(patternWindow);
            focusWindow(patternWindow);
        }
    }

    private boolean encodePattern(List<ItemStack> grid,
          QIOWorkbenchClosureMode closureMode, boolean skipCyclicRecipes) {
        QIOWorkbenchConfigurationMutation mutation =
              QIOWorkbenchConfigurationMutation.encodePattern(grid);
        if (closureMode == QIOWorkbenchClosureMode.NONE) return sendMutation(mutation);
        sendClosurePreview(mutation, closureMode, skipCyclicRecipes);
        return false;
    }

    private void openBatchWindow() {
        if (!editable() || identitySnapshot() == null || mutationPending != null ||
            closurePending != null) return;
        activeEditor = EditorMode.BATCH;
        if (batchWindow == null) {
            batchWindow = new GuiQIOWorkbenchBatchWindow(gui(), cache(),
                  this::encodeTargets, () -> editorFocused(EditorMode.BATCH),
                  () -> editorClosed(EditorMode.BATCH));
            ensureInventoryOpen(batchWindow);
            gui().addWindow(batchWindow);
        } else {
            ensureInventoryOpen(batchWindow);
            focusWindow(batchWindow);
        }
    }

    private void ensureInventoryOpen(GuiWindow anchor) {
        if (inventoryWindow != null) return;
        GuiPlayerInventoryWindow inventory = new GuiPlayerInventoryWindow(gui(),
              anchor.getRelativeRight() + 4, anchor.getRelativeY(),
              new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_INVENTORY,
                    QIOProcessingWindowTypes.WORKBENCH_INVENTORY_PATTERN),
              this::activeInventoryWindowData,
              container.getWorkbenchEditorInventorySlots(), this::inventoryClicked,
              this::inventoryClosed);
        inventoryWindow = inventory;
        gui().addWindow(inventory);
    }

    private SelectedWindowData activeInventoryWindowData() {
        return new SelectedWindowData(
              QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_INVENTORY,
              activeEditor == EditorMode.BATCH ?
                    QIOProcessingWindowTypes.WORKBENCH_INVENTORY_BATCH :
                    QIOProcessingWindowTypes.WORKBENCH_INVENTORY_PATTERN);
    }

    private boolean inventoryClicked(int index, int button) {
        return activeEditor == EditorMode.BATCH && batchWindow != null &&
              batchWindow.inventoryClicked(index, button);
    }

    private void inventoryClosed() {
        inventoryWindow = null;
    }

    private void editorFocused(EditorMode mode) {
        if (mode == EditorMode.PATTERN ? patternWindow != null : batchWindow != null) {
            activeEditor = mode;
        }
    }

    private void editorClosed(EditorMode mode) {
        if (mode == EditorMode.PATTERN) {
            patternWindow = null;
        } else {
            batchWindow = null;
        }
        if (patternWindow == null && batchWindow == null) {
            activeEditor = null;
            closeInventoryWindow();
        } else if (activeEditor == mode) {
            activeEditor = patternWindow != null ? EditorMode.PATTERN : EditorMode.BATCH;
            GuiPlayerInventoryWindow inventory = inventoryWindow;
            if (isTopWindow(inventory)) {
                inventory.onFocused();
            }
        }
    }

    private void closeEditorWindows() {
        if (patternWindow != null) patternWindow.close();
        if (batchWindow != null) batchWindow.close();
        closeCandidateWindow();
        closeInventoryWindow();
    }

    private void closeInventoryWindow() {
        GuiPlayerInventoryWindow inventory = inventoryWindow;
        inventoryWindow = null;
        if (inventory != null) inventory.close();
    }

    private void focusWindow(GuiWindow window) {
        if (gui() instanceof GuiMekanism<?> screen) {
            screen.focusWindow(window);
        } else {
            window.onFocused();
        }
    }

    private boolean isTopWindow(@Nullable GuiWindow window) {
        return window != null && gui() instanceof GuiMekanism<?> screen &&
              !screen.getWindows().isEmpty() &&
              screen.getWindows().iterator().next() == window;
    }

    private void restorePinnedEditorWindows() {
        if (identitySnapshot() == null || !editable()) return;
        if (!pinnedEditorRestoreChecked) {
            pinnedEditorRestoreChecked = true;
            if (new SelectedWindowData(
                  QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_PATTERN).wasPinned()) {
                openPatternWindow();
            }
            if (new SelectedWindowData(
                  QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_BATCH).wasPinned()) {
                openBatchWindow();
            }
        }
        if (!pinnedCandidateRestoreChecked && selectedRecipe() != null) {
            pinnedCandidateRestoreChecked = true;
            if (new SelectedWindowData(
                  QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_CANDIDATES).wasPinned()) {
                openRecipeConfigurationWindow();
            }
        }
    }

    private boolean encodeTargets(List<ItemStack> targets,
          QIOWorkbenchClosureMode closureMode, boolean skipCyclicRecipes) {
        QIOWorkbenchConfigurationMutation mutation =
              QIOWorkbenchConfigurationMutation.encodeTargets(targets);
        if (closureMode == QIOWorkbenchClosureMode.NONE) return sendMutation(mutation);
        sendClosurePreview(mutation, closureMode, skipCyclicRecipes);
        return false;
    }

    private void resetProduct() {
        if (selectedProductKey != null) sendMutation(
              QIOWorkbenchConfigurationMutation.resetProduct(selectedProductKey));
    }

    boolean toggleCandidate(String candidateId) {
        return sendMutation(QIOWorkbenchConfigurationMutation.candidate(
              Action.TOGGLE_CANDIDATE, selectedProductKey, selectedRecipeId,
              selectedRecipeSignature, selectedIngredientSlot,
              candidateId));
    }

    boolean moveCandidateToIndex(String candidateId, int targetIndex) {
        selectCandidate(candidateId);
        return sendMutation(QIOWorkbenchConfigurationMutation.moveCandidateToIndex(
              selectedProductKey, selectedRecipeId, selectedRecipeSignature,
              selectedIngredientSlot, candidateId, targetIndex));
    }

    boolean syncEquivalentCandidates() {
        if (selectedProductKey == null || selectedRecipeId == null ||
            selectedRecipeSignature == null || selectedIngredientSlot < 0) return false;
        return sendMutation(QIOWorkbenchConfigurationMutation.syncEquivalentCandidates(
              selectedProductKey, selectedRecipeId, selectedRecipeSignature,
              selectedIngredientSlot));
    }

    private void resetAll() {
        GuiConfirmationDialog.show(gui(), new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_reset_all_confirm"),
              () -> sendMutation(QIOWorkbenchConfigurationMutation.resetAll()),
              DialogType.DANGER);
    }

    private void restoreRecovery() {
        sendMutation(QIOWorkbenchConfigurationMutation.restoreRecovery());
    }

    private void copyUUID() {
        UUID configUUID = configUUID();
        if (configUUID != null) {
            GuiScreen.setClipboardString(configUUID.toString());
            statusKey = "gui.mekanismqioprocessing.workbench_status_uuid_copied";
        }
    }

    private void requestCopyPreview() {
        UUID source;
        try {
            source = UUID.fromString(sourceUUIDField.getText().trim());
        } catch (RuntimeException e) {
            statusKey = "gui.mekanismqioprocessing.workbench_status_invalid_uuid";
            return;
        }
        if (source.equals(configUUID()) || copyPending != null || !editable()) return;
        UUID requestId = UUID.randomUUID();
        cache().expectCopy(requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.copyPreview(
                    container.getTerminalWindowId(),
                    terminalState, requestId, source));
        copyPending = new Pending(cache().getCopyGeneration(), clientTick);
        statusKey = null;
    }

    private void showCopyConfirmation(QIOWorkbenchCopyPreview preview) {
        TextComponentTranslation message = new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_copy_confirm",
              preview.getSourceFrequencyName(), preview.getEncodedPatternCount(),
              preview.getIngredientOverrideCount());
        GuiConfirmationDialog.show(gui(), message,
              () -> confirmCopy(preview.getConfirmationNonce()), DialogType.DANGER);
    }

    private void confirmCopy(UUID confirmationNonce) {
        if (copyPending != null || !editable()) return;
        UUID requestId = UUID.randomUUID();
        cache().expectCopy(requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOWorkbenchConfigurationRequest.Message.copyConfirm(
                    container.getTerminalWindowId(),
                    terminalState, requestId, confirmationNonce));
        copyPending = new Pending(cache().getCopyGeneration(), clientTick);
        statusKey = null;
    }

    private void reloadProducts() {
        clearMutationViewRestore();
        cache().clearPages();
        productPending = null;
        recipePending = null;
        candidatePending = null;
        productGrid.resetScroll();
        recipeList.resetScroll();
        if (candidateWindow != null) candidateWindow.recipeChanged();
        requestProducts(0);
    }

    private void reloadProductsPreservingSelection() {
        int offset = pendingProductSelectionIndex >= 0 ?
              pageOffset(pendingProductSelectionIndex, PRODUCT_PAGE_SIZE) :
              pageOffset(pendingProductFirstVisibleIndex, PRODUCT_PAGE_SIZE);
        cache().clearPages();
        productPending = null;
        recipePending = null;
        candidatePending = null;
        requestProducts(offset);
    }

    private void updateButtons() {
        boolean editable = editable() && mutationPending == null && closurePending == null;
        boolean recipe = selectedRecipe() != null;
        resetProductButton.active = editable && selectedProductKey != null;
        addPatternButton.active = editable && identitySnapshot() != null;
        addPatternButton.setMessage(new TextComponentTranslation(cache().hasEncodingInput() ?
              "gui.mekanismqioprocessing.workbench_pattern_review" :
              "gui.mekanismqioprocessing.workbench_pattern_add"));
        batchPatternButton.active = editable && identitySnapshot() != null;
        batchPatternButton.setMessage(new TextComponentTranslation(cache().hasBatchTargets() ?
              "gui.mekanismqioprocessing.workbench_batch_review" :
              "gui.mekanismqioprocessing.workbench_batch_open"));
        toggleRecipeButton.active = editable && recipe;
        recipeUpButton.active = editable && recipe;
        recipeDownButton.active = editable && recipe;
        deletePatternButton.active = editable && recipe;
        resetAllButton.active = editable;
        QIOWorkbenchConfigurationSnapshot identity = identitySnapshot();
        restoreRecoveryButton.active = editable && identity != null &&
              identity.getRecoveryPatternCount() > 0;
        copyUUIDButton.active = configUUID() != null;
        UUID source = null;
        try {
            source = UUID.fromString(sourceUUIDField.getText().trim());
        } catch (RuntimeException ignored) {
        }
        importButton.active = editable && copyPending == null && source != null &&
              !source.equals(configUUID());
    }

    private boolean editable() {
        QIOWorkbenchConfigurationSnapshot snapshot = identitySnapshot();
        return snapshot != null && snapshot.isEditable();
    }

    @Nullable
    private UUID configUUID() {
        QIOWorkbenchConfigurationSnapshot snapshot = identitySnapshot();
        return snapshot == null ? null : snapshot.getConfigUUID();
    }

    @Nullable
    private QIOWorkbenchConfigurationSnapshot identitySnapshot() {
        if (cache().getCandidatePage() != null) return cache().getCandidatePage();
        if (cache().getRecipePage() != null) return cache().getRecipePage();
        return cache().getProductPage();
    }

    @Nullable
    Recipe selectedRecipe() {
        return selectedRecipeId == null ? null : cache().findRecipe(selectedRecipeId);
    }

    @Nullable
    Ingredient selectedIngredient() {
        Recipe recipe = selectedRecipe();
        if (recipe == null || selectedIngredientSlot < 0) return null;
        return recipe.getIngredients().stream().filter(ingredient ->
              ingredient.getSlot() == selectedIngredientSlot).findFirst().orElse(null);
    }

    @Nullable
    private Candidate selectedCandidate() {
        QIOWorkbenchConfigurationSnapshot page = cache().getCandidatePage();
        if (page == null || selectedCandidateId == null) return null;
        return page.getCandidates().stream().filter(candidate ->
              candidate.getCandidateId().equals(selectedCandidateId)).findFirst().orElse(null);
    }

    List<Candidate> candidateSnapshot() {
        QIOWorkbenchConfigurationSnapshot page = cache().getCandidatePage();
        return page == null ? Collections.emptyList() : page.getCandidates();
    }

    @Nullable
    String selectedCandidateId() {
        return selectedCandidateId;
    }

    void selectCandidate(@Nullable String candidateId) {
        selectedCandidateId = candidateId;
    }

    boolean canEditCandidates() {
        return editable() && mutationPending == null && closurePending == null &&
              selectedIngredient() != null;
    }

    boolean canSynchronizeEquivalentCandidates() {
        Ingredient selected = selectedIngredient();
        Recipe recipe = selectedRecipe();
        if (!canEditCandidates() || selected == null || recipe == null ||
            selected.getCandidateCount() <= 1) return false;
        int matchingCounts = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.getCandidateCount() == selected.getCandidateCount()) {
                matchingCounts++;
            }
        }
        return matchingCounts > 1;
    }

    private boolean sessionValid() {
        return openedSession != null && openedSession.equals(SessionKey.capture(terminalState));
    }

    private QIOWorkbenchConfigurationClientCache cache() {
        return container.getWorkbenchConfigurationClientCache();
    }

    @Nullable
    private static String statusKey(Status status) {
        return statusKey(status, null);
    }

    @Nullable
    private static String statusKey(Status status, @Nullable Action action) {
        if (status == Status.INVALID_PATTERN && action == Action.ENCODE_TARGETS) {
            return "gui.mekanismqioprocessing.workbench_status_invalid_targets";
        }
        return switch (status) {
            case OK, READY, APPLIED, UNCHANGED -> null;
            case LAST_CANDIDATE ->
                  "gui.mekanismqioprocessing.workbench_status_last_candidate";
            case ALREADY_IMPORTED ->
                  "gui.mekanismqioprocessing.workbench_status_already_imported";
            case ACCESS_DENIED, READ_ONLY ->
                  "gui.mekanismqioprocessing.workbench_status_access_denied";
            case INVALID_PATTERN ->
                  "gui.mekanismqioprocessing.workbench_status_invalid_pattern";
            case SOURCE_CHANGED ->
                  "gui.mekanismqioprocessing.workbench_status_source_changed";
            case TARGET_CHANGED, REVISION_CONFLICT, CATALOG_CHANGED ->
                  "gui.mekanismqioprocessing.workbench_status_target_changed";
            case NOT_FOUND -> "gui.mekanismqioprocessing.workbench_status_not_found";
            case EXPIRED -> "gui.mekanismqioprocessing.workbench_status_expired";
            default -> "gui.mekanismqioprocessing.workbench_status_invalid";
        };
    }

    private void closeLater() {
        if (gui() instanceof mekanism.client.gui.GuiMekanism<?> gui) {
            gui.queueWindowClose(this);
        } else {
            close();
        }
    }

    private abstract class BaseList extends GuiScrollList {

        private BaseList(IGuiWrapper gui, int x, int y, int width, int height,
              int rowHeight) {
            super(gui, x, y, width, height, rowHeight, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        @Override public void resetScroll() { scroll = 0; }

        final int getReloadOffset() {
            return Math.max(0, getCurrentSelection());
        }

        final void scrollToInclude(int index) {
            setCurrentSelection(Math.max(0,
                  index - Math.max(0, getFocusedElements() / 2)));
        }

        final void restoreScroll(int firstVisibleIndex, int selectedIndex) {
            setCurrentSelection(Math.max(0, firstVisibleIndex));
            int first = getCurrentSelection();
            if (selectedIndex >= 0 && (selectedIndex < first ||
                selectedIndex >= first + getFocusedElements())) {
                scrollToInclude(selectedIndex);
            }
        }

        protected void rowBackground(int index, boolean selected, boolean hovered) {
            int rowY = relativeY + 1 + index * elementHeight;
            if (selected || hovered) {
                GuiUtils.fill(relativeX + 1, rowY, relativeX + barXShift - 1,
                      rowY + elementHeight - 1, selected ? SELECTED : HOVERED);
            }
        }
    }

    private final class ProductGrid extends GuiElement {

        private static final int CELL = 18;
        private static final int COLUMNS = 6;
        private static final int ROWS = 8;
        private static final int ORIGIN_X = 2;
        private static final int ORIGIN_Y = 2;
        private static final ResourceLocation SLOTS = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots.png");
        private static final ResourceLocation SLOTS_DARK = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots_dark.png");
        private final GuiScrollBar scrollBar;

        private ProductGrid(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height);
            scrollBar = addChild(new GuiScrollBar(gui,
                  relativeX + COLUMNS * CELL + 4, relativeY, height,
                  this::totalRows, () -> ROWS));
            active = true;
        }

        @Override
        public void drawBackground(int mouseX, int mouseY, float partialTicks) {
            GuiUtils.fill(relativeX, relativeY, relativeX + width, relativeY + height,
                  0xFFB0B0B0);
            minecraft.renderEngine.bindTexture(cache().getProductTotalSize() == 0 ?
                  SLOTS_DARK : SLOTS);
            GuiUtils.blit(relativeX + ORIGIN_X, relativeY + ORIGIN_Y, 0, 0,
                  COLUMNS * CELL, ROWS * CELL, 288, 288);
            int first = firstVisibleIndex();
            int hovered = absoluteIndexAt(mouseX, mouseY);
            for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
                Product product = cache().getProduct(first + slot);
                if (product == null) continue;
                int x = relativeX + ORIGIN_X + slot % COLUMNS * CELL;
                int y = relativeY + ORIGIN_Y + slot / COLUMNS * CELL;
                if (selectedProductKeys.contains(product.getProductKey())) {
                    QIOGuiSelectionRenderer.draw(x, y, CELL, CELL);
                } else if (first + slot == hovered) {
                    GuiUtils.fill(x + 1, y + 1, x + 17, y + 17, HOVERED);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int first = firstVisibleIndex();
            for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
                Product product = cache().getProduct(first + slot);
                if (product == null) continue;
                ItemStack stack = product.getOutput().resolveItem();
                if (!stack.isEmpty()) {
                    gui().renderItemWithOverlay(stack,
                          relativeX + ORIGIN_X + slot % COLUMNS * CELL + 1,
                          relativeY + ORIGIN_Y + slot / COLUMNS * CELL + 1, 1F, "");
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOverCheckWindows(mouseX, mouseY)) {
                deleteTarget = DeleteTarget.PRODUCT;
                if (button == 0 && GuiScreen.isCtrlKeyDown()) {
                    Product product = productAt(mouseX, mouseY);
                    if (product != null) {
                        int index = absoluteIndexAt(mouseX, mouseY);
                        productDragAnchor = index;
                        productDragStartRow = index / COLUMNS;
                        productDragStartColumn = index % COLUMNS;
                        productDragBaseSelected = selectedProductKeys.contains(
                              product.getProductKey());
                        productDragSelectionBefore = new LinkedHashSet<>(selectedProductKeys);
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return isMouseOverCheckWindows(mouseX, mouseY) &&
                  scrollBar.adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            Product product = productAt(mouseX, mouseY);
            if (product != null) {
                if (GuiScreen.isCtrlKeyDown()) {
                    toggleProductSelection(product);
                } else {
                    selectOnlyProduct(product, true);
                }
            }
        }

        @Override
        public void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
            super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
            if (productDragAnchor < 0 || !GuiScreen.isCtrlKeyDown()) return;
            int current = absoluteIndexAt(mouseX, mouseY);
            if (current < 0) return;
            int currentRow = current / COLUMNS;
            int currentColumn = current % COLUMNS;
            int minRow = Math.min(productDragStartRow, currentRow);
            int maxRow = Math.max(productDragStartRow, currentRow);
            int minColumn = Math.min(productDragStartColumn, currentColumn);
            int maxColumn = Math.max(productDragStartColumn, currentColumn);
            selectedProductKeys.clear();
            selectedProductKeys.addAll(productDragSelectionBefore);
            for (int row = minRow; row <= maxRow; row++) {
                for (int column = minColumn; column <= maxColumn; column++) {
                    Product product = cache().getProduct(row * COLUMNS + column);
                    if (product == null) continue;
                    if (productDragBaseSelected) {
                        selectedProductKeys.remove(product.getProductKey());
                    } else {
                        if (selectedProductKeys.size() >=
                            QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS) continue;
                        selectedProductKeys.add(product.getProductKey());
                    }
                }
            }
            refreshPrimaryProductFromSelection();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            productDragAnchor = -1;
            productDragStartRow = -1;
            productDragStartColumn = -1;
            productDragSelectionBefore = Collections.emptySet();
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            Product product = productAt(mouseX, mouseY);
            if (product != null) {
                ItemStack stack = product.getOutput().resolveItem();
                if (!stack.isEmpty()) gui().renderItemTooltipWithExtra(stack, mouseX, mouseY,
                      Collections.singletonList(new TextComponentTranslation(
                            "gui.mekanismqioprocessing.workbench_product_multi_select_shortcut")
                            .getFormattedText()));
            }
        }

        private int absoluteIndexAt(double mouseX, double mouseY) {
            int slot = visibleSlotAt(mouseX, mouseY);
            return slot < 0 ? -1 : firstVisibleIndex() + slot;
        }

        private int visibleSlotAt(double mouseX, double mouseY) {
            int localX = (int) mouseX - getX() - ORIGIN_X;
            int localY = (int) mouseY - getY() - ORIGIN_Y;
            if (localX < 0 || localY < 0 || localX / CELL >= COLUMNS ||
                localY / CELL >= ROWS || localX % CELL == 0 || localY % CELL == 0 ||
                localX % CELL >= 17 || localY % CELL >= 17 ||
                !checkWindows(mouseX, mouseY)) return -1;
            return localY / CELL * COLUMNS + localX / CELL;
        }

        @Nullable
        private Product productAt(double mouseX, double mouseY) {
            return cache().getProduct(absoluteIndexAt(mouseX, mouseY));
        }

        private int totalRows() {
            return (cache().getProductTotalSize() + COLUMNS - 1) / COLUMNS;
        }

        private int firstVisibleIndex() {
            return scrollBar.getCurrentSelection() * COLUMNS;
        }

        private int getReloadOffset() {
            return Math.max(0, firstVisibleIndex());
        }

        private void restoreFirstVisibleIndex(int firstVisibleIndex) {
            if (firstVisibleIndex >= 0) {
                scrollBar.setCurrentSelection(firstVisibleIndex / COLUMNS);
            }
        }

        private int getMissingPageOffset(int pageSize) {
            if (cache().getProductPage() == null) return 0;
            int first = firstVisibleIndex();
            int last = Math.min(cache().getProductTotalSize(),
                  first + COLUMNS * ROWS);
            for (int index = first; index < last; index += pageSize) {
                int offset = index / pageSize * pageSize;
                if (!cache().isProductPageLoaded(offset)) return offset;
                int boundary = offset + pageSize;
                if (boundary < last && !cache().isProductPageLoaded(boundary)) {
                    return boundary;
                }
            }
            return -1;
        }

        private void resetScroll() {
            scrollBar.resetScroll();
        }
    }

    private final class RecipeList extends BaseList {

        private static final int DOUBLE_CLICK_TICKS = 5;
        @Nullable private String lastClickedRecipeId;
        private long lastClickTick = Long.MIN_VALUE;

        private RecipeList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, 46);
        }

        @Override protected int getMaxElements() { return cache().getRecipeTotalSize(); }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOverCheckWindows(mouseX, mouseY)) {
                deleteTarget = DeleteTarget.RECIPE;
            }
            if (button == 1 && GuiScreen.isShiftKeyDown()) {
                Recipe clicked = recipeAt(mouseX, mouseY);
                if (clicked != null) {
                    if (!selectedRecipes.containsKey(clicked.getRecipeId())) {
                        selectOnlyRecipe(clicked);
                    }
                    toggleRecipe();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean hasSelection() { return selectedRecipeId != null; }
        @Override protected void setSelected(int index) {
            Recipe recipe = cache().getRecipe(index);
            if (recipe == null) return;
            if (GuiScreen.isCtrlKeyDown()) {
                toggleRecipeSelection(recipe);
            } else {
                selectOnlyRecipe(recipe);
            }
        }
        @Override public void clearSelection() { selectRecipe(null); }

        @Override
        public void onClick(double mouseX, double mouseY) {
            Recipe clicked = recipeAt(mouseX, mouseY);
            super.onClick(mouseX, mouseY);
            if (clicked == null) {
                lastClickedRecipeId = null;
                lastClickTick = Long.MIN_VALUE;
                return;
            }
            if (GuiScreen.isCtrlKeyDown()) {
                lastClickedRecipeId = null;
                lastClickTick = Long.MIN_VALUE;
                return;
            }
            boolean doubleClick = !GuiScreen.isCtrlKeyDown() &&
                  clicked.getRecipeId().equals(lastClickedRecipeId) &&
                  clientTick - lastClickTick <= DOUBLE_CLICK_TICKS;
            lastClickedRecipeId = clicked.getRecipeId();
            lastClickTick = clientTick;
            if (doubleClick) {
                lastClickedRecipeId = null;
                lastClickTick = Long.MIN_VALUE;
                openRecipeConfigurationWindow();
            }
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(),
                  Math.max(0, cache().getRecipeTotalSize() - start));
            for (int index = 0; index < count; index++) {
                Recipe recipe = cache().getRecipe(start + index);
                if (recipe == null) continue;
                boolean hovered = rowHovered(mouseX, mouseY, index);
                rowBackground(index, selectedRecipes.containsKey(recipe.getRecipeId()), hovered);
                int rowY = relativeY + 1 + index * elementHeight;
                GuiUtils.fill(relativeX + barXShift - 8, rowY + 5,
                      relativeX + barXShift - 3, rowY + 10,
                      recipe.isEnabled() ? ENABLED : DISABLED);
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(),
                  Math.max(0, cache().getRecipeTotalSize() - start));
            for (int index = 0; index < count; index++) {
                Recipe recipe = cache().getRecipe(start + index);
                if (recipe == null) continue;
                int rowY = relativeY + 3 + index * elementHeight;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    int slot = ingredient.getSlot();
                    QIOWorkbenchGuiIngredientRenderer.render(gui(), ingredient,
                          relativeX + 24 + slot % 3 * 12,
                          rowY + slot / 3 * 12, 10);
                }
                getFont().drawString(">", relativeX + 65, rowY + 15, 0x404040);
                ItemStack output = recipe.getOutput().resolveItem();
                if (!output.isEmpty()) {
                    gui().renderItemWithOverlay(output, relativeX + 75, rowY + 10, 1F,
                          recipe.getOutputAmount() > 1 ?
                                Integer.toString(recipe.getOutputAmount()) : "");
                }
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            Recipe recipe = recipeAt(mouseX, mouseY);
            if (recipe == null) return;
            List<String> hints = java.util.Arrays.asList(
                  new TextComponentTranslation(
                        "gui.mekanismqioprocessing.workbench_route_double_click")
                        .getFormattedText(),
                  new TextComponentTranslation(
                        "gui.mekanismqioprocessing.workbench_route_toggle_shortcut")
                        .getFormattedText(),
                  new TextComponentTranslation(
                        "gui.mekanismqioprocessing.workbench_route_multi_select_shortcut")
                        .getFormattedText());
            Ingredient ingredient = hoveredIngredient(mouseX, mouseY);
            if (ingredient != null) {
                if (ingredient.isVirtualFluid()) {
                    List<String> tooltip = QIOWorkbenchGuiIngredientRenderer.fluidTooltip(
                          ingredient);
                    tooltip.addAll(hints);
                    displayTooltips(tooltip, mouseX, mouseY);
                } else {
                    gui().renderItemTooltipWithExtra(ingredient.getRepresentative(), mouseX,
                          mouseY, hints);
                }
                return;
            }
            ItemStack output = hoveredOutput(mouseX, mouseY);
            if (output.isEmpty()) {
                displayTooltips(hints, mouseX, mouseY);
            } else {
                gui().renderItemTooltipWithExtra(output, mouseX, mouseY, hints);
            }
        }

        @Nullable
        private Recipe recipeAt(double mouseX, double mouseY) {
            if (!checkWindows(mouseX, mouseY) || mouseX < getX() + 1 ||
                mouseX >= getX() + barXShift - 1 || mouseY < getY() + 1 ||
                mouseY >= getY() + height - 1) return null;
            int visibleRow = ((int) mouseY - getY() - 1) / elementHeight;
            if (visibleRow < 0 || visibleRow >= getFocusedElements()) return null;
            return cache().getRecipe(getCurrentSelection() + visibleRow);
        }

        @Nullable
        private Ingredient hoveredIngredient(int mouseX, int mouseY) {
            int localX = mouseX - getX() - 3;
            int localY = mouseY - getY() - 3;
            if (localY < 0) return null;
            int visibleRow = localY / elementHeight;
            int recipeIndex = getCurrentSelection() + visibleRow;
            Recipe recipe = cache().getRecipe(recipeIndex);
            if (recipe == null) return null;
            int inRowY = localY % elementHeight;
            if (localX >= 21 && localX < 57 && inRowY < 36) {
                int slot = inRowY / 12 * 3 + (localX - 21) / 12;
                Ingredient ingredient = recipe.getIngredients().get(slot);
                return ingredient.isEmpty() ? null : ingredient;
            }
            return null;
        }

        private ItemStack hoveredOutput(int mouseX, int mouseY) {
            int localX = mouseX - getX() - 3;
            int localY = mouseY - getY() - 3;
            if (localY < 0) return ItemStack.EMPTY;
            int visibleRow = localY / elementHeight;
            Recipe recipe = cache().getRecipe(getCurrentSelection() + visibleRow);
            if (recipe == null) return ItemStack.EMPTY;
            int inRowY = localY % elementHeight;
            if (localX >= 72 && localX < 92 && inRowY >= 8 && inRowY < 28) {
                return recipe.getOutput().resolveItem();
            }
            return ItemStack.EMPTY;
        }

        private boolean rowHovered(int mouseX, int mouseY, int visibleIndex) {
            return mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 &&
                  mouseY >= getY() + 1 + visibleIndex * elementHeight &&
                  mouseY < getY() + 1 + (visibleIndex + 1) * elementHeight;
        }

        private int getMissingPageOffset(int pageSize) {
            if (cache().getRecipePage() == null) return 0;
            int first = getCurrentSelection();
            int last = Math.min(cache().getRecipeTotalSize(),
                  first + getFocusedElements());
            for (int index = first; index < last; index += pageSize) {
                int offset = index / pageSize * pageSize;
                if (!cache().isRecipePageLoaded(offset)) return offset;
                int boundary = offset + pageSize;
                if (boundary < last && !cache().isRecipePageLoaded(boundary)) {
                    return boundary;
                }
            }
            return -1;
        }
    }

    private enum EditorMode {
        PATTERN,
        BATCH
    }

    private enum DeleteTarget {
        PRODUCT,
        RECIPE
    }

    private static final class Pending {

        private final long generation;
        private final long sentAtTick;
        private final int offset;

        private Pending(long generation, long sentAtTick) {
            this(generation, sentAtTick, -1);
        }

        private Pending(long generation, long sentAtTick, int offset) {
            this.generation = generation;
            this.sentAtTick = sentAtTick;
            this.offset = offset;
        }

        private boolean timedOut(long currentTick) {
            return currentTick - sentAtTick >= REQUEST_TIMEOUT_TICKS;
        }
    }

    private static final class SessionKey {

        private final UUID nonce;
        private final UUID terminal;
        private final UUID frequency;
        private final long targetRevision;
        private final long accessRevision;

        private SessionKey(UUID nonce, UUID terminal, UUID frequency,
              long targetRevision, long accessRevision) {
            this.nonce = nonce;
            this.terminal = terminal;
            this.frequency = frequency;
            this.targetRevision = targetRevision;
            this.accessRevision = accessRevision;
        }

        @Nullable
        private static SessionKey capture(QIOProcessingTerminalContainerState state) {
            return state.isValid() && state.getFrequencyUUID() != null ?
                  new SessionKey(state.getSessionNonce(), state.getTerminalUUID(),
                        state.getFrequencyUUID(), state.getTargetRevision(),
                        state.getAccessRevision()) : null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SessionKey other && targetRevision == other.targetRevision &&
                  accessRevision == other.accessRevision && nonce.equals(other.nonce) &&
                  terminal.equals(other.terminal) && frequency.equals(other.frequency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminal, frequency, targetRevision, accessRevision);
        }
    }
}
