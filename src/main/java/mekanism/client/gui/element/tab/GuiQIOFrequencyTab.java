package mekanism.client.gui.element.tab;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.window.GuiQIOFrequencySelectWindow;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.common.util.MekanismUtils;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** QIO frequency tab that opens the shared selector as an in-place window. */
public abstract class GuiQIOFrequencyTab<DATA> extends GuiInsetElement<DATA> {

    private static final SelectedWindowData WINDOW_DATA = new SelectedWindowData(WindowType.QIO_FREQUENCY);

    @Nullable
    private final Supplier<? extends GuiQIOFrequencyTab<?>> elementSupplier;

    protected GuiQIOFrequencyTab(IGuiWrapper gui, DATA data,
          @Nullable Supplier<? extends GuiQIOFrequencyTab<?>> elementSupplier) {
        super(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "frequency.png"), gui, data, -26, 6, 26, 18, true);
        this.elementSupplier = elementSupplier;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_QIO_FREQUENCY.argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(MekanismLang.SET_FREQUENCY.translate(), mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        openWindow();
    }

    private void openWindow() {
        GuiQIOFrequencySelectWindow window = createWindow(WINDOW_DATA);
        adoptWindow(window);
        gui().addWindow(window);
    }

    @Override
    public void openPinnedWindows() {
        super.openPinnedWindows();
        if (WINDOW_DATA.wasPinned()) {
            openWindow();
        }
    }

    public void adoptWindow(GuiWindow window) {
        window.setTabListeners(closed -> setCurrentTabActive(true), reattached -> setCurrentTabActive(false));
        setCurrentTabActive(false);
    }

    private void setCurrentTabActive(boolean active) {
        GuiQIOFrequencyTab<?> current = elementSupplier == null ? this : elementSupplier.get();
        if (current != null) {
            current.active = active;
        }
    }

    protected abstract GuiQIOFrequencySelectWindow createWindow(SelectedWindowData windowData);

    public static class Tile extends GuiQIOFrequencyTab<TileEntityQIOComponent> {

        public Tile(IGuiWrapper gui, TileEntityQIOComponent tile,
              Supplier<? extends GuiQIOFrequencyTab<?>> elementSupplier) {
            super(gui, tile, elementSupplier);
        }

        public Tile(IGuiWrapper gui, TileEntityQIOComponent tile) {
            super(gui, tile, null);
        }

        /** @deprecated Frequency selection no longer opens a separate GUI ID. */
        @Deprecated
        public Tile(IGuiWrapper gui, TileEntityQIOComponent tile, int ignoredGuiId) {
            this(gui, tile);
        }

        @Override
        protected GuiQIOFrequencySelectWindow createWindow(SelectedWindowData windowData) {
            return GuiQIOFrequencySelectWindow.forTile(gui(), dataSource, windowData);
        }
    }

    public static class Item extends GuiQIOFrequencyTab<PortableQIODashboardContainer> {

        public Item(IGuiWrapper gui, PortableQIODashboardContainer container,
              Supplier<? extends GuiQIOFrequencyTab<?>> elementSupplier) {
            super(gui, container, elementSupplier);
        }

        public Item(IGuiWrapper gui, PortableQIODashboardContainer container) {
            super(gui, container, null);
        }

        @Override
        protected GuiQIOFrequencySelectWindow createWindow(SelectedWindowData windowData) {
            return GuiQIOFrequencySelectWindow.forItem(gui(), dataSource, windowData);
        }
    }
}
