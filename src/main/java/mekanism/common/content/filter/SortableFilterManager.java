package mekanism.common.content.filter;

import mekanism.common.HashList;

public class SortableFilterManager<FILTER extends IFilter> extends FilterManager<FILTER> {

    public SortableFilterManager(Class<? extends FILTER> filterClass, Runnable markForSave) {
        super(filterClass, markForSave);
    }

    public SortableFilterManager(Class<? extends FILTER> filterClass, HashList<FILTER> filters, Runnable markForSave) {
        super(filterClass, filters, markForSave);
    }

    public boolean moveUp(int filterIndex) {
        return move(filterIndex, filterIndex - 1);
    }

    public boolean moveDown(int filterIndex) {
        return move(filterIndex, filterIndex + 1);
    }

    public boolean moveToTop(int filterIndex) {
        return moveTo(filterIndex, 0);
    }

    public boolean moveToBottom(int filterIndex) {
        return moveTo(filterIndex, count() - 1);
    }

    private boolean move(int source, int target) {
        if (source == target || source < 0 || target < 0 || source >= count() || target >= count()) {
            return false;
        }
        filters.swap(source, target);
        markForSave.run();
        if (filters.get(source).isEnabled() && filters.get(target).isEnabled()) {
            enabledFilters = null;
        }
        return true;
    }

    private boolean moveTo(int source, int target) {
        if (source == target || source < 0 || target < 0 || source >= count() || target >= count()) {
            return false;
        }
        FILTER sourceFilter = filters.get(source);
        filters.remove(source);
        filters.add(target, sourceFilter);
        markForSave.run();
        if (sourceFilter.isEnabled()) {
            enabledFilters = null;
        }
        return true;
    }
}
