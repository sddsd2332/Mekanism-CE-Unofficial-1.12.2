package mekanism.common.inventory.container.slot;

import mekanism.common.inventory.container.IGUIWindow;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntSupplier;

/** A real container slot whose visual position is owned by a QIO window. */
public class VirtualCraftingSlot extends Slot implements IVirtualSlot {

    private IntSupplier actualX = () -> xPos;
    private IntSupplier actualY = () -> yPos;
    @Nullable
    private IGUIWindow linkedWindow;
    @Nonnull
    private ItemStack stackToRender = ItemStack.EMPTY;
    private boolean shouldDrawOverlay;
    @Nullable
    private String tooltipOverride;

    public VirtualCraftingSlot(@Nonnull IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Nullable
    @Override
    public IGUIWindow getLinkedWindow() {
        return linkedWindow;
    }

    @Override
    public int getActualX() {
        return actualX.getAsInt();
    }

    @Override
    public int getActualY() {
        return actualY.getAsInt();
    }

    @Override
    public void updatePosition(@Nullable IGUIWindow window, @Nonnull IntSupplier xPositionSupplier, @Nonnull IntSupplier yPositionSupplier) {
        linkedWindow = window;
        actualX = xPositionSupplier;
        actualY = yPositionSupplier;
    }

    @Override
    public void updateRenderInfo(@Nonnull ItemStack stackToRender, boolean shouldDrawOverlay, @Nullable String tooltipOverride) {
        this.stackToRender = stackToRender;
        this.shouldDrawOverlay = shouldDrawOverlay;
        this.tooltipOverride = tooltipOverride;
    }

    @Nonnull
    @Override
    public ItemStack getStackToRender() {
        return stackToRender;
    }

    @Override
    public boolean shouldDrawOverlay() {
        return shouldDrawOverlay;
    }

    @Nullable
    @Override
    public String getTooltipOverride() {
        return tooltipOverride;
    }

    @Override
    public Slot getSlot() {
        return this;
    }
}
