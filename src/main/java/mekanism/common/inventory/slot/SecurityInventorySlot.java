package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.security.IOwnerItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.security.SecurityFrequency;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SecurityInventorySlot extends BasicInventorySlot {

    public static final Predicate<ItemStack> VALIDATOR = stack -> stack.getItem() instanceof IOwnerItem ownerItem && ownerItem.hasOwner(stack);
    public static final Predicate<ItemStack> LOCK_EXTRACT_PREDICATE = stack -> stack.getItem() instanceof IOwnerItem ownerItem && ownerItem.getOwnerUUID(stack) != null;
    public static final Predicate<ItemStack> LOCK_INSERT_PREDICATE = stack -> stack.getItem() instanceof IOwnerItem ownerItem && ownerItem.getOwnerUUID(stack) == null;

    public static SecurityInventorySlot unlock(Supplier<UUID> ownerSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(ownerSupplier, "Owner supplier cannot be null");
        return new SecurityInventorySlot(LOCK_INSERT_PREDICATE, stack -> {
            if (!(stack.getItem() instanceof IOwnerItem ownerItem)) {
                return false;
            }
            UUID stackOwner = ownerItem.getOwnerUUID(stack);
            return stackOwner != null && stackOwner.equals(ownerSupplier.get());
        }, listener, x, y);
    }

    public static SecurityInventorySlot lock(@Nullable IContentsListener listener, int x, int y) {
        return new SecurityInventorySlot(LOCK_EXTRACT_PREDICATE, LOCK_INSERT_PREDICATE, listener, x, y);
    }

    private SecurityInventorySlot(Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        super(canExtract, canInsert, VALIDATOR, listener, x, y);
    }

    public void unlock(UUID ownerUUID) {
        if (!isEmpty() && current.getItem() instanceof IOwnerItem ownerItem) {
            UUID stackOwner = ownerItem.getOwnerUUID(current);
            if (stackOwner != null && stackOwner.equals(ownerUUID)) {
                ownerItem.setOwnerUUID(current, null);
                if (ownerItem instanceof ISecurityItem securityItem && securityItem.hasSecurity(current)) {
                    securityItem.setSecurity(current, SecurityMode.PUBLIC);
                }
                onContentsChanged();
            }
        }
    }

    public void lock(UUID ownerUUID, SecurityFrequency frequency) {
        if (!isEmpty() && current.getItem() instanceof IOwnerItem ownerItem && ownerItem.hasOwner(current)) {
            UUID stackOwner = ownerItem.getOwnerUUID(current);
            if (stackOwner == null) {
                ownerItem.setOwnerUUID(current, ownerUUID);
                stackOwner = ownerUUID;
            }
            if (stackOwner.equals(ownerUUID) && ownerItem instanceof ISecurityItem securityItem && securityItem.hasSecurity(current)) {
                securityItem.setSecurity(current, frequency.securityMode);
                onContentsChanged();
            }
        }
    }
}
