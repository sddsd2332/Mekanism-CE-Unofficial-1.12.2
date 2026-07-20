package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiRightArrow;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.slot.GuiVirtualSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/** Floating 3x3 crafting window used by both QIO dashboards. */
public class GuiCraftingWindow extends GuiWindow {

    private final List<GuiVirtualSlot> slots = new ArrayList<>(10);
    private final byte index;
    private IQIOItemViewerContainer container;

    public GuiCraftingWindow(@Nonnull IGuiWrapper gui, int x, int y, @Nonnull IQIOItemViewerContainer container, byte index) {
        super(gui, x, y, 124, 80, new SelectedWindowData(WindowType.CRAFTING, index));
        this.index = index;
        this.container = container;
        interactionStrategy = InteractionStrategy.ALL;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                GuiVirtualSlot slot = addChild(new GuiVirtualSlot(SlotType.NORMAL, gui,
                      relativeX + 8 + column * 18, relativeY + 18 + row * 18));
                slot.updateVirtualSlot(this, container.getCraftingWindowSlot(index, row * 3 + column));
                slots.add(slot);
            }
        }
        addChild(new GuiRightArrow(gui, relativeX + 70, relativeY + 38).recipeViewerCrafting());
        GuiVirtualSlot output = addChild(new GuiVirtualSlot(SlotType.NORMAL, gui, relativeX + 100, relativeY + 36));
        output.updateVirtualSlot(this, container.getCraftingWindowSlot(index, 9));
        slots.add(output);
        addChild(new MekanismImageButton(gui, relativeX + width - 20, relativeY + height - 20, 14,
              getButtonLocation("clear_sides"), () -> container.clearCraftingWindow(index, net.minecraft.client.gui.GuiScreen.isShiftKeyDown()),
              getOnHover(MekanismLang.CRAFTING_WINDOW_CLEAR)));
    }

    public void updateContainer(@Nonnull IQIOItemViewerContainer container) {
        this.container = container;
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).updateVirtualSlot(this, container.getCraftingWindowSlot(index, i));
        }
    }

    public byte getIndex() {
        return index;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            // Slot and clear-window packets validate the selected crafting
            // grid server-side. Synchronize the newly clicked background
            // window before any child sends its action packet.
            gui().setSelectedWindow(new SelectedWindowData(WindowType.CRAFTING, index));
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(MekanismLang.CRAFTING_WINDOW.translate(index + 1), 6);
    }
}
