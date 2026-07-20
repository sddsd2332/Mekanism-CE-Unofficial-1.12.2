package mekanism.common.tile.qio;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.common.HashList;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.filter.QIOFilter;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableFilterList;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Shared direction, filter and inventory behavior for QIO automation blocks. */
public abstract class TileEntityQIOFilterHandler extends TileEntityQIOComponent {

    protected BasicInventorySlot filterSlot;
    /**
     * The list is deliberately kept on the tile rather than in the inventory
     * slot.  A slot can represent an item stack, but fluids and gases have no
     * vanilla item representation that is safe to use as the authoritative
     * filter payload.
     */
    private final List<QIOFilter> filters = new ArrayList<>();
    // Upstream QIO components transfer all resources until an enabled filter
    // is installed; the toggle then controls the no-filter case.
    private boolean filterless = true;

    protected TileEntityQIOFilterHandler(String name) {
        super(name);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        // Kept only so worlds written by the early port can migrate their
        // single physical filter slot into the authoritative filter list.
        filterSlot = builder.addSlot(BasicInventorySlot.at(BasicInventorySlot.internalOnly,
              BasicInventorySlot.internalOnly, listener, 8, 30));
        filterSlot.setSlotType(ContainerSlotType.IGNORED);
        filterSlot.setEnabledSupplier(() -> false);
        return builder.build();
    }

    @Nullable
    protected TileEntity getAdjacentTile() {
        if (world == null) {
            return null;
        }
        return MekanismUtils.getTileEntity(world, getPos().offset(getAdjacentSide()));
    }

    protected EnumFacing getAdjacentSide() {
        return (facing == null ? EnumFacing.NORTH : facing).getOpposite();
    }

    protected EnumFacing getHandlerSide() {
        return facing == null ? EnumFacing.NORTH : facing;
    }

    public ItemStack getFilterStack() {
        migrateLegacyFilterSlot();
        for (QIOFilter filter : filters) {
            if (filter instanceof mekanism.common.content.qio.filter.QIOItemStackFilter) {
                return ((mekanism.common.content.qio.filter.QIOItemStackFilter) filter).getItemStack();
            }
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    public List<QIOFilter> getFilters() {
        migrateLegacyFilterSlot();
        List<QIOFilter> copies = new ArrayList<>(filters.size());
        for (QIOFilter filter : filters) {
            QIOFilter copy = copyFilter(filter);
            if (copy != null) {
                copies.add(copy);
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private HashList<QIOFilter> getFiltersForSync() {
        HashList<QIOFilter> synced = new HashList<>();
        for (QIOFilter filter : filters) {
            QIOFilter copy = copyFilter(filter);
            if (copy != null) {
                synced.add(copy);
            }
        }
        return synced;
    }

    private void setFiltersFromSync(HashList<QIOFilter> values) {
        filters.clear();
        if (values != null) {
            for (QIOFilter filter : values) {
                QIOFilter copy = copyFilter(filter);
                if (copy != null && filters.size() < 16 && !containsEquivalentFilter(copy)) {
                    filters.add(copy);
                }
            }
        }
    }

    public void setFilters(@Nullable Collection<? extends QIOFilter> values) {
        filters.clear();
        if (values != null) {
            for (QIOFilter filter : values) {
                QIOFilter copy = copyFilter(filter);
                if (copy != null && filters.size() < 16 && !containsEquivalentFilter(copy)) {
                    filters.add(copy);
                }
            }
        }
        markDirty();
    }

    public boolean addFilter(@Nullable QIOFilter filter) {
        if (filter == null || filters.size() >= 16 || containsEquivalentFilter(filter)) {
            return false;
        }
        QIOFilter copy = filter.copy();
        if (copy == null) {
            return false;
        }
        filters.add(copy);
        markDirty();
        return true;
    }

    public boolean removeFilter(int index) {
        if (index < 0 || index >= filters.size()) {
            return false;
        }
        filters.remove(index);
        markDirty();
        return true;
    }

    public boolean moveFilter(int index, int target) {
        if (index < 0 || index >= filters.size() || target < 0 || target >= filters.size() || index == target) {
            return false;
        }
        QIOFilter filter = filters.remove(index);
        filters.add(target, filter);
        markDirty();
        return true;
    }

    public boolean removeFilter(@Nullable QIOFilter filter) {
        if (filter != null && filters.remove(filter)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean setFilterEnabled(int index, boolean enabled) {
        if (index < 0 || index >= filters.size()) {
            return false;
        }
        QIOFilter filter = filters.get(index);
        if (filter.isEnabled() != enabled) {
            filter.setEnabled(enabled);
            markDirty();
        }
        return true;
    }

    public boolean replaceFilter(int index, @Nullable QIOFilter replacement) {
        if (index < 0 || index >= filters.size() || replacement == null || !replacement.hasFilter()) {
            return false;
        }
        QIOFilter copy = replacement.copy();
        if (copy == null) {
            return false;
        }
        for (int i = 0; i < filters.size(); i++) {
            if (i != index && filtersEquivalent(filters.get(i), copy)) {
                return false;
            }
        }
        filters.set(index, copy);
        markDirty();
        return true;
    }

    public void clearFilters() {
        boolean changed = !filters.isEmpty() || filterSlot != null && !filterSlot.getStack().isEmpty();
        if (!filters.isEmpty()) {
            filters.clear();
        }
        if (filterSlot != null && !filterSlot.getStack().isEmpty()) {
            filterSlot.setStackUnchecked(ItemStack.EMPTY);
        }
        if (changed) {
            markDirty();
        }
    }

    /** Replaces the filter set with one selected filter, preserving slot UI compatibility. */
    public void setFilter(@Nullable QIOFilter filter) {
        filters.clear();
        QIOFilter copy = copyFilter(filter);
        if (copy != null) {
            filters.add(copy);
        }
        if (filterSlot != null && !filterSlot.getStack().isEmpty()) {
            filterSlot.setStackUnchecked(ItemStack.EMPTY);
        }
        markDirty();
    }

    private void migrateLegacyFilterSlot() {
        if (filterSlot == null || filterSlot.getStack().isEmpty()) {
            return;
        }
        ItemStack legacy = filterSlot.getStack().copy();
        filterSlot.setStackUncheckedNoUpdate(ItemStack.EMPTY);
        QIOFilter migrated = new mekanism.common.content.qio.filter.QIOItemStackFilter(legacy);
        if (!containsEquivalentFilter(migrated) && filters.size() < 16) {
            filters.add(migrated);
        }
    }

    public boolean isFilterless() {
        return filterless;
    }

    public void setFilterless(boolean filterless) {
        if (this.filterless != filterless) {
            this.filterless = filterless;
            markDirty();
        }
    }

    public void toggleFilterless() {
        setFilterless(!filterless);
    }

    protected boolean acceptsItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        migrateLegacyFilterSlot();
        boolean hasFilter = false;
        for (QIOFilter filter : filters) {
            if (filter.isEnabled()) {
                hasFilter = true;
            }
            if (filter.test(stack)) {
                return true;
            }
        }
        return !hasFilter && filterless;
    }

    public boolean matchesItem(@Nullable ItemStack stack) {
        return acceptsItem(stack);
    }

    protected boolean acceptsFluid(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) {
            return false;
        }
        boolean hasFilter = false;
        for (QIOFilter filter : filters) {
            if (filter.isEnabled()) {
                hasFilter = true;
            }
            if (filter.test(fluid)) {
                return true;
            }
        }
        return !hasFilter && filterless;
    }

    public boolean matchesFluid(@Nullable FluidStack fluid) {
        return acceptsFluid(fluid);
    }

    protected boolean acceptsGas(@Nullable GasStack gas) {
        if (gas == null || gas.amount <= 0 || gas.getGas() == null) {
            return false;
        }
        boolean hasFilter = false;
        for (QIOFilter filter : filters) {
            if (filter.isEnabled()) {
                hasFilter = true;
            }
            if (filter.test(gas)) {
                return true;
            }
        }
        return !hasFilter && filterless;
    }

    public boolean matchesGas(@Nullable GasStack gas) {
        return acceptsGas(gas);
    }

    protected boolean acceptsResource(@Nullable QIOResourceEntry entry) {
        if (entry == null || entry.getAmount() <= 0) {
            return false;
        }
        switch (entry.getKind()) {
            case ITEM:
                return acceptsItem(entry.getItem());
            case FLUID:
                return acceptsFluid(entry.getFluid());
            case GAS:
                return acceptsGas(entry.getGas());
            default:
                return false;
        }
    }

    public boolean matchesResource(@Nullable QIOResourceEntry entry) {
        return acceptsResource(entry);
    }

    protected int getMaxTransitCount() {
        return 64;
    }

    protected int getMaxTransitTypes() {
        return 4;
    }

    @Nullable
    protected QIOFrequency getFrequencyForTransfer() {
        QIOFrequency frequency = getQIOFrequency();
        return frequency != null && frequency.isValid() && !frequency.isRemoved() ? frequency : null;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        writeSustainedQIOData(nbtTags);
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound nbtTags) {
        nbtTags.setBoolean("qioFilterless", filterless);
        NBTTagList storedFilters = new NBTTagList();
        for (QIOFilter filter : filters) {
            if (filter != null) {
                storedFilters.appendTag(filter.write());
            }
        }
        nbtTags.setTag("qioFilters", storedFilters);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        readSustainedQIOData(nbtTags);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound nbtTags) {
        filterless = !nbtTags.hasKey("qioFilterless") || nbtTags.getBoolean("qioFilterless");
        filters.clear();
        NBTTagList storedFilters = nbtTags.getTagList("qioFilters", NBT.TAG_COMPOUND);
        for (int i = 0; i < storedFilters.tagCount() && filters.size() < 16; i++) {
            QIOFilter filter = QIOFilter.read(storedFilters.getCompoundTagAt(i));
            if (filter != null && !containsEquivalentFilter(filter)) {
                filters.add(filter);
            }
        }
        migrateLegacyFilterSlot();
    }

    @Nullable
    private static QIOFilter copyFilter(@Nullable QIOFilter filter) {
        return filter == null ? null : filter.copy();
    }

    private boolean containsEquivalentFilter(QIOFilter candidate) {
        if (candidate == null) {
            return false;
        }
        for (QIOFilter existing : filters) {
            if (filtersEquivalent(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean filtersEquivalent(QIOFilter first, QIOFilter second) {
        net.minecraft.nbt.NBTTagCompound firstData = first.write();
        firstData.setBoolean("enabled", true);
        net.minecraft.nbt.NBTTagCompound secondData = second.write();
        secondData.setBoolean("enabled", true);
        return firstData.equals(secondData);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::isFilterless, this::setFilterless));
        container.track(SyncableFilterList.create(this::getFiltersForSync, this::setFiltersFromSync,
              FilterListType.QIO, QIOFilter::writeToPacket));
    }
}
