package mekanism.common.content.filter;

import io.netty.buffer.ByteBuf;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.common.HashList;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableFilterList;
import mekanism.common.network.to_client.container.property.FilterListPropertyData.FilterListType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class FilterManager<FILTER extends IFilter> {

    private final Class<? extends FILTER> filterClass;
    protected final Runnable markForSave;
    protected final HashList<FILTER> filters;
    @Nullable
    protected List<FILTER> enabledFilters;

    public FilterManager(Class<? extends FILTER> filterClass, Runnable markForSave) {
        this(filterClass, new HashList<>(), markForSave);
    }

    public FilterManager(Class<? extends FILTER> filterClass, HashList<FILTER> filters, Runnable markForSave) {
        this.filterClass = filterClass;
        this.filters = filters;
        this.markForSave = markForSave;
    }

    public HashList<FILTER> getFilters() {
        return filters;
    }

    public List<FILTER> getEnabledFilters() {
        if (enabledFilters == null) {
            enabledFilters = new ArrayList<>();
            for (FILTER filter : filters) {
                if (filter.isEnabled()) {
                    enabledFilters.add(filter);
                }
            }
        }
        return enabledFilters;
    }

    public int count() {
        return filters.size();
    }

    public boolean hasEnabledFilters() {
        return !getEnabledFilters().isEmpty();
    }

    public boolean anyEnabledMatch(Predicate<FILTER> validator) {
        return getEnabledFilters().stream().anyMatch(validator);
    }

    public boolean isEmpty() {
        return filters.isEmpty();
    }

    public void toggleState(int index) {
        FILTER filter = index >= 0 && index < filters.size() ? filters.get(index) : null;
        if (filter != null) {
            filter.setEnabled(!filter.isEnabled());
            enabledFilters = null;
            markForSave.run();
        }
    }

    public boolean tryAddFilter(@Nullable IFilter toAdd, boolean save) {
        return filterClass.isInstance(toAdd) && addFilter(filterClass.cast(toAdd), save);
    }

    public boolean addFilter(FILTER filter) {
        return addFilter(filter, true);
    }

    public boolean addFilter(FILTER filter, boolean save) {
        if (filters.contains(filter)) {
            return false;
        }
        filters.add(filter);
        if (save) {
            markForSave.run();
        }
        if (enabledFilters != null && filter.isEnabled()) {
            enabledFilters.add(filter);
        }
        return true;
    }

    public boolean removeFilter(FILTER filter) {
        if (!filters.contains(filter)) {
            return false;
        }
        filters.remove(filter);
        markForSave.run();
        if (filter.isEnabled()) {
            enabledFilters = null;
        }
        return true;
    }

    public boolean tryEditFilter(@Nullable IFilter currentFilter, @Nullable IFilter newFilter) {
        if (!filterClass.isInstance(currentFilter)) {
            return false;
        }
        FILTER current = filterClass.cast(currentFilter);
        if (newFilter == null) {
            return removeFilter(current);
        }
        return filterClass.isInstance(newFilter) && editFilter(current, filterClass.cast(newFilter));
    }

    public boolean editFilter(FILTER currentFilter, FILTER newFilter) {
        int index = filters.indexOf(currentFilter);
        if (index < 0) {
            return false;
        }
        filters.replace(index, newFilter);
        markForSave.run();
        if (currentFilter.isEnabled() || newFilter.isEnabled()) {
            enabledFilters = null;
        }
        return true;
    }

    public void clear() {
        filters.clear();
        enabledFilters = null;
    }

    public void writeToNBT(NBTTagCompound nbtTags, BiConsumer<FILTER, NBTTagCompound> writer) {
        NBTTagList filterTags = new NBTTagList();
        for (FILTER filter : filters) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            writer.accept(filter, tagCompound);
            filterTags.appendTag(tagCompound);
        }
        if (filterTags.tagCount() != 0) {
            nbtTags.setTag(NBTConstants.FILTERS, filterTags);
        }
    }

    public void readFromNBT(NBTTagCompound nbtTags, Function<NBTTagCompound, FILTER> reader) {
        filters.clear();
        enabledFilters = new ArrayList<>();
        if (nbtTags.hasKey(NBTConstants.FILTERS)) {
            readFromNBTList(nbtTags.getTagList(NBTConstants.FILTERS, NBT.TAG_COMPOUND), reader);
        }
    }

    public void readFromNBTList(NBTTagList tagList, Function<NBTTagCompound, FILTER> reader) {
        filters.clear();
        enabledFilters = new ArrayList<>();
        for (int i = 0; i < tagList.tagCount(); i++) {
            FILTER filter = reader.apply(tagList.getCompoundTagAt(i));
            if (filter != null) {
                addFilter(filter, false);
            }
        }
    }

    public void writeToPacket(TileNetworkList data, BiConsumer<FILTER, TileNetworkList> writer) {
        data.add(filters.size());
        for (FILTER filter : filters) {
            writer.accept(filter, data);
        }
    }

    public void readFromPacket(ByteBuf dataStream, Function<ByteBuf, FILTER> reader) {
        filters.clear();
        enabledFilters = new ArrayList<>();
        int amount = dataStream.readInt();
        for (int i = 0; i < amount; i++) {
            FILTER filter = reader.apply(dataStream);
            if (filter != null) {
                addFilter(filter, false);
            }
        }
    }

    public void addContainerTrackers(MekanismContainer container, FilterListType type, BiConsumer<FILTER, TileNetworkList> writer) {
        container.track(SyncableFilterList.create(this::getFilters, value -> {
            filters.clear();
            for (FILTER filter : value) {
                filters.add(filter);
            }
            enabledFilters = null;
        }, type, writer));
    }
}
