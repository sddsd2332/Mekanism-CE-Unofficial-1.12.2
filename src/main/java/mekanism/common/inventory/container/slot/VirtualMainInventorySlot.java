package mekanism.common.inventory.container.slot;

import mekanism.common.inventory.container.IGUIWindow;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntSupplier;

/** Player main-inventory slot whose screen position is supplied by a GUI window. */
public class VirtualMainInventorySlot extends MainInventorySlot implements IVirtualSlot {

    private final VirtualSlotData virtualData = new VirtualSlotData(() -> xPos, () -> yPos);

    public VirtualMainInventorySlot(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Nullable
    @Override
    public IGUIWindow getLinkedWindow() {
        return virtualData.getLinkedWindow();
    }

    @Override
    public int getActualX() {
        return virtualData.getActualX();
    }

    @Override
    public int getActualY() {
        return virtualData.getActualY();
    }

    @Override
    public void updatePosition(@Nullable IGUIWindow window, IntSupplier xPositionSupplier,
          IntSupplier yPositionSupplier) {
        virtualData.updatePosition(window, xPositionSupplier, yPositionSupplier);
    }

    @Override
    public void updateRenderInfo(@Nonnull ItemStack stackToRender, boolean shouldDrawOverlay,
          @Nullable String tooltipOverride) {
        virtualData.updateRenderInfo(stackToRender, shouldDrawOverlay, tooltipOverride);
    }

    @Nonnull
    @Override
    public ItemStack getStackToRender() {
        return virtualData.getStackToRender();
    }

    @Override
    public boolean shouldDrawOverlay() {
        return virtualData.shouldDrawOverlay();
    }

    @Nullable
    @Override
    public String getTooltipOverride() {
        return virtualData.getTooltipOverride();
    }

    @Override
    public Slot getSlot() {
        return this;
    }
}
