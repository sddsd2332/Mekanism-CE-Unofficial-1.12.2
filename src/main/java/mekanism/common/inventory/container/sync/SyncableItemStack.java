package mekanism.common.inventory.container.sync;

import mekanism.common.network.to_client.container.property.IntPropertyData;
import mekanism.common.network.to_client.container.property.ItemStackPropertyData;
import mekanism.common.network.to_client.container.property.PropertyData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SyncableItemStack implements ISyncableData {

    public static SyncableItemStack create(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return new SyncableItemStack(getter, setter);
    }

    private final Supplier<ItemStack> getter;
    private final Consumer<ItemStack> setter;
    @Nonnull
    private ItemStack lastKnownValue = ItemStack.EMPTY;

    private SyncableItemStack(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Nonnull
    public ItemStack get() {
        ItemStack stack = getter.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public void set(@Nonnull ItemStack value) {
        setter.accept(value);
    }

    public void set(int amount) {
        ItemStack stack = get();
        if (!stack.isEmpty()) {
            stack.setCount(amount);
        }
    }

    @Override
    public DirtyType isDirty() {
        ItemStack value = get();
        if (value.isEmpty() && lastKnownValue.isEmpty()) {
            return DirtyType.CLEAN;
        }
        boolean sameItem = ItemHandlerHelper.canItemStacksStack(value, lastKnownValue);
        if (!sameItem || value.getCount() != lastKnownValue.getCount()) {
            lastKnownValue = value.copy();
            return sameItem ? DirtyType.SIZE : DirtyType.DIRTY;
        }
        return DirtyType.CLEAN;
    }

    @Override
    public PropertyData getPropertyData(short property, DirtyType dirtyType) {
        if (dirtyType == DirtyType.SIZE) {
            return new IntPropertyData(property, get().getCount());
        }
        return new ItemStackPropertyData(property, get());
    }
}
