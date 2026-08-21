package mekanism.qioprocessing.client.gui;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation.Action;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOManagementRecipeData.Status;
import mekanism.qioprocessing.common.network.PacketQIOManagementRecipeRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.ProductFilter;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.PageKind;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Product;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.ResourceAmount;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot.Route;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Remote recipe-profile editor owned by the management terminal session. */
/**
 * QIO 处理模块中的 GuiQIOManagementRecipeConfigWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOManagementRecipeConfigWindow extends GuiWindow {

    public static final int WIDTH = 336;
    private static final int HEIGHT = 220;
    private static final int PAGE_SIZE = QIOManagementRecipeSnapshot.MAX_PAGE_SIZE;
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private static final int SEARCH_DELAY_TICKS = 5;

    private static final int PRODUCT_LIST_WIDTH = 154;
    private static final int PRODUCT_FILTER_BUTTON_X = 6;
    private static final int PRODUCT_FILTER_BUTTON_WIDTH = 70;
    private static final int PRODUCT_SEARCH_X = 80;
    private static final int PRODUCT_SEARCH_WIDTH = PRODUCT_LIST_WIDTH -
          (PRODUCT_SEARCH_X - PRODUCT_FILTER_BUTTON_X);
    private static final int ROUTE_LIST_X = 170;
    private static final int ROUTE_LIST_WIDTH = 160;
    private static final int LIST_Y = 38;
    private static final int LIST_HEIGHT = 144;
    private static final int ROW_HEIGHT = 24;
    private static final int AMOUNT_ROW_Y = 186;
    private static final int BOTTOM_BUTTON_Y = 202;
    private static final int AMOUNT_FIELD_WIDTH = 64;
    private static final int GLOBAL_AMOUNT_FIELD_X = 6 + PRODUCT_LIST_WIDTH -
          AMOUNT_FIELD_WIDTH;
    private static final int ROUTE_AMOUNT_FIELD_X = ROUTE_LIST_X + ROUTE_LIST_WIDTH -
          AMOUNT_FIELD_WIDTH;
    private static final int PROFILE_MODE_BUTTON_X = WIDTH - 98;
    private static final int PROFILE_MODE_BUTTON_WIDTH = 92;
    private static final int TITLE_X = 32;
    private static final int TITLE_WIDTH = PROFILE_MODE_BUTTON_X - TITLE_X - 4;
    private static final int ROUTE_FILTER_MODE_BUTTON_X = ROUTE_LIST_X;
    private static final int ROUTE_FILTER_MODE_BUTTON_WIDTH = 64;

    private static final int INDICATOR_ENABLED = 0xFF57C78B;
    private static final int INDICATOR_PARTIAL = 0xFFE0C05A;
    private static final int INDICATOR_DISABLED = 0xFFC75656;
    private static final int INDICATOR_MULTI_ROUTE = 0xFF4E9FDB;
    private static final int ROW_HOVER_COLOR = 0x503A4B5F;
    private static final int ROW_SELECTED_COLOR = 0x80608CC1;
    private static final int ROW_FOCUSED_SELECTED_COLOR = 0xB04E9FDB;
    private static final int ROW_SELECTED_OUTLINE_COLOR = 0xFF8FC7FF;
    private static final int ROW_FOCUSED_SELECTED_OUTLINE_COLOR = 0xFFDDF2FF;
    private static final int RESOURCE_STEP = 18;
    private static final double SCROLL_PIXELS_PER_SECOND = 12.0D;
    private static final double MIN_SCROLL_EDGE_PAUSE = 0.5D;

    private final QIOManagementRecipeContainer container;
    private final QIOProcessingTerminalContainerState terminalState;
    private final QIOManagementRecipeClientCache cache;
    private final UUID deviceUUID;
    private final QIOAutomationMode mode;
    private final ITextComponent title;
    private final SessionIdentity session;
    private final ProductScrollList productList;
    private final RouteScrollList routeList;
    private final MekanismButton productFilterButton;
    private final MekanismButton toggleButton;
    private final MekanismButton upButton;
    private final MekanismButton downButton;
    private final MekanismButton resetProductButton;
    private final MekanismButton resetAllButton;
    private final MekanismButton profileModeButton;
    private final MekanismButton globalProfileButton;
    private final MekanismButton routeFilterModeButton;
    private final GuiTextField searchField;
    private final GuiTextField globalAmountField;
    private final GuiTextField routeAmountField;

    private final List<Product> products = new ArrayList<>();
    private final List<Route> routes = new ArrayList<>();
    private ProductFilter productFilter = ProductFilter.ALL;
    private SelectionFocus selectionFocus = SelectionFocus.PRODUCT;
    private String query = "";
    @Nullable private String selectedProductKey;
    @Nullable private String selectedRouteKey;
    @Nullable private QIOManagementRecipeSnapshot metadata;
    @Nullable private Pending pending;
    private int productTotal;
    private int routeTotal;
    private long directoryRevision;
    private long recipeProfileRevision;
    private long clientTick;
    private long searchAtTick = Long.MIN_VALUE;
    private long productGeneration;
    private long routeGeneration;
    private long mutationGeneration;
    private boolean productReloadQueued;
    private boolean routeReloadQueued;
    private boolean catalogHasProducts;
    private boolean closeScheduled;
    private boolean closed;

    public GuiQIOManagementRecipeConfigWindow(IGuiWrapper gui, int x, int y,
          QIOManagementRecipeContainer container, UUID deviceUUID,
          QIOAutomationMode mode, String machineName) {
        super(gui, x, y, WIDTH, HEIGHT, new SelectedWindowData(
              QIOProcessingWindowTypes.MANAGEMENT_RECIPE_CONFIG,
              mode == QIOAutomationMode.SCHEDULED ?
                    QIOProcessingWindowTypes.MANAGEMENT_RECIPE_SCHEDULED :
                    QIOProcessingWindowTypes.MANAGEMENT_RECIPE_PASSIVE));
        this.container = Objects.requireNonNull(container, "container");
        terminalState = container.getTerminalState();
        cache = container.getManagementRecipeClientCache();
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.mode = Objects.requireNonNull(mode, "mode");
        if (mode != QIOAutomationMode.SCHEDULED && mode != QIOAutomationMode.PASSIVE) {
            throw new IllegalArgumentException("Unsupported remote recipe mode " + mode);
        }
        title = tr(mode == QIOAutomationMode.SCHEDULED ?
              "gui.mekanismqioprocessing.management_auto_crafting_config_title" :
              "gui.mekanismqioprocessing.management_auto_processing_config_title",
              machineName);
        session = SessionIdentity.capture(terminalState);
        if (session == null) {
            throw new IllegalStateException("Management recipe window requires a session");
        }
        interactionStrategy = InteractionStrategy.ALL;

        profileModeButton = addChild(button(gui, PROFILE_MODE_BUTTON_X, 6,
              PROFILE_MODE_BUTTON_WIDTH,
              "gui.mekanismqioprocessing.config_profile_global",
              this::toggleProfileMode));
        routeFilterModeButton = addChild(button(gui, ROUTE_FILTER_MODE_BUTTON_X, 22,
              ROUTE_FILTER_MODE_BUTTON_WIDTH,
              "gui.mekanismqioprocessing.config_filter_blacklist",
              this::toggleFilterMode));
        globalProfileButton = addChild(new MekanismButton(gui,
              relativeX + PROFILE_MODE_BUTTON_X, relativeY + 22,
              PROFILE_MODE_BUTTON_WIDTH, 12,
              tr("gui.mekanismqioprocessing.config_profile_slot", 1),
              () -> cycleGlobalProfile(1), () -> cycleGlobalProfile(-1), null));
        productFilterButton = addChild(button(gui, PRODUCT_FILTER_BUTTON_X, 22,
              PRODUCT_FILTER_BUTTON_WIDTH, productFilterKey(),
              this::cycleProductFilter));
        searchField = addChild(new GuiTextField(gui, relativeX + PRODUCT_SEARCH_X,
              relativeY + 22, PRODUCT_SEARCH_WIDTH, 12)
              .setBackground(BackgroundType.DIGITAL)
              .setTextColor(screenTextColor())
              .setMaxLength(64)
              .setResponder(this::searchChanged));

        productList = addChild(new ProductScrollList(gui, relativeX + 6,
              relativeY + LIST_Y, PRODUCT_LIST_WIDTH, LIST_HEIGHT));
        routeList = addChild(new RouteScrollList(gui, relativeX + ROUTE_LIST_X,
              relativeY + LIST_Y, ROUTE_LIST_WIDTH, LIST_HEIGHT));

        globalAmountField = addChild(amountField(gui, GLOBAL_AMOUNT_FIELD_X,
              AMOUNT_ROW_Y, this::applyGlobalAmount));
        routeAmountField = addChild(amountField(gui, ROUTE_AMOUNT_FIELD_X,
              AMOUNT_ROW_Y, this::applyRouteAmount));
        globalAmountField.setTextValidator(text -> isValidAmountText(text,
              metadata == null ? Long.MAX_VALUE : metadata.getMaximumCraftAmount()));
        routeAmountField.setTextValidator(text -> {
            Route route = selectedRoute();
            return isValidAmountText(text, route == null ? Long.MAX_VALUE :
                  route.getMaximumCraftAmount());
        });

        resetProductButton = addChild(button(gui, 6, BOTTOM_BUTTON_Y, 94,
              "gui.mekanismqioprocessing.config_reset_product", this::resetProduct));
        resetAllButton = addChild(button(gui, 104, BOTTOM_BUTTON_Y, 66,
              "gui.mekanismqioprocessing.config_reset_all", this::resetAll));
        toggleButton = addChild(button(gui, 174, BOTTOM_BUTTON_Y, 54,
              "gui.mekanismqioprocessing.config_enable", this::toggleSelection));
        upButton = addChild(button(gui, 232, BOTTOM_BUTTON_Y, 46,
              "gui.mekanismqioprocessing.config_move_up", () -> moveSelection(-1)));
        downButton = addChild(button(gui, 282, BOTTOM_BUTTON_Y, 48,
              "gui.mekanismqioprocessing.config_move_down", () -> moveSelection(1)));

        productGeneration = cache.getProductGeneration();
        routeGeneration = cache.getRouteGeneration();
        mutationGeneration = cache.getMutationGeneration();
        directoryRevision = terminalState.getDeviceDirectoryRevision();
        recipeProfileRevision = terminalState.getAutomationRecipeProfileRevision();
        cache.clear();
        queueProductReload();
        updateControls();
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        if (!session.matches(terminalState)) {
            closeLater();
            return;
        }
        long currentDirectoryRevision = terminalState.getDeviceDirectoryRevision();
        if (currentDirectoryRevision >= 0 && currentDirectoryRevision != directoryRevision) {
            directoryRevision = currentDirectoryRevision;
            queueProductReload();
        }
        long currentRecipeProfileRevision =
              terminalState.getAutomationRecipeProfileRevision();
        if (currentRecipeProfileRevision >= 0 &&
            currentRecipeProfileRevision != recipeProfileRevision) {
            recipeProfileRevision = currentRecipeProfileRevision;
            queueProductReload();
        }
        processResponses();
        if (pending != null && clientTick - pending.sentAtTick >= REQUEST_TIMEOUT_TICKS) {
            RequestKind timedOut = pending.kind;
            pending = null;
            if (timedOut == RequestKind.ROUTES) {
                routeReloadQueued = true;
            } else {
                productReloadQueued = true;
            }
        }
        if (searchAtTick != Long.MIN_VALUE && clientTick >= searchAtTick) {
            searchAtTick = Long.MIN_VALUE;
            queueProductReload();
        }
        if (pending == null) {
            if (productReloadQueued) {
                productReloadQueued = false;
                requestProducts(0);
            } else if (routeReloadQueued && selectedProductKey != null) {
                routeReloadQueued = false;
                requestRoutes(0);
            } else if (productList.shouldRequestMore() && products.size() < productTotal) {
                requestProducts(products.size());
            } else if (routeList.shouldRequestMore() && routes.size() < routeTotal &&
                       selectedProductKey != null) {
                requestRoutes(routes.size());
            }
        }
        updateControls();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cache.clear();
            super.close();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawWindowTitle();
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_search"),
                  PRODUCT_SEARCH_X + 5, 24, TextAlignment.LEFT, 0x707070,
                  PRODUCT_SEARCH_WIDTH - 7, 0, false, 0.8F, getTimeOpened());
        }
        if (metadata == null || metadata.isIndividualProfile()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_routes"),
                  ROUTE_LIST_X + 2, 24, TextAlignment.LEFT, titleTextColor(),
                  ROUTE_LIST_WIDTH, 0, false, 0.9F, getTimeOpened());
        }
        drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_global_amount_short"),
              8, AMOUNT_ROW_Y + 2, TextAlignment.LEFT, titleTextColor(),
              GLOBAL_AMOUNT_FIELD_X - 12, 0, false, 0.82F, getTimeOpened());
        Route route = selectedRoute();
        String amountText = tr("gui.mekanismqioprocessing.config_route_amount_short")
              .getFormattedText() + ": " + (route == null ? "-" : route.getCraftAmount());
        drawScaledScrollingString(new TextComponentString(amountText), ROUTE_LIST_X + 2,
              AMOUNT_ROW_Y + 2, TextAlignment.LEFT, titleTextColor(),
              ROUTE_AMOUNT_FIELD_X - ROUTE_LIST_X - 6, 0, false, 0.82F,
              getTimeOpened());
        if (routeAmountField.isEmpty() && !routeAmountField.isFocused()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_amount_inherit"),
                  ROUTE_AMOUNT_FIELD_X + 4, AMOUNT_ROW_Y + 2, TextAlignment.LEFT,
                  0x707070, AMOUNT_FIELD_WIDTH - 6, 0, false, 0.8F,
                  getTimeOpened());
        }
        if (metadata == null && (pending != null || productReloadQueued)) {
            drawEmptyMessage("gui.mekanismqioprocessing.config_loading");
        } else if (products.isEmpty() && pending == null) {
            drawEmptyMessage(query.isEmpty() && productFilter == ProductFilter.ALL ?
                  "gui.mekanismqioprocessing.config_empty" :
                  "gui.mekanismqioprocessing.config_no_match");
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        if (showTooltip(profileModeButton,
              "gui.mekanismqioprocessing.config_profile_mode_tooltip", mouseX, mouseY) ||
            showTooltip(globalProfileButton,
                  "gui.mekanismqioprocessing.config_profile_slot_tooltip", mouseX, mouseY) ||
            showTooltip(routeFilterModeButton,
                  "gui.mekanismqioprocessing.config_filter_mode_tooltip", mouseX, mouseY) ||
            showTooltip(productFilterButton,
                  "gui.mekanismqioprocessing.management_product_filter_tooltip", mouseX,
                  mouseY) ||
            showTooltip(searchField,
                  "gui.mekanismqioprocessing.config_search_tooltip", mouseX, mouseY) ||
            showTooltip(toggleButton,
                  "gui.mekanismqioprocessing.config_toggle_tooltip", mouseX, mouseY) ||
            showTooltip(upButton,
                  "gui.mekanismqioprocessing.config_move_up_tooltip", mouseX, mouseY) ||
            showTooltip(downButton,
                  "gui.mekanismqioprocessing.config_move_down_tooltip", mouseX, mouseY) ||
            showTooltip(resetProductButton,
                  "gui.mekanismqioprocessing.config_reset_product_tooltip", mouseX, mouseY) ||
            showTooltip(resetAllButton,
                  "gui.mekanismqioprocessing.config_reset_all_tooltip", mouseX, mouseY) ||
            showTooltip(globalAmountField,
                  "gui.mekanismqioprocessing.config_global_amount_tooltip", mouseX, mouseY) ||
            showTooltip(routeAmountField,
                  "gui.mekanismqioprocessing.config_route_amount_tooltip", mouseX,
                  mouseY)) {
            return;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return routeList.mouseScrolled(mouseX, mouseY, delta) ||
              productList.mouseScrolled(mouseX, mouseY, delta) ||
              super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    boolean isFor(UUID candidate) {
        return deviceUUID.equals(candidate);
    }

    private void processResponses() {
        long currentProductGeneration = cache.getProductGeneration();
        if (currentProductGeneration != productGeneration) {
            productGeneration = currentProductGeneration;
            if (pending != null && pending.kind == RequestKind.PRODUCTS) {
                Pending response = pending;
                pending = null;
                acceptProductResponse(response);
            }
        }
        long currentRouteGeneration = cache.getRouteGeneration();
        if (currentRouteGeneration != routeGeneration) {
            routeGeneration = currentRouteGeneration;
            if (pending != null && pending.kind == RequestKind.ROUTES) {
                Pending response = pending;
                pending = null;
                acceptRouteResponse(response);
            }
        }
        long currentMutationGeneration = cache.getMutationGeneration();
        if (currentMutationGeneration != mutationGeneration) {
            mutationGeneration = currentMutationGeneration;
            if (pending != null && pending.kind == RequestKind.MUTATION) {
                pending = null;
                Status status = cache.getMutationStatus();
                if (status == Status.INVALID_TARGET || status == Status.UNAVAILABLE) {
                    closeLater();
                } else {
                    queueProductReload();
                }
            }
        }
    }

    private void acceptProductResponse(Pending response) {
        Status status = cache.getProductStatus();
        QIOManagementRecipeSnapshot snapshot = cache.getProductPage();
        if (status != Status.OK || snapshot == null ||
            !deviceUUID.equals(snapshot.getDeviceUUID())) {
            if (status == Status.INVALID_TARGET || status == Status.UNAVAILABLE) {
                closeLater();
            }
            return;
        }
        if (!response.query.equals(query) || response.filter != productFilter ||
            !snapshot.getQuery().equals(query)) {
            queueProductReload();
            return;
        }
        if (response.offset == 0) {
            products.clear();
            productList.resetScroll();
        } else if (response.offset != products.size() || !sameSource(metadata, snapshot)) {
            queueProductReload();
            return;
        }
        products.addAll(snapshot.getProducts());
        productTotal = snapshot.getTotalSize();
        if (productFilter == ProductFilter.ALL) {
            catalogHasProducts = productTotal > 0;
        }
        metadata = snapshot;
        validateProductSelection();
        syncAmountFields(selectedRoute());
    }

    private void acceptRouteResponse(Pending response) {
        Status status = cache.getRouteStatus();
        QIOManagementRecipeSnapshot snapshot = cache.getRoutePage();
        if (status != Status.OK || snapshot == null ||
            !deviceUUID.equals(snapshot.getDeviceUUID())) {
            if (status == Status.INVALID_TARGET || status == Status.UNAVAILABLE) {
                closeLater();
            }
            return;
        }
        if (!Objects.equals(response.productKey, selectedProductKey) ||
            !snapshot.getProductKey().equals(selectedProductKey)) {
            routeReloadQueued = selectedProductKey != null;
            return;
        }
        if (!sameSource(metadata, snapshot)) {
            queueProductReload();
            return;
        }
        if (response.offset == 0) {
            routes.clear();
            routeList.resetScroll();
        } else if (response.offset != routes.size()) {
            routeReloadQueued = true;
            return;
        }
        routes.addAll(snapshot.getRoutes());
        routeTotal = snapshot.getTotalSize();
        metadata = snapshot;
        validateRouteSelection();
        syncAmountFields(selectedRoute());
    }

    private void queueProductReload() {
        if (pending != null && pending.kind != RequestKind.MUTATION) {
            pending = null;
        }
        products.clear();
        routes.clear();
        productTotal = 0;
        routeTotal = 0;
        metadata = null;
        selectedProductKey = null;
        selectedRouteKey = null;
        productList.resetScroll();
        routeList.resetScroll();
        cache.clearRecipes();
        productReloadQueued = true;
        routeReloadQueued = false;
    }

    private void queueRouteReload() {
        if (pending != null && pending.kind != RequestKind.MUTATION) {
            pending = null;
        }
        routes.clear();
        routeTotal = 0;
        selectedRouteKey = null;
        routeList.resetScroll();
        cache.clearRoutePage();
        routeReloadQueued = selectedProductKey != null;
    }

    private void requestProducts(int offset) {
        if (pending != null || !session.matches(terminalState)) return;
        UUID requestId = UUID.randomUUID();
        cache.expect(PageKind.PRODUCTS, requestId);
        pending = Pending.products(offset, query, productFilter, clientTick);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOManagementRecipeRequest.Message.products(
                    container.getTerminalWindowId(), terminalState, requestId, deviceUUID,
                    offset, PAGE_SIZE, query, productFilter));
    }

    private void requestRoutes(int offset) {
        if (pending != null || selectedProductKey == null ||
            !session.matches(terminalState)) return;
        UUID requestId = UUID.randomUUID();
        cache.expect(PageKind.ROUTES, requestId);
        pending = Pending.routes(offset, selectedProductKey, clientTick);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOManagementRecipeRequest.Message.routes(
                    container.getTerminalWindowId(), terminalState, requestId, deviceUUID,
                    selectedProductKey, offset, PAGE_SIZE, ""));
    }

    private void sendMutation(QIOAutomationRecipeProfileMutation mutation) {
        if (pending != null || metadata == null || !metadata.isEditable() ||
            !session.matches(terminalState)) return;
        UUID requestId = UUID.randomUUID();
        cache.expectMutation(requestId);
        pending = Pending.mutation(clientTick);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOManagementRecipeRequest.Message.mutate(
                    container.getTerminalWindowId(), terminalState, requestId, deviceUUID,
                    metadata.getProfileRevision(), mutation));
    }

    private void searchChanged(String value) {
        String updated = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!updated.equals(query)) {
            query = updated;
            searchAtTick = clientTick + SEARCH_DELAY_TICKS;
        }
    }

    private void cycleProductFilter() {
        ProductFilter[] values = ProductFilter.values();
        productFilter = values[(productFilter.ordinal() + 1) % values.length];
        productFilterButton.setMessage(tr(productFilterKey()));
        queueProductReload();
    }

    private String productFilterKey() {
        return switch (productFilter) {
            case ALL -> "gui.mekanismqioprocessing.management_products_all";
            case ENABLED -> "gui.mekanismqioprocessing.management_products_enabled";
            case DISABLED -> "gui.mekanismqioprocessing.management_products_disabled";
            case PARTIAL -> "gui.mekanismqioprocessing.management_products_partial";
        };
    }

    private void toggleProfileMode() {
        sendMutation(QIOAutomationRecipeProfileMutation.simple(Action.TOGGLE_PROFILE_MODE));
    }

    private void cycleGlobalProfile(int direction) {
        sendMutation(QIOAutomationRecipeProfileMutation.cycleGlobalProfile(direction));
    }

    private void toggleFilterMode() {
        sendMutation(QIOAutomationRecipeProfileMutation.simple(
              Action.TOGGLE_ROUTE_FILTER_MODE));
    }

    private void toggleSelection() {
        Product product = selectedProduct();
        Route route = selectedRoute();
        if (GuiScreen.isCtrlKeyDown()) {
            if (product != null) {
                sendMutation(QIOAutomationRecipeProfileMutation.product(
                      Action.DISABLE_PRODUCT, product.getProductKey()));
            }
        } else if (route != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.route(Action.TOGGLE_ROUTE,
                  route.getProductKey(), route.getRouteKey()));
        }
    }

    private void toggleProduct(Product product) {
        sendMutation(QIOAutomationRecipeProfileMutation.product(Action.TOGGLE_PRODUCT,
              product.getProductKey()));
    }

    private void moveSelection(int direction) {
        if (selectionFocus == SelectionFocus.PRODUCT) {
            Product product = selectedProduct();
            if (product != null) {
                sendMutation(GuiScreen.isCtrlKeyDown() ?
                      QIOAutomationRecipeProfileMutation.product(direction < 0 ?
                            Action.MOVE_PRODUCT_TO_TOP : Action.MOVE_PRODUCT_TO_BOTTOM,
                            product.getProductKey()) :
                      QIOAutomationRecipeProfileMutation.moveProduct(
                            product.getProductKey(), direction));
            }
        } else {
            Route route = selectedRoute();
            if (route != null) {
                sendMutation(GuiScreen.isCtrlKeyDown() ?
                      QIOAutomationRecipeProfileMutation.route(direction < 0 ?
                            Action.MOVE_ROUTE_TO_TOP : Action.MOVE_ROUTE_TO_BOTTOM,
                            route.getProductKey(), route.getRouteKey()) :
                      QIOAutomationRecipeProfileMutation.moveRoute(route.getProductKey(),
                            route.getRouteKey(), direction));
            }
        }
    }

    private void resetProduct() {
        Product product = selectedProduct();
        if (product != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.product(Action.RESET_PRODUCT,
                  product.getProductKey()));
        }
    }

    private void resetAll() {
        if (GuiScreen.isShiftKeyDown()) {
            sendMutation(QIOAutomationRecipeProfileMutation.setAllRoutesEnabled(
                  !areAllLoadedProductsEnabled()));
        } else {
            sendMutation(QIOAutomationRecipeProfileMutation.simple(Action.RESET_ALL));
        }
    }

    private void applyGlobalAmount() {
        if (metadata != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.amount(
                  Action.SET_GLOBAL_CRAFT_AMOUNT, "",
                  parseAmount(globalAmountField.getText(), metadata.getCraftAmount(),
                        metadata.getMaximumCraftAmount())));
        }
    }

    private void applyRouteAmount() {
        Route route = selectedRoute();
        if (route == null) return;
        if (routeAmountField.isEmpty()) {
            sendMutation(QIOAutomationRecipeProfileMutation.amount(
                  Action.CLEAR_ROUTE_CRAFT_AMOUNT, route.getRouteKey(), 0));
        } else {
            sendMutation(QIOAutomationRecipeProfileMutation.amount(
                  Action.SET_ROUTE_CRAFT_AMOUNT, route.getRouteKey(),
                  parseAmount(routeAmountField.getText(), route.getCraftAmount(),
                        route.getMaximumCraftAmount())));
        }
    }

    private void validateProductSelection() {
        Product selected = selectedProduct();
        if (selected == null && !products.isEmpty()) {
            selected = products.get(0);
            selectedProductKey = selected.getProductKey();
            selectionFocus = SelectionFocus.PRODUCT;
            queueRouteReload();
        }
    }

    private void validateRouteSelection() {
        if (selectedRoute() == null) {
            selectedRouteKey = routes.isEmpty() ? null : routes.get(0).getRouteKey();
        }
    }

    @Nullable
    private Product selectedProduct() {
        if (selectedProductKey != null) {
            for (Product product : products) {
                if (selectedProductKey.equals(product.getProductKey())) return product;
            }
        }
        return null;
    }

    @Nullable
    private Route selectedRoute() {
        if (selectedRouteKey != null) {
            for (Route route : routes) {
                if (selectedRouteKey.equals(route.getRouteKey())) return route;
            }
        }
        return null;
    }

    private void selectProduct(Product product) {
        if (!product.getProductKey().equals(selectedProductKey)) {
            selectedProductKey = product.getProductKey();
            selectionFocus = SelectionFocus.PRODUCT;
            queueRouteReload();
        } else {
            selectionFocus = SelectionFocus.PRODUCT;
        }
        updateControls();
    }

    private void selectRoute(Route route) {
        selectedRouteKey = route.getRouteKey();
        selectionFocus = SelectionFocus.ROUTE;
        syncAmountFields(route);
        updateControls();
    }

    private void updateControls() {
        Product product = selectedProduct();
        Route route = selectedRoute();
        boolean available = metadata != null && metadata.isEditable() && pending == null;
        boolean ctrl = GuiScreen.isCtrlKeyDown();

        toggleButton.active = available && (ctrl ? product != null : route != null);
        if (selectionFocus == SelectionFocus.PRODUCT) {
            upButton.active = available && product != null && product.getOrder() > 0;
            downButton.active = available && product != null &&
                  product.getOrder() + 1 < productTotal;
        } else {
            upButton.active = available && route != null && route.getOrder() > 0;
            downButton.active = available && route != null && route.getOrder() + 1 < routeTotal;
        }
        resetProductButton.active = available && product != null;
        boolean hasCatalog = catalogHasProducts || productTotal > 0;
        resetAllButton.active = available && hasCatalog;
        productFilterButton.active = metadata != null;
        globalAmountField.setEnabled(available && hasCatalog);
        routeAmountField.setEnabled(available && route != null);
        profileModeButton.active = available && hasCatalog;
        routeFilterModeButton.visible = metadata != null && hasCatalog &&
              mode == QIOAutomationMode.SCHEDULED;
        routeFilterModeButton.active = available && routeFilterModeButton.visible;
        globalProfileButton.visible = metadata != null && hasCatalog &&
              !metadata.isIndividualProfile();
        globalProfileButton.active = available && globalProfileButton.visible;

        if (metadata != null) {
            profileModeButton.setMessage(tr(metadata.isIndividualProfile() ?
                  "gui.mekanismqioprocessing.config_profile_machine" :
                  "gui.mekanismqioprocessing.config_profile_global"));
            routeFilterModeButton.setMessage(tr(metadata.getRouteFilterMode() ==
                  RouteFilterMode.WHITELIST ?
                  "gui.mekanismqioprocessing.config_filter_whitelist" :
                  "gui.mekanismqioprocessing.config_filter_blacklist"));
            globalProfileButton.setMessage(tr(
                  "gui.mekanismqioprocessing.config_profile_slot",
                  metadata.getGlobalProfileSlot()));
        }
        productFilterButton.setMessage(tr(productFilterKey()));
        toggleButton.setMessage(tr(ctrl ?
              "gui.mekanismqioprocessing.config_disable_product" :
              route != null && route.isProfileEnabled() ?
                    "gui.mekanismqioprocessing.config_disable" :
                    "gui.mekanismqioprocessing.config_enable"));
        upButton.setMessage(tr(ctrl ? "gui.mekanismqioprocessing.config_move_top" :
              "gui.mekanismqioprocessing.config_move_up"));
        downButton.setMessage(tr(ctrl ? "gui.mekanismqioprocessing.config_move_bottom" :
              "gui.mekanismqioprocessing.config_move_down"));
        resetAllButton.setMessage(tr(GuiScreen.isShiftKeyDown() ?
              areAllLoadedProductsEnabled() ?
                    "gui.mekanismqioprocessing.config_disable_all" :
                    "gui.mekanismqioprocessing.config_enable_all" :
              "gui.mekanismqioprocessing.config_reset_all"));
        updateProfileButtonLayout();
        syncAmountFields(route);
    }

    private void updateProfileButtonLayout() {
        if (metadata != null && mode == QIOAutomationMode.SCHEDULED) {
            int targetX = metadata.isIndividualProfile() ? PROFILE_MODE_BUTTON_X :
                  ROUTE_FILTER_MODE_BUTTON_X;
            moveElementToX(routeFilterModeButton,
                  getGuiLeft() + relativeX + targetX);
            routeFilterModeButton.setWidth(metadata.isIndividualProfile() ?
                  PROFILE_MODE_BUTTON_WIDTH : ROUTE_FILTER_MODE_BUTTON_WIDTH);
        }
        moveElementToX(globalProfileButton,
              getGuiLeft() + relativeX + PROFILE_MODE_BUTTON_X);
        globalProfileButton.setWidth(PROFILE_MODE_BUTTON_WIDTH);
    }

    private static void moveElementToX(GuiElement element, int targetX) {
        int delta = targetX - element.getX();
        if (delta != 0) element.move(delta, 0);
    }

    private void syncAmountFields(@Nullable Route route) {
        if (metadata != null && !globalAmountField.isFocused()) {
            String text = Long.toString(metadata.getCraftAmount());
            if (!text.equals(globalAmountField.getText())) {
                globalAmountField.setTextSilently(text);
            }
        }
        if (!routeAmountField.isFocused()) {
            String text = route != null && route.hasCraftAmountOverride() ?
                  Long.toString(route.getCraftAmount()) : "";
            if (!text.equals(routeAmountField.getText())) {
                routeAmountField.setTextSilently(text);
            }
        }
    }

    private boolean areAllLoadedProductsEnabled() {
        if (products.isEmpty()) return false;
        for (Product product : products) {
            if (product.getProfileEnabledCount() < product.getRouteCount()) return false;
        }
        return true;
    }

    private MekanismButton button(IGuiWrapper gui, int x, int y, int width, String key,
          Runnable action) {
        return new MekanismButton(gui, relativeX + x, relativeY + y, width, 12,
              tr(key), action, null);
    }

    private GuiTextField amountField(IGuiWrapper gui, int x, int y, Runnable enterHandler) {
        return new GuiTextField(gui, relativeX + x, relativeY + y,
              AMOUNT_FIELD_WIDTH, 12)
              .setBackground(BackgroundType.DIGITAL)
              .setTextColor(screenTextColor())
              .setMaxLength(19)
              .setInputValidator((c, keyCode) -> Character.isDigit(c) ||
                    GuiMekanism.isTextboxKey(c, keyCode))
              .setEnterHandler(enterHandler);
    }

    private static boolean isValidAmountText(String text, long maximum) {
        if (text == null || text.isEmpty()) return true;
        if (!text.chars().allMatch(Character::isDigit)) return false;
        try {
            long amount = Long.parseLong(text);
            return amount > 0 && amount <= maximum;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static long parseAmount(String text, long fallback, long maximum) {
        long checkedFallback = Math.max(1, Math.min(maximum, fallback));
        try {
            return text == null || text.isEmpty() ? checkedFallback :
                  Math.max(1, Math.min(maximum, Long.parseLong(text)));
        } catch (RuntimeException ignored) {
            return checkedFallback;
        }
    }

    private boolean showTooltip(GuiElement element, String key, int mouseX, int mouseY) {
        if (element.visible && mouseX >= element.getX() &&
            mouseX < element.getX() + element.getWidth() && mouseY >= element.getY() &&
            mouseY < element.getY() + element.getHeight()) {
            displayTooltip(tr(key), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void drawEmptyMessage(String key) {
        drawScaledScrollingString(tr(key), 6, 100, TextAlignment.CENTER,
              screenTextColor(), WIDTH - 12, 0, false, 1F, getTimeOpened());
    }

    private void drawWindowTitle() {
        String text = title.getFormattedText();
        int textWidth = getFont().getStringWidth(text);
        int titleX = relativeX + TITLE_X;
        int titleY = relativeY + 5;
        if (textWidth <= TITLE_WIDTH) {
            getFont().drawString(text, titleX + (TITLE_WIDTH - textWidth) / 2F,
                  titleY, titleTextColor(), false);
        } else {
            drawSmoothString(text, titleX, relativeY + 3, TITLE_WIDTH, 14,
                  titleTextColor());
        }
    }

    private void drawRowSelection(int x, int y, int width, int height,
          boolean selected, boolean focused, boolean hovered) {
        if (selected) {
            GuiUtils.fill(x, y, x + width, y + height,
                  focused ? ROW_FOCUSED_SELECTED_COLOR : ROW_SELECTED_COLOR);
            GuiUtils.drawOutline(x, y, width, height,
                  focused ? ROW_FOCUSED_SELECTED_OUTLINE_COLOR :
                        ROW_SELECTED_OUTLINE_COLOR);
        } else if (hovered) {
            GuiUtils.fill(x, y, x + width, y + height, ROW_HOVER_COLOR);
        }
    }

    private void drawSmoothString(String text, int x, int y, int width, int height,
          int color) {
        int textWidth = getFont().getStringWidth(text);
        if (textWidth <= 0 || width <= 0) return;
        boolean scrolling = textWidth > width;
        float drawX = x;
        if (scrolling) {
            enableGuiScissor(x, y, x + width, y + height);
            drawX -= scrollingOffset(textWidth, width);
        }
        float drawY = y + (height - getFont().FONT_HEIGHT) / 2F;
        getFont().drawString(text, drawX, drawY, color, false);
        if (scrolling) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawResources(List<ResourceAmount> resources, int x, int y,
          int width, int height) {
        if (resources.isEmpty() || width <= 0) return;
        int contentWidth = resourceStripWidth(resources);
        boolean scrolling = contentWidth > width;
        float offset = scrolling ? scrollingOffset(contentWidth, width) : 0;
        if (scrolling) enableGuiScissor(x, y, x + width, y + height);
        for (int index = 0; index < resources.size(); index++) {
            float drawX = x + index * RESOURCE_STEP - offset;
            if (drawX > x - RESOURCE_STEP && drawX < x + width) {
                QIOGuiResourceRenderer.renderIcon(gui(),
                      resources.get(index).getResource(), Math.round(drawX),
                      y + Math.max(0, (height - 16) / 2), 16);
            }
        }
        if (scrolling) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Nullable
    private ResourceAmount hoveredResource(List<ResourceAmount> resources,
          int mouseX, int mouseY, int x, int y, int width, int height) {
        int absoluteX = getGuiLeft() + x;
        int absoluteY = getGuiTop() + y;
        if (mouseX < absoluteX || mouseX >= absoluteX + width ||
            mouseY < absoluteY || mouseY >= absoluteY + height) return null;
        int contentWidth = resourceStripWidth(resources);
        float offset = contentWidth > width ? scrollingOffset(contentWidth, width) : 0;
        for (int index = 0; index < resources.size(); index++) {
            float resourceX = absoluteX + index * RESOURCE_STEP - offset;
            if (mouseX >= resourceX && mouseX < resourceX + 16) {
                return resources.get(index);
            }
        }
        return null;
    }

    private void renderResourceTooltip(ResourceAmount amount, int mouseX, int mouseY) {
        List<String> extra = Collections.singletonList(tr(
              "gui.mekanismqioprocessing.config_resource_amount",
              amount.getAmount()).getFormattedText());
        ItemStack item = QIOGuiResourceRenderer.item(amount.getResource());
        if (!item.isEmpty()) {
            gui().renderItemTooltipWithExtra(item, mouseX, mouseY, extra);
        } else {
            List<String> tooltip = QIOGuiResourceRenderer.tooltip(amount.getResource());
            tooltip.addAll(extra);
            displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private static String resourceSummary(List<ResourceAmount> resources) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < resources.size(); index++) {
            if (index > 0) result.append(" + ");
            ResourceAmount amount = resources.get(index);
            result.append(QIOGuiResourceRenderer.name(amount.getResource()))
                  .append(" x").append(amount.getAmount());
        }
        return result.toString();
    }

    private static int resourceStripWidth(List<ResourceAmount> resources) {
        return Math.max(16, (resources.size() - 1) * RESOURCE_STEP + 16);
    }

    private float scrollingOffset(double contentWidth, double areaWidth) {
        double overflow = contentWidth - areaWidth;
        if (overflow <= 0) return 0;
        double seconds = Math.max(0, GuiElement.getMillis() - getTimeOpened()) / 1_000D;
        double travel = overflow / SCROLL_PIXELS_PER_SECOND;
        double cycle = MIN_SCROLL_EDGE_PAUSE * 2 + travel * 2;
        double position = seconds % cycle;
        if (position < MIN_SCROLL_EDGE_PAUSE) return 0;
        position -= MIN_SCROLL_EDGE_PAUSE;
        if (position < travel) return (float) (position * SCROLL_PIXELS_PER_SECOND);
        if (position < travel + MIN_SCROLL_EDGE_PAUSE) return (float) overflow;
        position -= travel + MIN_SCROLL_EDGE_PAUSE;
        return (float) (overflow - position * SCROLL_PIXELS_PER_SECOND);
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

    private void closeLater() {
        if (!closeScheduled) {
            closeScheduled = true;
            if (gui() instanceof GuiMekanism<?> mekanismGui) {
                mekanismGui.queueWindowClose(this);
            } else {
                close();
            }
        }
    }

    private static boolean sameSource(@Nullable QIOManagementRecipeSnapshot first,
          QIOManagementRecipeSnapshot second) {
        return first == null || first.getDeviceUUID().equals(second.getDeviceUUID()) &&
              first.getRouteDirectoryRevision() == second.getRouteDirectoryRevision() &&
              first.getProfileRevision() == second.getProfileRevision() &&
              first.getPolicyRevision() == second.getPolicyRevision();
    }

    private static TextComponentTranslation tr(String key, Object... args) {
        return new TextComponentTranslation(key, args);
    }

    private final class ProductScrollList extends GuiScrollList {

        private ProductScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        @Override protected int getMaxElements() { return products.size(); }
        @Override public boolean hasSelection() { return selectedProduct() != null; }
        @Override protected void setSelected(int index) {
            if (index >= 0 && index < products.size()) selectProduct(products.get(index));
        }
        @Override public void clearSelection() {
            selectedProductKey = null;
            selectedRouteKey = null;
        }

        private boolean shouldRequestMore() {
            return getCurrentSelection() + getFocusedElements() + 2 >= getMaxElements();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Product product = hoveredProduct(mouseX, mouseY);
            if (button == 1 && GuiScreen.isShiftKeyDown() && product != null &&
                metadata != null && metadata.isEditable() && pending == null) {
                selectProduct(product);
                toggleProduct(product);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, products.size() - start));
            for (int index = 0; index < count; index++) {
                Product product = products.get(start + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean selected = product.getProductKey().equals(selectedProductKey);
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRowSelection(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      selected, selectionFocus == SelectionFocus.PRODUCT, hovered);
                int indicatorX = relativeX + barXShift - 10;
                GuiUtils.fill(indicatorX, rowY + 4, indicatorX + 6, rowY + 10,
                      productColor(product));
                if (product.getRouteCount() > 1) {
                    GuiUtils.fill(indicatorX, rowY + 12, indicatorX + 6, rowY + 18,
                          INDICATOR_MULTI_ROUTE);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, products.size() - start));
            for (int index = 0; index < count; index++) {
                Product product = products.get(start + index);
                int rowY = relativeY + 4 + index * elementHeight;
                QIOGuiResourceRenderer.renderIcon(gui(),
                      product.getProduct().getResource(), relativeX + 4, rowY, 16);
                drawSmoothString(QIOGuiResourceRenderer.name(
                      product.getProduct().getResource()), relativeX + 25, rowY,
                      barXShift - 38, 16, screenTextColor());
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Product product = hoveredProduct(mouseX, mouseY);
            if (product != null) renderResourceTooltip(product.getProduct(), mouseX, mouseY);
        }

        @Nullable
        private Product hoveredProduct(double mouseX, double mouseY) {
            if (mouseX < getX() + 1 || mouseX >= getX() + barXShift - 1 ||
                mouseY < getY() + 1 || mouseY >= getY() + height - 1) return null;
            int index = getCurrentSelection() + (int) (mouseY - getY() - 1) / elementHeight;
            return index >= 0 && index < products.size() ? products.get(index) : null;
        }
    }

    private final class RouteScrollList extends GuiScrollList {

        private RouteScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        @Override protected int getMaxElements() { return routes.size(); }
        @Override public boolean hasSelection() { return selectedRoute() != null; }
        @Override protected void setSelected(int index) {
            if (index >= 0 && index < routes.size()) selectRoute(routes.get(index));
        }
        @Override public void clearSelection() { selectedRouteKey = null; }

        private boolean shouldRequestMore() {
            return getCurrentSelection() + getFocusedElements() + 2 >= getMaxElements();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Route route = hoveredRoute(mouseX, mouseY);
            if (button == 1 && GuiScreen.isShiftKeyDown() && route != null &&
                metadata != null && metadata.isEditable() && pending == null) {
                selectRoute(route);
                sendMutation(QIOAutomationRecipeProfileMutation.route(Action.TOGGLE_ROUTE,
                      route.getProductKey(), route.getRouteKey()));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, routes.size() - start));
            for (int index = 0; index < count; index++) {
                Route route = routes.get(start + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean selected = route.getRouteKey().equals(selectedRouteKey);
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRowSelection(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      selected, selectionFocus == SelectionFocus.ROUTE, hovered);
                GuiUtils.fill(relativeX + barXShift - 10, rowY + 4,
                      relativeX + barXShift - 4, rowY + 10, routeColor(route));
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, routes.size() - start));
            for (int index = 0; index < count; index++) {
                Route route = routes.get(start + index);
                int rowY = relativeY + 4 + index * elementHeight;
                int inputX = relativeX + 4;
                int inputWidth = route.getConfigurationInputs().isEmpty() ? 38 : 24;
                int outputX = relativeX + barXShift - 54;
                int textX = inputX + inputWidth + 4;
                int textWidth = Math.max(20, outputX - textX - 4);
                drawResources(route.getInputs(), inputX, rowY, inputWidth, 16);
                if (!route.getConfigurationInputs().isEmpty()) {
                    drawResources(route.getConfigurationInputs(), inputX + inputWidth + 1,
                          rowY, 13, 16);
                }
                drawSmoothString(resourceSummary(route.getInputs()), textX, rowY,
                      textWidth, 16, route.isEffectiveEnabled() ? screenTextColor() :
                            route.isProfileEnabled() ? 0xE0C05A : 0x8A8A8A);
                drawResources(route.getOutputs(), outputX, rowY, 38, 16);
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Route route = hoveredRoute(mouseX, mouseY);
            if (route == null) return;
            int visibleIndex = (int) (mouseY - getY() - 1) / elementHeight;
            int rowY = relativeY + 4 + visibleIndex * elementHeight;
            ResourceAmount input = hoveredResource(route.getInputs(), mouseX, mouseY,
                  relativeX + 4, rowY,
                  route.getConfigurationInputs().isEmpty() ? 38 : 24, 16);
            if (input != null) {
                renderResourceTooltip(input, mouseX, mouseY);
                return;
            }
            if (!route.getConfigurationInputs().isEmpty()) {
                ResourceAmount configuration = hoveredResource(route.getConfigurationInputs(),
                      mouseX, mouseY, relativeX + 29, rowY, 13, 16);
                if (configuration != null) {
                    renderResourceTooltip(configuration, mouseX, mouseY);
                    return;
                }
            }
            ResourceAmount output = hoveredResource(route.getOutputs(), mouseX, mouseY,
                  relativeX + barXShift - 54, rowY, 38, 16);
            if (output != null) {
                renderResourceTooltip(output, mouseX, mouseY);
                return;
            }
            List<String> tooltip = new ArrayList<>();
            tooltip.add(resourceSummary(route.getInputs()));
            if (!route.getConfigurationInputs().isEmpty()) {
                tooltip.add(tr("gui.mekanismqioprocessing.config_retained_input",
                      resourceSummary(route.getConfigurationInputs())).getFormattedText());
            }
            tooltip.add("->");
            tooltip.add(resourceSummary(route.getOutputs()));
            tooltip.add(tr(routeStatusKey(route)).getFormattedText());
            tooltip.add(route.getRouteId());
            displayTooltips(tooltip, mouseX, mouseY);
        }

        @Nullable
        private Route hoveredRoute(double mouseX, double mouseY) {
            if (mouseX < getX() + 1 || mouseX >= getX() + barXShift - 1 ||
                mouseY < getY() + 1 || mouseY >= getY() + height - 1) return null;
            int index = getCurrentSelection() + (int) (mouseY - getY() - 1) / elementHeight;
            return index >= 0 && index < routes.size() ? routes.get(index) : null;
        }
    }

    private static int productColor(Product product) {
        if (product.getEffectiveEnabledCount() < product.getProfileEnabledCount() ||
            product.getProfileEnabledCount() > 0 &&
                  product.getProfileEnabledCount() < product.getRouteCount()) {
            return INDICATOR_PARTIAL;
        }
        return product.getProfileEnabledCount() > 0 ? INDICATOR_ENABLED :
              INDICATOR_DISABLED;
    }

    private static int routeColor(Route route) {
        return route.isEffectiveEnabled() ? INDICATOR_ENABLED :
              route.isProfileEnabled() ? INDICATOR_PARTIAL : INDICATOR_DISABLED;
    }

    private static String routeStatusKey(Route route) {
        return route.isEffectiveEnabled() ?
              "gui.mekanismqioprocessing.config_route_enabled" :
              route.isProfileEnabled() ?
                    "gui.mekanismqioprocessing.config_route_policy_blocked" :
                    "gui.mekanismqioprocessing.config_route_disabled";
    }

    private enum SelectionFocus {
        PRODUCT,
        ROUTE
    }

    private enum RequestKind {
        PRODUCTS,
        ROUTES,
        MUTATION
    }

    private static final class Pending {
        private final RequestKind kind;
        private final int offset;
        private final String query;
        @Nullable private final ProductFilter filter;
        @Nullable private final String productKey;
        private final long sentAtTick;

        private Pending(RequestKind kind, int offset, String query,
              @Nullable ProductFilter filter, @Nullable String productKey,
              long sentAtTick) {
            this.kind = kind;
            this.offset = offset;
            this.query = query;
            this.filter = filter;
            this.productKey = productKey;
            this.sentAtTick = sentAtTick;
        }

        private static Pending products(int offset, String query, ProductFilter filter,
              long tick) {
            return new Pending(RequestKind.PRODUCTS, offset, query, filter, null, tick);
        }

        private static Pending routes(int offset, String productKey, long tick) {
            return new Pending(RequestKind.ROUTES, offset, "", null, productKey, tick);
        }

        private static Pending mutation(long tick) {
            return new Pending(RequestKind.MUTATION, 0, "", null, null, tick);
        }
    }

    private static final class SessionIdentity {
        private final UUID nonce;
        private final UUID terminalUUID;
        private final long targetRevision;
        private final UUID frequencyUUID;
        private final long accessRevision;

        private SessionIdentity(UUID nonce, UUID terminalUUID, long targetRevision,
              UUID frequencyUUID, long accessRevision) {
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequencyUUID = frequencyUUID;
            this.accessRevision = accessRevision;
        }

        @Nullable
        private static SessionIdentity capture(QIOProcessingTerminalContainerState state) {
            return state.isValid() && state.getSessionNonce() != null &&
                  state.getTerminalUUID() != null && state.getFrequencyUUID() != null ?
                  new SessionIdentity(state.getSessionNonce(), state.getTerminalUUID(),
                        state.getTargetRevision(), state.getFrequencyUUID(),
                        state.getAccessRevision()) : null;
        }

        private boolean matches(QIOProcessingTerminalContainerState state) {
            return state.matches(nonce, terminalUUID, targetRevision, frequencyUUID,
                  accessRevision);
        }
    }
}
