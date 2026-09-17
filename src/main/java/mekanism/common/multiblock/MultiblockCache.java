package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.DataHandlerUtils;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.io.IOException;

public abstract class MultiblockCache<T extends SynchronizedData<T>> implements IMekanismInventory {

    public Set<Coord4D> locations = new ObjectOpenHashSet<>();
    private final List<IInventorySlot> inventorySlots = new ArrayList<>();

    public abstract void apply(T data);

    public abstract void sync(T data);

    public abstract void load(NBTTagCompound nbtTags);

    public abstract void save(NBTTagCompound nbtTags);

    /** Validate detached previews before any source inventory is consumed or tombstoned. */
    public void validateMerge(MultiblockCache<T> incoming) throws IOException {
        requireMerge(getClass() == incoming.getClass(), "Mismatched cache types");
        // Old empty caches may predate inventory-slot serialization. Extending only the detached
        // preview with empty slots makes the existing slot-wise merge well-defined.
        ensureInventorySlots(Math.max(inventorySlots.size(), incoming.inventorySlots.size()));
        requireMerge(StackUtils.getMergeRejects(getInventorySlots(null), incoming.getInventorySlots(null)).isEmpty(),
              "Merged items do not fit; source inventories are retained");
    }

    public void validateCapacity(T target) throws IOException {
        List<IInventorySlot> slots = target.getInternalInventorySlots();
        for (int i = 0; i < inventorySlots.size(); i++) {
            ItemStack stack = inventorySlots.get(i).getStack();
            if (!stack.isEmpty()) {
                requireMerge(i < slots.size() && stack.getCount() <= slots.get(i).getLimit(stack), "Stored items exceed target slots");
            }
        }
    }

    protected static void requireMerge(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }

    protected static void validateGasMerge(GasStack current, GasStack incoming) throws IOException {
        if (current != null && incoming != null) {
            requireMerge(current.isGasEqual(incoming), "Different gases cannot share one cache tank");
            requireMerge((long) current.amount + incoming.amount <= Integer.MAX_VALUE, "Merged gas exceeds integer capacity");
        }
    }

    protected static void validateFluidMerge(FluidStack current, FluidStack incoming) throws IOException {
        if (current != null && incoming != null) {
            requireMerge(current.isFluidEqual(incoming), "Different fluids cannot share one cache tank");
            requireMerge((long) current.amount + incoming.amount <= Integer.MAX_VALUE, "Merged fluid exceeds integer capacity");
        }
    }

    protected static void validateSum(double current, double incoming, double maximum, String field) throws IOException {
        requireMerge(Double.isFinite(current) && Double.isFinite(incoming) && current >= 0 && incoming >= 0 &&
              current <= maximum - incoming, "Merged " + field + " exceeds supported range");
    }

    protected static void validateAmount(int amount, int capacity, String field) throws IOException {
        requireMerge(amount >= 0 && amount <= capacity, "Stored " + field + " exceeds target capacity; source inventory is retained");
    }

    protected void applyInventory(T data) {
        List<IInventorySlot> dataSlots = data.getInternalInventorySlots();
        for (int i = 0; i < inventorySlots.size() && i < dataSlots.size(); i++) {
            dataSlots.get(i).deserializeNBT(inventorySlots.get(i).serializeNBT());
        }
    }

    protected void syncInventory(T data) {
        List<IInventorySlot> dataSlots = data.getInternalInventorySlots();
        ensureInventorySlots(dataSlots.size());
        for (int i = 0; i < dataSlots.size(); i++) {
            BasicInventorySlot cachedSlot = (BasicInventorySlot) inventorySlots.get(i);
            ItemStack cached = cachedSlot.getStack();
            ItemStack source = dataSlots.get(i).getStack();
            if (source.isEmpty()) {
                if (!cached.isEmpty()) {
                    cachedSlot.setEmpty();
                }
            } else if (!cached.isEmpty() && ItemStack.areItemsEqual(cached, source) && ItemStack.areItemStackTagsEqual(cached, source)) {
                cached.setCount(source.getCount());
            } else {
                cachedSlot.setStackUnchecked(source);
            }
        }
    }

    @Nullable
    protected static GasStack syncGasStack(@Nullable GasStack cached, @Nullable GasStack source) {
        if (source == null) {
            return null;
        }
        if (cached != null && cached.isGasEqual(source)) {
            cached.amount = source.amount;
            return cached;
        }
        return source.copy();
    }

    @Nullable
    protected static FluidStack syncFluidStack(@Nullable FluidStack cached, @Nullable FluidStack source) {
        if (source == null) {
            return null;
        }
        if (cached != null && cached.isFluidEqual(source)) {
            cached.amount = source.amount;
            return cached;
        }
        return source.copy();
    }

    protected void loadInventory(NBTTagCompound nbtTags) {
        ensureInventorySlots(nbtTags.getInteger(NBTConstants.ITEMS + "_stored"));
        // Empty slots are omitted from the serialized list; this is a full snapshot.
        for (IInventorySlot slot : inventorySlots) {
            slot.setEmpty();
        }
        DataHandlerUtils.readContainers(inventorySlots, nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
    }

    protected void saveInventory(NBTTagCompound nbtTags) {
        nbtTags.setInteger(NBTConstants.ITEMS + "_stored", inventorySlots.size());
        nbtTags.setTag(NBTConstants.ITEMS, DataHandlerUtils.writeContainers(inventorySlots));
    }

    protected void ensureInventorySlots(int count) {
        while (inventorySlots.size() < count) {
            inventorySlots.add(BasicInventorySlot.at(this, 0, 0));
        }
        while (inventorySlots.size() > count) {
            inventorySlots.remove(inventorySlots.size() - 1);
        }
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return inventorySlots;
    }

    @Override
    public boolean hasInventory() {
        return !inventorySlots.isEmpty();
    }

    @Override
    public void onContentsChanged() {
    }
}
