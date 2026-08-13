package mekanism.qioprocessing.client.gui;

import mekanism.api.gas.GasStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.inventory.container.QIOSmartProcessingClientCache;
import mekanism.qioprocessing.common.terminal.QIOSmartProcessingResourceEntry;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Presentation-only, virtualized grid for resources that can be ordered. */
final class GuiQIOSmartProcessingResourceGrid extends GuiElement
      implements IJEIIngredientHelper {

    private static final ResourceLocation SLOTS = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_SLOT, "slots.png");
    private static final ResourceLocation SLOTS_DARK = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_SLOT, "slots_dark.png");
    private static final int SELECTION_COLOR = 0xFFFFC44D;
    private static final int CRAFTABLE_COLOR = 0xFF49C46B;

    private final int columns;
    private final int rows;
    private final Supplier<QIOSmartProcessingClientCache> cacheSupplier;
    private final Supplier<PortableResourceDescriptor> selectedSupplier;
    private final Consumer<QIOSmartProcessingResourceEntry> selectionConsumer;
    private boolean rotatingSelection;
    private final GuiScrollBar scrollBar;
    private final Map<PortableResourceDescriptor, DisplayResource> displayCache =
          new LinkedHashMap<>(128, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(
                    Map.Entry<PortableResourceDescriptor, DisplayResource> eldest) {
                  return size() > 512;
              }
          };

    GuiQIOSmartProcessingResourceGrid(IGuiWrapper gui, int x, int y, int columns, int rows,
          Supplier<QIOSmartProcessingClientCache> cacheSupplier,
          Supplier<PortableResourceDescriptor> selectedSupplier,
          Consumer<QIOSmartProcessingResourceEntry> selectionConsumer) {
        super(gui, x, y, columns * 18 + 18, rows * 18);
        this.columns = columns;
        this.rows = rows;
        this.cacheSupplier = cacheSupplier;
        this.selectedSupplier = selectedSupplier;
        this.selectionConsumer = selectionConsumer;
        scrollBar = addChild(new GuiScrollBar(gui, relativeX + columns * 18 + 4,
              relativeY, rows * 18, this::getTotalRows, () -> rows));
        active = true;
    }

    GuiQIOSmartProcessingResourceGrid rotatingSelection() {
        rotatingSelection = true;
        return this;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        QIOSmartProcessingClientCache cache = cacheSupplier.get();
        minecraft.renderEngine.bindTexture(cache.getTotalSize() == 0 ? SLOTS_DARK : SLOTS);
        GuiUtils.blit(relativeX, relativeY, 0, 0, columns * 18, rows * 18, 288, 288);
        int first = getFirstVisibleIndex();
        PortableResourceDescriptor selected = selectedSupplier.get();
        for (int slot = 0; slot < columns * rows; slot++) {
            QIOSmartProcessingResourceEntry entry = cache.getEntry(first + slot);
            if (entry == null) continue;
            int x = relativeX + slot % columns * 18;
            int y = relativeY + slot / columns * 18;
            renderEntry(entry, x, y);
            if (entry.getResource().equals(selected)) {
                if (rotatingSelection) {
                    QIOGuiSelectionRenderer.draw(x, y, 18, 18);
                } else {
                    GuiUtils.drawOutline(x, y, 18, 18, SELECTION_COLOR);
                }
            }
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        int slot = getSlot(mouseX, mouseY);
        if (slot >= 0) {
            int x = relativeX + slot % columns * 18 + 1;
            int y = relativeY + slot / columns * 18 + 1;
            GuiUtils.fill(x, y, x + 16, y + 16, GuiSlot.DEFAULT_HOVER_COLOR);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOverCheckWindows(mouseX, mouseY) && scrollBar.adjustScroll(delta) ||
              super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !checkWindows(mouseX, mouseY)) return false;
        QIOSmartProcessingResourceEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) return super.mouseClicked(mouseX, mouseY, button);
        selectionConsumer.accept(entry);
        playClickSound();
        return true;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        QIOSmartProcessingResourceEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) return;
        PortableResourceDescriptor resource = entry.getResource();
        DisplayResource display = display(resource);
        List<String> extra = productionTooltip(entry);
        if (resource.getKind() == PortableResourceDescriptor.Kind.ITEM) {
            ItemStack stack = display.item;
            if (!stack.isEmpty()) {
                if (extra.isEmpty()) gui().renderItemTooltip(stack, mouseX, mouseY);
                else gui().renderItemTooltipWithExtra(stack, mouseX, mouseY, extra);
            }
            return;
        }
        List<String> tooltip = new ArrayList<>();
        if (resource.getKind() == PortableResourceDescriptor.Kind.FLUID) {
            FluidStack fluid = display.fluid;
            tooltip.add(fluid == null ? resource.getRegistryName() : fluid.getLocalizedName());
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_unit_fluid").getFormattedText());
        } else {
            GasStack gas = display.gas;
            tooltip.add(gas == null || gas.getGas() == null ? resource.getRegistryName() :
                  gas.getGas().getLocalizedName());
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_unit_gas").getFormattedText());
        }
        tooltip.addAll(extra);
        displayTooltips(tooltip, mouseX, mouseY);
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        QIOSmartProcessingResourceEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) return null;
        DisplayResource display = display(entry.getResource());
        return switch (entry.getResource().getKind()) {
            case ITEM -> display.item;
            case FLUID -> display.fluid;
            case GAS -> display.gas;
        };
    }

    int getMissingPageOffset(int pageSize) {
        QIOSmartProcessingClientCache cache = cacheSupplier.get();
        if (cache.getSourceRevision() < 0) return 0;
        int first = getFirstVisibleIndex();
        int last = Math.min(cache.getTotalSize(), first + columns * rows);
        for (int index = first; index < last; index += pageSize) {
            int offset = index / pageSize * pageSize;
            if (!cache.isPageLoaded(offset)) return offset;
            int nextBoundary = offset + pageSize;
            if (nextBoundary < last && !cache.isPageLoaded(nextBoundary)) return nextBoundary;
        }
        return -1;
    }

    int getFirstVisiblePageOffset(int pageSize) {
        return getFirstVisibleIndex() / pageSize * pageSize;
    }

    void resetScroll() {
        scrollBar.resetScroll();
    }

    private int getTotalRows() {
        return (cacheSupplier.get().getTotalSize() + columns - 1) / columns;
    }

    private int getFirstVisibleIndex() {
        return scrollBar.getCurrentSelection() * columns;
    }

    @Nullable
    private QIOSmartProcessingResourceEntry entryAt(double mouseX, double mouseY) {
        int slot = getSlot(mouseX, mouseY);
        return slot < 0 ? null : cacheSupplier.get().getEntry(getFirstVisibleIndex() + slot);
    }

    private int getSlot(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseY < getY() || mouseX >= getX() + columns * 18 ||
              mouseY >= getY() + rows * 18) return -1;
        int slotX = (int) ((mouseX - getGuiLeft() - relativeX) / 18);
        int slotY = (int) ((mouseY - getGuiTop() - relativeY) / 18);
        if (slotX < 0 || slotY < 0 || slotX >= columns || slotY >= rows) return -1;
        int startX = getX() + slotX * 18 + 1;
        int startY = getY() + slotY * 18 + 1;
        if (mouseX < startX || mouseX >= startX + 16 || mouseY < startY ||
              mouseY >= startY + 16 || !checkWindows(mouseX, mouseY)) return -1;
        return slotY * columns + slotX;
    }

    private void renderEntry(QIOSmartProcessingResourceEntry entry, int x, int y) {
        PortableResourceDescriptor resource = entry.getResource();
        DisplayResource display = display(resource);
        switch (resource.getKind()) {
            case ITEM -> {
                ItemStack stack = display.item;
                if (!stack.isEmpty()) gui().renderItemWithOverlay(stack, x + 1, y + 1, 1, "");
            }
            case FLUID -> GuiUtils.drawFluidBarSprite(x, y, 18, 18, 16,
                  display.fluid, true);
            case GAS -> GuiUtils.drawGasBarSprite(x, y, 18, 18, 16,
                  display.gas, true);
        }
        if (entry.isSchedulable()) {
            GuiUtils.fill(x + 13, y + 2, x + 16, y + 5, CRAFTABLE_COLOR);
        }
        if (entry.getInProduction() > 0) {
            renderSlotText(UnitDisplayUtils.getDisplay(entry.getInProduction(), 1),
                  x + 1, y + 1, entry.isMergeable() ? SELECTION_COLOR : 0xFFFFFFFF);
        }
    }

    private void renderSlotText(String text, int x, int y, int color) {
        GlStateManager.pushMatrix();
        MekanismRenderer.resetColor();
        float scale = 0.6F;
        int width = getFont().getStringWidth(text);
        if (width > 0) scale = Math.min(1, 16F / (width * scale)) * scale;
        GlStateManager.translate(x + 16 - width * scale, y + 11, 200);
        GlStateManager.scale(scale, scale, scale);
        getFont().drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private List<String> productionTooltip(QIOSmartProcessingResourceEntry entry) {
        if (entry.getInProduction() <= 0) return Collections.emptyList();
        List<String> tooltip = new ArrayList<>(2);
        tooltip.add(new TextComponentTranslation(
              "gui.mekanismqioprocessing.order_in_production",
              TextUtils.format(entry.getInProduction())).getFormattedText());
        if (entry.isMergeable()) {
            tooltip.add(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.order_mergeable",
                  TextUtils.format(entry.getMergeableInProduction())).getFormattedText());
        }
        return tooltip;
    }

    private DisplayResource display(PortableResourceDescriptor resource) {
        DisplayResource display = displayCache.get(resource);
        if (display == null) {
            display = new DisplayResource(resource.resolveItem(), resource.resolveFluid(),
                  resource.resolveGas());
            displayCache.put(resource, display);
        }
        return display;
    }

    private static final class DisplayResource {
        private final ItemStack item;
        private final FluidStack fluid;
        private final GasStack gas;

        private DisplayResource(ItemStack item, FluidStack fluid, GasStack gas) {
            this.item = item;
            this.fluid = fluid;
            this.gas = gas;
        }
    }
}
