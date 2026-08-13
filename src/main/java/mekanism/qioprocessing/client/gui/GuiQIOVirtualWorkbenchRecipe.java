package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.qioprocessing.common.content.processor.QIOProcessorDisplaySnapshot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Read-only 3x3 workbench pattern, progress arrow and large primary output. */
final class GuiQIOVirtualWorkbenchRecipe extends GuiElement implements IJEIIngredientHelper {

    static final int WIDTH = 136;
    static final int COMPACT_WIDTH = 120;
    static final int HEIGHT = 54;
    private static final int GRID_WIDTH = 54;
    private static final int NORMAL_GRID_X = 24;
    private static final int COMPACT_GRID_X = 0;
    private static final int NORMAL_PROGRESS_X = 82;
    private static final int COMPACT_PROGRESS_X = 64;
    private static final int PROGRESS_Y = 22;
    private static final int OUTPUT_X = 117;
    private static final int COMPACT_OUTPUT_X = 102;
    private static final int COMPACT_OUTPUT_Y = 18;
    private static final int OUTPUT_Y = 18;

    private final Supplier<QIOProcessorDisplaySnapshot.Lane> laneSupplier;
    private final boolean compact;

    GuiQIOVirtualWorkbenchRecipe(IGuiWrapper gui, int x, int y,
          Supplier<QIOProcessorDisplaySnapshot.Lane> laneSupplier,
          DoubleSupplier progressSupplier) {
        this(gui, x, y, laneSupplier, progressSupplier, false);
    }

    GuiQIOVirtualWorkbenchRecipe(IGuiWrapper gui, int x, int y,
          Supplier<QIOProcessorDisplaySnapshot.Lane> laneSupplier,
          DoubleSupplier progressSupplier, boolean compact) {
        super(gui, x, y, compact ? COMPACT_WIDTH : WIDTH, HEIGHT);
        this.laneSupplier = Objects.requireNonNull(laneSupplier, "laneSupplier");
        Objects.requireNonNull(progressSupplier, "progressSupplier");
        this.compact = compact;
        for (int slot = 0; slot < 9; slot++) {
            final int index = slot;
            int gridX = compact ? COMPACT_GRID_X : NORMAL_GRID_X;
            addChild(displaySlot(gui, relativeX + gridX + slot % 3 * 18,
                  relativeY + slot / 3 * 18, () -> gridStack(index)));
        }
        addChild(new GuiProgress(progressSupplier::getAsDouble, ProgressType.RIGHT,
              gui, relativeX + (compact ? COMPACT_PROGRESS_X : NORMAL_PROGRESS_X), relativeY + PROGRESS_Y)
              .recipeViewerCrafting());
        int outputX = compact ? COMPACT_OUTPUT_X : OUTPUT_X;
        int outputY = compact ? COMPACT_OUTPUT_Y : OUTPUT_Y;
        addChild(new GuiSlot(SlotType.OUTPUT, gui, relativeX + outputX,
              relativeY + outputY) {
            @Override
            protected void drawContents() {
                ItemStack output = outputStack();
                if (!output.isEmpty()) {
                    gui().renderItem(output, relativeX + 1, relativeY + 1);
                }
            }

            @Override
            public void renderToolTip(int mouseX, int mouseY) {
                super.renderToolTip(mouseX, mouseY);
                ItemStack output = outputStack();
                if (!output.isEmpty() && isMouseOverTooltip(mouseX, mouseY)) {
                    gui().renderItemTooltip(output, mouseX, mouseY);
                }
            }
        }.setRenderHover(true));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        double localX = mouseX - getGuiLeft() - relativeX;
        double localY = mouseY - getGuiTop() - relativeY;
        int gridX = compact ? COMPACT_GRID_X : NORMAL_GRID_X;
        if (localX >= gridX && localX < gridX + GRID_WIDTH && localY >= 0 && localY < HEIGHT) {
            int column = (int) (localX - gridX) / 18;
            int row = (int) localY / 18;
            ItemStack stack = gridStack(row * 3 + column);
            return stack.isEmpty() ? null : stack;
        }
        int outputWidth = 18;
        int outputHeight = 18;
        int outputX = compact ? COMPACT_OUTPUT_X : OUTPUT_X;
        int outputY = compact ? COMPACT_OUTPUT_Y : OUTPUT_Y;
        if (localX >= outputX && localX < outputX + outputWidth &&
              localY >= outputY && localY < outputY + outputHeight) {
            ItemStack output = outputStack();
            return output.isEmpty() ? null : output;
        }
        return null;
    }

    private GuiSlot displaySlot(IGuiWrapper gui, int x, int y,
          Supplier<ItemStack> stackSupplier) {
        return new GuiSlot(SlotType.NORMAL, gui, x, y) {
            @Override
            public void renderToolTip(int mouseX, int mouseY) {
                super.renderToolTip(mouseX, mouseY);
                ItemStack stack = stackSupplier.get();
                if (!stack.isEmpty() && isMouseOverTooltip(mouseX, mouseY)) {
                    gui().renderItemTooltip(stack, mouseX, mouseY);
                }
            }
        }.stored(stackSupplier).setRenderHover(true);
    }

    private ItemStack gridStack(int slot) {
        QIOProcessorDisplaySnapshot.Lane lane = laneSupplier.get();
        return lane == null ? ItemStack.EMPTY : lane.getGridStack(slot);
    }

    private ItemStack outputStack() {
        QIOProcessorDisplaySnapshot.Lane lane = laneSupplier.get();
        return lane == null ? ItemStack.EMPTY : lane.getOutput();
    }
}
