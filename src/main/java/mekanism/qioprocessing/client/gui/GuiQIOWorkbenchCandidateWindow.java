package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Candidate;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Ingredient;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot.Recipe;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recipe-level workbench editor with a 3x3 ingredient grid and draggable alternatives. */
/**
 * QIO 处理模块中的 GuiQIOWorkbenchCandidateWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOWorkbenchCandidateWindow extends GuiWindow {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 168;
    private static final int ENABLED = 0xFF57C78B;
    private static final int DISABLED = 0xFFC75656;
    private static final int ALTERNATIVE_BACKGROUND = 0xFF315E8C;
    private final GuiQIOWorkbenchConfigurationWindow owner;
    private final RecipeGrid recipeGrid;
    private final CandidateGrid grid;
    private final GuiTextField priorityField;
    private final MekanismButton applyPriorityButton;
    private final MekanismButton toggleButton;
    private final MekanismButton syncButton;
    private boolean closed;

    GuiQIOWorkbenchCandidateWindow(IGuiWrapper gui,
          GuiQIOWorkbenchConfigurationWindow owner) {
        super(gui, (gui.getWidth() - WIDTH) / 2, 42, WIDTH, HEIGHT,
              new SelectedWindowData(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_CANDIDATES));
        this.owner = owner;
        interactionStrategy = InteractionStrategy.ALL;
        recipeGrid = addChild(new RecipeGrid(gui, relativeX + 12, relativeY + 30));
        grid = addChild(new CandidateGrid(gui, relativeX + 86, relativeY + 24));
        priorityField = addChild(new GuiTextField(gui, relativeX + 142, relativeY + 120,
              34, 12).setMaxLength(2).setInputValidator(character ->
                    character >= '0' && character <= '9').setEnterHandler(this::applyPriority));
        applyPriorityButton = addChild(new MekanismButton(gui, relativeX + 180,
              relativeY + 118, 68, 16, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_priority_apply"),
              this::applyPriority, getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_priority_apply_tooltip"))));
        toggleButton = addChild(new MekanismButton(gui, relativeX + 86,
              relativeY + 142, 78, 18, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_toggle"),
              this::toggleSelected, getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_toggle_tooltip"))));
        syncButton = addChild(new MekanismButton(gui, relativeX + 168,
              relativeY + 142, 80, 18, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_sync"),
              owner::syncEquivalentCandidates, getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidate_sync_tooltip"))));
        updateControls();
    }

    @Override
    public void tick() {
        super.tick();
        grid.synchronizeCandidates();
        updateControls();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_recipe_configuration_title"), 5);
        drawScaledScrollingString(new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_recipe_grid"),
              8, 19, mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT,
              titleTextColor(), 66, 0, false, 0.72F, getTimeOpened());
        drawScaledScrollingString(new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_candidates"),
              86, 15, mekanism.client.render.IFancyFontRenderer.TextAlignment.LEFT,
              titleTextColor(), 162, 0, false, 0.72F, getTimeOpened());
        getFont().drawString(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_candidate_priority").getFormattedText(),
              relativeX + 86, relativeY + 123, titleTextColor());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keyboard.KEY_ESCAPE && grid.cancelDrag()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void recipeChanged() {
        grid.resetContext();
        priorityField.clear();
    }

    void ingredientChanged() {
        grid.resetContext();
        priorityField.clear();
    }

    private void applyPriority() {
        Candidate selected = selectedCandidate();
        if (selected == null) return;
        try {
            int priority = Integer.parseInt(priorityField.getText());
            if (priority >= 1 && priority <= owner.candidateSnapshot().size()) {
                owner.moveCandidateToIndex(selected.getCandidateId(), priority - 1);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void toggleSelected() {
        Candidate selected = selectedCandidate();
        if (selected != null) owner.toggleCandidate(selected.getCandidateId());
    }

    private void updateControls() {
        Candidate selected = selectedCandidate();
        boolean editable = owner.canEditCandidates() && selected != null;
        applyPriorityButton.active = editable;
        toggleButton.active = editable;
        syncButton.active = owner.canSynchronizeEquivalentCandidates();
        priorityField.setEditable(editable);
        if (selected != null && !priorityField.isFocused()) {
            priorityField.setTextSilently(Integer.toString(selected.getOrder() + 1));
        }
    }

    @Nullable
    private Candidate selectedCandidate() {
        String selectedId = owner.selectedCandidateId();
        if (selectedId == null) return null;
        for (Candidate candidate : owner.candidateSnapshot()) {
            if (selectedId.equals(candidate.getCandidateId())) return candidate;
        }
        return null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        grid.cancelDrag();
        owner.candidateWindowClosed();
        super.close();
    }

    private final class RecipeGrid extends GuiElement {

        private static final int SLOT_SIZE = 18;
        private static final ResourceLocation SLOTS = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots.png");

        private RecipeGrid(IGuiWrapper gui, int x, int y) {
            super(gui, x, y, 3 * SLOT_SIZE, 3 * SLOT_SIZE);
            active = true;
        }

        @Override
        public void drawBackground(int mouseX, int mouseY, float partialTicks) {
            super.drawBackground(mouseX, mouseY, partialTicks);
            minecraft.renderEngine.bindTexture(SLOTS);
            GuiUtils.blit(relativeX, relativeY, 0, 0, width, height, 288, 288);
            Recipe recipe = owner.selectedRecipe();
            Ingredient selected = owner.selectedIngredient();
            if (recipe == null) return;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                int slot = ingredient.getSlot();
                int x = relativeX + slot % 3 * SLOT_SIZE;
                int y = relativeY + slot / 3 * SLOT_SIZE;
                if (ingredient.getCandidateCount() > 1) {
                    GuiUtils.fill(x + 1, y + 1, x + SLOT_SIZE - 1,
                          y + SLOT_SIZE - 1, ALTERNATIVE_BACKGROUND);
                }
                if (selected != null && slot == selected.getSlot()) {
                    QIOGuiSelectionRenderer.draw(x, y, SLOT_SIZE, SLOT_SIZE);
                }
                QIOWorkbenchGuiIngredientRenderer.render(gui(), ingredient,
                      x + 1, y + 1, 16);
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                int x = relativeX + slot % 3 * SLOT_SIZE + 1;
                int y = relativeY + slot / 3 * SLOT_SIZE + 1;
                GuiUtils.fill(x, y, x + 16, y + 16, GuiSlot.DEFAULT_HOVER_COLOR);
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            Ingredient ingredient = ingredientAt(mouseX, mouseY);
            if (ingredient == null || ingredient.isEmpty()) return;
            List<String> extra = new ArrayList<>();
            if (ingredient.getCandidateCount() > 1) {
                extra.add(new TextComponentTranslation(
                      "gui.mekanismqioprocessing.workbench_candidate_select",
                      ingredient.getEnabledCandidateCount(),
                      ingredient.getCandidateCount()).getFormattedText());
            }
            if (ingredient.isVirtualFluid()) {
                List<String> tooltip = QIOWorkbenchGuiIngredientRenderer.fluidTooltip(
                      ingredient);
                tooltip.addAll(extra);
                displayTooltips(tooltip, mouseX, mouseY);
            } else if (!extra.isEmpty()) {
                gui().renderItemTooltipWithExtra(ingredient.getRepresentative(), mouseX, mouseY,
                      extra);
            } else {
                gui().renderItemTooltip(ingredient.getRepresentative(), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !checkWindows(mouseX, mouseY)) return false;
            int slot = slotAt(mouseX, mouseY);
            if (slot < 0) return super.mouseClicked(mouseX, mouseY, button);
            owner.selectIngredient(ingredientAt(mouseX, mouseY));
            return true;
        }

        private int slotAt(double mouseX, double mouseY) {
            int localX = (int) mouseX - getX();
            int localY = (int) mouseY - getY();
            if (localX < 0 || localY < 0 || localX >= width || localY >= height) return -1;
            int inX = localX % SLOT_SIZE;
            int inY = localY % SLOT_SIZE;
            return inX >= 1 && inX < 17 && inY >= 1 && inY < 17 ?
                  localY / SLOT_SIZE * 3 + localX / SLOT_SIZE : -1;
        }

        @Nullable
        private Ingredient ingredientAt(double mouseX, double mouseY) {
            int slot = slotAt(mouseX, mouseY);
            Recipe recipe = owner.selectedRecipe();
            if (recipe == null || slot < 0) return null;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.getSlot() == slot) return ingredient;
            }
            return null;
        }

    }

    private final class CandidateGrid extends GuiElement {

        private static final int COLUMNS = 8;
        private static final int ROWS = 5;
        private static final int SLOT_SIZE = 18;
        private static final int DRAG_THRESHOLD_SQUARED = 9;
        private static final ResourceLocation SLOTS = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots.png");
        private final GuiScrollBar scrollBar;
        private final List<Candidate> display = new ArrayList<>();
        private final Map<String, AnimatedPosition> positions = new HashMap<>();
        @Nullable private String pressedCandidateId;
        private int pressedIndex = -1;
        private int targetIndex = -1;
        private double pressedX;
        private double pressedY;
        private double dragMouseX;
        private double dragMouseY;
        private boolean draggingCandidate;
        private long lastAutoScrollAt;

        private CandidateGrid(IGuiWrapper gui, int x, int y) {
            super(gui, x, y, COLUMNS * SLOT_SIZE + 18, ROWS * SLOT_SIZE);
            scrollBar = addChild(new GuiScrollBar(gui, relativeX + COLUMNS * SLOT_SIZE + 4,
                  relativeY, ROWS * SLOT_SIZE, this::totalRows, () -> ROWS));
            active = true;
            synchronizeCandidates();
        }

        @Override
        public void move(int changeX, int changeY) {
            super.move(changeX, changeY);
            positions.values().forEach(position -> {
                position.x += changeX;
                position.y += changeY;
            });
        }

        private int totalRows() {
            return Math.max(1, (display.size() + COLUMNS - 1) / COLUMNS);
        }

        private void resetContext() {
            clearDragState();
            scrollBar.resetScroll();
            display.clear();
            positions.clear();
            synchronizeCandidates();
        }

        private void synchronizeCandidates() {
            if (draggingCandidate) return;
            List<Candidate> source = owner.candidateSnapshot();
            if (sameOrder(source, display)) return;
            display.clear();
            display.addAll(source);
            positions.keySet().removeIf(id -> display.stream().noneMatch(candidate ->
                  id.equals(candidate.getCandidateId())));
            if (owner.selectedCandidateId() == null && !display.isEmpty()) {
                owner.selectCandidate(display.get(0).getCandidateId());
            }
        }

        @Override
        public void drawBackground(int mouseX, int mouseY, float partialTicks) {
            super.drawBackground(mouseX, mouseY, partialTicks);
            minecraft.renderEngine.bindTexture(SLOTS);
            GuiUtils.blit(relativeX, relativeY, 0, 0, COLUMNS * SLOT_SIZE,
                  ROWS * SLOT_SIZE, 288, 288);
            int first = firstVisibleIndex();
            int end = Math.min(display.size(), first + COLUMNS * ROWS);
            for (int index = first; index < end; index++) {
                Candidate candidate = display.get(index);
                int visible = index - first;
                float targetX = relativeX + visible % COLUMNS * SLOT_SIZE;
                float targetY = relativeY + visible / COLUMNS * SLOT_SIZE;
                AnimatedPosition position = positions.computeIfAbsent(
                      candidate.getCandidateId(), ignored ->
                            new AnimatedPosition(targetX, targetY));
                position.approach(targetX, targetY);
                if (draggingCandidate && candidate.getCandidateId().equals(
                      pressedCandidateId)) {
                    continue;
                }
                renderCandidate(candidate, Math.round(position.x), Math.round(position.y));
            }
            if (draggingCandidate) {
                Candidate dragged = candidateById(pressedCandidateId);
                if (dragged != null) {
                    renderCandidate(dragged, (int) dragMouseX - getGuiLeft() - 9,
                          (int) dragMouseY - getGuiTop() - 9);
                }
            }
        }

        private void renderCandidate(Candidate candidate, int x, int y) {
            boolean selected = candidate.getCandidateId().equals(owner.selectedCandidateId());
            if (selected) QIOGuiSelectionRenderer.draw(x, y, SLOT_SIZE, SLOT_SIZE);
            QIOWorkbenchGuiIngredientRenderer.render(gui(), candidate, x + 1, y + 1, 16);
            GuiUtils.fill(x + 13, y + 2, x + 17, y + 6,
                  candidate.isEnabled() ? ENABLED : DISABLED);
            MekanismRenderer.resetColor();
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            if (draggingCandidate) return;
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                int x = relativeX + slot % COLUMNS * SLOT_SIZE + 1;
                int y = relativeY + slot / COLUMNS * SLOT_SIZE + 1;
                GuiUtils.fill(x, y, x + 16, y + 16, GuiSlot.DEFAULT_HOVER_COLOR);
            }
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            if (draggingCandidate) return;
            Candidate candidate = candidateAt(mouseX, mouseY);
            if (candidate != null) {
                List<String> extra = java.util.Arrays.asList(
                      new TextComponentTranslation(candidate.isEnabled() ?
                                  "gui.mekanismqioprocessing.workbench_candidate_tooltip_enabled" :
                                  "gui.mekanismqioprocessing.workbench_candidate_tooltip_disabled",
                            candidate.getOrder() + 1).getFormattedText(),
                      new TextComponentTranslation(
                            "gui.mekanismqioprocessing.workbench_candidate_toggle_shortcut")
                            .getFormattedText());
                if (candidate.isVirtualFluid()) {
                    List<String> tooltip = QIOWorkbenchGuiIngredientRenderer.fluidTooltip(
                          candidate);
                    tooltip.addAll(extra);
                    displayTooltips(tooltip, mouseX, mouseY);
                } else {
                    gui().renderItemTooltipWithExtra(candidate.getDisplayStack(), mouseX,
                          mouseY, extra);
                }
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return isMouseOverCheckWindows(mouseX, mouseY) && scrollBar.adjustScroll(delta) ||
                  super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && draggingCandidate) {
                return cancelDrag();
            }
            if (button == 1 && GuiScreen.isShiftKeyDown() && checkWindows(mouseX, mouseY)) {
                Candidate candidate = candidateAt(mouseX, mouseY);
                if (candidate != null) {
                    owner.selectCandidate(candidate.getCandidateId());
                    if (!candidate.isEnabled() || enabledCandidateCount() > 1) {
                        owner.toggleCandidate(candidate.getCandidateId());
                    }
                    return true;
                }
            }
            if (button != 0 || !checkWindows(mouseX, mouseY)) return false;
            Candidate candidate = candidateAt(mouseX, mouseY);
            if (candidate == null) return super.mouseClicked(mouseX, mouseY, button);
            pressedCandidateId = candidate.getCandidateId();
            pressedIndex = indexOf(pressedCandidateId);
            targetIndex = pressedIndex;
            pressedX = dragMouseX = mouseX;
            pressedY = dragMouseY = mouseY;
            draggingCandidate = false;
            owner.selectCandidate(pressedCandidateId);
            return true;
        }

        private int enabledCandidateCount() {
            int enabled = 0;
            for (Candidate candidate : owner.candidateSnapshot()) {
                if (candidate.isEnabled()) enabled++;
            }
            return enabled;
        }

        @Override
        public void onDrag(double mouseX, double mouseY, double mouseXOld,
              double mouseYOld) {
            super.onDrag(mouseX, mouseY, mouseXOld, mouseYOld);
            if (pressedCandidateId == null) return;
            dragMouseX = mouseX;
            dragMouseY = mouseY;
            double dx = mouseX - pressedX;
            double dy = mouseY - pressedY;
            if (!draggingCandidate && dx * dx + dy * dy >= DRAG_THRESHOLD_SQUARED) {
                draggingCandidate = true;
            }
            if (!draggingCandidate) return;
            autoScroll(mouseY);
            int next = dropIndex(mouseX, mouseY);
            if (next >= 0 && next != targetIndex) {
                Candidate dragged = candidateById(pressedCandidateId);
                if (dragged != null) {
                    display.remove(dragged);
                    next = Math.max(0, Math.min(display.size(), next));
                    display.add(next, dragged);
                    targetIndex = next;
                }
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (pressedCandidateId != null && draggingCandidate && targetIndex >= 0 &&
                targetIndex != pressedIndex) {
                if (!owner.moveCandidateToIndex(pressedCandidateId, targetIndex)) {
                    display.clear();
                    display.addAll(owner.candidateSnapshot());
                }
            }
            clearDragState();
            super.onRelease(mouseX, mouseY);
        }

        private void autoScroll(double mouseY) {
            long now = GuiElement.getMillis();
            if (now - lastAutoScrollAt < 90) return;
            if (mouseY < getY() + 8) {
                if (scrollBar.adjustScroll(1)) lastAutoScrollAt = now;
            } else if (mouseY >= getY() + height - 8) {
                if (scrollBar.adjustScroll(-1)) lastAutoScrollAt = now;
            }
        }

        private int dropIndex(double mouseX, double mouseY) {
            if (display.isEmpty()) return -1;
            int column = Math.max(0, Math.min(COLUMNS - 1,
                  (int) ((mouseX - getX()) / SLOT_SIZE)));
            int row = Math.max(0, Math.min(ROWS - 1,
                  (int) ((mouseY - getY()) / SLOT_SIZE)));
            return Math.min(display.size() - 1, firstVisibleIndex() + row * COLUMNS + column);
        }

        private int firstVisibleIndex() {
            return scrollBar.getCurrentSelection() * COLUMNS;
        }

        private int slotAt(double mouseX, double mouseY) {
            int column = (int) ((mouseX - getX()) / SLOT_SIZE);
            int row = (int) ((mouseY - getY()) / SLOT_SIZE);
            if (column < 0 || row < 0 || column >= COLUMNS || row >= ROWS) return -1;
            int x = getX() + column * SLOT_SIZE + 1;
            int y = getY() + row * SLOT_SIZE + 1;
            if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) return -1;
            int index = firstVisibleIndex() + row * COLUMNS + column;
            return index < display.size() ? row * COLUMNS + column : -1;
        }

        @Nullable
        private Candidate candidateAt(double mouseX, double mouseY) {
            int slot = slotAt(mouseX, mouseY);
            if (slot < 0) return null;
            int index = firstVisibleIndex() + slot;
            return index >= 0 && index < display.size() ? display.get(index) : null;
        }

        @Nullable
        private Candidate candidateById(@Nullable String id) {
            if (id == null) return null;
            for (Candidate candidate : display) {
                if (id.equals(candidate.getCandidateId())) return candidate;
            }
            return null;
        }

        private int indexOf(@Nullable String id) {
            if (id == null) return -1;
            for (int index = 0; index < display.size(); index++) {
                if (id.equals(display.get(index).getCandidateId())) return index;
            }
            return -1;
        }

        private boolean cancelDrag() {
            if (pressedCandidateId == null) return false;
            display.clear();
            display.addAll(owner.candidateSnapshot());
            clearDragState();
            return true;
        }

        private void clearDragState() {
            pressedCandidateId = null;
            pressedIndex = -1;
            targetIndex = -1;
            draggingCandidate = false;
        }

        private boolean sameOrder(List<Candidate> left, List<Candidate> right) {
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                Candidate a = left.get(index);
                Candidate b = right.get(index);
                if (!a.getCandidateId().equals(b.getCandidateId()) ||
                    a.isEnabled() != b.isEnabled() || a.getOrder() != b.getOrder()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class AnimatedPosition {

        private float x;
        private float y;

        private AnimatedPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        private void approach(float targetX, float targetY) {
            x += (targetX - x) * 0.42F;
            y += (targetY - y) * 0.42F;
            if (Math.abs(targetX - x) < 0.1F) x = targetX;
            if (Math.abs(targetY - y) < 0.1F) y = targetY;
        }
    }
}
