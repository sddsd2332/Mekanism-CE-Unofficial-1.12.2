package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.tier.BinTier;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

public class BinInventorySlot extends BasicInventorySlot {

    public static BinInventorySlot create(@Nullable IContentsListener listener, BinTier tier) {
        Objects.requireNonNull(tier, "Bin tier cannot be null");
        return create(listener, () -> tier);
    }

    public static BinInventorySlot create(@Nullable IContentsListener listener, Supplier<BinTier> tierSupplier) {
        Objects.requireNonNull(tierSupplier, "Bin tier supplier cannot be null");
        return new BinInventorySlot(listener, tierSupplier);
    }

    private final Supplier<BinTier> tierSupplier;
    private ItemStack lockStack = ItemStack.EMPTY;

    private BinInventorySlot(@Nullable IContentsListener listener, Supplier<BinTier> tierSupplier) {
        super(Integer.MAX_VALUE, alwaysTrueBi, alwaysTrueBi, stack -> BasicBlockType.get(stack) != BasicBlockType.BIN, listener, 0, 0);
        this.tierSupplier = tierSupplier;
        obeyStackLimit = false;
    }

    @Override
    public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
        if (isEmpty()) {
            if (isLocked() && !ItemHandlerHelper.canItemStacksStack(lockStack, stack)) {
                return stack;
            } else if (isCreative() && action.execute() && automationType != AutomationType.EXTERNAL) {
                ItemStack simulatedRemainder = super.insertItem(stack, Action.SIMULATE, automationType);
                if (simulatedRemainder.isEmpty()) {
                    setStackUnchecked(StackUtils.size(stack, getLimit(stack)));
                }
                return simulatedRemainder;
            }
        }
        return super.insertItem(stack, action.combine(!isCreative()), automationType);
    }

    @Override
    public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
        return super.extractItem(amount, action.combine(!isCreative()), automationType);
    }

    @Override
    public int setStackSize(int amount, Action action) {
        return super.setStackSize(amount, action.combine(!isCreative()));
    }

    @Override
    public int getLimit(ItemStack stack) {
        return tierSupplier.get().getStorage();
    }

    @Nullable
    @Override
    public InventoryContainerSlot createContainerSlot() {
        return null;
    }

    public ItemStack getBottomStack() {
        if (isEmpty()) {
            return ItemStack.EMPTY;
        }
        return StackUtils.size(current, Math.min(getCount(), current.getMaxStackSize()));
    }

    public boolean setLocked(boolean lock) {
        if (isCreative() || isLocked() == lock || (lock && isEmpty())) {
            return false;
        }
        lockStack = lock ? StackUtils.size(current, 1) : ItemStack.EMPTY;
        return true;
    }

    public void setLockStack(ItemStack stack) {
        lockStack = stack.isEmpty() ? ItemStack.EMPTY : StackUtils.size(stack, 1);
    }

    public boolean isLocked() {
        return !lockStack.isEmpty();
    }

    public ItemStack getRenderStack() {
        return isLocked() ? getLockStack() : getStack();
    }

    public ItemStack getLockStack() {
        return lockStack;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        if (isLocked()) {
            nbt.setTag(NBTConstants.LOCK_STACK, lockStack.writeToNBT(new NBTTagCompound()));
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.LOCK_STACK, NBT.TAG_COMPOUND)) {
            lockStack = new ItemStack(nbt.getCompoundTag(NBTConstants.LOCK_STACK));
        } else {
            lockStack = ItemStack.EMPTY;
        }
        super.deserializeNBT(nbt);
    }

    private boolean isCreative() {
        return tierSupplier.get() == BinTier.CREATIVE;
    }
}
