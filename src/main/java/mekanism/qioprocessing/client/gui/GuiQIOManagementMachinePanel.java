package mekanism.qioprocessing.client.gui;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollList;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.qioprocessing.client.QIODeviceLocatorRenderer;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDeviceClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDeviceGroupClientCache;
import mekanism.qioprocessing.common.inventory.container.QIOManagementDevicePageContainer;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.network.PacketQIOManagementDeviceGroupPageRequest;
import mekanism.qioprocessing.common.network.PacketQIOManagementDevicePageRequest;
import mekanism.qioprocessing.common.network.QIOProcessingPacketHandler;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Two-column management browser for machine types and their persisted device records. */
public final class GuiQIOManagementMachinePanel extends GuiElement {

    private static final int PAGE_SIZE = 48;
    private static final int TYPE_ROW_HEIGHT = 22;
    private static final int DEVICE_ROW_HEIGHT = 32;
    private static final int TYPE_TITLE_WIDTH = 50;
    private static final int CATEGORY_BUTTON_WIDTH = 48;
    private static final int HEADER_GAP = 2;
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    private static final int INITIAL_PAGE_TIMEOUT_TICKS = 20;
    private static final int EMPTY_PAGE_RECHECK_TICKS = 10;
    private static final int DOUBLE_CLICK_TICKS = 5;
    private static final int SELECTED = 0xB04E9FDB;
    private static final int HOVERED = 0x503A4B5F;
    private static final int ONLINE = 0xFF57C78B;
    private static final int OFFLINE = 0xFFC75656;
    private static final double SCROLL_PIXELS_PER_SECOND = 12.0D;
    private static final double MIN_SCROLL_EDGE_PAUSE = 0.5D;

    private final QIOManagementDevicePageContainer deviceContainer;
    private final QIOManagementRecipeContainer recipeContainer;
    private final QIOProcessingTerminalContainerState terminalState;
    private final TypeList typeList;
    private final DeviceList deviceList;
    private final GuiTextField searchField;
    private final MekanismButton categoryButton;
    private final int typeX;
    private final int deviceX;
    private final int typeWidth;
    private final int deviceWidth;
    private final int searchX;
    private final int searchWidth;
    private final Map<String, String> groupSearchText = new HashMap<>();

    private DeviceCategory category = DeviceCategory.ALL;
    private String searchQuery = "";
    @Nullable private SessionKey sessionKey;
    @Nullable private String selectedTypeKey;
    @Nullable private UUID selectedDeviceUUID;
    @Nullable private UUID lastClickedDeviceUUID;
    @Nullable private GuiQIOManagementRecipeConfigWindow recipeWindow;
    @Nullable private String pendingLocateTypeKey;
    @Nullable private Pending groupRequest;
    @Nullable private Pending deviceRequest;
    private boolean groupRequestFirstPage;
    private boolean deviceRequestFirstPage;
    private long lastGroupGeneration = Long.MIN_VALUE;
    private long lastDeviceGeneration = Long.MIN_VALUE;
    private long lastDirectoryRevision = Long.MIN_VALUE;
    private long lastRecipeProfileRevision = Long.MIN_VALUE;
    private boolean directoryReloadPending;
    private boolean recipeProfileReloadPending;
    private long initialRequestNotBeforeTick;
    private long emptyGroupPageAtTick = -1;
    private boolean emptyGroupPageRechecked;
    private int pendingGroupTopIndex = -1;
    private int pendingDeviceTopIndex = -1;
    private long clientTick;
    private long lastDeviceClickTick = Long.MIN_VALUE;

    public GuiQIOManagementMachinePanel(IGuiWrapper gui,
          QIOManagementDevicePageContainer deviceContainer,
          QIOManagementRecipeContainer recipeContainer,
          int x, int y, int width, int height) {
        super(gui, x, y, width, height);
        this.deviceContainer = Objects.requireNonNull(deviceContainer, "deviceContainer");
        this.recipeContainer = Objects.requireNonNull(recipeContainer, "recipeContainer");
        terminalState = deviceContainer.getTerminalState();

        typeWidth = Math.max(132, Math.min(160, width * 42 / 100));
        deviceWidth = width - typeWidth - 2;
        typeX = x;
        deviceX = typeX + typeWidth + 2;
        int listY = y + 15;
        int listHeight = Math.max(42, height - 15);

        int categoryX = typeX + TYPE_TITLE_WIDTH + HEADER_GAP;
        searchX = categoryX + CATEGORY_BUTTON_WIDTH + HEADER_GAP;
        searchWidth = Math.max(24, typeX + typeWidth - searchX);
        categoryButton = addChild(new MekanismButton(gui, categoryX, y,
              CATEGORY_BUTTON_WIDTH, 12, new TextComponentTranslation(category.key),
              this::cycleCategory, null));
        searchField = addChild(new GuiTextField(gui, searchX, y, searchWidth, 12)
              .setBackground(BackgroundType.ELEMENT_HOLDER)
              .setTextColor(0xFFFFFF)
              .setMaxLength(64)
              .setResponder(this::searchChanged));
        typeList = addChild(new TypeList(gui, typeX, listY, typeWidth, listHeight));
        deviceList = addChild(new DeviceList(gui, deviceX, listY, deviceWidth, listHeight));
    }

    @Override
    public void tick() {
        super.tick();
        if (!visible) {
            sessionKey = null;
            return;
        }
        clientTick++;
        SessionKey current = SessionKey.capture(terminalState);
        if (!Objects.equals(current, sessionKey)) {
            sessionKey = current;
            lastDirectoryRevision = Long.MIN_VALUE;
            lastRecipeProfileRevision = Long.MIN_VALUE;
            directoryReloadPending = false;
            recipeProfileReloadPending = false;
            clearAll();
            initialRequestNotBeforeTick = clientTick + 1;
        }
        refreshDirectoryIfChanged();
        refreshRecipeProfilesIfChanged();
        acknowledgeResponses();
        retryTimedOutRequests();
        synchronizeSelections();
        ensureCurrentSessionPages();
        requestPagesNearScrollEnd();
        advancePendingGroupLocate();
        categoryButton.setMessage(new TextComponentTranslation(category.key));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawSmoothScrollingString(localize("gui.mekanismqioprocessing.machine_types"),
              typeX + 2, relativeY, TYPE_TITLE_WIDTH - 4, 12, 0x404040);
        if (searchField.isEmpty() && !searchField.isFocused()) {
            drawSmoothScrollingString(
                  localize("gui.mekanismqioprocessing.management_search"),
                  searchX + 4, relativeY, Math.max(8, searchWidth - 8), 12, 0x707070);
        }
        drawColumnTitle("gui.mekanismqioprocessing.machine_name_configuration_hint", deviceX,
              deviceWidth);
        if (!terminalState.isValid()) {
            drawEmpty(deviceX, deviceWidth, "gui.mekanismqioprocessing.session_waiting");
        } else if (terminalState.getFrequencyUUID() == null) {
            drawEmpty(deviceX, deviceWidth,
                  "gui.mekanismqioprocessing.bind_frequency_first");
        } else if (groups().isEmpty()) {
            boolean loading = groupRequest != null ||
                  deviceContainer.getDeviceGroupClientCache().getSourceRevision() < 0 ||
                  deviceContainer.getDeviceGroupClientCache().getNextCursor() != null;
            drawEmpty(typeX, typeWidth, loading ?
                  "gui.mekanismqioprocessing.loading" :
                  searchQuery.isEmpty() && category == DeviceCategory.ALL ?
                        "gui.mekanismqioprocessing.no_devices" :
                        "gui.mekanismqioprocessing.management_no_search_results");
        } else if (selectedTypeKey != null && devices().isEmpty()) {
            drawEmpty(deviceX, deviceWidth, deviceRequest != null ||
                  deviceContainer.getDeviceClientCache().getSourceRevision() < 0 ?
                  "gui.mekanismqioprocessing.loading" :
                  "gui.mekanismqioprocessing.no_devices");
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        QIOManagementDeviceGroupSnapshot group = typeList.getHoveredGroup(mouseX, mouseY);
        if (group != null) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.management_type_highlight_tooltip")
                  .getFormattedText());
            displayTooltips(tooltip, mouseX, mouseY);
            return;
        }
        QIOAutomationDeviceSnapshot device = deviceList.getHoveredDevice(mouseX, mouseY);
        if (device != null) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(new TextComponentTranslation(canConfigureRecipes(device) ?
                  "gui.mekanismqioprocessing.management_double_click_config" :
                  "gui.mekanismqioprocessing.management_no_route_config").getFormattedText());
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.management_device_highlight_tooltip")
                  .getFormattedText());
            displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private void drawColumnTitle(String key, int x, int maximumWidth) {
        String title = localize(key);
        getFont().drawString(getFont().trimStringToWidth(title, Math.max(12, maximumWidth - 4)),
              x + 2, relativeY + 2, 0x404040);
    }

    private void drawEmpty(int x, int width, String key) {
        String text = getFont().trimStringToWidth(localize(key), Math.max(12, width - 10));
        getFont().drawString(text, x + 5, relativeY + 24, 0x787878);
    }

    private void cycleCategory() {
        resetDoubleClick();
        category = category.next();
        selectedTypeKey = null;
        selectedDeviceUUID = null;
        deviceContainer.getDeviceClientCache().clear();
        deviceRequest = null;
        pendingLocateTypeKey = null;
        pendingDeviceTopIndex = -1;
        typeList.resetScroll();
        deviceList.resetScroll();
        selectFirstVisibleType();
    }

    private void searchChanged(String value) {
        String updated = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (updated.equals(searchQuery)) {
            return;
        }
        searchQuery = updated;
        resetDoubleClick();
        pendingLocateTypeKey = null;
        typeList.resetScroll();
        List<QIOManagementDeviceGroupSnapshot> visible = groups();
        if (selectedTypeKey == null || visible.stream().noneMatch(
              group -> group.getTypeKey().equals(selectedTypeKey))) {
            selectType(visible.isEmpty() ? null : visible.get(0));
        }
    }

    private void ensureCurrentSessionPages() {
        if (!terminalState.isValid() || terminalState.getFrequencyUUID() == null ||
            terminalState.getSessionNonce() == null ||
            clientTick < initialRequestNotBeforeTick) {
            return;
        }
        UUID nonce = terminalState.getSessionNonce();
        QIOManagementDeviceGroupClientCache groupCache =
              deviceContainer.getDeviceGroupClientCache();
        if (!emptyGroupPageRechecked && emptyGroupPageAtTick >= 0 &&
            clientTick - emptyGroupPageAtTick >= EMPTY_PAGE_RECHECK_TICKS &&
            groupRequest == null && groupCache.hasPageFor(nonce) &&
            groupCache.getGroups().isEmpty()) {
            emptyGroupPageRechecked = true;
            requestGroups(null);
            return;
        }
        if (groupRequest == null && !groupCache.hasPageFor(nonce)) {
            requestGroups(null);
            return;
        }
        if (selectedTypeKey != null && deviceRequest == null &&
            !deviceContainer.getDeviceClientCache().hasPageFor(nonce, selectedTypeKey)) {
            requestDevices(null);
        }
    }

    private void requestPagesNearScrollEnd() {
        if (groupRequest == null && (!searchQuery.isEmpty() ||
            typeList.shouldRequestMore()) &&
            deviceContainer.getDeviceGroupClientCache().getNextCursor() != null) {
            requestGroups(deviceContainer.getDeviceGroupClientCache().getNextCursor());
        }
        if (selectedTypeKey != null && deviceRequest == null &&
            deviceList.shouldRequestMore() &&
            deviceContainer.getDeviceClientCache().getNextCursor() != null) {
            requestDevices(deviceContainer.getDeviceClientCache().getNextCursor());
        }
    }

    private void acknowledgeResponses() {
        long groupGeneration = deviceContainer.getDeviceGroupClientCache().getGeneration();
        if (groupRequest != null && groupRequest.generation != groupGeneration) {
            groupRequest = null;
            if (pendingGroupTopIndex >= 0) {
                typeList.restoreTopIndex(pendingGroupTopIndex);
                pendingGroupTopIndex = -1;
            }
        }
        long deviceGeneration = deviceContainer.getDeviceClientCache().getPageGeneration();
        if (deviceRequest != null && deviceRequest.generation != deviceGeneration) {
            deviceRequest = null;
            if (pendingDeviceTopIndex >= 0) {
                deviceList.restoreTopIndex(pendingDeviceTopIndex);
                pendingDeviceTopIndex = -1;
            }
        }
    }

    private void refreshDirectoryIfChanged() {
        long revision = terminalState.getDeviceDirectoryRevision();
        if (revision < 0) return;
        if (lastDirectoryRevision == Long.MIN_VALUE) {
            lastDirectoryRevision = revision;
            return;
        }
        if (revision != lastDirectoryRevision) {
            lastDirectoryRevision = revision;
            directoryReloadPending = true;
        }
        if (directoryReloadPending && groupRequest == null && terminalState.isValid() &&
            terminalState.getFrequencyUUID() != null) {
            directoryReloadPending = false;
            requestGroups(null);
        }
    }

    private void refreshRecipeProfilesIfChanged() {
        long revision = terminalState.getAutomationRecipeProfileRevision();
        if (revision < 0) return;
        if (lastRecipeProfileRevision == Long.MIN_VALUE) {
            lastRecipeProfileRevision = revision;
            return;
        }
        if (revision != lastRecipeProfileRevision) {
            lastRecipeProfileRevision = revision;
            recipeProfileReloadPending = true;
        }
        if (recipeProfileReloadPending && deviceRequest == null &&
            selectedTypeKey != null && terminalState.isValid() &&
            terminalState.getFrequencyUUID() != null) {
            recipeProfileReloadPending = false;
            deviceContainer.getDeviceClientCache().clear();
            requestDevices(null);
        }
    }

    private void retryTimedOutRequests() {
        UUID nonce = terminalState.getSessionNonce();
        boolean hasGroupPage = nonce != null &&
              deviceContainer.getDeviceGroupClientCache().hasPageFor(nonce);
        boolean hasDevicePage = nonce != null && selectedTypeKey != null &&
              deviceContainer.getDeviceClientCache().hasPageFor(nonce, selectedTypeKey);
        if (groupRequest != null && timedOut(groupRequest,
              hasGroupPage ? REQUEST_TIMEOUT_TICKS : INITIAL_PAGE_TIMEOUT_TICKS)) {
            if (!groupRequestFirstPage) deviceContainer.getDeviceGroupClientCache().clear();
            groupRequest = null;
            pendingGroupTopIndex = -1;
            requestGroups(null);
        }
        if (deviceRequest != null && timedOut(deviceRequest,
              hasDevicePage ? REQUEST_TIMEOUT_TICKS : INITIAL_PAGE_TIMEOUT_TICKS)) {
            if (!deviceRequestFirstPage) deviceContainer.getDeviceClientCache().clear();
            deviceRequest = null;
            pendingDeviceTopIndex = -1;
            requestDevices(null);
        }
    }

    private boolean timedOut(Pending request, int timeout) {
        return clientTick - request.sentAtTick >= timeout;
    }

    private void synchronizeSelections() {
        long groupGeneration = deviceContainer.getDeviceGroupClientCache().getGeneration();
        if (groupGeneration != lastGroupGeneration) {
            lastGroupGeneration = groupGeneration;
            UUID nonce = terminalState.getSessionNonce();
            List<QIOManagementDeviceGroupSnapshot> allGroups = allGroups();
            if (nonce != null && deviceContainer.getDeviceGroupClientCache().hasPageFor(nonce)) {
                if (allGroups.isEmpty() && !emptyGroupPageRechecked &&
                    emptyGroupPageAtTick < 0) {
                    emptyGroupPageAtTick = clientTick;
                } else if (!allGroups.isEmpty()) {
                    emptyGroupPageAtTick = -1;
                    emptyGroupPageRechecked = true;
                }
            }
            List<QIOManagementDeviceGroupSnapshot> visibleGroups = groups();
            if (selectedTypeKey == null || visibleGroups.stream().noneMatch(
                  group -> group.getTypeKey().equals(selectedTypeKey))) {
                selectType(visibleGroups.isEmpty() ? null : visibleGroups.get(0));
            } else if (deviceContainer.getDeviceClientCache().getSourceRevision() !=
                       deviceContainer.getDeviceGroupClientCache().getSourceRevision() &&
                       deviceRequest == null) {
                requestDevices(null);
            }
        }
        long deviceGeneration = deviceContainer.getDeviceClientCache().getPageGeneration();
        if (deviceGeneration != lastDeviceGeneration) {
            lastDeviceGeneration = deviceGeneration;
            List<QIOAutomationDeviceSnapshot> devices = devices();
            if (selectedDeviceUUID == null || devices.stream().noneMatch(
                  device -> device.getDeviceUUID().equals(selectedDeviceUUID))) {
                selectDevice(devices.isEmpty() ? null : devices.get(0));
            }
        }
    }

    private void selectFirstVisibleType() {
        List<QIOManagementDeviceGroupSnapshot> visible = groups();
        selectType(visible.isEmpty() ? null : visible.get(0));
    }

    private void clearAll() {
        deviceContainer.getDeviceGroupClientCache().clear();
        deviceContainer.getDeviceClientCache().clear();
        selectedTypeKey = null;
        selectedDeviceUUID = null;
        pendingLocateTypeKey = null;
        groupRequest = null;
        deviceRequest = null;
        pendingGroupTopIndex = -1;
        pendingDeviceTopIndex = -1;
        emptyGroupPageAtTick = -1;
        emptyGroupPageRechecked = false;
        groupSearchText.clear();
        resetDoubleClick();
        typeList.resetScroll();
        deviceList.resetScroll();
    }

    private void selectType(@Nullable QIOManagementDeviceGroupSnapshot group) {
        String key = group == null ? null : group.getTypeKey();
        if (Objects.equals(key, selectedTypeKey)) return;
        if (pendingLocateTypeKey != null && !pendingLocateTypeKey.equals(key)) {
            pendingLocateTypeKey = null;
        }
        selectedTypeKey = key;
        resetDoubleClick();
        selectedDeviceUUID = null;
        deviceContainer.getDeviceClientCache().clear();
        deviceList.resetScroll();
        deviceRequest = null;
        pendingDeviceTopIndex = -1;
        if (key != null) requestDevices(null);
    }

    private void selectDevice(@Nullable QIOAutomationDeviceSnapshot device) {
        selectedDeviceUUID = device == null ? null : device.getDeviceUUID();
    }

    private void resetDoubleClick() {
        lastClickedDeviceUUID = null;
        lastDeviceClickTick = Long.MIN_VALUE;
    }

    private boolean canConfigureRecipes(QIOAutomationDeviceSnapshot device) {
        QIOAutomationMode mode = device.getMode();
        return device.getKind() == QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE &&
              device.hasRecipeProfile() &&
              (mode == QIOAutomationMode.SCHEDULED || mode == QIOAutomationMode.PASSIVE);
    }

    private void openRecipeConfig(QIOAutomationDeviceSnapshot device) {
        if (!canConfigureRecipes(device)) return;
        if (recipeWindow != null) {
            recipeWindow.close();
        }
        ItemStack stack = machineStack(device.getPresentation());
        String machineName = stack.isEmpty() ? device.getBlockId() : stack.getDisplayName();
        GuiQIOManagementRecipeConfigWindow opened =
              new GuiQIOManagementRecipeConfigWindow(gui(),
                    (getGuiWidth() - GuiQIOManagementRecipeConfigWindow.WIDTH) / 2,
                    18, recipeContainer, device.getDeviceUUID(), device.getMode(),
                    machineName);
        recipeWindow = opened;
        opened.setTabListeners(window -> {
            if (recipeWindow == opened) recipeWindow = null;
        }, window -> { });
        gui().addWindow(opened);
    }

    private void locateDevice(QIOAutomationDeviceSnapshot device) {
        QIODeviceLocatorRenderer.INSTANCE.showLocation(device);
        closeForHighlight();
    }

    private void beginGroupLocate(QIOManagementDeviceGroupSnapshot group) {
        pendingLocateTypeKey = group.getTypeKey();
        if (!group.getTypeKey().equals(selectedTypeKey)) {
            selectType(group);
        }
        advancePendingGroupLocate();
    }

    private void advancePendingGroupLocate() {
        if (pendingLocateTypeKey == null ||
            !pendingLocateTypeKey.equals(selectedTypeKey)) {
            return;
        }
        UUID nonce = terminalState.getSessionNonce();
        QIOManagementDeviceClientCache cache = deviceContainer.getDeviceClientCache();
        if (nonce == null || !cache.hasPageFor(nonce, pendingLocateTypeKey)) {
            if (deviceRequest == null) {
                requestDevices(null);
            }
            return;
        }
        if (cache.getNextCursor() != null) {
            if (deviceRequest == null) {
                requestDevices(cache.getNextCursor());
            }
            return;
        }
        if (cache.getDevices().size() != cache.getTotalSize()) {
            return;
        }
        List<QIOAutomationDeviceSnapshot> located = cache.getDevices();
        pendingLocateTypeKey = null;
        QIODeviceLocatorRenderer.INSTANCE.showLocations(located);
        closeForHighlight();
    }

    private void closeForHighlight() {
        if (minecraft.player != null) {
            minecraft.player.closeScreen();
        } else {
            minecraft.displayGuiScreen(null);
        }
    }

    private void requestGroups(@Nullable QIOPageCursor cursor) {
        if (!terminalState.isValid() || groupRequest != null) return;
        pendingGroupTopIndex = cursor == null ? -1 : typeList.getCurrentSelection();
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOManagementDeviceGroupPageRequest.Message.create(
                    deviceContainer.getTerminalWindowId(), terminalState, PAGE_SIZE, cursor));
        groupRequestFirstPage = cursor == null;
        groupRequest = new Pending(
              deviceContainer.getDeviceGroupClientCache().getGeneration(), clientTick);
    }

    private void requestDevices(@Nullable QIOPageCursor cursor) {
        if (!terminalState.isValid() || selectedTypeKey == null || deviceRequest != null) return;
        pendingDeviceTopIndex = cursor == null ? -1 : deviceList.getCurrentSelection();
        deviceContainer.getDeviceClientCache().expectView(selectedTypeKey);
        QIOProcessingPacketHandler.INSTANCE.sendToServer(
              PacketQIOManagementDevicePageRequest.Message.create(
                    deviceContainer.getTerminalWindowId(), terminalState, PAGE_SIZE, cursor,
                    selectedTypeKey));
        deviceRequestFirstPage = cursor == null;
        deviceRequest = new Pending(
              deviceContainer.getDeviceClientCache().getPageGeneration(), clientTick);
    }

    private List<QIOManagementDeviceGroupSnapshot> allGroups() {
        return deviceContainer.getDeviceGroupClientCache().getGroups();
    }

    private List<QIOManagementDeviceGroupSnapshot> groups() {
        List<QIOManagementDeviceGroupSnapshot> visible = new ArrayList<>();
        for (QIOManagementDeviceGroupSnapshot group : allGroups()) {
            if (category.matches(group.getKind()) && matchesSearch(group)) {
                visible.add(group);
            }
        }
        return visible;
    }

    private boolean matchesSearch(QIOManagementDeviceGroupSnapshot group) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        String searchable = groupSearchText.computeIfAbsent(group.getTypeKey(), ignored -> {
            ItemStack stack = machineStack(group.getPresentation());
            String displayName = stack.isEmpty() ? "" : stack.getDisplayName();
            return (displayName + '\n' + group.getPresentation().getItemId() + '\n' +
                  group.getBlockId() + '\n' +
                  group.getProfileScopeId() + '\n' + group.getTypeKey())
                  .toLowerCase(Locale.ROOT);
        });
        return searchable.contains(searchQuery);
    }

    private List<QIOAutomationDeviceSnapshot> devices() {
        return selectedTypeKey == null ? Collections.emptyList() :
              deviceContainer.getDeviceClientCache().getDevices();
    }

    private ItemStack machineStack(MachinePresentationDescriptor presentation) {
        return presentation == null ? ItemStack.EMPTY : presentation.createStack();
    }

    private String deviceStatus(QIOAutomationDeviceSnapshot device) {
        if (device.hasRecipeProfile()) {
            String filter = localize(device.getRecipeRouteFilterMode() == RouteFilterMode.WHITELIST ?
                  "gui.mekanismqioprocessing.config_filter_whitelist" :
                  "gui.mekanismqioprocessing.config_filter_blacklist");
            return device.isIndividualRecipeProfile() ?
                  localize("gui.mekanismqioprocessing.management_status_individual", filter) :
                  localize("gui.mekanismqioprocessing.management_status_global", filter,
                        device.getGlobalRecipeProfileSlot());
        }
        if (device.getKind() == QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE &&
            "OUTPUT_ONLY".equals(device.getModeName())) {
            return localize("gui.mekanismqioprocessing.management_status_output");
        }
        return localize("gui.mekanismqioprocessing.management_status_simple",
              localize(device.isOnline() ? "gui.mekanismqioprocessing.online" :
                    "gui.mekanismqioprocessing.offline"));
    }

    private void drawRow(int x, int y, int width, int height, boolean selected,
          boolean hovered) {
        if (selected) {
            GuiUtils.fill(x, y, x + width, y + height, SELECTED);
        } else if (hovered) {
            GuiUtils.fill(x, y, x + width, y + height, HOVERED);
        }
    }

    private void drawSmoothScrollingString(String text, int x, int y, int width,
          int height, int color) {
        int textWidth = getFont().getStringWidth(text);
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
        getFont().drawString(text, drawX, drawY, color, false);
        if (scrolling) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
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
        double offset;
        if (cyclePosition < travelTime) {
            offset = cyclePosition * SCROLL_PIXELS_PER_SECOND;
        } else if (cyclePosition < travelTime + MIN_SCROLL_EDGE_PAUSE) {
            offset = overflowWidth;
        } else {
            cyclePosition -= travelTime + MIN_SCROLL_EDGE_PAUSE;
            offset = overflowWidth - cyclePosition * SCROLL_PIXELS_PER_SECOND;
        }
        return (float) (leftToRight ? offset : overflowWidth - offset);
    }

    private void enableGuiScissor(int minX, int minY, int maxX, int maxY) {
        double scaleX = minecraft.displayWidth / (double) minecraft.currentScreen.width;
        double scaleY = minecraft.displayHeight / (double) minecraft.currentScreen.height;
        int scissorX = (int) Math.floor((getGuiLeft() + minX) * scaleX);
        int scissorY = (int) Math.floor(minecraft.displayHeight -
              (getGuiTop() + maxY) * scaleY);
        int scissorWidth = Math.max(0,
              (int) Math.ceil((maxX - minX) * scaleX));
        int scissorHeight = Math.max(0,
              (int) Math.ceil((maxY - minY) * scaleY));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private void drawCoordinates(QIOAutomationDeviceSnapshot device, int x, int y,
          int width, int color) {
        BlockPos position = device.getLocation().position();
        String[] labels = {
              localize("gui.mekanismqioprocessing.management_dimension_label"),
              "X:", "Y:", "Z:"
        };
        String[] values = {
              Integer.toString(device.getLocation().dimension()),
              Integer.toString(position.getX()), Integer.toString(position.getY()),
              Integer.toString(position.getZ())
        };
        int labelWidth = 0;
        for (String label : labels) {
            labelWidth += getFont().getStringWidth(label);
        }
        int valueSpace = Math.max(32, width - labelWidth - 6);
        int valueWidth = Math.max(8, valueSpace / 4);
        int cursor = x;
        for (int index = 0; index < labels.length; index++) {
            getFont().drawString(labels[index], cursor, y + 2, color);
            cursor += getFont().getStringWidth(labels[index]);
            int currentWidth = index == labels.length - 1 ?
                  Math.max(8, x + width - cursor) : valueWidth;
            drawSmoothScrollingString(values[index], cursor, y, currentWidth, 13, color);
            cursor += currentWidth + (index == labels.length - 1 ? 0 : 2);
        }
    }

    private static String localize(String key, Object... arguments) {
        return new TextComponentTranslation(key, arguments).getFormattedText();
    }

    private abstract class PagedList extends GuiScrollList {

        private PagedList(IGuiWrapper gui, int x, int y, int width, int height,
              int rowHeight) {
            super(gui, x, y, width, height, rowHeight, GuiInnerScreen.SCREEN,
                  GuiInnerScreen.SCREEN_SIZE);
        }

        protected final boolean shouldRequestMore() {
            return getCurrentSelection() + getFocusedElements() + 2 >= getMaxElements();
        }

        protected final void restoreTopIndex(int index) {
            int scrollable = Math.max(0, getMaxElements() - getFocusedElements());
            scroll = scrollable == 0 ? 0 : Math.max(0, Math.min(1,
                  index / (scrollable + 0.5D)));
        }

        @Override
        public void resetScroll() {
            scroll = 0;
        }
    }

    private final class TypeList extends PagedList {

        private TypeList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, TYPE_ROW_HEIGHT);
        }

        @Override protected int getMaxElements() { return groups().size(); }
        @Override public boolean hasSelection() { return selectedTypeKey != null; }
        @Override protected void setSelected(int index) {
            List<QIOManagementDeviceGroupSnapshot> groups = groups();
            if (index >= 0 && index < groups.size()) selectType(groups.get(index));
        }
        @Override public void clearSelection() { selectType(null); }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            QIOManagementDeviceGroupSnapshot group = getHoveredGroup(mouseX, mouseY);
            if (button == 1 && GuiScreen.isCtrlKeyDown() && group != null) {
                beginGroupLocate(group);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<QIOManagementDeviceGroupSnapshot> groups = groups();
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, groups.size() - start));
            for (int index = 0; index < count; index++) {
                QIOManagementDeviceGroupSnapshot group = groups.get(start + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRow(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      group.getTypeKey().equals(selectedTypeKey), hovered);
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<QIOManagementDeviceGroupSnapshot> groups = groups();
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, groups.size() - start));
            for (int index = 0; index < count; index++) {
                QIOManagementDeviceGroupSnapshot group = groups.get(start + index);
                int rowY = relativeY + 3 + index * elementHeight;
                ItemStack stack = machineStack(group.getPresentation());
                if (!stack.isEmpty()) {
                    gui().renderItemWithOverlay(stack, relativeX + 3, rowY - 1, 1F, null);
                }
                String name = stack.isEmpty() ? group.getBlockId() : stack.getDisplayName();
                String countText = group.getOnlineCount() + "/" + group.getTotalCount();
                int countWidth = getFont().getStringWidth(countText);
                drawSmoothScrollingString(name, relativeX + 22, rowY,
                      Math.max(12, barXShift - 29 - countWidth), 20, 0x404040);
                getFont().drawString(countText, relativeX + barXShift - countWidth - 3,
                      rowY + 4, group.getOnlineCount() > 0 ? 0x208050 : 0xA05050);
            }
        }

        @Nullable
        private QIOManagementDeviceGroupSnapshot getHoveredGroup(double mouseX,
              double mouseY) {
            if (mouseX < getX() + 1 || mouseX >= getX() + barXShift - 1 ||
                mouseY < getY() + 1 || mouseY >= getY() + getHeight() - 1) {
                return null;
            }
            int visibleIndex = (int) (mouseY - getY() - 1) / elementHeight;
            int index = getCurrentSelection() + visibleIndex;
            List<QIOManagementDeviceGroupSnapshot> groups = groups();
            return index >= 0 && index < groups.size() ? groups.get(index) : null;
        }
    }

    private final class DeviceList extends PagedList {

        private DeviceList(IGuiWrapper gui, int x, int y, int width, int height) {
            super(gui, x, y, width, height, DEVICE_ROW_HEIGHT);
        }

        @Override protected int getMaxElements() { return devices().size(); }
        @Override public boolean hasSelection() { return selectedDeviceUUID != null; }
        @Override protected void setSelected(int index) {
            List<QIOAutomationDeviceSnapshot> devices = devices();
            if (index >= 0 && index < devices.size()) selectDevice(devices.get(index));
        }
        @Override public void clearSelection() { selectDevice(null); }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            QIOAutomationDeviceSnapshot device = getHoveredDevice(mouseX, mouseY);
            if (button == 1 && GuiScreen.isCtrlKeyDown() && device != null) {
                resetDoubleClick();
                locateDevice(device);
                return true;
            }
            if (button == 0 && device != null) {
                UUID deviceUUID = device.getDeviceUUID();
                boolean doubleClick = deviceUUID.equals(lastClickedDeviceUUID) &&
                      clientTick - lastDeviceClickTick <= DOUBLE_CLICK_TICKS;
                selectDevice(device);
                if (doubleClick) {
                    resetDoubleClick();
                    openRecipeConfig(device);
                } else {
                    lastClickedDeviceUUID = deviceUUID;
                    lastDeviceClickTick = clientTick;
                }
                return true;
            }
            resetDoubleClick();
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderElements(int mouseX, int mouseY, float partialTicks) {
            List<QIOAutomationDeviceSnapshot> devices = devices();
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, devices.size() - start));
            for (int index = 0; index < count; index++) {
                QIOAutomationDeviceSnapshot device = devices.get(start + index);
                int rowY = relativeY + 1 + index * elementHeight;
                boolean hovered = mouseX >= getX() + 1 &&
                      mouseX < getX() + barXShift - 1 &&
                      mouseY >= getY() + 1 + index * elementHeight &&
                      mouseY < getY() + 1 + (index + 1) * elementHeight;
                drawRow(relativeX + 1, rowY, barXShift - 2, elementHeight,
                      device.getDeviceUUID().equals(selectedDeviceUUID), hovered);
                GuiUtils.fill(relativeX + barXShift - 9, rowY + 21,
                      relativeX + barXShift - 4, rowY + 26,
                      device.isOnline() ? ONLINE : OFFLINE);
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            List<QIOAutomationDeviceSnapshot> devices = devices();
            int start = getCurrentSelection();
            int count = Math.min(getFocusedElements(), Math.max(0, devices.size() - start));
            for (int index = 0; index < count; index++) {
                QIOAutomationDeviceSnapshot device = devices.get(start + index);
                int rowY = relativeY + 3 + index * elementHeight;
                int color = device.isOnline() ? 0x404040 : 0x8A6060;
                drawCoordinates(device, relativeX + 4, rowY,
                      Math.max(20, barXShift - 8), color);
                drawSmoothScrollingString(deviceStatus(device), relativeX + 4,
                      rowY + 15, Math.max(12, barXShift - 16), 13,
                      device.isOnline() ? 0x555555 : 0x9A7070);
            }
        }

        @Nullable
        private QIOAutomationDeviceSnapshot getHoveredDevice(double mouseX,
              double mouseY) {
            if (mouseX < getX() + 1 || mouseX >= getX() + barXShift - 1 ||
                mouseY < getY() + 1 || mouseY >= getY() + getHeight() - 1) {
                return null;
            }
            int visibleIndex = (int) (mouseY - getY() - 1) / elementHeight;
            int index = getCurrentSelection() + visibleIndex;
            List<QIOAutomationDeviceSnapshot> devices = devices();
            return index >= 0 && index < devices.size() ? devices.get(index) : null;
        }
    }

    private enum DeviceCategory {
        ALL(null, "gui.mekanismqioprocessing.management_category_all"),
        MACHINES(QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "gui.mekanismqioprocessing.management_category_machines"),
        TERMINALS(QIOAutomationDeviceSnapshot.Kind.TERMINAL,
              "gui.mekanismqioprocessing.management_category_terminals"),
        CRAFTING(QIOAutomationDeviceSnapshot.Kind.CRAFTING_PROCESSOR,
              "gui.mekanismqioprocessing.management_category_crafting");

        @Nullable private final QIOAutomationDeviceSnapshot.Kind kind;
        private final String key;

        DeviceCategory(QIOAutomationDeviceSnapshot.Kind kind, String key) {
            this.kind = kind;
            this.key = key;
        }

        private DeviceCategory next() {
            DeviceCategory[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private boolean matches(QIOAutomationDeviceSnapshot.Kind candidate) {
            return kind == null || kind == candidate;
        }
    }

    private static final class Pending {
        private final long generation;
        private final long sentAtTick;

        private Pending(long generation, long sentAtTick) {
            this.generation = generation;
            this.sentAtTick = sentAtTick;
        }
    }

    private static final class SessionKey {
        private final UUID nonce;
        private final UUID terminalUUID;
        private final long targetRevision;
        @Nullable private final UUID frequencyUUID;
        private final long accessRevision;

        private SessionKey(UUID nonce, UUID terminalUUID, long targetRevision,
              @Nullable UUID frequencyUUID, long accessRevision) {
            this.nonce = nonce;
            this.terminalUUID = terminalUUID;
            this.targetRevision = targetRevision;
            this.frequencyUUID = frequencyUUID;
            this.accessRevision = accessRevision;
        }

        @Nullable
        private static SessionKey capture(QIOProcessingTerminalContainerState state) {
            return state.isValid() ? new SessionKey(state.getSessionNonce(),
                  state.getTerminalUUID(), state.getTargetRevision(), state.getFrequencyUUID(),
                  state.getAccessRevision()) : null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SessionKey other && targetRevision == other.targetRevision &&
                  accessRevision == other.accessRevision && nonce.equals(other.nonce) &&
                  terminalUUID.equals(other.terminalUUID) &&
                  Objects.equals(frequencyUUID, other.frequencyUUID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonce, terminalUUID, targetRevision, frequencyUUID,
                  accessRevision);
        }
    }
}
