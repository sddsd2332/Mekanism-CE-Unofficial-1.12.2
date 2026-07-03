package mekanism.common.inventory;

import mekanism.api.NBTConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.block.states.BlockStateBasic.BasicBlockType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.BinInventorySlot;
import mekanism.common.item.ItemBlockBasic;
import mekanism.common.tier.BinTier;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class BinMekanismInventory extends ItemStackMekanismInventory {

    private BinInventorySlot binSlot;
    private int pendingCount;

    private BinMekanismInventory(@Nonnull ItemStack stack) {
        super(stack);
        migrateLegacyData();
    }

    @Nonnull
    @Override
    protected List<IInventorySlot> getInitialInventory() {
        binSlot = BinInventorySlot.create(this, BinTier.values()[((ItemBlockBasic) stack.getItem()).getBaseTier(stack).ordinal()]);
        return Collections.singletonList(binSlot);
    }

    @Nullable
    public static BinMekanismInventory create(@Nonnull ItemStack stack) {
        if (!stack.isEmpty() && BasicBlockType.get(stack) == BasicBlockType.BIN && stack.getItem() instanceof ItemBlockBasic) {
            return new BinMekanismInventory(stack);
        }
        return null;
    }

    public BinInventorySlot getBinSlot() {
        return binSlot;
    }

    public ItemStack getStack() {
        return binSlot.getBottomStack();
    }

    public ItemStack removeStack() {
        if (MekanismConfig.current().mekce.BinRecipeRemovesItem.val()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getStack();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return binSlot.extractItem(stack.getCount(), mekanism.api.Action.EXECUTE, mekanism.api.AutomationType.MANUAL);
    }

    public ItemStack add(ItemStack stack) {
        return binSlot.insertItem(stack, mekanism.api.Action.EXECUTE, mekanism.api.AutomationType.MANUAL);
    }

    public boolean isValid(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0 && binSlot.isItemValid(stack) &&
              binSlot.insertItem(stack, mekanism.api.Action.SIMULATE, mekanism.api.AutomationType.MANUAL).getCount() < stack.getCount();
    }

    public int getMaxStorage() {
        return getTier().getStorage();
    }

    public BinTier getTier() {
        return BinTier.values()[((ItemBlockBasic) stack.getItem()).getBaseTier(stack).ordinal()];
    }

    public int getItemCount() {
        return binSlot.getCount();
    }

    public void setItemCount(int count) {
        pendingCount = Math.max(0, count);
        if (count <= 0 || binSlot.isEmpty()) {
            if (count <= 0) {
                binSlot.setEmpty();
            }
        } else {
            binSlot.setStackUnchecked(StackUtils.size(binSlot.getStack(), Math.min(count, binSlot.getLimit(binSlot.getStack()))));
        }
        onContentsChanged();
    }

    public ItemStack getItemType() {
        return binSlot.isEmpty() ? ItemStack.EMPTY : StackUtils.size(binSlot.getStack(), 1);
    }

    public void setItemType(ItemStack stack) {
        if (stack.isEmpty()) {
            binSlot.setEmpty();
        } else {
            int count = binSlot.isEmpty() ? pendingCount : binSlot.getCount();
            binSlot.setStackUnchecked(StackUtils.size(stack, Math.min(count, binSlot.getLimit(stack))));
        }
        onContentsChanged();
    }

    public boolean isLocked() {
        return binSlot.isLocked();
    }

    public ItemStack getRenderStack() {
        return binSlot.getRenderStack();
    }

    public ItemStack getLockStack() {
        return binSlot.getLockStack();
    }

    public void setLockStack(ItemStack stack) {
        binSlot.setLockStack(stack);
        onContentsChanged();
    }

    private void migrateLegacyData() {
        if (!binSlot.isEmpty() || !ItemDataUtils.hasData(stack, "itemCount")) {
            return;
        }
        int count = ItemDataUtils.getInt(stack, "itemCount");
        if (count > 0) {
            ItemStack stored = new ItemStack(ItemDataUtils.getCompound(stack, "storedItem"));
            if (!stored.isEmpty()) {
                binSlot.setStackUnchecked(StackUtils.size(stored, Math.min(count, binSlot.getLimit(stored))));
            }
        }
        if (ItemDataUtils.hasData(stack, NBTConstants.LOCK_STACK, NBT.TAG_COMPOUND)) {
            binSlot.setLockStack(new ItemStack(ItemDataUtils.getCompound(stack, NBTConstants.LOCK_STACK)));
        }
        ItemDataUtils.removeData(stack, "itemCount");
        ItemDataUtils.removeData(stack, "storedItem");
        ItemDataUtils.removeData(stack, NBTConstants.LOCK_STACK);
        onContentsChanged();
    }
}
