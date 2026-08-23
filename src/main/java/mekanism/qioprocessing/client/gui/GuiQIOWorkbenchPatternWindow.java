package mekanism.qioprocessing.client.gui;

import mekanism.api.functions.TriPredicate;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostItemConsumer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationClientCache;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Fixed 3x3 ghost-grid editor with a calculated output and real player inventory access. */
/**
 * QIO 处理模块中的 GuiQIOWorkbenchPatternWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOWorkbenchPatternWindow extends GuiWindow {

    private static final int WIDTH = 198;
    private static final int HEIGHT = 136;
    private static final int WINDOW_Y = 33;
    private static final int GRID_X = 50;
    private static final int GRID_Y = 24;
    private static final int OUTPUT_X = 122;
    private static final int OUTPUT_Y = 42;
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
    private List<ItemStack> lastOutputGrid = Collections.emptyList();
    private ItemStack output = ItemStack.EMPTY;

    public GuiQIOWorkbenchPatternWindow(IGuiWrapper gui,
          QIOWorkbenchConfigurationClientCache cache,
          TriPredicate<List<ItemStack>, QIOWorkbenchClosureMode, Boolean> encoder,
          Runnable focusHandler, Runnable closeHandler) {
        super(gui, (gui.getWidth() - WIDTH) / 2, WINDOW_Y, WIDTH, HEIGHT,
              new SelectedWindowData(QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_PATTERN));
        this.cache = Objects.requireNonNull(cache, "cache");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.focusHandler = Objects.requireNonNull(focusHandler, "focusHandler");
        this.closeHandler = Objects.requireNonNull(closeHandler, "closeHandler");
        interactionStrategy = InteractionStrategy.ALL;
        for (int slot = 0; slot < 9; slot++) {
            final int index = slot;
            GuiSlot guiSlot = new GuiSlot(SlotType.NORMAL, gui,
                  relativeX + GRID_X + slot % 3 * 18,
                  relativeY + GRID_Y + slot / 3 * 18) {
                @Override
                public void renderToolTip(int mouseX, int mouseY) {
                    super.renderToolTip(mouseX, mouseY);
                    ItemStack stack = GuiQIOWorkbenchPatternWindow.this.cache
                          .getEncodingSlot(index);
                    if (!stack.isEmpty() && isMouseOverTooltip(mouseX, mouseY)) {
                        gui.renderItemTooltip(stack, mouseX, mouseY);
                    }
                }
            }.stored(() -> this.cache.getEncodingSlot(index)).setRenderHover(true)
                  .click((element, mouseX, mouseY) -> setFromCarried(index));
            guiSlot.setGhostHandler(new IGhostItemConsumer() {
                @Override
                public void accept(Object ingredient) {
                    ItemStack stack = supportedTarget(ingredient);
                    if (stack != null) {
                        GuiQIOWorkbenchPatternWindow.this.cache.setEncodingSlot(index, stack);
                    }
                }
            });
            addChild(guiSlot);
        }
        addChild(new GuiSlot(SlotType.NORMAL, gui, relativeX + OUTPUT_X,
              relativeY + OUTPUT_Y) {
            @Override
            public void renderToolTip(int mouseX, int mouseY) {
                super.renderToolTip(mouseX, mouseY);
                if (!output.isEmpty() && isMouseOverTooltip(mouseX, mouseY)) {
                    gui.renderItemTooltip(output, mouseX, mouseY);
                }
            }
        }.stored(() -> output).setRenderHover(true));
        closureModeButton = addChild(new MekanismButton(gui, relativeX + 10,
              relativeY + 86, 87, 20, closureModeText(), this::cycleClosureMode, null));
        cycleFilterButton = addChild(new MekanismButton(gui, relativeX + 101,
              relativeY + 86, 87, 20, cycleFilterText(), this::toggleCycleFilter,
              getOnHover(() -> new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_cycle_filter_tooltip"))));
        addChild(new MekanismButton(gui, relativeX + 10, relativeY + 110, 82, 20,
              new TextComponentTranslation("gui.mekanismqioprocessing.workbench_pattern_clear"),
              cache::clearEncodingGrid, null));
        encodeButton = addChild(new MekanismButton(gui, relativeX + 96, relativeY + 110,
              92, 20, new TextComponentTranslation(
                    "gui.mekanismqioprocessing.workbench_pattern_encode"), this::encode, null));
        refreshOutput();
        updateButton();
    }

    @Override
    public void tick() {
        super.tick();
        refreshOutput();
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
              "gui.mekanismqioprocessing.workbench_pattern_title"), 5);
        getFont().drawString(">", relativeX + 108, relativeY + 48, titleTextColor());
    }

    private boolean setFromCarried(int slot) {
        ItemStack carried = minecraft.player == null ? ItemStack.EMPTY :
              minecraft.player.inventory.getItemStack();
        cache.setEncodingSlot(slot, carried);
        return true;
    }

    private void encode() {
        if (cache.hasEncodingInput() && !output.isEmpty() &&
            encoder.test(cache.getEncodingGrid(), closureMode, skipCyclicRecipes)) {
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
        encodeButton.active = cache.hasEncodingInput() && !output.isEmpty();
        cycleFilterButton.active = closureMode != QIOWorkbenchClosureMode.NONE;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeHandler.run();
        super.close();
    }

    private void refreshOutput() {
        List<ItemStack> grid = cache.getEncodingGrid();
        if (sameGrid(lastOutputGrid, grid)) return;
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(stack -> copy.add(stack.copy()));
        lastOutputGrid = Collections.unmodifiableList(copy);
        output = calculateOutput(grid);
    }

    private ItemStack calculateOutput(List<ItemStack> grid) {
        if (minecraft.world == null || grid.size() != 9 ||
            grid.stream().allMatch(ItemStack::isEmpty)) return ItemStack.EMPTY;
        ItemStack exact = calculateOutputForGrid(grid);
        if (!exact.isEmpty()) return exact;
        // Some integrations attach a context-only ForgeCaps payload while an item is held. Try
        // the same recipe with a fresh stack that preserves ordinary NBT but has no serialized
        // capability state, so the encode control remains usable for the server-side fallback.
        List<ItemStack> neutral = new ArrayList<>(grid.size());
        for (ItemStack stack : grid) {
            ItemStack copy = withoutSerializedCapabilities(stack);
            neutral.add(copy);
        }
        return calculateOutputForGrid(neutral);
    }

    private ItemStack calculateOutputForGrid(List<ItemStack> grid) {
        try {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            for (int slot = 0; slot < 9; slot++) {
                inventory.setInventorySlotContents(slot, grid.get(slot).copy());
            }
            IRecipe recipe = CraftingManager.findMatchingRecipe(inventory, minecraft.world);
            if (recipe == null) return ItemStack.EMPTY;
            ItemStack result = recipe.getCraftingResult(inventory);
            return result == null ? ItemStack.EMPTY : result.copy();
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack withoutSerializedCapabilities(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        try {
            ItemStack copy = new ItemStack(stack.getItem(), stack.getCount(), stack.getMetadata());
            NBTTagCompound tag = stack.getTagCompound();
            copy.setTagCompound(tag == null ? null : tag.copy());
            return copy;
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean sameGrid(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int slot = 0; slot < first.size(); slot++) {
            if (!ItemStack.areItemStacksEqual(first.get(slot), second.get(slot))) return false;
        }
        return true;
    }
}
