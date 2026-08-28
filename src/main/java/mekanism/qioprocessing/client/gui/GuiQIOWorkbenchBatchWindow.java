package mekanism.qioprocessing.client.gui;

import mekanism.api.functions.TriPredicate;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.scroll.GuiScrollBar;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostItemConsumer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationClientCache;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Scrollable exact-output target list for one atomic batch workbench import. */
/**
 * QIO 处理模块中的 GuiQIOWorkbenchBatchWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOWorkbenchBatchWindow extends GuiWindow {

    private static final int WIDTH = 214;
    private static final int HEIGHT = 178;
    private static final int WINDOW_Y = 43;
    private final QIOWorkbenchConfigurationClientCache cache;
    private final TriPredicate<List<ItemStack>, QIOWorkbenchClosureMode, Boolean> encoder;
    private final Runnable focusHandler;
    private final Runnable closeHandler;
    private final MekanismButton encodeButton;
    private final MekanismButton closureModeButton;
    private final MekanismButton cycleFilterButton;
    private QIOWorkbenchClosureMode closureMode = QIOWorkbenchClosureMode.NONE;
    private boolean skipCyclicRecipes;
    private boolean closed;

    public GuiQIOWorkbenchBatchWindow(IGuiWrapper gui,
          QIOWorkbenchConfigurationClientCache cache,
          TriPredicate<List<ItemStack>, QIOWorkbenchClosureMode, Boolean> encoder,
          Runnable focusHandler,
          Runnable closeHandler) {
        super(gui, (gui.getWidth() - WIDTH) / 2, WINDOW_Y, WIDTH, HEIGHT,
              new SelectedWindowData(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_BATCH));
        this.cache = Objects.requireNonNull(cache, "cache");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.focusHandler = Objects.requireNonNull(focusHandler, "focusHandler");
        this.closeHandler = Objects.requireNonNull(closeHandler, "closeHandler");
        interactionStrategy = InteractionStrategy.ALL;
        addChild(new TargetGrid(gui, relativeX + 8, relativeY + 24));
        closureModeButton = addChild(new MekanismButton(gui, relativeX + 8,
              relativeY + 126, 97, 20, closureModeText(), this::cycleClosureMode, null));
        cycleFilterButton = addChild(new MekanismButton(gui, relativeX + 109,
              relativeY + 126, 97, 20, cycleFilterText(), this::toggleCycleFilter,
              getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_cycle_filter_tooltip"))));
        addChild(new MekanismButton(gui, relativeX + 8, relativeY + 150, 82, 20,
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_batch_clear"),
              cache::clearBatchTargets, null));
        encodeButton = addChild(new MekanismButton(gui, relativeX + 94,
              relativeY + 150, 112, 20, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_batch_encode"), this::encode, null));
        updateButton();
    }

    @Override
    public void tick() {
        super.tick();
        updateButton();
    }

    @Override
    public void onFocused() {
        focusHandler.run();
        super.onFocused();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_batch_title"), 5);
        getFont().drawString(new TextComponentTranslation(
              "gui.mekanismqioprocessing.workbench_batch_count",
              cache.getBatchTargetCount(),
              QIOWorkbenchConfigurationClientCache.MAX_BATCH_TARGETS).getFormattedText(),
              relativeX + 8, relativeY + 115, titleTextColor());
    }

    private void encode() {
        if (cache.hasBatchTargets() && encoder.test(cache.getBatchTargets(), closureMode,
              skipCyclicRecipes)) {
            close();
        }
    }

    private void cycleClosureMode() {
        closureMode = closureMode.next();
        closureModeButton.setMessage(closureModeText());
        updateButton();
    }

    private void toggleCycleFilter() {
        if (closureMode == QIOWorkbenchClosureMode.NONE) return;
        skipCyclicRecipes = !skipCyclicRecipes;
        cycleFilterButton.setMessage(cycleFilterText());
    }

    private TextComponentTranslation closureModeText() {
        return new TextComponentTranslation(switch (closureMode) {
            case NONE -> "gui.mekanismqioprocessing.workbench_closure_none";
            case PREFERRED -> "gui.mekanismqioprocessing.workbench_closure_preferred";
            case ALL -> "gui.mekanismqioprocessing.workbench_closure_all";
        });
    }

    private TextComponentTranslation cycleFilterText() {
        return new TextComponentTranslation(skipCyclicRecipes ?
              "gui.mekanismqioprocessing.workbench_cycle_filter_on" :
              "gui.mekanismqioprocessing.workbench_cycle_filter_off");
    }

    private void updateButton() {
        boolean recursive = MekanismConfig.current().qioProcessing.recipeCatalogScanMode
              .val().allowsRecursiveImport();
        if (!recursive) {
            closureMode = QIOWorkbenchClosureMode.NONE;
            skipCyclicRecipes = false;
        }
        closureModeButton.visible = recursive;
        cycleFilterButton.visible = recursive;
        encodeButton.active = cache.hasBatchTargets();
        cycleFilterButton.active = recursive &&
              closureMode != QIOWorkbenchClosureMode.NONE;
    }

    private ItemStack playerStack(int index) {
        return minecraft.player == null || index < 0 ||
              index >= minecraft.player.inventory.mainInventory.size() ? ItemStack.EMPTY :
              minecraft.player.inventory.mainInventory.get(index);
    }

    boolean inventoryClicked(int index, int button) {
        if (button != 0 || !GuiScreen.isShiftKeyDown()) return false;
        ItemStack stack = playerStack(index);
        if (!stack.isEmpty() && cache.addBatchTarget(stack)) {
            playClickSound();
        }
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeHandler.run();
        super.close();
    }

    private final class TargetGrid extends GuiElement
          implements IJEIIngredientHelper, IRecipeViewerGhostTarget {

        private static final int COLUMNS = 10;
        private static final int ROWS = 5;
        private static final ResourceLocation SLOTS = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots.png");
        private static final ResourceLocation SLOTS_DARK = MekanismUtils.getResource(
              MekanismUtils.ResourceType.GUI_SLOT, "slots_dark.png");
        private final GuiScrollBar scrollBar;

        private TargetGrid(IGuiWrapper gui, int x, int y) {
            super(gui, x, y, COLUMNS * 18 + 18, ROWS * 18);
            scrollBar = addChild(new GuiScrollBar(gui, relativeX + COLUMNS * 18 + 4,
                  relativeY, ROWS * 18, this::totalRows, () -> ROWS));
            active = true;
        }

        @Override
        public void drawBackground(int mouseX, int mouseY, float partialTicks) {
            super.drawBackground(mouseX, mouseY, partialTicks);
            minecraft.renderEngine.bindTexture(cache.hasBatchTargets() ? SLOTS : SLOTS_DARK);
            GuiUtils.blit(relativeX, relativeY, 0, 0, COLUMNS * 18, ROWS * 18,
                  288, 288);
            int first = firstVisibleIndex();
            for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
                ItemStack stack = cache.getBatchTarget(first + slot);
                if (!stack.isEmpty()) {
                    gui().renderItem(stack, relativeX + slot % COLUMNS * 18 + 1,
                          relativeY + slot / COLUMNS * 18 + 1);
                }
            }
        }

        @Override
        public void renderForeground(int mouseX, int mouseY) {
            super.renderForeground(mouseX, mouseY);
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                int x = relativeX + slot % COLUMNS * 18 + 1;
                int y = relativeY + slot / COLUMNS * 18 + 1;
                GuiUtils.fill(x, y, x + 16, y + 16, GuiSlot.DEFAULT_HOVER_COLOR);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return isMouseOverCheckWindows(mouseX, mouseY) &&
                  scrollBar.adjustScroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !checkWindows(mouseX, mouseY)) return false;
            int slot = slotAt(mouseX, mouseY);
            if (slot < 0) return super.mouseClicked(mouseX, mouseY, button);
            int index = firstVisibleIndex() + slot;
            boolean changed;
            if (index < cache.getBatchTargetCount()) {
                changed = cache.removeBatchTarget(index);
            } else {
                ItemStack carried = minecraft.player == null ? ItemStack.EMPTY :
                      minecraft.player.inventory.getItemStack();
                changed = cache.addBatchTarget(carried);
            }
            if (changed) playClickSound();
            return changed;
        }

        @Override
        public void renderToolTip(int mouseX, int mouseY) {
            super.renderToolTip(mouseX, mouseY);
            ItemStack stack = stackAt(mouseX, mouseY);
            if (!stack.isEmpty()) gui().renderItemTooltip(stack, mouseX, mouseY);
        }

        @Nullable
        @Override
        public Object getIngredient(double mouseX, double mouseY) {
            ItemStack stack = stackAt(mouseX, mouseY);
            return stack.isEmpty() ? null : stack;
        }

        @Nullable
        @Override
        public IGhostIngredientConsumer getGhostHandler() {
            if (cache.getBatchTargetCount() >=
                QIOWorkbenchConfigurationClientCache.MAX_BATCH_TARGETS) return null;
            return new IGhostItemConsumer() {
                @Override
                public void accept(Object ingredient) {
                    ItemStack stack = supportedTarget(ingredient);
                    if (stack != null) cache.addBatchTarget(stack);
                }
            };
        }

        private int totalRows() {
            return (cache.getBatchTargetCount() + COLUMNS - 1) / COLUMNS;
        }

        private int firstVisibleIndex() {
            return scrollBar.getCurrentSelection() * COLUMNS;
        }

        private ItemStack stackAt(double mouseX, double mouseY) {
            int slot = slotAt(mouseX, mouseY);
            return slot < 0 ? ItemStack.EMPTY :
                  cache.getBatchTarget(firstVisibleIndex() + slot);
        }

        private int slotAt(double mouseX, double mouseY) {
            if (mouseX < getX() || mouseY < getY() ||
                mouseX >= getX() + COLUMNS * 18 ||
                mouseY >= getY() + ROWS * 18 || !checkWindows(mouseX, mouseY)) return -1;
            int slotX = (int) ((mouseX - getX()) / 18);
            int slotY = (int) ((mouseY - getY()) / 18);
            int startX = getX() + slotX * 18 + 1;
            int startY = getY() + slotY * 18 + 1;
            if (mouseX < startX || mouseX >= startX + 16 || mouseY < startY ||
                mouseY >= startY + 16) return -1;
            return slotY * COLUMNS + slotX;
        }
    }
}
