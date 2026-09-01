package mekanism.client.gui.element.custom;

import mekanism.api.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.qio.QIOFilterGuiResource;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.FilterSelectButton;
import mekanism.client.gui.element.button.RadioButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.content.qio.filter.QIOItemStackFilter;
import mekanism.common.content.qio.filter.QIOModIDFilter;
import mekanism.common.content.qio.filter.QIOOreDictFilter;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOFilterHandler;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/** 26.2-style movable filter rows backed by the mixed-resource QIO filter list. */
public class GuiQIOFilterList extends GuiElement {

    private static final int SCROLL_WIDTH = 14;
    private static final ResourceLocation FILTER_BACKGROUND = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_BUTTON, "filter_holder.png");
    private static final List<String> MOVE_UP = Arrays.asList(
          MekanismLang.MOVE_UP.translate().getFormattedText(), MekanismLang.MOVE_TO_TOP.translate().getFormattedText());
    private static final List<String> MOVE_DOWN = Arrays.asList(
          MekanismLang.MOVE_DOWN.translate().getFormattedText(), MekanismLang.MOVE_TO_BOTTOM.translate().getFormattedText());

    private final TileEntityQIOFilterHandler tile;
    private final GuiScrollBar scrollBar;
    private final int contentWidth;
    private final int visibleRows;
    private final int rowHeight;
    private final int listHeight;
    private final GuiSlot[] slots;
    private final RadioButton[] toggleButtons;
    private final FilterSelectButton[] upButtons;
    private final FilterSelectButton[] downButtons;
    @Nullable
    private final BiConsumer<QIOFilter, Integer> editHandler;

    public GuiQIOFilterList(IGuiWrapper gui, int x, int y, int width, TileEntityQIOFilterHandler tile) {
        this(gui, x, y, width - SCROLL_WIDTH - 1, 4, 24, 4 * 24, tile, null);
    }

    public GuiQIOFilterList(IGuiWrapper gui, int x, int y, int contentWidth, int visibleRows, int rowHeight,
          int scrollHeight, TileEntityQIOFilterHandler tile, @Nullable BiConsumer<QIOFilter, Integer> editHandler) {
        super(gui, x, y, contentWidth + SCROLL_WIDTH + 1, Math.max(visibleRows * rowHeight, scrollHeight));
        this.tile = tile;
        this.contentWidth = contentWidth;
        this.visibleRows = visibleRows;
        this.rowHeight = rowHeight;
        listHeight = visibleRows * rowHeight;
        this.editHandler = editHandler;
        slots = new GuiSlot[visibleRows];
        toggleButtons = new RadioButton[visibleRows];
        upButtons = new FilterSelectButton[visibleRows];
        downButtons = new FilterSelectButton[visibleRows];

        int scrollY = relativeY - (scrollHeight > listHeight ? 1 : 0);
        scrollBar = addChild(new GuiScrollBar(gui, relativeX + contentWidth + 1, scrollY, scrollHeight,
              () -> tile.getFilters().size(), () -> visibleRows));
        for (int row = 0; row < visibleRows; row++) {
            final int visibleRow = row;
            int rowY = relativeY + row * rowHeight;
            slots[row] = addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 2, rowY + 2));
            toggleButtons[row] = addChild(new RadioButton(gui, relativeX + contentWidth - RadioButton.RADIO_SIZE - 17,
                  rowY + rowHeight / 2 - RadioButton.RADIO_SIZE / 2,
                  () -> isEnabled(visibleRow), (element, mouseX, mouseY) -> toggleFilter(visibleRow),
                  MekanismLang.FILTER_STATE.translate(EnumColor.BRIGHT_GREEN, MekanismLang.MODULE_ENABLED_LOWER),
                  MekanismLang.FILTER_STATE.translate(EnumColor.RED, MekanismLang.MODULE_DISABLED_LOWER)));
            int arrowX = relativeX + contentWidth - 14;
            upButtons[row] = addChild(new FilterSelectButton(gui, arrowX, rowY + rowHeight / 2 - 8, false,
                  (element, mouseX, mouseY) -> moveFilter(visibleRow, false)));
            downButtons[row] = addChild(new FilterSelectButton(gui, arrowX, rowY + rowHeight / 2 + 1, true,
                  (element, mouseX, mouseY) -> moveFilter(visibleRow, true)));
        }
        active = true;
        playClickSound = true;
        updateControls();
    }

    @Override
    public void tick() {
        super.tick();
        updateControls();
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        updateControls();
        for (int row = 0; row < visibleRows; row++) {
            QIOFilter filter = getFilter(row);
            if (filter == null) {
                continue;
            }
            int rowY = relativeY + row * rowHeight;
            boolean hovered = mouseX >= getGuiLeft() + relativeX && mouseX < getGuiLeft() + relativeX + contentWidth &&
                  mouseY >= getGuiTop() + rowY && mouseY < getGuiTop() + rowY + rowHeight && checkWindows(mouseX, mouseY);
            GuiUtils.blitNineSlicedSized(FILTER_BACKGROUND, relativeX, rowY, contentWidth, rowHeight, 4, 4,
                  156, 29, 0, hovered ? 0 : 29, 156, 58);
            EnumColor rowColor = getFilterColor(filter);
            GuiUtils.fill(relativeX, rowY, relativeX + contentWidth, rowY + rowHeight,
                  MekanismRenderer.getColorARGB(rowColor, 0.3F));
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        updateControls();
        for (int row = 0; row < visibleRows; row++) {
            QIOFilter filter = getFilter(row);
            if (filter != null) {
                renderFilter(filter, row, relativeY + row * rowHeight);
            }
        }
    }

    private void renderFilter(QIOFilter filter, int row, int rowY) {
        int iconX = relativeX + 3;
        int iconY = rowY + 3;
        if (filter instanceof QIOOreDictFilter) {
            renderPreviewStack(OreDictCache.getOreDictStacks(((QIOOreDictFilter) filter).getOreDictName(), false), iconX, iconY);
        } else if (filter instanceof QIOModIDFilter) {
            renderPreviewStack(OreDictCache.getQIOModIDStacks(((QIOModIDFilter) filter).getModID()), iconX, iconY);
        } else {
            QIOFilterGuiResource.render(gui(), filter, iconX, iconY, 16);
        }
        int textWidth = contentWidth - RadioButton.RADIO_SIZE - 17 - 20;
        drawScaledScrollingString(new TextComponentString(getFilterName(filter)), 19, row * rowHeight + 3,
              TextAlignment.LEFT, titleTextColor(), textWidth, 3, false, 1, getMillis());
        drawScaledScrollingString(new TextComponentString(getFilterDetail(filter)), 19, row * rowHeight + 12,
              TextAlignment.LEFT, titleTextColor(), textWidth, 3, false, 0.7F, getMillis());
    }

    private void renderPreviewStack(List<ItemStack> stacks, int x, int y) {
        if (!stacks.isEmpty()) {
            int index = (int) ((getMillis() / 1_000L) % stacks.size());
            gui().renderItem(stacks.get(index), x, y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!checkWindows(mouseX, mouseY)) {
            return false;
        }
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            // The scrollbar is invoked directly so row clicks can be
            // distinguished below; explicitly retain it as the focused child
            // so mouse-drag events reach its normalized scroll state.
            setFocusedChild(scrollBar);
            return true;
        }
        setFocusedChild(null);
        int row = getRow(mouseX, mouseY);
        QIOFilter filter = row < 0 ? null : getFilter(row);
        if (row >= 0 && filter != null) {
            if (toggleButtons[row].mouseClicked(mouseX, mouseY, button) ||
                  upButtons[row].visible && upButtons[row].mouseClicked(mouseX, mouseY, button) ||
                  downButtons[row].visible && downButtons[row].mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 0 && filter != null && editHandler != null) {
            editHandler.accept(filter, getFilterIndex(row));
            playClickSound();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOverCheckWindows(mouseX, mouseY) && scrollBar.adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        int row = getRow(mouseX, mouseY);
        QIOFilter filter = row < 0 ? null : getFilter(row);
        if (filter == null) {
            return;
        }
        if (upButtons[row].visible && upButtons[row].isMouseOver(mouseX, mouseY)) {
            displayTooltips(MOVE_UP, mouseX, mouseY);
        } else if (downButtons[row].visible && downButtons[row].isMouseOver(mouseX, mouseY)) {
            displayTooltips(MOVE_DOWN, mouseX, mouseY);
        } else if (!toggleButtons[row].isMouseOver(mouseX, mouseY)) {
            List<String> tooltip = new ArrayList<>(3);
            tooltip.add(getFilterName(filter));
            tooltip.add(getFilterDetail(filter));
            tooltip.add(LangUtils.localize(filter.isEnabled() ? "gui.qio.filter.enabled" : "gui.qio.filter.disabled"));
            displayTooltips(tooltip, mouseX, mouseY);
        }
    }

    private void updateControls() {
        int filterCount = tile.getFilters().size();
        for (int row = 0; row < visibleRows; row++) {
            int index = getFilterIndex(row);
            boolean visible = index >= 0 && index < filterCount;
            slots[row].visible = visible;
            toggleButtons[row].visible = visible;
            upButtons[row].visible = visible && index > 0;
            downButtons[row].visible = visible && index < filterCount - 1;
        }
    }

    private boolean toggleFilter(int row) {
        QIOFilter filter = getFilter(row);
        if (filter == null) {
            return false;
        }
        PacketQIOComponentConfig.setFilterEnabled(tile, getFilterIndex(row), !filter.isEnabled());
        return true;
    }

    private boolean moveFilter(int row, boolean down) {
        int index = getFilterIndex(row);
        int filterCount = tile.getFilters().size();
        if (index < 0 || index >= filterCount || down && index >= filterCount - 1 || !down && index == 0) {
            return false;
        }
        int target = GuiScreen.isShiftKeyDown() ? (down ? filterCount - 1 : 0) : index + (down ? 1 : -1);
        PacketQIOComponentConfig.moveFilter(tile, index, target);
        return true;
    }

    private int getRow(double mouseX, double mouseY) {
        int localX = (int) mouseX - getGuiLeft() - relativeX;
        int localY = (int) mouseY - getGuiTop() - relativeY;
        if (localX < 0 || localX >= contentWidth || localY < 0 || localY >= listHeight) {
            return -1;
        }
        return localY / rowHeight;
    }

    private int getFilterIndex(int row) {
        return scrollBar.getCurrentSelection() + row;
    }

    @Nullable
    private QIOFilter getFilter(int row) {
        List<QIOFilter> filters = tile.getFilters();
        int index = getFilterIndex(row);
        return index >= 0 && index < filters.size() ? filters.get(index) : null;
    }

    private boolean isEnabled(int row) {
        QIOFilter filter = getFilter(row);
        return filter != null && filter.isEnabled();
    }

    private EnumColor getFilterColor(QIOFilter filter) {
        if (filter instanceof QIOOreDictFilter) {
            return EnumColor.BRIGHT_GREEN;
        } else if (filter instanceof QIOModIDFilter) {
            return EnumColor.RED;
        }
        return QIOFilterGuiResource.color(filter);
    }

    private String getFilterName(QIOFilter filter) {
        if (filter instanceof QIOOreDictFilter) {
            return ((QIOOreDictFilter) filter).getOreDictName();
        } else if (filter instanceof QIOModIDFilter) {
            return ((QIOModIDFilter) filter).getModID();
        }
        return QIOFilterGuiResource.name(filter);
    }

    private String getFilterDetail(QIOFilter filter) {
        if (filter instanceof QIOOreDictFilter) {
            return MekanismLang.TAG_FILTER.translate().getFormattedText();
        } else if (filter instanceof QIOModIDFilter) {
            return MekanismLang.MODID_FILTER.translate().getFormattedText();
        } else if (filter instanceof QIOItemStackFilter && ((QIOItemStackFilter) filter).isFuzzyMode()) {
            return MekanismLang.FUZZY_MODE.translate().getFormattedText();
        }
        String type = QIOFilterGuiResource.typeName(filter);
        return type.equals(LangUtils.localize("gui.qio.filter.unknown")) ? filter.getType() : type;
    }
}
