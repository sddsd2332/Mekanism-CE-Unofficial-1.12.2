package mekanism.common.inventory.container.slot;

import mekanism.common.inventory.container.IGUIWindow;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntSupplier;

public interface IVirtualSlot {

    @Nullable
    IGUIWindow getLinkedWindow();

    int getActualX();

    int getActualY();

    void updatePosition(@Nullable IGUIWindow window, IntSupplier xPositionSupplier, IntSupplier yPositionSupplier);

    default void updatePosition(IntSupplier xPositionSupplier, IntSupplier yPositionSupplier) {
        updatePosition(null, xPositionSupplier, yPositionSupplier);
    }

    void updateRenderInfo(@Nonnull ItemStack stackToRender, boolean shouldDrawOverlay, @Nullable String tooltipOverride);

    @Nonnull
    ItemStack getStackToRender();

    boolean shouldDrawOverlay();

    @Nullable
    String getTooltipOverride();

    Slot getSlot();
}
