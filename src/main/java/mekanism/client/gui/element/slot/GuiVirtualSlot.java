package mekanism.client.gui.element.slot;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.VirtualSlotContainerScreen;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mekanism.common.inventory.container.IGUIWindow;
import mekanism.common.inventory.container.slot.IVirtualSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GuiVirtualSlot extends GuiSlot implements IJEIIngredientHelper {

    @Nullable
    private IVirtualSlot virtualSlot;

    public GuiVirtualSlot(@Nullable IGUIWindow window, SlotType type, IGuiWrapper gui, int x, int y,
          @Nullable VirtualInventoryContainerSlot containerSlot) {
        this(type, gui, x, y);
        if (containerSlot != null) {
            SlotOverlay slotOverlay = containerSlot.getSlotOverlay();
            if (slotOverlay != null) {
                with(slotOverlay);
            }
            updateVirtualSlot(window, containerSlot);
        }
    }

    public GuiVirtualSlot(SlotType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        setRenderHover(true);
    }

    public boolean isElementForSlot(IVirtualSlot virtualSlot) {
        return this.virtualSlot == virtualSlot;
    }

    public void updateVirtualSlot(@Nullable IGUIWindow window, @Nonnull IVirtualSlot virtualSlot) {
        this.virtualSlot = virtualSlot;
        this.virtualSlot.updatePosition(window, () -> relativeX + 1, () -> relativeY + 1);
    }

    @Override
    protected void drawContents() {
        if (virtualSlot != null) {
            ItemStack stack = virtualSlot.getStackToRender();
            if (!stack.isEmpty()) {
                int xPos = relativeX + 1;
                int yPos = relativeY + 1;
                if (virtualSlot.shouldDrawOverlay()) {
                    mekanism.client.gui.GuiUtils.fill(xPos, yPos, xPos + 16, yPos + 16, DEFAULT_HOVER_COLOR);
                }
                gui().renderItemWithOverlay(stack, xPos, yPos, 1, virtualSlot.getTooltipOverride());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom() &&
            gui() instanceof VirtualSlotContainerScreen<?> screen && virtualSlot != null) {
            return screen.slotClicked(virtualSlot.getSlot(), button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    @Override
    public Object getIngredient(double mouseX, double mouseY) {
        return virtualSlot == null ? null : virtualSlot.getStackToRender();
    }
}
