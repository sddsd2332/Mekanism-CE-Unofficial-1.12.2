package mekanism.qioprocessing.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.common.QIOProcessingWindowTypes;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.machine.QIOAutomationContainerState;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.function.Supplier;

/** Generic machine tab for binding an installed QIO automation upgrade. */
/**
 * QIO 处理模块中的 GuiQIOAutomationFrequencyTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOAutomationFrequencyTab extends GuiInsetElement<QIOAutomationContainerState> {

    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(
          QIOProcessingWindowTypes.AUTOMATION_FREQUENCY);
    private final TileEntityContainerBlock tile;
    private final MekanismTileContainer<?> container;
    private final Supplier<GuiQIOAutomationFrequencyTab> elementSupplier;
    private boolean pendingPinnedOpen;
    private boolean pinnedChecked;
    private boolean windowOpen;

    public GuiQIOAutomationFrequencyTab(IGuiWrapper gui, TileEntityContainerBlock tile,
          MekanismTileContainer<?> container, QIOAutomationContainerState state,
          Supplier<GuiQIOAutomationFrequencyTab> elementSupplier) {
        super(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "frequency.png"),
              gui, state, gui.getWidth() + 24, 6, 26, 18, false);
        this.tile = tile;
        this.container = container;
        this.elementSupplier = elementSupplier;
        visible = shouldShow();
    }

    @Override
    public void tick() {
        super.tick();
        visible = shouldShow();
        windowOpen = hasOpenWindow();
        active = visible && !windowOpen;
        if (!visible) {
            pendingPinnedOpen = false;
        } else if (pendingPinnedOpen && !windowOpen) {
            pendingPinnedOpen = false;
            openWindow();
        }
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_QIO_FREQUENCY.argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(new TextComponentTranslation("gui.mekanismqioprocessing.automation_frequency"),
              mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        openWindow();
    }

    @Override
    public void openPinnedWindows() {
        super.openPinnedWindows();
        if (!pinnedChecked) {
            pinnedChecked = true;
            pendingPinnedOpen = WINDOW_DATA.wasPinned();
            if (visible && pendingPinnedOpen) {
                pendingPinnedOpen = false;
                openWindow();
            }
        }
    }

    private void openWindow() {
        if (!shouldShow() || hasOpenWindow()) {
            return;
        }
        pendingPinnedOpen = false;
        GuiQIOAutomationFrequencyWindow window = new GuiQIOAutomationFrequencyWindow(gui(), tile,
              container, dataSource, WINDOW_DATA);
        window.setTabListeners(closed -> {
            GuiQIOAutomationFrequencyTab tab = elementSupplier.get();
            tab.windowOpen = false;
            tab.active = tab.shouldShow();
        }, reattached -> {
            GuiQIOAutomationFrequencyTab tab = elementSupplier.get();
            tab.windowOpen = true;
            tab.active = false;
        });
        windowOpen = true;
        active = false;
        gui().addWindow(window);
    }

    private boolean hasOpenWindow() {
        return gui() instanceof GuiMekanism<?> mekanismGui && mekanismGui.getWindows().stream()
              .anyMatch(window -> window instanceof GuiQIOAutomationFrequencyWindow frequencyWindow &&
                    frequencyWindow.isFor(tile));
    }

    private boolean shouldShow() {
        if (dataSource.getMode() == null ||
            dataSource.getState() ==
                  mekanism.qioprocessing.api.machine.QIOAutomationHost.State.DRAINING_CHANGE) {
            return false;
        }
        return QIOAutomationUpgradeSupport.isModeInstalled(tile, dataSource.getMode());
    }
}
