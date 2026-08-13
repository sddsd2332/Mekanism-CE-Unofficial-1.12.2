package mekanism.qioprocessing.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.window.GuiWindowCreatorTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Locked-until-bound side tab for the frequency workbench editor. */
public final class GuiQIOWorkbenchConfigurationTab extends
      GuiWindowCreatorTab<Void, GuiQIOWorkbenchConfigurationTab> {

    private static final ResourceLocation ICON = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_BUTTON, "recipe_viewer_frequency.png");

    private final QIOWorkbenchConfigurationContainer container;
    private boolean windowOpen;
    private boolean wasAvailable;

    public GuiQIOWorkbenchConfigurationTab(IGuiWrapper gui,
          QIOWorkbenchConfigurationContainer container,
          Supplier<GuiQIOWorkbenchConfigurationTab> self) {
        super(ICON, gui, null, 32, true, self);
        this.container = container;
        active = available();
    }

    @Override
    public void tick() {
        super.tick();
        boolean available = available();
        if (available && !wasAvailable) {
            openPinnedWindows();
        }
        wasAvailable = available;
        windowOpen = hasOpenWindow();
        active = available && !windowOpen;
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (available()) {
            super.onClick(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean isMouseOverTooltip(double mouseX, double mouseY) {
        return visible && mouseX >= getX() + border && mouseX < getRight() - border &&
              mouseY >= getY() + border && mouseY < getBottom() - border;
    }

    @Override
    public void openPinnedWindows() {
        if (available() && !hasOpenWindow()) {
            super.openPinnedWindows();
            if (!hasOpenWindow() && hasPinnedEditorWindow()) {
                openWindow(getNextWindowData());
            }
        }
    }

    @Override
    protected void disableTab() {
        windowOpen = true;
        super.disableTab();
    }

    @Override
    protected Consumer<GuiWindow> getCloseListener() {
        return window -> {
            GuiQIOWorkbenchConfigurationTab tab = getElementSupplier().get();
            tab.windowOpen = false;
            tab.active = tab.available();
        };
    }

    @Override
    protected Consumer<GuiWindow> getReAttachListener() {
        return window -> {
            GuiQIOWorkbenchConfigurationTab tab = getElementSupplier().get();
            tab.windowOpen = true;
            tab.disableTab();
        };
    }

    @Override
    protected GuiWindow createWindow(SelectedWindowData windowData) {
        return new GuiQIOWorkbenchConfigurationWindow(gui(),
              (getGuiWidth() - GuiQIOWorkbenchConfigurationWindow.WIDTH) / 2, 15,
              container, windowData);
    }

    @Override
    protected SelectedWindowData getNextWindowData() {
        return new SelectedWindowData(
              QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_CONFIGURATION);
    }

    @Override
    protected List<SelectedWindowData> getValidWindows() {
        return available() ? Collections.singletonList(getNextWindowData()) :
              Collections.emptyList();
    }

    @Override
    @Nullable
    protected Integer getTabColor() {
        return SpecialColors.TAB_CRAFTING_WINDOW.argb();
    }

    @Override
    protected ITextComponent getTooltipText() {
        return new TextComponentTranslation(available() ?
              "gui.mekanismqioprocessing.workbench_configuration_tab" :
              "gui.mekanismqioprocessing.bind_frequency_first");
    }

    private boolean available() {
        QIOProcessingTerminalContainerState state = container.getTerminalState();
        return state.isValid() && state.getFrequencyUUID() != null;
    }

    private boolean hasOpenWindow() {
        return gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.getWindows().stream()
              .anyMatch(window -> window instanceof GuiQIOWorkbenchConfigurationWindow);
    }

    private boolean hasPinnedEditorWindow() {
        return new SelectedWindowData(
              QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_PATTERN).wasPinned() ||
              new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_BATCH).wasPinned() ||
              new SelectedWindowData(
                    QIOProcessingWindowTypes.MANAGEMENT_WORKBENCH_CANDIDATES).wasPinned();
    }
}
