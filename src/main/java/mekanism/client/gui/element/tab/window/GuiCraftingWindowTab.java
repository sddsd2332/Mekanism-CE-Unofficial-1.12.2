package mekanism.client.gui.element.tab.window;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.window.GuiCraftingWindow;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/** Side tab that manages the three persistent QIO crafting windows. */
public class GuiCraftingWindowTab extends GuiInsetElement<Void> {

    private final boolean[] openWindows = new boolean[IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS];
    private final Supplier<IQIOItemViewerContainer> containerSupplier;
    private int currentWindows;

    public GuiCraftingWindowTab(@Nonnull IGuiWrapper gui, @Nonnull Supplier<IQIOItemViewerContainer> containerSupplier) {
        super(MekanismUtils.getResource(ResourceType.GUI_BUTTON, "crafting.png"), gui, null, -26, 34, 26, 18, true);
        this.containerSupplier = containerSupplier;
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(MekanismLang.CRAFTING_TAB.translate(currentWindows, IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS), mouseX, mouseY);
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_CRAFTING_WINDOW.argb());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            GuiCraftingWindow window = createWindow();
            window.setTabListeners(this::onWindowClosed, this::onWindowReattached);
            gui().addWindow(window);
            disableTab();
            return true;
        }
        return false;
    }

    private void onWindowClosed(GuiWindow window) {
        if (window instanceof GuiCraftingWindow) {
            int index = ((GuiCraftingWindow) window).getIndex();
            if (index >= 0 && index < openWindows.length && openWindows[index]) {
                openWindows[index] = false;
                currentWindows = Math.max(0, currentWindows - 1);
            }
        }
        if (currentWindows < openWindows.length) {
            active = true;
        }
    }

    private void onWindowReattached(GuiWindow window) {
        if (window instanceof GuiCraftingWindow) {
            GuiCraftingWindow craftingWindow = (GuiCraftingWindow) window;
            craftingWindow.updateContainer(containerSupplier.get());
            int index = craftingWindow.getIndex();
            if (index >= 0 && index < openWindows.length && !openWindows[index]) {
                openWindows[index] = true;
                currentWindows++;
            }
        }
    }

    public void adoptWindow(@Nonnull GuiCraftingWindow window) {
        window.updateContainer(containerSupplier.get());
        window.setTabListeners(this::onWindowClosed, this::onWindowReattached);
        int index = window.getIndex();
        if (index >= 0 && index < openWindows.length && !openWindows[index]) {
            openWindows[index] = true;
            currentWindows++;
        }
        if (currentWindows >= openWindows.length) {
            active = false;
        }
    }

    private void disableTab() {
        currentWindows++;
        if (currentWindows >= openWindows.length) {
            active = false;
        }
    }

    private GuiCraftingWindow createWindow() {
        byte index = 0;
        for (byte i = 0; i < openWindows.length; i++) {
            if (!openWindows[i]) {
                index = i;
                break;
            }
        }
        openWindows[index] = true;
        return new GuiCraftingWindow(gui(), (gui().getWidth() - 124) / 2, 15, containerSupplier.get(), index);
    }

    @Override
    public void openPinnedWindows() {
        super.openPinnedWindows();
        for (byte i = 0; i < openWindows.length; i++) {
            if (!openWindows[i] && new SelectedWindowData(WindowType.CRAFTING, i).wasPinned()) {
                openWindows[i] = true;
                currentWindows++;
                GuiCraftingWindow window = new GuiCraftingWindow(gui(), (gui().getWidth() - 124) / 2, 15, containerSupplier.get(), i);
                window.setTabListeners(this::onWindowClosed, this::onWindowReattached);
                gui().addWindow(window);
            }
        }
        if (currentWindows >= openWindows.length) {
            active = false;
        }
    }
}
