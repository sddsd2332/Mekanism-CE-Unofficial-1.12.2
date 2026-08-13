package mekanism.qioprocessing.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;

import java.util.function.Supplier;

/** Frequency selector tab using the terminal session-bound binding protocol. */
public final class GuiQIOProcessingTerminalFrequencyTab extends
      GuiQIOFrequencyTab<QIOProcessingTerminalFrequencyContainer> {

    public GuiQIOProcessingTerminalFrequencyTab(IGuiWrapper gui,
          QIOProcessingTerminalFrequencyContainer container,
          Supplier<? extends GuiQIOFrequencyTab<?>> tabSupplier) {
        super(gui, container, tabSupplier, new SelectedWindowData(
              QIOProcessingWindowTypes.TERMINAL_FREQUENCY));
    }

    @Override
    protected GuiQIOFrequencySelectWindow createWindow(SelectedWindowData windowData) {
        return new GuiQIOProcessingTerminalFrequencyWindow(gui(), dataSource,
              windowData);
    }
}
