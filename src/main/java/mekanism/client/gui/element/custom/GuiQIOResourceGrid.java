package mekanism.client.gui.element.custom;

import mekanism.api.gas.GasStack;
import mekanism.api.EnumColor;
import mekanism.api.qio.client.QIOResourceRenderer;
import mekanism.api.qio.client.QIOResourceRendererRegistry;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOAmount;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.QIOSearchQueryParser;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.network.qio.PacketQIOViewerAction;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Scrollable, kind-aware QIO resource grid shared by Dashboard screens. */
public class GuiQIOResourceGrid extends GuiElement implements IJEIIngredientHelper {

    private static final ResourceLocation SLOTS = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_SLOT, "slots.png");
    private static final ResourceLocation SLOTS_DARK = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_SLOT, "slots_dark.png");

    private final int columns;
    private final int rows;
    private final Supplier<QIOItemViewerContainer> containerSupplier;
    private final GuiScrollBar scrollBar;
    private final List<QIOResourceKind> visibleKinds = new ArrayList<>();
    private final List<QIOResourceEntry> pausedEntries = new ArrayList<>();
    private String search = "";
    private QIOSearchQueryParser.ISearchQuery searchQuery = QIOSearchQueryParser.parse("");
    private SortMode sortMode = SortMode.NAME;
    private boolean descending;
    private boolean sortingPaused;

    public GuiQIOResourceGrid(IGuiWrapper gui, int x, int y, int columns, int rows,
          Supplier<QIOItemViewerContainer> containerSupplier) {
        super(gui, x, y, columns * 18 + 18, rows * 18);
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        this.containerSupplier = containerSupplier;
        scrollBar = addChild(new GuiScrollBar(gui, relativeX + this.columns * 18 + 4, relativeY, this.rows * 18,
              this::getTotalRows, () -> this.rows));
        visibleKinds.addAll(EnumSet.allOf(QIOResourceKind.class));
        SortMode[] modes = SortMode.values();
        int configuredMode = MekanismConfig.current().client.qioItemViewerSortType.val();
        sortMode = modes[Math.max(0, Math.min(modes.length - 1, configuredMode))];
        descending = MekanismConfig.current().client.qioItemViewerSortDescending.val();
        active = true;
    }

    public GuiQIOResourceGrid kinds(QIOResourceKind... kinds) {
        visibleKinds.clear();
        if (kinds != null) {
            for (QIOResourceKind kind : kinds) {
                if (kind != null && !visibleKinds.contains(kind)) {
                    visibleKinds.add(kind);
                }
            }
        }
        rebuildPausedEntries();
        return this;
    }

    public GuiQIOResourceGrid search(String query) {
        search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        searchQuery = QIOSearchQueryParser.parse(search);
        rebuildPausedEntries();
        return this;
    }

    public String getSearch() {
        return search;
    }

    public GuiQIOResourceGrid setSortMode(SortMode sortMode) {
        this.sortMode = sortMode == null ? SortMode.NAME : sortMode;
        MekanismConfig.current().client.qioItemViewerSortType.set(this.sortMode.ordinal());
        Mekanism.configuration.save();
        rebuildPausedEntries();
        return this;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public GuiQIOResourceGrid cycleSortMode() {
        SortMode[] values = SortMode.values();
        setSortMode(values[(sortMode.ordinal() + 1) % values.length]);
        return this;
    }

    public GuiQIOResourceGrid toggleSortDirection() {
        descending = !descending;
        MekanismConfig.current().client.qioItemViewerSortDescending.set(descending);
        Mekanism.configuration.save();
        rebuildPausedEntries();
        return this;
    }

    public boolean isDescending() {
        return descending;
    }

    public boolean showsOnly(QIOResourceKind kind) {
        return visibleKinds.size() == 1 && visibleKinds.contains(kind);
    }

    public boolean showsAllKinds() {
        return visibleKinds.size() == QIOResourceKind.values().length;
    }

    public enum SortMode {
        NAME,
        SIZE,
        MOD,
        REGISTRY_NAME
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        List<QIOResourceEntry> entries = getVisibleEntries();
        minecraft.renderEngine.bindTexture(entries.isEmpty() ? SLOTS_DARK : SLOTS);
        // The slot texture is a 16x16 slot atlas (288px high). Tile it in
        // bounded chunks so the 26.2 maximum of 48 rows never samples past
        // the texture edge.
        for (int rowStart = 0; rowStart < rows; rowStart += 16) {
            int rowCount = Math.min(16, rows - rowStart);
            GuiUtils.blit(relativeX, relativeY + rowStart * 18, 0, 0,
                  columns * 18, rowCount * 18, 288, 288);
        }
        for (int i = 0; i < columns * rows; i++) {
            int slotX = relativeX + (i % columns) * 18;
            int slotY = relativeY + (i / columns) * 18;
            int index = scrollBar.getCurrentSelection() * columns + i;
            if (index < entries.size()) {
                renderEntry(entries.get(index), slotX, slotY);
            }
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        int slot = getSlot(mouseX, mouseY);
        if (slot >= 0) {
            int x = relativeX + (slot % columns) * 18 + 1;
            int y = relativeY + (slot / columns) * 18 + 1;
            GuiUtils.fill(x, y, x + 16, y + 16, GuiSlot.DEFAULT_HOVER_COLOR);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOverCheckWindows(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        return scrollBar.adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button < 0 || button > 2 || !checkWindows(mouseX, mouseY)) {
            return false;
        }
        int slot = getSlot(mouseX, mouseY);
        if (slot < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        QIOItemViewerContainer container = containerSupplier.get();
        if (container == null) {
            return false;
        }
        List<QIOResourceEntry> entries = getVisibleEntries();
        int index = scrollBar.getCurrentSelection() * columns + slot;
        QIOResourceEntry entry = index < entries.size() ? entries.get(index) : null;
        int windowId = container.windowId;
        ItemStack held = minecraft.player.inventory.getItemStack();
        boolean shift = GuiScreen.isShiftKeyDown();
        // Match the high-version slot scroll: placing the carried stack does
        // not require an existing resource entry under the clicked grid cell.
        if (shift) {
            if (entry != null && hasUsableDisplayPayload(entry) && entry.getKind() != null) {
                PacketQIOViewerAction.sendShiftTake(windowId, entry.getUUID(),
                      PacketQIOViewerAction.getShiftTakeAmount(entry));
            }
        } else if (!held.isEmpty()) {
            if (entry != null && hasUsableDisplayPayload(entry) &&
                  (entry.getKind() == QIOResourceKind.FLUID && PacketQIOViewerAction.canTakeFluidIntoHeldStack(entry, held) ||
                  entry.getKind() == QIOResourceKind.GAS && PacketQIOViewerAction.canTakeGasIntoHeldStack(entry, held))) {
                PacketQIOViewerAction.sendTake(windowId, entry.getUUID(), entry.getAmount());
            } else if (entry != null && hasUsableDisplayPayload(entry) && button == 2 &&
                  PacketQIOViewerAction.canTakeIntoHeldStack(entry, held)) {
                // Middle-click tops up a matching item stack by one.
                PacketQIOViewerAction.sendTake(windowId, entry.getUUID(), 1);
            } else {
                PacketQIOViewerAction.sendPut(windowId, PacketQIOViewerAction.getHeldPutAmount(held, button != 0));
            }
        } else if (entry != null && hasUsableDisplayPayload(entry)) {
            if (entry.getKind() == QIOResourceKind.FLUID || entry.getKind() == QIOResourceKind.GAS) {
                // Mixed resources are moved to a compatible inventory
                // container when the cursor is empty.
                PacketQIOViewerAction.sendShiftTake(windowId, entry.getUUID(),
                      PacketQIOViewerAction.getShiftTakeAmount(entry));
            } else if (entry.getKind() == QIOResourceKind.ITEM && entry.getAmount() > 0) {
                int maxStackSize = Math.min(Integer.MAX_VALUE, entry.getItem().getMaxStackSize());
                long max = Math.min(entry.getAmount(), maxStackSize);
                long amount = button == 0 ? max : button == 1 ? Math.max(1, max / 2) : 1;
                PacketQIOViewerAction.sendTake(windowId, entry.getUUID(), amount);
            }
        }
        playClickSound();
        return true;
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        int slot = getSlot(mouseX, mouseY);
        if (slot < 0) {
            return null;
        }
        List<QIOResourceEntry> entries = getVisibleEntries();
        int index = scrollBar.getCurrentSelection() * columns + slot;
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        QIOResourceEntry entry = entries.get(index);
        if (!hasUsableDisplayPayload(entry)) {
            return null;
        }
        Object renderedIngredient = getRenderedIngredient(entry);
        if (renderedIngredient != null) {
            return renderedIngredient;
        }
        QIOResourceKind kind = entry.getKind();
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case ITEM:
                return entry.getItem();
            case FLUID:
                return entry.createFluidStack(1);
            case GAS:
                return entry.createGasStack(1);
            default:
                return null;
        }
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        int slot = getSlot(mouseX, mouseY);
        List<QIOResourceEntry> entries = getVisibleEntries();
        int index = slot < 0 ? -1 : scrollBar.getCurrentSelection() * columns + slot;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        QIOResourceEntry entry = entries.get(index);
        List<String> renderedTooltip = getRenderedTooltip(entry);
        if (renderedTooltip != null) {
            List<String> tooltip = new ArrayList<>(renderedTooltip.size() + 1);
            for (String line : renderedTooltip) {
                if (line != null && !line.isEmpty()) {
                    tooltip.add(line);
                }
            }
            if (tooltip.isEmpty()) {
                tooltip.add(getResourceName(entry));
            }
            tooltip.add(getStoredTooltip(entry.getExactAmount()));
            displayTooltips(tooltip, mouseX, mouseY);
        } else if (!hasUsableDisplayPayload(entry)) {
            displayTooltips(java.util.Arrays.asList(getResourceName(entry),
                  getStoredTooltip(entry.getExactAmount())), mouseX, mouseY);
        } else if (entry.getKind() == QIOResourceKind.ITEM) {
            if (!entry.getExactAmount().isExpanded() && entry.getAmount() < 10_000) {
                gui().renderItemTooltip(entry.getItem(), mouseX, mouseY);
            } else {
                gui().renderItemTooltipWithExtra(entry.getItem(), mouseX, mouseY,
                      java.util.Collections.singletonList(getStoredTooltip(entry.getExactAmount())));
            }
        } else {
            List<String> tooltip = new ArrayList<>(2);
            tooltip.add(getResourceName(entry));
            tooltip.add(getStoredTooltip(entry.getExactAmount()));
            displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private String getStoredTooltip(QIOAmount amount) {
        return MekanismLang.QIO_STORED_COUNT.translateColored(EnumColor.GREY, EnumColor.INDIGO,
              TextUtils.format(amount.toBigInteger())).getFormattedText();
    }

    @Nonnull
    private List<QIOResourceEntry> getVisibleEntries() {
        updateSortingPause();
        if (!sortingPaused) {
            return getSortedEntries();
        }
        QIOItemViewerContainer container = containerSupplier.get();
        if (container == null || container.isKilled()) {
            pausedEntries.clear();
            return java.util.Collections.emptyList();
        }
        Map<UUID, QIOResourceEntry> currentEntries = new LinkedHashMap<>();
        for (QIOResourceEntry entry : getFilteredEntries()) {
            currentEntries.put(entry.getUUID(), entry);
        }
        for (int i = 0; i < pausedEntries.size(); i++) {
            QIOResourceEntry previous = pausedEntries.get(i);
            QIOResourceEntry current = currentEntries.remove(previous.getUUID());
            pausedEntries.set(i, current == null ? previous.withAmount(0) : current);
        }
        //26.2 appends newly discovered resources while sorting is paused instead of moving existing slots.
        pausedEntries.addAll(currentEntries.values());
        return new ArrayList<>(pausedEntries);
    }

    @Nonnull
    private List<QIOResourceEntry> getFilteredEntries() {
        QIOItemViewerContainer container = containerSupplier.get();
        if (container == null || container.isKilled()) {
            return java.util.Collections.emptyList();
        }
        List<QIOResourceEntry> entries = new ArrayList<>();
        for (QIOResourceEntry entry : container.getResourceEntries()) {
            if (entry.getKind() == null ? !showsAllKinds() : !visibleKinds.contains(entry.getKind())) {
                continue;
            }
            // Match 26.2: an incomplete/invalid expression behaves like no
            // filter while the user is still typing it.
            if (!searchQuery.isInvalid() && !searchQuery.matches(entry)) {
                continue;
            }
            entries.add(entry);
        }
        return entries;
    }

    @Nonnull
    private List<QIOResourceEntry> getSortedEntries() {
        List<QIOResourceEntry> entries = getFilteredEntries();
        Comparator<QIOResourceEntry> comparator;
        switch (sortMode) {
            case SIZE:
                comparator = descending ? Comparator.comparing(QIOResourceEntry::getExactAmount).reversed()
                      .thenComparing(this::getResourceName) : Comparator.comparing(QIOResourceEntry::getExactAmount)
                      .thenComparing(this::getResourceName);
                break;
            case MOD:
                comparator = descending ? Comparator.comparing(this::getModId, Comparator.reverseOrder())
                      .thenComparing(this::getResourceName) : Comparator.comparing(this::getModId)
                      .thenComparing(this::getResourceName);
                break;
            case REGISTRY_NAME:
                comparator = descending ? Comparator.comparing(this::getRegistryName, Comparator.reverseOrder())
                      .thenComparing(QIOResourceEntry::getExactAmount) : Comparator.comparing(this::getRegistryName)
                      .thenComparing(QIOResourceEntry::getExactAmount);
                break;
            case NAME:
            default:
                comparator = Comparator.comparing(this::getResourceName);
                if (descending) {
                    comparator = comparator.reversed();
                }
                break;
        }
        comparator = comparator.thenComparing(entry -> entry.getUUID().toString());
        entries.sort(comparator);
        return entries;
    }

    private void updateSortingPause() {
        boolean pause = GuiScreen.isShiftKeyDown();
        if (pause == sortingPaused) {
            return;
        }
        sortingPaused = pause;
        pausedEntries.clear();
        if (sortingPaused) {
            pausedEntries.addAll(getSortedEntries());
        }
    }

    private void rebuildPausedEntries() {
        if (sortingPaused) {
            pausedEntries.clear();
            pausedEntries.addAll(getSortedEntries());
        }
    }

    private int getTotalRows() {
        return (getVisibleEntries().size() + columns - 1) / columns;
    }

    private int getSlot(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseY < getY() || mouseX >= getX() + columns * 18 || mouseY >= getY() + rows * 18) {
            return -1;
        }
        int slotX = (int) ((mouseX - getGuiLeft() - relativeX) / 18);
        int slotY = (int) ((mouseY - getGuiTop() - relativeY) / 18);
        if (slotX < 0 || slotY < 0 || slotX >= columns || slotY >= rows) {
            return -1;
        }
        int slotStartX = getX() + slotX * 18 + 1;
        int slotStartY = getY() + slotY * 18 + 1;
        if (mouseX < slotStartX || mouseX >= slotStartX + 16 || mouseY < slotStartY || mouseY >= slotStartY + 16 ||
              !checkWindows(mouseX, mouseY)) {
            return -1;
        }
        return slotY * columns + slotX;
    }

    private void renderEntry(QIOResourceEntry entry, int x, int y) {
        boolean rendered = renderWithRegisteredRenderer(entry, x + 1, y + 1);
        boolean usablePayload = hasUsableDisplayPayload(entry);
        QIOResourceKind kind = entry.getKind();
        if (rendered) {
            // The count overlay remains owned by the grid so every codec uses identical scaling.
        } else if (usablePayload && kind == QIOResourceKind.ITEM) {
            ItemStack stack = entry.getItem();
            if (!stack.isEmpty()) {
                gui().renderItemWithOverlay(stack, x + 1, y + 1, 1, "");
                rendered = true;
            }
        }
        if (!rendered) {
            int color = resourceColor(entry);
            GuiUtils.fill(x + 3, y + 3, x + 15, y + 15, color);
            String marker = "?";
            if (usablePayload && kind == QIOResourceKind.FLUID) {
                FluidStack fluid = entry.getFluid();
                if (fluid != null) {
                    GuiUtils.drawFluidBarSprite(x, y, 18, 18, 16, fluid, true);
                    marker = "F";
                }
            } else if (usablePayload && kind == QIOResourceKind.GAS) {
                GasStack gas = entry.getGas();
                if (gas != null) {
                    GuiUtils.drawGasBarSprite(x, y, 18, 18, 16, gas, true);
                    marker = "G";
                }
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 5, y + 4, 100);
            GlStateManager.scale(0.75F, 0.75F, 0.75F);
            getFont().drawString(marker, 0, 0, 0xFFFFFFFF);
            GlStateManager.popMatrix();
        }
        String count = getCountText(entry.getExactAmount());
        if (count != null) {
            renderSlotText(count, x + 1, y + 1, entry.getExactAmount().isZero() ? 0xFFFFFF55 : 0xFFFFFFFF);
        }
    }

    private void renderSlotText(String text, int x, int y, int color) {
        GlStateManager.pushMatrix();
        MekanismRenderer.resetColor();
        float scale = 0.6F;
        int textWidth = getFont().getStringWidth(text);
        if (textWidth > 0) {
            scale = Math.min(1, 16F / (textWidth * scale)) * scale;
        }
        float yAdd = 4 - scale * 4;
        GlStateManager.translate(x + 16 - textWidth * scale, y + 9 + yAdd, 200);
        GlStateManager.scale(scale, scale, scale);
        getFont().drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean hasPersistentData() {
        return true;
    }

    @Override
    public void syncFrom(GuiElement element) {
        if (element instanceof GuiQIOResourceGrid) {
            GuiQIOResourceGrid old = (GuiQIOResourceGrid) element;
            search = old.search;
            searchQuery = QIOSearchQueryParser.parse(search);
            visibleKinds.clear();
            visibleKinds.addAll(old.visibleKinds);
            sortMode = old.sortMode;
            descending = old.descending;
            sortingPaused = old.sortingPaused;
            pausedEntries.clear();
            pausedEntries.addAll(old.pausedEntries);
        }
        // Copy the child scrollbar last. Public search/sort mutators rebuild
        // data and used to wipe the scroll state copied by this hook.
        super.syncFrom(element);
    }

    private int resourceColor(QIOResourceEntry entry) {
        int hash = entry.getUUID().hashCode();
        int r = 80 + (hash & 0x7F);
        int g = 80 + ((hash >>> 8) & 0x7F);
        int b = 80 + ((hash >>> 16) & 0x7F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private String getRegistryName(QIOResourceEntry entry) {
        String renderedName = getRenderedRegistryName(entry);
        if (renderedName != null) {
            return renderedName;
        }
        if (!hasUsableDisplayPayload(entry)) {
            return entry.getDescriptor().getCodecId().toString();
        }
        if (entry.getKind() == QIOResourceKind.ITEM) {
            net.minecraft.util.ResourceLocation registryName = entry.getItem().getItem().getRegistryName();
            return registryName == null ? "" : registryName.toString();
        } else if (entry.getKind() == QIOResourceKind.FLUID) {
            FluidStack fluid = entry.getFluid();
            return fluid == null || fluid.getFluid() == null ? "" : fluid.getFluid().getName();
        }
        if (entry.getKind() == null) {
            return entry.getDescriptor().getCodecId().toString();
        }
        GasStack gas = entry.getGas();
        return gas == null || gas.getGas() == null ? "" : gas.getGas().getName();
    }

    private String getModId(QIOResourceEntry entry) {
        String renderedModId = getRenderedModId(entry);
        if (renderedModId != null) {
            return renderedModId;
        }
        if (!hasUsableDisplayPayload(entry)) {
            return entry.getDescriptor().getCodecId().getNamespace();
        }
        if (entry.getKind() == QIOResourceKind.ITEM) {
            return MekanismUtils.getModId(entry.getItem());
        } else if (entry.getKind() == QIOResourceKind.FLUID) {
            FluidStack fluid = entry.getFluid();
            if (fluid != null && fluid.getFluid() != null && fluid.getFluid().getStill(fluid) != null) {
                return fluid.getFluid().getStill(fluid).getNamespace();
            }
        } else if (entry.getKind() == QIOResourceKind.GAS) {
            GasStack gas = entry.getGas();
            if (gas != null && gas.getGas() != null && gas.getGas().getIcon() != null) {
                return gas.getGas().getIcon().getNamespace();
            }
        } else if (entry.getKind() == null) {
            return entry.getDescriptor().getCodecId().getNamespace();
        }
        String registryName = getRegistryName(entry);
        int separator = registryName.indexOf(':');
        return separator > 0 ? registryName.substring(0, separator) : "";
    }

    @Nonnull
    private String getResourceName(QIOResourceEntry entry) {
        String renderedName = getRenderedDisplayName(entry);
        if (renderedName != null) {
            return renderedName;
        }
        if (!hasUsableDisplayPayload(entry)) {
            return entry.getDescriptor().getCodecId().toString();
        }
        if (entry.getKind() == QIOResourceKind.ITEM) {
            ItemStack stack = entry.getItem();
            return stack.isEmpty() ? entry.getDescriptor().getCodecId().toString() : stack.getDisplayName();
        } else if (entry.getKind() == QIOResourceKind.FLUID) {
            FluidStack stack = entry.getFluid();
            return stack == null || stack.getFluid() == null ? LangUtils.localize("gui.qio.resource.unknown_fluid") : stack.getLocalizedName();
        } else if (entry.getKind() == QIOResourceKind.GAS) {
            GasStack stack = entry.getGas();
            return stack == null || stack.getGas() == null ? LangUtils.localize("gui.qio.resource.unknown_gas") : stack.getGas().getLocalizedName();
        }
        return entry.getDescriptor().getCodecId().toString();
    }

    private boolean renderWithRegisteredRenderer(QIOResourceEntry entry, int x, int y) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return false;
        }
        GlStateManager.pushMatrix();
        try {
            resolved.renderer.render(resolved.resource, x, y);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            GlStateManager.popMatrix();
            MekanismRenderer.resetColor();
        }
    }

    @Nullable
    private Object getRenderedIngredient(QIOResourceEntry entry) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return null;
        }
        try {
            return resolved.renderer.getIngredient(resolved.resource);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private List<String> getRenderedTooltip(QIOResourceEntry entry) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return null;
        }
        try {
            List<String> tooltip = resolved.renderer.getTooltip(resolved.resource);
            return tooltip == null ? null : new ArrayList<>(tooltip);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private String getRenderedDisplayName(QIOResourceEntry entry) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return null;
        }
        try {
            String name = resolved.renderer.getDisplayName(resolved.resource);
            return name == null || name.isEmpty() ? null : name;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private String getRenderedRegistryName(QIOResourceEntry entry) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return null;
        }
        try {
            String name = resolved.renderer.getRegistryName(resolved.resource);
            return name == null || name.isEmpty() ? null : name;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private String getRenderedModId(QIOResourceEntry entry) {
        RendererResolution resolved = resolveRenderer(entry);
        if (resolved == null) {
            return null;
        }
        try {
            String modId = resolved.renderer.getModId(resolved.resource);
            return modId == null || modId.isEmpty() ? null : modId;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private RendererResolution resolveRenderer(QIOResourceEntry entry) {
        if (!hasUsableDisplayPayload(entry)) {
            return null;
        }
        QIOResourceRenderer<?> renderer = QIOResourceRendererRegistry.INSTANCE.get(
              entry.getDescriptor().getCodecId());
        if (renderer == null) {
            return null;
        }
        Object resource = entry.getDescriptor().resolve();
        if (resource == null || !renderer.getValueClass().isInstance(resource)) {
            return null;
        }
        return new RendererResolution((QIOResourceRenderer<Object>) renderer, resource);
    }

    private boolean hasUsableDisplayPayload(@Nullable QIOResourceEntry entry) {
        return entry != null && entry.hasDisplayPayload() && entry.getDescriptor().isResolved();
    }

    private static final class RendererResolution {

        private final QIOResourceRenderer<Object> renderer;
        private final Object resource;

        private RendererResolution(QIOResourceRenderer<Object> renderer, Object resource) {
            this.renderer = renderer;
            this.resource = resource;
        }
    }

    @Nullable
    private String getCountText(QIOAmount count) {
        if (count.isZero()) {
            return "0";
        }
        if (!count.isExpanded() && count.longValueClamped() == 1) {
            return null;
        }
        if (!count.isExpanded()) {
            long value = count.longValueClamped();
            return value < 10_000 ? Long.toString(value) : UnitDisplayUtils.getDisplay(value, 1);
        }
        String digits = count.toString();
        String decimals = digits.substring(1, Math.min(3, digits.length()));
        return digits.charAt(0) + (decimals.isEmpty() ? "" : "." + decimals) + "e" + (digits.length() - 1);
    }
}
