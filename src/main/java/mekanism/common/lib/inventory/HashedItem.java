package mekanism.common.lib.inventory;

import mekanism.api.inventory.IHashedItem;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class HashedItem implements IHashedItem {

    public static HashedItem create(@Nonnull ItemStack stack) {
        return new HashedItem(StackUtils.size(stack, 1));
    }

    /**
     * Uses the passed in stack as the raw stack, instead of making a copy of it with a size of one.
     *
     * @apiNote When using this, you should be very careful to not accidentally modify the backing stack, this is mainly for use where we want to use an {@link ItemStack}
     * as a key in a map that is local to a single method, and don't want the overhead of copying the stack when it is not needed.
     */
    public static HashedItem raw(@Nonnull ItemStack stack) {
        return new HashedItem(stack);
    }

    @Nonnull
    private final ItemStack itemStack;
    private final int hashCode;

    protected HashedItem(@Nonnull ItemStack stack) {
        this.itemStack = stack;
        this.hashCode = initHashCode();
    }

    protected HashedItem(HashedItem other) {
        this.itemStack = other.itemStack;
        this.hashCode = other.hashCode;
    }

    @Nonnull
    public ItemStack getInternalStack() {
        return itemStack;
    }

    @Nonnull
    public ItemStack getStack() {
        return getInternalStack();
    }

    @Nonnull
    @Override
    public ItemStack createStack(int size) {
        return StackUtils.size(itemStack, size);
    }

    public int getMaxStackSize() {
        return itemStack.getMaxStackSize();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof IHashedItem) {
            IHashedItem other = (IHashedItem) obj;
            return ItemHandlerHelper.canItemStacksStack(itemStack, other.getInternalStack());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int initHashCode() {
        int code = 1;
        code = 31 * code + itemStack.getItem().hashCode();
        if (itemStack.hasTagCompound()) {
            code = 31 * code + itemStack.getTagCompound().hashCode();
        }
        return code;
    }

    public static class UUIDAwareHashedItem extends HashedItem {

        private final UUID uuid;
        private final boolean overrideHash;

        /**
         * @apiNote For use on the client side, hash is taken into account for equals and hashCode
         */
        public UUIDAwareHashedItem(ItemStack stack, UUID uuid) {
            super(StackUtils.size(stack, 1));
            this.uuid = uuid;
            this.overrideHash = true;
        }

        public UUIDAwareHashedItem(HashedItem other, UUID uuid) {
            super(other);
            this.uuid = uuid;
            this.overrideHash = false;
        }

        @Nullable
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (overrideHash && uuid != null) {
                return obj instanceof UUIDAwareHashedItem && uuid.equals(((UUIDAwareHashedItem) obj).uuid) && super.equals(obj);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            if (overrideHash && uuid != null) {
                return 31 * super.hashCode() + uuid.hashCode();
            }
            return super.hashCode();
        }

        /**
         * Converts this to a raw HashedItem that doesn't care about UUID anymore.
         */
        public HashedItem asRawHashedItem() {
            return new HashedItem(this);
        }
    }
}
