package mekanism.common.inventory.container.slot;

import mekanism.common.inventory.container.IGUIWindow;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntSupplier;

/** Shared moving-position and render state for real slots displayed by a GUI window. */
final class VirtualSlotData {

    private IntSupplier actualX;
    private IntSupplier actualY;
    @Nullable private IGUIWindow linkedWindow;
    @Nonnull private ItemStack stackToRender = ItemStack.EMPTY;
    private boolean shouldDrawOverlay;
    @Nullable private String tooltipOverride;

    VirtualSlotData(IntSupplier defaultX, IntSupplier defaultY) {
        actualX = defaultX;
        actualY = defaultY;
    }

    @Nullable
    IGUIWindow getLinkedWindow() {
        return linkedWindow;
    }

    int getActualX() {
        return actualX.getAsInt();
    }

    int getActualY() {
        return actualY.getAsInt();
    }

    void updatePosition(@Nullable IGUIWindow window, IntSupplier xPositionSupplier,
          IntSupplier yPositionSupplier) {
        linkedWindow = window;
        actualX = xPositionSupplier;
        actualY = yPositionSupplier;
    }

    void updateRenderInfo(@Nonnull ItemStack stackToRender, boolean shouldDrawOverlay,
          @Nullable String tooltipOverride) {
        this.stackToRender = stackToRender;
        this.shouldDrawOverlay = shouldDrawOverlay;
        this.tooltipOverride = tooltipOverride;
    }

    @Nonnull
    ItemStack getStackToRender() {
        return stackToRender;
    }

    boolean shouldDrawOverlay() {
        return shouldDrawOverlay;
    }

    @Nullable
    String getTooltipOverride() {
        return tooltipOverride;
    }
}
