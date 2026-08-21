package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.text.TextUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.content.processor.QIOProcessorDisplaySnapshot;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Movable display window for one factory crafting lane. */
/**
 * QIO 处理模块中的 GuiQIOCraftingProcessorRecipeWindow 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class GuiQIOCraftingProcessorRecipeWindow extends GuiWindow {

    private static final int WIDTH = 136;
    private static final int HEIGHT = 119;

    private final QIOCraftingProcessor processor;
    private final Runnable closeHandler;
    private final int lane;
    private boolean closed;

    GuiQIOCraftingProcessorRecipeWindow(IGuiWrapper gui, int x, int y,
          QIOCraftingProcessor processor, int lane, Runnable closeHandler) {
        super(gui, x, y, WIDTH, HEIGHT, new SelectedWindowData(
              QIOProcessingWindowTypes.CRAFTING_PROCESSOR_RECIPE, (byte) lane));
        this.processor = Objects.requireNonNull(processor, "processor");
        this.closeHandler = Objects.requireNonNull(closeHandler, "closeHandler");
        if (lane < 0 || lane >= processor.getVisibleLaneCount()) {
            throw new IllegalArgumentException("QIO crafting processor lane is invalid");
        }
        this.lane = lane;
        interactionStrategy = InteractionStrategy.ALL;
        addChild(new GuiQIOVirtualWorkbenchRecipe(gui, relativeX + 8,
              relativeY + 27, this::displayLane,
              () -> processor.getScaledLaneProgress(this.lane), true));
        addChild(new GuiInnerScreen(gui, relativeX + 6, relativeY + 84,
              WIDTH - 12, 29, this::laneInformation).clearFormat().padding(3)
              .clearSpacing().textScale(0.75F));
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation(
              "gui.mekanismqioprocessing.processor_lane_recipe_title", lane + 1), 5);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeHandler.run();
        super.close();
    }

    private QIOProcessorDisplaySnapshot.Lane displayLane() {
        return processor.getLaneDisplay(lane);
    }

    private List<ITextComponent> laneInformation() {
        QIOProcessorDisplaySnapshot.Lane display = displayLane();
        ITextComponent state = QIOProcessorGuiText.laneState(
              display == null ? null : display.getState());
        long batch = display == null ? 0 : display.getOperationCount();
        return Arrays.asList(
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.processor_lane_state_batch",
                    state, TextUtils.format(batch)),
              new TextComponentTranslation(
                    "gui.mekanismqioprocessing.processor_lane_progress",
                    TextUtils.format(processor.getLaneCurrentTicks(lane)),
                    TextUtils.format(processor.getLaneTotalTicks(lane))));
    }
}
