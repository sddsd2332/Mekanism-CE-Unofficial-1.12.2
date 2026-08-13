package mekanism.client.gui.qio;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiDigitalIconToggle;
import mekanism.client.gui.element.GuiDropdown;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.custom.GuiResizeControls;
import mekanism.client.gui.element.custom.GuiResizeControls.ResizeController;
import mekanism.client.gui.element.custom.GuiResizeControls.ResizeType;
import mekanism.client.gui.element.custom.GuiQIOResourceGrid;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.tab.GuiTargetDirectionTab;
import mekanism.client.gui.element.tab.GuiToggleClientConfigTab;
import mekanism.client.gui.element.tab.window.GuiCraftingWindowTab;
import mekanism.client.gui.element.text.BackgroundType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiCraftingWindow;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared modern viewer layout for the block and portable QIO dashboards. */
@SideOnly(Side.CLIENT)
public abstract class GuiQIOViewerScreen<CONTAINER extends QIOItemViewerContainer> extends GuiMekanism<CONTAINER> implements ResizeController {

    private static final Set<Character> SEARCH_SPECIAL_CHARS = new HashSet<>(Arrays.asList('_', ' ', '-', '/', '.', '"', '\'', '|', '(', ')', ':', '@', '$', '#', '~'));

    private static final ResourceLocation SEARCH_AUTO_OFF = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "searchbar_autofocus_off.png");
    private static final ResourceLocation SEARCH_AUTO_ON = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "searchbar_autofocus_on.png");
    private static final ResourceLocation REJECTS_FREQUENCY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "recipe_viewer_frequency.png");
    private static final ResourceLocation REJECTS_INVENTORY = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_BUTTON, "recipe_viewer_inventory.png");

    private final int searchDropdownX;
    protected GuiQIOResourceGrid resourceGrid;
    protected GuiTextField searchField;
    protected GuiQIOFrequencyTab<?> frequencyTab;
    private GuiCraftingWindowTab craftingWindowTab;
    private boolean loadPinned = true;
    private boolean replacingViewer;

    protected GuiQIOViewerScreen(CONTAINER container) {
        super(container);
        dynamicSlots = true;
        xSize = 16 + QIOItemViewerContainer.getConfiguredColumns() * 18 + 18;
        ySize = QIOItemViewerContainer.SLOTS_START_Y + QIOItemViewerContainer.getConfiguredRows() * 18 + 96;
        searchDropdownX = xSize - 63;
        // 26.2 keeps the inventory caption anchored to the left edge while
        // the player inventory slots themselves are centered by the container.
        inventoryLabelY = ySize - 93;
        titleLabelY = 5;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        int rows = QIOItemViewerContainer.getConfiguredRows();
        addButton(new GuiInnerScreen(this, 7, 15, xSize - 16, 12, this::getFrequencyText)
              .tooltip(this::getFrequencyTooltip));
        resourceGrid = addButton(new GuiQIOResourceGrid(this, 7, QIOItemViewerContainer.SLOTS_START_Y,
              QIOItemViewerContainer.getConfiguredColumns(), rows, this::getViewerContainer));
        searchField = addButton(new GuiTextField(this, 0, 50, 30, xSize - 60, 10)
              .setOffset(0, -1)
              .setBackground(BackgroundType.ELEMENT_HOLDER)
              .setTextColor(0xFFFFFF)
              .setMaxLength(50)
              .setInputValidator(this::isValidSearchChar)
              .setResponder(text -> resourceGrid.search(text)));
        addButton(new GuiDropdown<>(this, searchDropdownX, QIOItemViewerContainer.SLOTS_START_Y + rows * 18 + 1,
              41, ViewerSortMode.class, this::getSortMode, this::setSortMode));
        addButton(new GuiDigitalIconToggle<>(this, xSize - 21, QIOItemViewerContainer.SLOTS_START_Y + rows * 18 + 1,
              12, 12, ViewerSortDirection.class, this::getSortDirection, this::setSortDirection));
        addButton(new GuiTargetDirectionTab(this, getViewerContainer(), 60));
        addButton(new GuiToggleClientConfigTab(this, ySize - 35, true, REJECTS_FREQUENCY, REJECTS_INVENTORY,
              MekanismConfig.current().client.qioRejectsToInventory,
              MekanismLang.QIO_REJECTS_TO_INVENTORY.translate(),
              MekanismLang.QIO_REJECTS_TO_FREQUENCY.translate()));
        addButton(new GuiToggleClientConfigTab(this, 6, false, SEARCH_AUTO_OFF, SEARCH_AUTO_ON,
              MekanismConfig.current().client.qioAutoFocusSearchBar,
              MekanismLang.QIO_SEARCH_AUTO_FOCUS.translate(),
              MekanismLang.QIO_SEARCH_MANUAL_FOCUS.translate()));
        // Match 26.2: the tab is centered against the scaled screen, not just
        // the current image height (the two differ by a pixel on odd heights).
        addButton(new GuiResizeControls(this, height / 2 - guiTop));
        craftingWindowTab = addButton(new GuiCraftingWindowTab(this, () -> (mekanism.common.inventory.container.IQIOItemViewerContainer) inventorySlots));
        if (MekanismConfig.current().client.qioAutoFocusSearchBar.val()) {
            setFocused(searchField);
        }
    }

    @Override
    public void initGui() {
        // 26.2 derives the maximum row count from the scaled window height,
        // rather than from the current viewer dimensions. Clamp persisted
        // settings before creating slots so an oversized setting cannot make
        // the inventory or resize tab overlap the screen on first open.
        int maxRows = getMaxRows();
        if (QIOItemViewerContainer.getConfiguredRows() > maxRows) {
            MekanismConfig.current().client.qioItemViewerSlotsY.set(maxRows);
            Mekanism.configuration.save();
            ySize = QIOItemViewerContainer.SLOTS_START_Y + maxRows * 18 + 96;
            inventoryLabelY = ySize - 93;
            ((QIOItemViewerContainer) inventorySlots).repositionPlayerInventory();
        }
        super.initGui();
    }

    private List<net.minecraft.util.text.ITextComponent> getFrequencyText() {
        QIOFrequency frequency = getQIOFrequency();
        return Collections.singletonList(frequency == null ? new TextComponentTranslation("frequency.mekanism.none") :
              MekanismLang.FREQUENCY.translate(frequency.getName()));
    }

    private List<net.minecraft.util.text.ITextComponent> getFrequencyTooltip() {
        QIOFrequency frequency = getQIOFrequency();
        if (frequency == null) {
            return Collections.emptyList();
        }
        QIOItemViewerContainer container = getViewerContainer();
        return QIOGuiCapacityText.forContainer(container);
    }

    private boolean isValidSearchChar(char character) {
        return SEARCH_SPECIAL_CHARS.contains(character) || Character.isLetterOrDigit(character);
    }

    @Nonnull
    private QIOItemViewerContainer getViewerContainer() {
        return (QIOItemViewerContainer) inventorySlots;
    }

    protected abstract String getViewerTitle();

    protected abstract QIOFrequency getQIOFrequency();

    protected abstract GuiQIOViewerScreen<CONTAINER> recreate(CONTAINER container);

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(getViewerTitle()), 4);
        drawScaledScrollingString(MekanismLang.LIST_SEARCH.translate(), 4, 31, TextAlignment.RIGHT, titleTextColor(),
              searchField.getRelativeX() - 4, 3, false, 1, getTimeOpened());
        renderInventoryTextAndOther(MekanismLang.LIST_SORT.translate(), xSize - searchDropdownX - 5);
        super.drawForegroundText(mouseX, mouseY);
    }

    @Override
    public void resize(ResizeType type, boolean adjustMax) {
        int columns = QIOItemViewerContainer.getConfiguredColumns();
        int rows = QIOItemViewerContainer.getConfiguredRows();
        switch (type) {
            case EXPAND_X -> columns = adjustMax ? QIOItemViewerContainer.SLOTS_X_MAX : columns + 1;
            case SHRINK_X -> columns = adjustMax ? QIOItemViewerContainer.SLOTS_X_MIN : columns - 1;
            case EXPAND_Y -> rows = adjustMax ? getMaxRows() : rows + 1;
            case SHRINK_Y -> rows = adjustMax ? QIOItemViewerContainer.SLOTS_Y_MIN : rows - 1;
        }
        columns = Math.max(QIOItemViewerContainer.SLOTS_X_MIN, Math.min(QIOItemViewerContainer.SLOTS_X_MAX, columns));
        rows = Math.max(QIOItemViewerContainer.SLOTS_Y_MIN, Math.min(getMaxRows(), rows));
        if (columns == QIOItemViewerContainer.getConfiguredColumns() && rows == QIOItemViewerContainer.getConfiguredRows()) {
            return;
        }
        MekanismConfig.current().client.qioItemViewerSlotsX.set(columns);
        MekanismConfig.current().client.qioItemViewerSlotsY.set(rows);
        Mekanism.configuration.save();
        recreateViewer();
    }

    @Override
    public int getMaxRows() {
        int maxRows = MathHelper.ceil(height * 0.05F - 8) + 1;
        return Math.max(QIOItemViewerContainer.SLOTS_Y_MIN,
              Math.min(QIOItemViewerContainer.SLOTS_Y_MAX, maxRows));
    }

    private void recreateViewer() {
        replacingViewer = true;
        @SuppressWarnings("unchecked")
        CONTAINER container = (CONTAINER) inventorySlots;
        container.repositionPlayerInventory();
        GuiQIOViewerScreen<CONTAINER> replacement = recreate(container);
        replacement.loadPinned = false;
        List<GuiWindow> currentWindows = new ArrayList<>(windows);
        mc.displayGuiScreen(replacement);
        replacement.searchField.setText(searchField.getText());
        // LRU iteration is front-to-back while addWindow inserts at the
        // front. Reattach back-to-front so resizing preserves the focused
        // crafting window and the complete z-order.
        for (int i = currentWindows.size() - 1; i >= 0; i--) {
            GuiWindow window = currentWindows.get(i);
            if (window instanceof GuiCraftingWindow && replacement.craftingWindowTab != null) {
                replacement.craftingWindowTab.adoptWindow((GuiCraftingWindow) window);
            }
            if (window instanceof GuiQIOFrequencySelectWindow && replacement.frequencyTab != null) {
                replacement.frequencyTab.adoptWindow(window);
            }
            replacement.adoptTransferredWindow(window);
            window.transferToNewGui(replacement);
            replacement.addWindow(window);
        }
    }

    /** Extension hook for module-specific viewer windows preserved across a resize. */
    protected void adoptTransferredWindow(GuiWindow window) {
    }

    @Override
    public void onGuiClosed() {
        if (!replacingViewer) {
            super.onGuiClosed();
        }
    }

    @Override
    protected void initPinnedWindows() {
        if (loadPinned) {
            super.initPinnedWindows();
        }
    }

    private ViewerSortMode getSortMode() {
        return ViewerSortMode.values()[resourceGrid.getSortMode().ordinal()];
    }

    private void setSortMode(ViewerSortMode mode) {
        resourceGrid.setSortMode(GuiQIOResourceGrid.SortMode.values()[mode.ordinal()]);
    }

    private ViewerSortDirection getSortDirection() {
        return resourceGrid.isDescending() ? ViewerSortDirection.DESCENDING : ViewerSortDirection.ASCENDING;
    }

    private void setSortDirection(ViewerSortDirection direction) {
        if (resourceGrid.isDescending() != (direction == ViewerSortDirection.DESCENDING)) {
            resourceGrid.toggleSortDirection();
        }
    }

    public enum ViewerSortMode implements GuiDropdown.IDropdownOption {
        NAME(MekanismLang.LIST_SORT_NAME, MekanismLang.LIST_SORT_NAME_DESC),
        SIZE(MekanismLang.LIST_SORT_COUNT, MekanismLang.LIST_SORT_COUNT_DESC),
        MOD(MekanismLang.LIST_SORT_MOD, MekanismLang.LIST_SORT_MOD_DESC),
        REGISTRY_NAME(MekanismLang.LIST_SORT_REGISTRY_NAME, MekanismLang.LIST_SORT_REGISTRY_NAME_DESC);

        private final MekanismLang name;
        private final MekanismLang tooltip;

        ViewerSortMode(MekanismLang name, MekanismLang tooltip) {
            this.name = name;
            this.tooltip = tooltip;
        }

        @Override
        public net.minecraft.util.text.ITextComponent getShortName() {
            return name.translate();
        }

        @Override
        public net.minecraft.util.text.ITextComponent getTooltip() {
            return tooltip.translate();
        }
    }

    public enum ViewerSortDirection implements GuiDigitalIconToggle.IIconToggleOption {
        ASCENDING(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "arrow_up.png"), MekanismLang.LIST_SORT_ASCENDING_DESC),
        DESCENDING(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "arrow_down.png"), MekanismLang.LIST_SORT_DESCENDING_DESC);

        private final ResourceLocation icon;
        private final MekanismLang tooltip;

        ViewerSortDirection(ResourceLocation icon, MekanismLang tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }

        @Override
        public ResourceLocation getIcon() {
            return icon;
        }

        @Override
        public net.minecraft.util.text.ITextComponent getTooltip() {
            return tooltip.translate();
        }
    }

}
