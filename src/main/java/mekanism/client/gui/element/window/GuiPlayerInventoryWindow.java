package mekanism.client.gui.element.window;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.slot.GuiVirtualSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.slot.IVirtualSlot;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Standalone movable player-inventory window backed by real virtual-position slots. */
public class GuiPlayerInventoryWindow extends GuiWindow {

    public static final int WIDTH = 178;
    public static final int HEIGHT = 101;
    public static final int SLOT_X = 8;
    public static final int MAIN_SLOT_Y = 20;
    public static final int HOTBAR_SLOT_Y = 78;
    private static final int SLOT_COUNT = 36;

    private final Supplier<SelectedWindowData> selectedWindowDataSupplier;
    @Nullable private final SlotClickHandler clickHandler;
    @Nullable private final Runnable closeHandler;
    private boolean closed;

    public GuiPlayerInventoryWindow(IGuiWrapper gui, int x, int y,
          SelectedWindowData selectedWindowData, List<? extends IVirtualSlot> slots,
          @Nullable SlotClickHandler clickHandler, @Nullable Runnable closeHandler) {
        this(gui, x, y, selectedWindowData, () -> selectedWindowData, slots,
              clickHandler, closeHandler);
    }

    public GuiPlayerInventoryWindow(IGuiWrapper gui, int x, int y,
          SelectedWindowData windowData,
          Supplier<SelectedWindowData> selectedWindowDataSupplier,
          List<? extends IVirtualSlot> slots, @Nullable SlotClickHandler clickHandler,
          @Nullable Runnable closeHandler) {
        super(gui, x, y, WIDTH, HEIGHT, windowData);
        this.selectedWindowDataSupplier = Objects.requireNonNull(
              selectedWindowDataSupplier, "selectedWindowDataSupplier");
        this.clickHandler = clickHandler;
        this.closeHandler = closeHandler;
        if (slots.size() != SLOT_COUNT) {
            throw new IllegalArgumentException("Expected 36 player inventory slots, got " +
                  slots.size());
        }
        interactionStrategy = InteractionStrategy.ALL;
        int virtualIndex = 0;
        for (int slotY = 0; slotY < 3; slotY++) {
            for (int slotX = 0; slotX < 9; slotX++) {
                addInventorySlot(gui, slots.get(virtualIndex++),
                      9 + slotX + slotY * 9, relativeX + SLOT_X + slotX * 18,
                      relativeY + MAIN_SLOT_Y + slotY * 18);
            }
        }
        for (int slotX = 0; slotX < 9; slotX++) {
            addInventorySlot(gui, slots.get(virtualIndex++), slotX,
                  relativeX + SLOT_X + slotX * 18, relativeY + HOTBAR_SLOT_Y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            selectWindowContext();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onFocused() {
        selectWindowContext();
    }

    @Override
    public void renderForeground(int mouseX, int mouseY) {
        super.renderForeground(mouseX, mouseY);
        drawTitleText(new TextComponentTranslation("container.inventory"), 5);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        super.close();
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    private void selectWindowContext() {
        gui().setSelectedWindow(Objects.requireNonNull(selectedWindowDataSupplier.get(),
              "selectedWindowDataSupplier returned null"));
    }

    private void addInventorySlot(IGuiWrapper gui, IVirtualSlot virtualSlot,
          int inventoryIndex, int x, int y) {
        GuiVirtualSlot guiSlot = new GuiVirtualSlot(SlotType.NORMAL, gui, x, y) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (clickHandler != null && mouseX >= getX() && mouseY >= getY() &&
                    mouseX < getRight() && mouseY < getBottom() &&
                    clickHandler.onClick(inventoryIndex, button)) {
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        };
        guiSlot.updateVirtualSlot(this, virtualSlot);
        addChild(guiSlot);
    }

    @FunctionalInterface
    public interface SlotClickHandler {

        boolean onClick(int inventoryIndex, int mouseButton);
    }
}
