package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Three-row, icon-backed list for configured maintenance rules. */
/**
 * QIO 处理模块中的 GuiQIOMaintenanceRuleList 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class GuiQIOMaintenanceRuleList extends GuiElement {

    private static final int ROWS = 3;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_HEIGHT = ROWS * ROW_HEIGHT;
    private static final int CONTENT_WIDTH = 202;
    private static final int SCROLL_HEIGHT = 90;

    private final Supplier<List<QIOMaintenanceRule>> rulesSupplier;
    private final Consumer<Integer> selectionConsumer;
    private final Consumer<Integer> toggleConsumer;
    private final GuiScrollBar scrollBar;
    private final GuiSlot[] slots = new GuiSlot[ROWS];

    GuiQIOMaintenanceRuleList(IGuiWrapper gui, int x, int y,
          Supplier<List<QIOMaintenanceRule>> rulesSupplier,
          Consumer<Integer> selectionConsumer, Consumer<Integer> toggleConsumer) {
        super(gui, x, y, CONTENT_WIDTH + 15, SCROLL_HEIGHT);
        this.rulesSupplier = rulesSupplier;
        this.selectionConsumer = selectionConsumer;
        this.toggleConsumer = toggleConsumer;
        scrollBar = addChild(new GuiScrollBar(gui, relativeX + CONTENT_WIDTH + 1,
              relativeY - 1, SCROLL_HEIGHT, this::ruleCount, () -> ROWS));
        for (int row = 0; row < ROWS; row++) {
            slots[row] = addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + 2,
                  relativeY + row * ROW_HEIGHT + 2));
        }
        active = true;
        playClickSound = true;
    }

    private int ruleCount() {
        return rulesSupplier.get().size();
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        List<QIOMaintenanceRule> rules = rulesSupplier.get();
        if (rules.isEmpty()) {
            for (GuiSlot slot : slots) slot.visible = false;
            return;
        }
        int first = scrollBar.getCurrentSelection();
        for (int row = 0; row < ROWS; row++) {
            int index = first + row;
            if (index >= rules.size()) {
                slots[row].visible = false;
                continue;
            }
            slots[row].visible = true;
            int rowY = relativeY + row * ROW_HEIGHT;
            if (index == selectedIndex()) {
                GuiUtils.fill(relativeX + 1, rowY + 1, relativeX + CONTENT_WIDTH - 1,
                      rowY + ROW_HEIGHT - 1, 0x805A83B5);
            }
        }
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        List<QIOMaintenanceRule> rules = rulesSupplier.get();
        if (rules.isEmpty()) {
            drawScaledScrollingString(new TextComponentTranslation(
                  "gui.mekanismqioprocessing.no_rules"), 4, 28, TextAlignment.CENTER,
                  0x606060, CONTENT_WIDTH - 8, 1, false, 0.75F, getTimeOpened());
            return;
        }
        int first = scrollBar.getCurrentSelection();
        for (int row = 0; row < ROWS; row++) {
            int index = first + row;
            if (index < 0 || index >= rules.size()) continue;
            QIOMaintenanceRule rule = rules.get(index);
            int rowY = relativeY + row * ROW_HEIGHT;
            renderResource(rule.getResource(), relativeX + 3, rowY + 3);
            String name = resourceName(rule.getResource());
            String detail = new TextComponentTranslation(
                  "gui.mekanismqioprocessing.maintenance_rule_detail",
                  UnitDisplayUtils.getDisplay(rule.getTargetAmount(), 1),
                  UnitDisplayUtils.getDisplay(rule.getMaximumSingleRequest(), 1)).getFormattedText();
            int color = rule.isEnabled() ? 0x404040 : 0x888888;
            drawScaledScrollingString(new TextComponentString((rule.isEnabled() ? "" : "[X] ") + name),
                  23, row * ROW_HEIGHT + 3, TextAlignment.LEFT, color, CONTENT_WIDTH - 27, 2,
                  false, 0.78F, getTimeOpened());
            drawScaledScrollingString(new TextComponentString(detail), 23, row * ROW_HEIGHT + 12,
                  TextAlignment.LEFT, color, CONTENT_WIDTH - 27, 2, false, 0.65F,
                  getTimeOpened());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!checkWindows(mouseX, mouseY)) return false;
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            setFocusedChild(scrollBar);
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        int index = row < 0 ? -1 : scrollBar.getCurrentSelection() + row;
        if (index >= 0 && index < rulesSupplier.get().size()) {
            if (button == 0) {
                selectionConsumer.accept(index);
                playClickSound();
                return true;
            } else if (button == 1) {
                toggleConsumer.accept(index);
                playClickSound();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOverCheckWindows(mouseX, mouseY) && scrollBar.adjustScroll(delta) ||
              super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        int row = rowAt(mouseX, mouseY);
        int index = row < 0 ? -1 : scrollBar.getCurrentSelection() + row;
        List<QIOMaintenanceRule> rules = rulesSupplier.get();
        if (index < 0 || index >= rules.size()) return;
        QIOMaintenanceRule rule = rules.get(index);
        PortableResourceDescriptor resource = rule.getResource();
        if (resource.getKind() == PortableResourceDescriptor.Kind.ITEM) {
            ItemStack stack = QIOGuiResourceRenderer.item(resource);
            if (!stack.isEmpty()) {
                gui().renderItemTooltipWithExtra(stack, mouseX, mouseY,
                      ruleTooltip(rule));
                return;
            }
        }
        List<String> tooltip = new java.util.ArrayList<>(QIOGuiResourceRenderer.tooltip(resource));
        tooltip.add(QIOGuiResourceRenderer.identity(resource));
        tooltip.addAll(ruleTooltip(rule));
        displayTooltips(tooltip, mouseX, mouseY);
    }

    @Nullable
    private Integer selectedIndexValue;

    void setSelectedIndex(int index) {
        selectedIndexValue = index < 0 || index >= ruleCount() ? null : index;
    }

    int getSelectedIndex() {
        return selectedIndexValue == null ? -1 : selectedIndexValue;
    }

    boolean isAtLoadedEnd() {
        return scrollBar.getCurrentSelection() + ROWS >= ruleCount();
    }

    private int selectedIndex() {
        return getSelectedIndex();
    }

    private int rowAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - getGuiLeft() - relativeX;
        int localY = (int) mouseY - getGuiTop() - relativeY;
        return localX >= 0 && localX < CONTENT_WIDTH && localY >= 0 && localY < LIST_HEIGHT ?
              localY / ROW_HEIGHT : -1;
    }

    private void renderResource(PortableResourceDescriptor resource, int x, int y) {
        QIOGuiResourceRenderer.renderIcon(gui(), resource, x, y, 16);
    }

    private static String resourceName(PortableResourceDescriptor resource) {
        return QIOGuiResourceRenderer.name(resource);
    }

    private static List<String> ruleTooltip(QIOMaintenanceRule rule) {
        return java.util.Arrays.asList(
              new TextComponentTranslation(rule.isEnabled() ?
                    "gui.mekanismqioprocessing.maintenance_state_enabled" :
                    "gui.mekanismqioprocessing.maintenance_state_disabled").getFormattedText(),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.maintenance_toggle_hint").getFormattedText(),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.maintenance_delete_hint").getFormattedText());
    }
}
