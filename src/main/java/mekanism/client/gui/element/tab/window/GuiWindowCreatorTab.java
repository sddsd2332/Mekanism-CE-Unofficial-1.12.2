package mekanism.client.gui.element.tab.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class GuiWindowCreatorTab<DATA_SOURCE, ELEMENT extends GuiWindowCreatorTab<DATA_SOURCE, ELEMENT>> extends GuiInsetElement<DATA_SOURCE> {

    private final Supplier<ELEMENT> elementSupplier;

    protected GuiWindowCreatorTab(ResourceLocation icon, IGuiWrapper gui, DATA_SOURCE dataSource, int y, boolean left, Supplier<ELEMENT> elementSupplier) {
        super(icon, gui, dataSource, left ? -26 : gui.getWidth(), y, 26, 18, left);
        this.elementSupplier = elementSupplier;
    }

    protected GuiWindowCreatorTab(ResourceLocation icon, IGuiWrapper gui, DATA_SOURCE dataSource, int x, int y, int height, int innerSize, boolean left,
          Supplier<ELEMENT> elementSupplier) {
        super(icon, gui, dataSource, x, y, height, innerSize, left);
        this.elementSupplier = elementSupplier;
    }

    @Override
    protected void colorTab() {
        Integer tabColor = getTabColor();
        if (tabColor != null) {
            MekanismRenderer.color(tabColor);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        openWindow(getNextWindowData());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        ITextComponent tooltip = getTooltipText();
        if (tooltip != null) {
            displayTooltip(tooltip, mouseX, mouseY);
        }
    }

    private void openWindow(SelectedWindowData windowData) {
        GuiWindow window = createWindow(windowData);
        window.setTabListeners(getCloseListener(), getReAttachListener());
        disableTab();
        gui().addWindow(window);
    }

    protected final Supplier<ELEMENT> getElementSupplier() {
        return elementSupplier;
    }

    protected void disableTab() {
        active = false;
    }

    protected Consumer<GuiWindow> getCloseListener() {
        return window -> elementSupplier.get().active = true;
    }

    protected Consumer<GuiWindow> getReAttachListener() {
        return window -> elementSupplier.get().disableTab();
    }

    public void adoptWindows(GuiWindow... windows) {
        for (GuiWindow window : windows) {
            window.setTabListeners(getCloseListener(), getReAttachListener());
        }
    }

    protected abstract GuiWindow createWindow(SelectedWindowData windowData);

    protected abstract SelectedWindowData getNextWindowData();

    protected List<SelectedWindowData> getValidWindows() {
        return Collections.singletonList(getNextWindowData());
    }

    @Override
    public void openPinnedWindows() {
        super.openPinnedWindows();
        for (SelectedWindowData windowData : getValidWindows()) {
            if (windowData.wasPinned()) {
                openWindow(windowData);
            }
        }
    }

    @Nullable
    protected Integer getTabColor() {
        return null;
    }

    @Nullable
    protected abstract ITextComponent getTooltipText();
}
