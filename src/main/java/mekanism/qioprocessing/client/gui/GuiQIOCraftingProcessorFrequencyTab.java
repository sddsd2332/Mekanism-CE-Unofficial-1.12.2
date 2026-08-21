package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.ContainerQIOCraftingProcessor;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.function.Supplier;

/** Frequency tab for QIO workbench processing blocks. */
/**
 * QIO 处理模块中的 GuiQIOCraftingProcessorFrequencyTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOCraftingProcessorFrequencyTab extends
      GuiQIOFrequencyTab<ContainerQIOCraftingProcessor> {

    public GuiQIOCraftingProcessorFrequencyTab(IGuiWrapper gui,
          ContainerQIOCraftingProcessor container,
          Supplier<? extends GuiQIOFrequencyTab<?>> tabSupplier) {
        super(gui, container, tabSupplier, new SelectedWindowData(
              QIOProcessingWindowTypes.CRAFTING_PROCESSOR_FREQUENCY));
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        displayTooltip(new TextComponentTranslation(
              "gui.mekanismqioprocessing.crafting_processor_frequency"), mouseX, mouseY);
    }

    @Override
    protected GuiQIOFrequencySelectWindow createWindow(SelectedWindowData windowData) {
        return new GuiQIOProcessingTerminalFrequencyWindow(gui(), dataSource,
              windowData);
    }
}
