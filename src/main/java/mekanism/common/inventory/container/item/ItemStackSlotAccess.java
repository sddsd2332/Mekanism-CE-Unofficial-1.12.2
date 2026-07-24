package mekanism.common.inventory.container.item;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Stable access to the player inventory slot that backed an item GUI when it
 * was opened. Main-hand access captures the selected hotbar slot; offhand
 * access always uses vanilla's offhand inventory slot.
 */
public final class ItemStackSlotAccess {

    public static final int OFFHAND_SLOT = 40;

    private final InventoryPlayer inventory;
    private final EnumHand hand;
    private final int slot;
    private final ItemStack openingStack;

    public ItemStackSlotAccess(@Nonnull InventoryPlayer inventory, @Nonnull EnumHand hand, int slot) {
        this(inventory, hand, slot, getStack(inventory, hand, slot));
    }

    public ItemStackSlotAccess(@Nonnull InventoryPlayer inventory, @Nonnull EnumHand hand, int slot,
          @Nonnull ItemStack openingStack) {
        this.inventory = Objects.requireNonNull(inventory, "Inventory cannot be null");
        this.hand = Objects.requireNonNull(hand, "Hand cannot be null");
        if (!isValidSlot(hand, slot)) {
            throw new IllegalArgumentException("Invalid " + hand + " item GUI slot: " + slot);
        }
        this.slot = slot;
        this.openingStack = Objects.requireNonNull(openingStack, "Opening stack cannot be null");
    }

    public static int getSlotForHand(@Nonnull InventoryPlayer inventory, @Nonnull EnumHand hand) {
        return hand == EnumHand.OFF_HAND ? OFFHAND_SLOT : inventory.currentItem;
    }

    public static boolean isValidSlot(@Nonnull EnumHand hand, int slot) {
        return hand == EnumHand.OFF_HAND ? slot == OFFHAND_SLOT : slot >= 0 && slot < InventoryPlayer.getHotbarSize();
    }

    @Nonnull
    public ItemStack getStack() {
        return getStack(inventory, hand, slot);
    }

    public void setStack(@Nonnull ItemStack stack) {
        if (hand == EnumHand.OFF_HAND) {
            inventory.offHandInventory.set(0, stack);
        } else {
            inventory.mainInventory.set(slot, stack);
        }
    }

    /**
     * The server requires the exact stack instance that opened the container.
     * Client inventory synchronization is allowed to replace that instance.
     */
    public boolean isOriginalStackPresent() {
        boolean clientSide = inventory.player != null && inventory.player.world != null && inventory.player.world.isRemote;
        return isOriginalStackPresent(clientSide);
    }

    boolean isOriginalStackPresent(boolean clientSide) {
        ItemStack current = getStack();
        return !openingStack.isEmpty() && !current.isEmpty() && (clientSide || current == openingStack);
    }

    @Nonnull
    public ItemStack getOpeningStack() {
        return openingStack;
    }

    @Nonnull
    public EnumHand getHand() {
        return hand;
    }

    public int getSlot() {
        return slot;
    }

    @Nonnull
    public static ItemStack getStack(@Nonnull InventoryPlayer inventory, @Nonnull EnumHand hand, int slot) {
        if (!isValidSlot(hand, slot)) {
            return ItemStack.EMPTY;
        }
        if (hand == EnumHand.OFF_HAND) {
            return inventory.offHandInventory.isEmpty() ? ItemStack.EMPTY : inventory.offHandInventory.get(0);
        }
        return slot >= inventory.mainInventory.size() ? ItemStack.EMPTY : inventory.mainInventory.get(slot);
    }
}
