package mekanism.qioprocessing.client.gui;

import mekanism.api.Coord4D;
import mekanism.api.gas.GasStack;
import mekanism.api.processing.MachineResourceKind;
import mekanism.api.processing.MachineResourceStack;
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
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigClientCache;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigSnapshot;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigSnapshot.Route;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation.Action;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import mekanism.qioprocessing.common.network.PacketQIOAutomationRecipeConfig.QIOAutomationRecipeConfigMessage;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** AE-aligned product and route profile editor for QIO machine automation. */
public final class GuiQIOAutomationRecipeConfigWindow extends GuiWindow {

    public static final int WIDTH = 336;
    private static final int HEIGHT = 220;
    private static final int PAGE_SIZE = 64;
    private static final long REQUEST_TIMEOUT_TICKS = 100;

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
    private static final int ROUTE_FILTER_MODE_BUTTON_X = ROUTE_LIST_X;
    private static final int ROUTE_FILTER_MODE_BUTTON_WIDTH = 64;

    private static final int INDICATOR_SIZE = 6;
    private static final int INDICATOR_ENABLED = 0xFF57C78B;
    private static final int INDICATOR_PARTIAL = 0xFFE0C05A;
    private static final int INDICATOR_DISABLED = 0xFFC75656;
    private static final int INDICATOR_MULTI_ROUTE = 0xFF4E9FDB;
    private static final int ROW_HOVER_COLOR = 0x503A4B5F;
    private static final int ROW_SELECTED_COLOR = 0x80608CC1;
    private static final int ROW_FOCUSED_SELECTED_COLOR = 0xB04E9FDB;
    private static final int ROW_SELECTED_OUTLINE_COLOR = 0xFF8FC7FF;
    private static final int ROW_FOCUSED_SELECTED_OUTLINE_COLOR = 0xFFDDF2FF;
    private static final int SCROLL_RESOURCE_STEP = 18;
    private static final double SCROLL_PIXELS_PER_SECOND = 12.0D;
    private static final double MIN_SCROLL_EDGE_PAUSE = 0.5D;

    private final TileEntityContainerBlock tile;
    private final QIOAutomationContainerState state;
    private final QIOAutomationRecipeConfigType configType;
    private final Coord4D coord;
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

    private final Map<String, ProductView> loadedProducts = new LinkedHashMap<>();
    private List<ProductView> products = Collections.emptyList();
    private List<ProductView> filteredProducts = Collections.emptyList();
    private ProductFilterMode productFilterMode = ProductFilterMode.ALL;
    private SelectionFocus selectionFocus = SelectionFocus.PRODUCT;
    private String productSearch = "";
    @Nullable
    private String selectedProductKey;
    @Nullable
    private String selectedRouteKey;
    private int offset;
    private int loadedRouteCount;
    private long loadedProfileRevision = Long.MIN_VALUE;
    private long loadedPolicyRevision = Long.MIN_VALUE;
    private long clientTick;
    private long lastGeneration = Long.MIN_VALUE;
    @Nullable
    private UUID frequencyUUID;
    @Nullable
    private UUID pendingRequestId;
    private long pendingSince = Long.MIN_VALUE;
    private boolean closeScheduled;
    private boolean closed;

    public GuiQIOAutomationRecipeConfigWindow(IGuiWrapper gui, int x, int y,
          TileEntityContainerBlock tile, MekanismTileContainer<?> container,
          QIOAutomationContainerState state, QIOAutomationRecipeConfigType configType,
          SelectedWindowData windowData) {
        super(gui, x, y, WIDTH, HEIGHT, windowData);
        this.tile = Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(container, "container");
        this.state = Objects.requireNonNull(state, "state");
        this.configType = Objects.requireNonNull(configType, "configType");
        if (windowData.type != configType.getWindowType()) {
            throw new IllegalArgumentException(
                  "QIO automation config window type does not match its recipe mode");
        }
        coord = Coord4D.get(tile);
        interactionStrategy = InteractionStrategy.ALL;

        profileModeButton = addChild(button(gui, PROFILE_MODE_BUTTON_X, 6,
              PROFILE_MODE_BUTTON_WIDTH,
              "gui.mekanismqioprocessing.config_profile_global", this::toggleProfileMode));
        routeFilterModeButton = addChild(button(gui, ROUTE_FILTER_MODE_BUTTON_X, 22,
              ROUTE_FILTER_MODE_BUTTON_WIDTH,
              "gui.mekanismqioprocessing.config_filter_blacklist", this::toggleFilterMode));
        globalProfileButton = addChild(new MekanismButton(gui,
              relativeX + PROFILE_MODE_BUTTON_X, relativeY + 22,
              PROFILE_MODE_BUTTON_WIDTH, 12,
              tr("gui.mekanismqioprocessing.config_profile_slot"),
              () -> cycleGlobalProfile(1), () -> cycleGlobalProfile(-1), null));
        productFilterButton = addChild(button(gui, PRODUCT_FILTER_BUTTON_X, 22,
              PRODUCT_FILTER_BUTTON_WIDTH,
              productFilterMode.translationKey, this::cycleProductFilterMode));
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
              snapshot() == null ? Long.MAX_VALUE : snapshot().getMaximumCraftAmount()));
        routeAmountField.setTextValidator(text -> {
            Route route = getSelectedRoute();
            return isValidAmountText(text, route == null ? Long.MAX_VALUE :
                  route.getMaximumCraftAmount());
        });

        resetProductButton = addChild(button(gui, 6, BOTTOM_BUTTON_Y, 94,
              "gui.mekanismqioprocessing.config_reset_product", this::resetProduct));
        resetAllButton = addChild(button(gui, 104, BOTTOM_BUTTON_Y, 66,
              "gui.mekanismqioprocessing.config_reset_all", this::resetAllOrToggleAll));
        toggleButton = addChild(button(gui, 174, BOTTOM_BUTTON_Y, 54,
              "gui.mekanismqioprocessing.config_enable", this::toggleSelection));
        upButton = addChild(button(gui, 232, BOTTOM_BUTTON_Y, 46,
              "gui.mekanismqioprocessing.config_move_up", () -> moveSelection(-1)));
        downButton = addChild(button(gui, 282, BOTTOM_BUTTON_Y, 48,
              "gui.mekanismqioprocessing.config_move_down", () -> moveSelection(1)));

        frequencyUUID = currentFrequencyUUID();
        clearCache();
        startReload(true);
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        clientTick++;
        if (!isConfigAvailable()) {
            clearCache();
            clearLoadedProducts();
            validateSelection();
            updateButtons();
            closeLater();
            return;
        }
        UUID currentFrequency = currentFrequencyUUID();
        if (!Objects.equals(currentFrequency, frequencyUUID)) {
            frequencyUUID = currentFrequency;
            selectedProductKey = null;
            selectedRouteKey = null;
            clearCache();
            startReload(true);
        }

        QIOAutomationRecipeConfigClientCache.View view = cacheView();
        if (view != null && view.isStale() && pendingRequestId == null) {
            startReload(false);
            view = cacheView();
        }
        if (pendingRequestId != null && view != null && !view.isPending() &&
            view.getGeneration() != lastGeneration) {
            pendingRequestId = null;
            pendingSince = Long.MIN_VALUE;
        } else if (pendingRequestId != null &&
                   clientTick - pendingSince >= REQUEST_TIMEOUT_TICKS) {
            if (view != null && view.isStale()) {
                startReload(false);
            } else {
                requestPage(offset);
            }
            view = cacheView();
        }
        if (view != null && view.getGeneration() != lastGeneration) {
            lastGeneration = view.getGeneration();
            // A stale broadcast has already scheduled a replacement request. Its old
            // snapshot must not be consumed while that replacement is pending.
            if (view.isStale()) {
                if (!view.isPending()) {
                    startReload(false);
                }
                updateButtons();
                return;
            }
            if (view.getStatus() == QIOAutomationRecipeConfigClientCache.Status.OK) {
                acceptSnapshot(view.getSnapshot());
                syncAmountFields(getSelectedRoute());
            } else if (view.getSnapshot() != null && pendingRequestId == null) {
                startReload(false);
            }
        }
        updateButtons();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            clearCache();
            super.close();
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(tr(configType.getTitleKey()), 5);
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_search"),
                  PRODUCT_SEARCH_X + 5, 24, TextAlignment.LEFT, 0x707070,
                  PRODUCT_SEARCH_WIDTH - 7, 0, false, 0.8F, getTimeOpened());
        }

        QIOAutomationRecipeConfigSnapshot snapshot = snapshot();
        if (snapshot == null || snapshot.isIndividualProfile()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_routes"),
                  ROUTE_LIST_X + 2, 24, TextAlignment.LEFT, titleTextColor(),
                  ROUTE_LIST_WIDTH, 0, false, 0.9F, getTimeOpened());
        }
        drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_global_amount_short"),
              8, AMOUNT_ROW_Y + 2, TextAlignment.LEFT, titleTextColor(),
              GLOBAL_AMOUNT_FIELD_X - 12, 0, false, 0.82F, getTimeOpened());

        Route selectedRoute = getSelectedRoute();
        String routeAmount = tr("gui.mekanismqioprocessing.config_route_amount_short")
              .getFormattedText() + ": " +
              (selectedRoute == null ? "-" : selectedRoute.getCraftAmount());
        drawScaledScrollingString(new TextComponentString(routeAmount), ROUTE_LIST_X + 2,
              AMOUNT_ROW_Y + 2, TextAlignment.LEFT, titleTextColor(),
              ROUTE_AMOUNT_FIELD_X - ROUTE_LIST_X - 6, 0, false, 0.82F,
              getTimeOpened());
        if (routeAmountField.isEmpty() && !routeAmountField.isFocused()) {
            drawScaledScrollingString(tr("gui.mekanismqioprocessing.config_amount_inherit"),
                  ROUTE_AMOUNT_FIELD_X + 4, AMOUNT_ROW_Y + 2, TextAlignment.LEFT,
                  0x707070, AMOUNT_FIELD_WIDTH - 6, 0, false, 0.8F, getTimeOpened());
        }

        QIOAutomationRecipeConfigClientCache.View view = cacheView();
        if (view == null || (snapshot == null && (view.isPending() ||
              view.getStatus() == QIOAutomationRecipeConfigClientCache.Status.OK))) {
            drawEmptyMessage("gui.mekanismqioprocessing.config_loading");
        } else if (view.getStatus() != QIOAutomationRecipeConfigClientCache.Status.OK) {
            drawEmptyMessage(statusKey(view.getStatus()));
        } else if (loadedRouteCount == 0) {
            drawEmptyMessage("gui.mekanismqioprocessing.config_empty");
        } else if (getFilteredProducts().isEmpty()) {
            drawEmptyMessage("gui.mekanismqioprocessing.config_no_match");
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
                  "gui.mekanismqioprocessing.config_product_filter_tooltip", mouseX, mouseY) ||
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
                  "gui.mekanismqioprocessing.config_route_amount_tooltip", mouseX, mouseY)) {
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

    boolean isFor(TileEntityContainerBlock candidate, QIOAutomationRecipeConfigType type) {
        return tile == candidate && configType == type;
    }

    private void drawEmptyMessage(String key) {
        drawScaledScrollingString(tr(key), 6, 100, TextAlignment.CENTER,
              screenTextColor(), WIDTH - 12, 0, false, 1F, getTimeOpened());
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
              .setInputValidator(this::isPositiveIntegerInput)
              .setEnterHandler(enterHandler);
    }

    private boolean isPositiveIntegerInput(char c, int keyCode) {
        return Character.isDigit(c) || GuiMekanism.isTextboxKey(c, keyCode);
    }

    private static boolean isValidAmountText(String text, long maximum) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        if (!text.chars().allMatch(Character::isDigit)) {
            return false;
        }
        try {
            long amount = Long.parseLong(text);
            return amount > 0 && amount <= maximum;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean showTooltip(GuiElement element, String key, int mouseX, int mouseY) {
        if (element.visible && isMouseOverArea(mouseX, mouseY, element.getX(), element.getY(),
              element.getWidth(), element.getHeight())) {
            displayTooltip(tr(key), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static boolean isMouseOverArea(double mouseX, double mouseY, int x, int y,
          int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void searchChanged(String value) {
        productSearch = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        rebuildFilteredProducts();
        productList.resetScroll();
        routeList.resetScroll();
        validateSelection();
        updateButtons();
    }

    private void cycleProductFilterMode() {
        productFilterMode = productFilterMode.next(configType ==
              QIOAutomationRecipeConfigType.SCHEDULED);
        rebuildFilteredProducts();
        productList.resetScroll();
        routeList.resetScroll();
        validateSelection();
        updateButtons();
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
        if (GuiScreen.isCtrlKeyDown()) {
            ProductView product = getSelectedProduct();
            if (product != null) {
                sendMutation(QIOAutomationRecipeProfileMutation.product(
                      Action.DISABLE_PRODUCT, product.productKey));
            }
            return;
        }
        toggleRoute();
    }

    private void toggleRoute() {
        Route route = getSelectedRoute();
        if (route != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.route(Action.TOGGLE_ROUTE,
                  route.getProductKey(), route.getRouteKey()));
        }
    }

    private void toggleProduct(ProductView product) {
        sendMutation(QIOAutomationRecipeProfileMutation.product(Action.TOGGLE_PRODUCT,
              product.productKey));
    }

    private void moveSelection(int direction) {
        if (selectionFocus == SelectionFocus.PRODUCT) {
            moveProduct(direction);
        } else {
            moveRoute(direction);
        }
    }

    private void moveProduct(int direction) {
        ProductView product = getSelectedProduct();
        if (product != null) {
            sendMutation(GuiScreen.isCtrlKeyDown() ?
                  QIOAutomationRecipeProfileMutation.product(direction < 0 ?
                        Action.MOVE_PRODUCT_TO_TOP : Action.MOVE_PRODUCT_TO_BOTTOM,
                        product.productKey) :
                  QIOAutomationRecipeProfileMutation.moveProduct(product.productKey,
                        direction));
        }
    }

    private void moveRoute(int direction) {
        Route route = getSelectedRoute();
        if (route != null) {
            sendMutation(GuiScreen.isCtrlKeyDown() ?
                  QIOAutomationRecipeProfileMutation.route(direction < 0 ?
                        Action.MOVE_ROUTE_TO_TOP : Action.MOVE_ROUTE_TO_BOTTOM,
                        route.getProductKey(), route.getRouteKey()) :
                  QIOAutomationRecipeProfileMutation.moveRoute(route.getProductKey(),
                        route.getRouteKey(), direction));
        }
    }

    private void resetProduct() {
        ProductView product = getSelectedProduct();
        if (product != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.product(Action.RESET_PRODUCT,
                  product.productKey));
        }
    }

    private void resetAllOrToggleAll() {
        if (GuiScreen.isShiftKeyDown()) {
            sendMutation(QIOAutomationRecipeProfileMutation.setAllRoutesEnabled(
                  !areAllVisibleRoutesEnabled()));
        } else {
            sendMutation(QIOAutomationRecipeProfileMutation.simple(Action.RESET_ALL));
        }
    }

    private void applyGlobalAmount() {
        QIOAutomationRecipeConfigSnapshot snapshot = snapshot();
        if (snapshot != null) {
            sendMutation(QIOAutomationRecipeProfileMutation.amount(
                  Action.SET_GLOBAL_CRAFT_AMOUNT, "",
                  parseAmount(globalAmountField.getText(), snapshot.getCraftAmount(),
                        snapshot.getMaximumCraftAmount())));
        }
    }

    private void applyRouteAmount() {
        Route route = getSelectedRoute();
        if (route == null) {
            return;
        }
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

    private void requestPage(int requestedOffset) {
        if (!isConfigAvailable()) {
            return;
        }
        offset = Math.max(0, requestedOffset);
        UUID requestId = UUID.randomUUID();
        pendingRequestId = requestId;
        pendingSince = clientTick;
        QIOAutomationRecipeConfigClientCache.expect(coord, configType, requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              QIOAutomationRecipeConfigMessage.request(coord, configType, requestId,
                    offset, PAGE_SIZE, ""));
    }

    private void sendMutation(QIOAutomationRecipeProfileMutation mutation) {
        QIOAutomationRecipeConfigSnapshot snapshot = snapshot();
        if (snapshot == null || pendingRequestId != null || !editable()) {
            return;
        }
        UUID requestId = UUID.randomUUID();
        offset = 0;
        pendingRequestId = requestId;
        pendingSince = clientTick;
        QIOAutomationRecipeConfigClientCache.expect(coord, configType, requestId);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              QIOAutomationRecipeConfigMessage.mutate(coord, configType, requestId,
                    snapshot.getProfileRevision(), mutation, 0, PAGE_SIZE, ""));
    }

    private void startReload(boolean clearVisibleRoutes) {
        offset = 0;
        loadedProfileRevision = Long.MIN_VALUE;
        loadedPolicyRevision = Long.MIN_VALUE;
        if (clearVisibleRoutes) {
            clearLoadedProducts();
            validateSelection();
        }
        requestPage(0);
        QIOAutomationRecipeConfigClientCache.View view = cacheView();
        if (view != null) {
            // Do not consume the old generation that caused this reload.
            lastGeneration = view.getGeneration();
        }
    }

    private void acceptSnapshot(@Nullable QIOAutomationRecipeConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!snapshot.getQuery().isEmpty()) {
            // This window intentionally loads the complete catalog and filters locally;
            // never merge a legacy/server-side filtered page into that catalog.
            startReload(false);
            return;
        }
        if (snapshot.getOffset() == 0) {
            clearLoadedProducts();
            loadedProfileRevision = snapshot.getProfileRevision();
            loadedPolicyRevision = snapshot.getPolicyRevision();
        } else if (snapshot.getProfileRevision() != loadedProfileRevision ||
                   snapshot.getPolicyRevision() != loadedPolicyRevision ||
                   snapshot.getOffset() != loadedRouteCount) {
            startReload(false);
            return;
        }
        for (Route route : snapshot.getRoutes()) {
            ProductView product = loadedProducts.computeIfAbsent(route.getProductKey(),
                  key -> new ProductView(key, route.getOutputs(), route.getProductOrder()));
            product.routes.add(route);
        }
        loadedRouteCount += snapshot.getRoutes().size();
        publishProducts();
        if (snapshot.hasNextPage()) {
            requestPage(snapshot.getOffset() + snapshot.getRoutes().size());
        }
    }

    private void clearLoadedProducts() {
        loadedProducts.clear();
        loadedRouteCount = 0;
        products = Collections.emptyList();
        filteredProducts = Collections.emptyList();
    }

    private void publishProducts() {
        products = Collections.unmodifiableList(new ArrayList<>(loadedProducts.values()));
        rebuildFilteredProducts();
        validateSelection();
    }

    private void rebuildFilteredProducts() {
        if ((productFilterMode == ProductFilterMode.ALL && productSearch.isEmpty()) ||
            products.isEmpty()) {
            filteredProducts = products;
            return;
        }
        List<ProductView> filtered = new ArrayList<>();
        for (ProductView product : products) {
            if (productFilterMode.matches(product) && matchesSearch(product)) {
                filtered.add(product);
            }
        }
        filteredProducts = Collections.unmodifiableList(filtered);
    }

    private boolean matchesSearch(ProductView product) {
        if (productSearch.isEmpty() || product.productKey.toLowerCase(Locale.ROOT)
              .contains(productSearch)) {
            return true;
        }
        for (MachineResourceStack output : product.outputs) {
            if (resourceName(output).toLowerCase(Locale.ROOT).contains(productSearch)) {
                return true;
            }
        }
        return false;
    }

    private void validateSelection() {
        List<ProductView> filtered = getFilteredProducts();
        if (filtered.isEmpty()) {
            selectedProductKey = null;
            selectedRouteKey = null;
            return;
        }
        ProductView product = getSelectedProduct();
        if (product == null) {
            product = filtered.get(0);
            selectedProductKey = product.productKey;
        }
        if (getSelectedRoute() == null) {
            selectedRouteKey = product.routes.isEmpty() ? null :
                  product.routes.get(0).getRouteKey();
        }
    }

    private List<ProductView> getFilteredProducts() {
        return filteredProducts;
    }

    @Nullable
    private ProductView getSelectedProduct() {
        if (selectedProductKey == null) {
            return null;
        }
        for (ProductView product : getFilteredProducts()) {
            if (selectedProductKey.equals(product.productKey)) {
                return product;
            }
        }
        return null;
    }

    @Nullable
    private Route getSelectedRoute() {
        ProductView product = getSelectedProduct();
        if (product == null || selectedRouteKey == null) {
            return null;
        }
        for (Route route : product.routes) {
            if (selectedRouteKey.equals(route.getRouteKey())) {
                return route;
            }
        }
        return null;
    }

    private void updateButtons() {
        QIOAutomationRecipeConfigSnapshot snapshot = snapshot();
        ProductView product = getSelectedProduct();
        Route route = getSelectedRoute();
        boolean hasSnapshot = snapshot != null;
        boolean available = hasSnapshot && editable() && pendingRequestId == null;
        boolean hasProduct = product != null;
        boolean hasRoute = route != null;
        boolean ctrlDown = GuiScreen.isCtrlKeyDown();

        toggleButton.active = available && (ctrlDown ? hasProduct : hasRoute);
        if (selectionFocus == SelectionFocus.PRODUCT) {
            upButton.active = available && hasProduct && product.productOrder > 0;
            downButton.active = available && hasProduct && hasProductAfter(product, snapshot);
        } else {
            upButton.active = available && hasRoute && route.getRouteOrder() > 0;
            downButton.active = available && hasRoute && hasRouteAfter(route, snapshot);
        }
        resetProductButton.active = available && hasProduct;
        resetAllButton.active = available && snapshot.getTotalSize() > 0;
        productFilterButton.active = hasSnapshot && snapshot.getTotalSize() > 0;
        globalAmountField.setEnabled(available && snapshot.getTotalSize() > 0);
        routeAmountField.setEnabled(available && hasRoute);
        profileModeButton.active = available && snapshot.getTotalSize() > 0;
        routeFilterModeButton.visible = hasSnapshot && snapshot.getTotalSize() > 0 &&
              snapshot.isRouteFilterMutable();
        routeFilterModeButton.active = available && routeFilterModeButton.visible;
        globalProfileButton.visible = hasSnapshot && snapshot.getTotalSize() > 0 &&
              !snapshot.isIndividualProfile();
        globalProfileButton.active = available && globalProfileButton.visible;

        if (hasSnapshot) {
            profileModeButton.setMessage(tr(snapshot.isIndividualProfile() ?
                  "gui.mekanismqioprocessing.config_profile_machine" :
                  "gui.mekanismqioprocessing.config_profile_global"));
            routeFilterModeButton.setMessage(tr(snapshot.getRouteFilterMode() ==
                  RouteFilterMode.WHITELIST ?
                  "gui.mekanismqioprocessing.config_filter_whitelist" :
                  "gui.mekanismqioprocessing.config_filter_blacklist"));
            globalProfileButton.setMessage(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.config_profile_slot",
                  snapshot.getGlobalProfileSlot()));
        }
        productFilterButton.setMessage(tr(productFilterMode.translationKey));
        toggleButton.setMessage(tr(ctrlDown ?
              "gui.mekanismqioprocessing.config_disable_product" :
              route != null && route.isProfileEnabled() ?
                    "gui.mekanismqioprocessing.config_disable" :
                    "gui.mekanismqioprocessing.config_enable"));
        upButton.setMessage(tr(ctrlDown ?
              "gui.mekanismqioprocessing.config_move_top" :
              "gui.mekanismqioprocessing.config_move_up"));
        downButton.setMessage(tr(ctrlDown ?
              "gui.mekanismqioprocessing.config_move_bottom" :
              "gui.mekanismqioprocessing.config_move_down"));
        resetAllButton.setMessage(tr(GuiScreen.isShiftKeyDown() ?
              areAllVisibleRoutesEnabled() ?
                    "gui.mekanismqioprocessing.config_disable_all" :
                    "gui.mekanismqioprocessing.config_enable_all" :
              "gui.mekanismqioprocessing.config_reset_all"));
        updateProfileButtonLayout(snapshot);
        syncAmountFields(route);
    }

    private void updateProfileButtonLayout(
          @Nullable QIOAutomationRecipeConfigSnapshot snapshot) {
        if (snapshot != null && snapshot.isRouteFilterMutable()) {
            if (snapshot.isIndividualProfile()) {
                moveElementToX(routeFilterModeButton,
                      getGuiLeft() + relativeX + PROFILE_MODE_BUTTON_X);
                routeFilterModeButton.setWidth(PROFILE_MODE_BUTTON_WIDTH);
            } else {
                moveElementToX(routeFilterModeButton,
                      getGuiLeft() + relativeX + ROUTE_FILTER_MODE_BUTTON_X);
                routeFilterModeButton.setWidth(ROUTE_FILTER_MODE_BUTTON_WIDTH);
            }
        }
        moveElementToX(globalProfileButton,
              getGuiLeft() + relativeX + PROFILE_MODE_BUTTON_X);
        globalProfileButton.setWidth(PROFILE_MODE_BUTTON_WIDTH);
    }

    private static void moveElementToX(GuiElement element, int targetX) {
        int delta = targetX - element.getX();
        if (delta != 0) {
            element.move(delta, 0);
        }
    }

    private boolean hasProductAfter(ProductView selected,
          QIOAutomationRecipeConfigSnapshot snapshot) {
        for (ProductView product : products) {
            if (product.productOrder > selected.productOrder) {
                return true;
            }
        }
        return snapshot.hasNextPage();
    }

    private boolean hasRouteAfter(Route selected,
          QIOAutomationRecipeConfigSnapshot snapshot) {
        ProductView product = getSelectedProduct();
        if (product != null) {
            for (Route route : product.routes) {
                if (route.getRouteOrder() > selected.getRouteOrder()) {
                    return true;
                }
            }
        }
        return snapshot.hasNextPage() && !products.isEmpty() &&
              products.get(products.size() - 1).productKey.equals(selected.getProductKey());
    }

    private boolean areAllVisibleRoutesEnabled() {
        boolean found = false;
        for (ProductView product : products) {
            for (Route route : product.routes) {
                found = true;
                if (!route.isProfileEnabled()) {
                    return false;
                }
            }
        }
        return found;
    }

    private void syncAmountFields(@Nullable Route route) {
        QIOAutomationRecipeConfigSnapshot snapshot = snapshot();
        if (snapshot != null && !globalAmountField.isFocused()) {
            String text = Long.toString(snapshot.getCraftAmount());
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

    private boolean editable() {
        QIOAutomationRecipeConfigClientCache.View view = cacheView();
        return view != null && view.getStatus() ==
              QIOAutomationRecipeConfigClientCache.Status.OK &&
              view.getSnapshot() != null && view.getSnapshot().isEditable() &&
              isConfigAvailable();
    }

    @Nullable
    private QIOAutomationRecipeConfigSnapshot snapshot() {
        QIOAutomationRecipeConfigClientCache.View view = cacheView();
        return view == null ? null : view.getSnapshot();
    }

    @Nullable
    private QIOAutomationRecipeConfigClientCache.View cacheView() {
        return QIOAutomationRecipeConfigClientCache.get(coord, configType);
    }

    private boolean isConfigAvailable() {
        return GuiQIOAutomationRecipeConfigTab.canOpen(tile, state, configType);
    }

    @Nullable
    private UUID currentFrequencyUUID() {
        return state.getReference() == null ? null :
              state.getReference().getFrequencyUUID();
    }

    private void clearCache() {
        QIOAutomationRecipeConfigClientCache.clear(coord, configType);
        pendingRequestId = null;
        pendingSince = Long.MIN_VALUE;
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

    private static long parseAmount(String text, long fallback, long maximum) {
        long checkedFallback = Math.max(1, Math.min(maximum, fallback));
        if (text == null || text.isEmpty()) {
            return checkedFallback;
        }
        try {
            return Math.max(1, Math.min(maximum, Long.parseLong(text)));
        } catch (RuntimeException ignored) {
            return checkedFallback;
        }
    }

    private static String statusKey(QIOAutomationRecipeConfigClientCache.Status status) {
        return switch (status) {
            case REVISION_CONFLICT -> "gui.mekanismqioprocessing.config_conflict";
            case INVALID_TARGET -> "gui.mekanismqioprocessing.config_invalid";
            case UNAVAILABLE -> "gui.mekanismqioprocessing.config_unavailable";
            default -> "gui.mekanismqioprocessing.config_loading";
        };
    }

    private static TextComponentTranslation tr(String key, Object... args) {
        return new TextComponentTranslation(key, args);
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

    private void drawSmoothScrollingString(TextComponentString text, int x, int y,
          int width, int height, int color) {
        int textWidth = getFont().getStringWidth(text.getFormattedText());
        if (textWidth <= 0 || width <= 0) {
            return;
        }
        boolean scrolling = textWidth > width;
        float drawX = x;
        if (scrolling) {
            enableGuiScissor(x, y, x + width, y + height);
            drawX -= getScrollingOffset(textWidth, width, getTimeOpened(),
                  !getFont().getBidiFlag());
        }
        float drawY = y + (height - getFont().FONT_HEIGHT) / 2F;
        getFont().drawString(text.getFormattedText(), drawX, drawY, color, false);
        if (scrolling) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void drawScrollingResourceStrip(List<MachineResourceStack> stacks, int x, int y,
          int width, int height) {
        if (stacks.isEmpty() || width <= 0) {
            return;
        }
        int contentWidth = getResourceStripWidth(stacks);
        boolean scrolling = contentWidth > width;
        float offset = scrolling ? getScrollingOffset(contentWidth, width,
              getTimeOpened(), true) : 0;
        if (scrolling) {
            enableGuiScissor(x, y, x + width, y + height);
        }
        for (int index = 0; index < stacks.size(); index++) {
            float resourceX = x + index * SCROLL_RESOURCE_STEP - offset;
            if (resourceX > x - SCROLL_RESOURCE_STEP && resourceX < x + width) {
                drawResource(stacks.get(index), Math.round(resourceX),
                      y + Math.max(0, (height - 16) / 2));
            }
        }
        if (scrolling) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void drawResource(MachineResourceStack stack, int x, int y) {
        MachineResourceStack display = displayResource(stack);
        switch (display.kind()) {
            case ITEM -> {
                ItemStack item = display.itemStack();
                if (!item.isEmpty()) {
                    gui().renderItemWithOverlay(item, x, y, 1F, null);
                }
            }
            case FLUID -> {
                FluidStack fluid = display.fluidStack();
                if (fluid != null) {
                    GuiUtils.drawFluidBarSprite(x, y, 16, 16, 16, fluid, true);
                }
            }
            case GAS -> {
                GasStack gas = display.gasStack();
                if (gas != null) {
                    GuiUtils.drawGasBarSprite(x, y, 16, 16, 16, gas, true);
                }
            }
        }
    }

    @Nullable
    private MachineResourceStack getHoveredScrollingResource(
          List<MachineResourceStack> stacks, int mouseX, int mouseY,
          int x, int y, int width, int height) {
        if (stacks.isEmpty() || width <= 0) {
            return null;
        }
        int absoluteX = getGuiLeft() + x;
        int absoluteY = getGuiTop() + y;
        if (!isMouseOverArea(mouseX, mouseY, absoluteX, absoluteY, width, height)) {
            return null;
        }
        int contentWidth = getResourceStripWidth(stacks);
        float offset = contentWidth > width ? getScrollingOffset(contentWidth, width,
              getTimeOpened(), true) : 0;
        for (int index = 0; index < stacks.size(); index++) {
            float resourceX = absoluteX + index * SCROLL_RESOURCE_STEP - offset;
            if (mouseX >= resourceX && mouseX < resourceX + 16) {
                return stacks.get(index);
            }
        }
        return null;
    }

    private static int getResourceStripWidth(List<MachineResourceStack> stacks) {
        return Math.max(16, (stacks.size() - 1) * SCROLL_RESOURCE_STEP + 16);
    }

    private float getScrollingOffset(double contentWidth, double areaWidth,
          long msVisible, boolean leftToRight) {
        double overflowWidth = contentWidth - areaWidth;
        if (overflowWidth <= 0) {
            return 0;
        }
        long visibleDuration = Math.max(0, GuiElement.getMillis() - msVisible);
        double seconds = visibleDuration / 1_000D;
        double travelTime = overflowWidth / SCROLL_PIXELS_PER_SECOND;
        double cycleTime = MIN_SCROLL_EDGE_PAUSE * 2 + travelTime * 2;
        double cyclePosition = seconds % cycleTime;
        if (cyclePosition < MIN_SCROLL_EDGE_PAUSE) {
            return leftToRight ? 0 : (float) overflowWidth;
        }
        cyclePosition -= MIN_SCROLL_EDGE_PAUSE;
        double value;
        if (cyclePosition < travelTime) {
            value = cyclePosition * SCROLL_PIXELS_PER_SECOND;
        } else if (cyclePosition < travelTime + MIN_SCROLL_EDGE_PAUSE) {
            value = overflowWidth;
        } else {
            cyclePosition -= travelTime + MIN_SCROLL_EDGE_PAUSE;
            value = overflowWidth - cyclePosition * SCROLL_PIXELS_PER_SECOND;
        }
        return (float) (leftToRight ? value : overflowWidth - value);
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

    private static int productIndicatorColor(ProductView product) {
        boolean enabled = false;
        boolean disabled = false;
        boolean policyBlocked = false;
        for (Route route : product.routes) {
            enabled |= route.isProfileEnabled();
            disabled |= !route.isProfileEnabled();
            policyBlocked |= route.isProfileEnabled() && !route.isPolicyAllowed();
        }
        if (policyBlocked || enabled && disabled) {
            return INDICATOR_PARTIAL;
        }
        return enabled ? INDICATOR_ENABLED : INDICATOR_DISABLED;
    }

    private static String productStatusKey(ProductView product) {
        boolean enabled = false;
        boolean disabled = false;
        boolean policyBlocked = false;
        for (Route route : product.routes) {
            enabled |= route.isProfileEnabled();
            disabled |= !route.isProfileEnabled();
            policyBlocked |= route.isProfileEnabled() && !route.isPolicyAllowed();
        }
        if (policyBlocked) {
            return "gui.mekanismqioprocessing.config_route_policy_blocked";
        }
        if (enabled && disabled) {
            return "gui.mekanismqioprocessing.config_route_partial";
        }
        return enabled ? "gui.mekanismqioprocessing.config_route_enabled" :
              "gui.mekanismqioprocessing.config_route_disabled";
    }

    private static int routeIndicatorColor(Route route) {
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

    private void renderResourceTooltip(MachineResourceStack stack, int mouseX, int mouseY) {
        String amount = tr("gui.mekanismqioprocessing.config_resource_amount",
              stack.amount()).getFormattedText();
        if (stack.kind() == MachineResourceKind.ITEM) {
            ItemStack item = displayResource(stack).itemStack();
            if (!item.isEmpty()) {
                gui().renderItemTooltipWithExtra(item, mouseX, mouseY,
                      Collections.singletonList(amount));
                return;
            }
        }
        List<String> tooltip = new ArrayList<>();
        tooltip.add(resourceName(stack));
        tooltip.add(amount);
        displayTooltips(tooltip, mouseX, mouseY);
    }

    private static String resourceName(MachineResourceStack stack) {
        MachineResourceStack display = displayResource(stack);
        return switch (display.kind()) {
            case ITEM -> {
                ItemStack item = display.itemStack();
                yield item.isEmpty() ? "item" : item.getDisplayName();
            }
            case FLUID -> {
                FluidStack fluid = display.fluidStack();
                yield fluid == null ? "fluid" : fluid.getLocalizedName();
            }
            case GAS -> {
                GasStack gas = display.gasStack();
                yield gas == null || gas.getGas() == null ? "gas" :
                      gas.getGas().getLocalizedName();
            }
        };
    }

    private static MachineResourceStack displayResource(MachineResourceStack stack) {
        return stack.amount() > Integer.MAX_VALUE ? stack.withAmount(1) : stack;
    }

    private static String resourceSummary(List<MachineResourceStack> stacks) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < stacks.size(); index++) {
            if (index > 0) {
                builder.append(" + ");
            }
            MachineResourceStack stack = stacks.get(index);
            builder.append(resourceName(stack)).append(" x").append(stack.amount());
        }
        return builder.toString();
    }

    private final class ProductScrollList extends GuiScrollList {

        private ProductScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        @Override
        protected int getMaxElements() {
            return getFilteredProducts().size();
        }

        @Override
        public boolean hasSelection() {
            return getSelectedProduct() != null;
        }

        @Override
        protected void setSelected(int index) {
            List<ProductView> filtered = getFilteredProducts();
            if (index >= 0 && index < filtered.size()) {
                selectProduct(filtered.get(index));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (editable() && pendingRequestId == null && button == 1 &&
                GuiScreen.isShiftKeyDown() && isMouseOverRows(mouseX, mouseY)) {
                ProductView product = getHoveredProduct((int) mouseY);
                if (product != null) {
                    selectProduct(product);
                    toggleProduct(product);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void clearSelection() {
            selectedProductKey = null;
            selectedRouteKey = null;
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<ProductView> filtered = getFilteredProducts();
            int current = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(),
                  filtered.size() - current));
            for (int index = 0; index < max; index++) {
                ProductView product = filtered.get(current + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean selected = product.productKey.equals(selectedProductKey);
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRowSelection(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      selected, selectionFocus == SelectionFocus.PRODUCT, hovered);
                int indicatorX = relativeX + barXShift - 10;
                GuiUtils.fill(indicatorX, rowY + 4, indicatorX + INDICATOR_SIZE,
                      rowY + 4 + INDICATOR_SIZE, productIndicatorColor(product));
                if (product.hasMultipleRoutes()) {
                    GuiUtils.fill(indicatorX, rowY + 12, indicatorX + INDICATOR_SIZE,
                          rowY + 12 + INDICATOR_SIZE, INDICATOR_MULTI_ROUTE);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<ProductView> filtered = getFilteredProducts();
            int current = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(),
                  filtered.size() - current));
            for (int index = 0; index < max; index++) {
                ProductView product = filtered.get(current + index);
                int rowY = relativeY + 4 + index * elementHeight;
                drawScrollingResourceStrip(product.outputs, relativeX + 4, rowY,
                      18, 16);
                drawSmoothScrollingString(new TextComponentString(product.displayName()),
                      relativeX + 25, rowY, barXShift - 38, 16, screenTextColor());
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            ProductView product = getHoveredProduct(mouseY);
            int visibleIndex = getHoveredVisibleIndex(mouseY);
            if (product == null || visibleIndex < 0) {
                return;
            }
            int indicatorX = getX() + barXShift - 10;
            if (mouseX >= indicatorX && mouseX < indicatorX + INDICATOR_SIZE) {
                int rowY = (mouseY - getY() - 1) % elementHeight;
                if (rowY >= 4 && rowY < 4 + INDICATOR_SIZE) {
                    displayTooltip(tr(productStatusKey(product)), mouseX, mouseY);
                    return;
                }
                if (product.hasMultipleRoutes() && rowY >= 12 &&
                    rowY < 12 + INDICATOR_SIZE) {
                    displayTooltip(tr("gui.mekanismqioprocessing.config_multi_route"),
                          mouseX, mouseY);
                    return;
                }
            }
            MachineResourceStack hovered = getHoveredScrollingResource(product.outputs,
                  mouseX, mouseY, relativeX + 4,
                  relativeY + 4 + visibleIndex * elementHeight, 18, 16);
            if (hovered != null) {
                renderResourceTooltip(hovered, mouseX, mouseY);
            } else if (product.outputs.size() > 1) {
                List<String> tooltip = new ArrayList<>();
                for (MachineResourceStack output : product.outputs) {
                    tooltip.add(resourceName(output));
                }
                displayTooltips(tooltip, mouseX, mouseY);
            } else if (product.hasMultipleRoutes()) {
                displayTooltip(tr("gui.mekanismqioprocessing.config_multi_route"),
                      mouseX, mouseY);
            }
        }

        private void selectProduct(ProductView product) {
            selectionFocus = SelectionFocus.PRODUCT;
            selectedProductKey = product.productKey;
            selectedRouteKey = product.routes.isEmpty() ? null :
                  product.routes.get(0).getRouteKey();
            routeList.resetScroll();
            updateButtons();
        }

        @Nullable
        private ProductView getHoveredProduct(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0) {
                return null;
            }
            int index = getCurrentSelection() + relativeMouseY / elementHeight;
            List<ProductView> filtered = getFilteredProducts();
            return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
        }

        private int getHoveredVisibleIndex(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0 || relativeMouseY >= height - 2) {
                return -1;
            }
            int visibleIndex = relativeMouseY / elementHeight;
            return visibleIndex >= 0 && visibleIndex < getFocusedElements() ?
                  visibleIndex : -1;
        }

        private boolean isMouseOverRows(double mouseX, double mouseY) {
            return mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 &&
                  mouseY >= getY() + 1 && mouseY < getY() + height - 1;
        }
    }

    private final class RouteScrollList extends GuiScrollList {

        private RouteScrollList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, ROW_HEIGHT, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        private List<Route> routes() {
            ProductView product = getSelectedProduct();
            return product == null ? Collections.emptyList() : product.routes;
        }

        @Override
        protected int getMaxElements() {
            return routes().size();
        }

        @Override
        public boolean hasSelection() {
            return getSelectedRoute() != null;
        }

        @Override
        protected void setSelected(int index) {
            List<Route> routes = routes();
            if (index >= 0 && index < routes.size()) {
                selectRoute(routes.get(index));
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (editable() && pendingRequestId == null && button == 1 &&
                GuiScreen.isShiftKeyDown() && isMouseOverRows(mouseX, mouseY)) {
                Route route = getHoveredRoute((int) mouseY);
                if (route != null) {
                    selectRoute(route);
                    toggleRoute();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void clearSelection() {
            selectedRouteKey = null;
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<Route> routes = routes();
            int current = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), routes.size() - current));
            for (int index = 0; index < max; index++) {
                Route route = routes.get(current + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean selected = route.getRouteKey().equals(selectedRouteKey);
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRowSelection(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      selected, selectionFocus == SelectionFocus.ROUTE, hovered);
                GuiUtils.fill(relativeX + barXShift - 10, rowY + 4,
                      relativeX + barXShift - 4, rowY + 10,
                      routeIndicatorColor(route));
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<Route> routes = routes();
            int current = getCurrentSelection();
            int max = Math.max(0, Math.min(getFocusedElements(), routes.size() - current));
            for (int index = 0; index < max; index++) {
                Route route = routes.get(current + index);
                int rowY = relativeY + 4 + index * elementHeight;
                int inputX = relativeX + 4;
                int inputWidth = 38;
                int outputWidth = 38;
                int outputX = relativeX + barXShift - 54;
                int textX = inputX + inputWidth + 4;
                int textWidth = Math.max(20, outputX - textX - 4);
                int textColor = route.isEffectiveEnabled() ? screenTextColor() :
                      route.isProfileEnabled() ? 0xE0C05A : 0x8A8A8A;
                drawScrollingResourceStrip(route.getInputs(), inputX, rowY,
                      inputWidth, 16);
                drawSmoothScrollingString(new TextComponentString(
                      resourceSummary(route.getInputs())), textX, rowY,
                      textWidth, 16, textColor);
                drawScrollingResourceStrip(route.getOutputs(), outputX, rowY,
                      outputWidth, 16);
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Route route = getHoveredRoute(mouseY);
            int visibleIndex = getHoveredVisibleIndex(mouseY);
            if (route == null || visibleIndex < 0) {
                return;
            }
            int rowY = relativeY + 4 + visibleIndex * elementHeight;
            MachineResourceStack input = getHoveredScrollingResource(route.getInputs(),
                  mouseX, mouseY, relativeX + 4, rowY, 38, 16);
            if (input != null) {
                renderResourceTooltip(input, mouseX, mouseY);
                return;
            }
            MachineResourceStack output = getHoveredScrollingResource(route.getOutputs(),
                  mouseX, mouseY, relativeX + barXShift - 54, rowY, 38, 16);
            if (output != null) {
                renderResourceTooltip(output, mouseX, mouseY);
                return;
            }
            List<String> tooltip = new ArrayList<>();
            tooltip.add(resourceSummary(route.getInputs()));
            tooltip.add("->");
            tooltip.add(resourceSummary(route.getOutputs()));
            tooltip.add(tr(routeStatusKey(route)).getFormattedText());
            tooltip.add(route.getRouteId());
            displayTooltips(tooltip, mouseX, mouseY);
        }

        private void selectRoute(Route route) {
            selectionFocus = SelectionFocus.ROUTE;
            selectedRouteKey = route.getRouteKey();
            updateButtons();
        }

        @Nullable
        private Route getHoveredRoute(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0) {
                return null;
            }
            List<Route> routes = routes();
            int index = getCurrentSelection() + relativeMouseY / elementHeight;
            return index >= 0 && index < routes.size() ? routes.get(index) : null;
        }

        private int getHoveredVisibleIndex(int mouseY) {
            int relativeMouseY = mouseY - getY() - 1;
            if (relativeMouseY < 0 || relativeMouseY >= height - 2) {
                return -1;
            }
            int visibleIndex = relativeMouseY / elementHeight;
            return visibleIndex >= 0 && visibleIndex < getFocusedElements() ?
                  visibleIndex : -1;
        }

        private boolean isMouseOverRows(double mouseX, double mouseY) {
            return mouseX >= getX() + 1 && mouseX < getX() + barXShift - 1 &&
                  mouseY >= getY() + 1 && mouseY < getY() + height - 1;
        }
    }

    private static final class ProductView {

        private final String productKey;
        private final List<MachineResourceStack> outputs;
        private final List<Route> routes = new ArrayList<>();
        private final int productOrder;

        private ProductView(String productKey, List<MachineResourceStack> outputs,
              int productOrder) {
            this.productKey = productKey;
            this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
            this.productOrder = productOrder;
        }

        private String displayName() {
            return outputs.isEmpty() ? productKey : resourceName(outputs.get(0));
        }

        private boolean hasMultipleRoutes() {
            if (routes.size() > 1) {
                return true;
            }
            return !routes.isEmpty() && routes.get(0).getRouteOrder() > 0;
        }

        private boolean isSelfReferential() {
            for (Route route : routes) {
                for (MachineResourceStack input : route.getInputs()) {
                    for (MachineResourceStack output : route.getOutputs()) {
                        if (input.sameResource(output)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private enum ProductFilterMode {
        ALL("gui.mekanismqioprocessing.config_products_all") {
            @Override
            boolean matches(ProductView product) {
                return true;
            }
        },
        MULTI_ROUTE("gui.mekanismqioprocessing.config_products_multi") {
            @Override
            boolean matches(ProductView product) {
                return product.hasMultipleRoutes();
            }
        },
        SINGLE_ROUTE("gui.mekanismqioprocessing.config_products_single") {
            @Override
            boolean matches(ProductView product) {
                return !product.hasMultipleRoutes();
            }
        },
        SELF_REFERENTIAL("gui.mekanismqioprocessing.config_products_self_referential") {
            @Override
            boolean matches(ProductView product) {
                return product.isSelfReferential();
            }
        };

        private final String translationKey;

        ProductFilterMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private ProductFilterMode next(boolean includeSelfReferential) {
            ProductFilterMode[] values = values();
            ProductFilterMode next = values[(ordinal() + 1) % values.length];
            return !includeSelfReferential && next == SELF_REFERENTIAL ? ALL : next;
        }

        abstract boolean matches(ProductView product);
    }

    private enum SelectionFocus {
        PRODUCT,
        ROUTE
    }
}
