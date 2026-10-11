package mekanism.common.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

/**
 * Owned holder layout with an O(1) revision check, including same-size replacements and sublists.
 */
public final class RevisionedList<E> extends AbstractList<E> implements RandomAccess {
    private final List<E> values = new ArrayList<>();
    private long revision;

    public long getRevision() {
        return revision;
    }

    @Override
    public E get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public E set(int index, E element) {
        E previous = values.set(index, element);
        revision++;
        return previous;
    }

    @Override
    public void add(int index, E element) {
        values.add(index, element);
        revision++;
        modCount++;
    }

    @Override
    public E remove(int index) {
        E previous = values.remove(index);
        revision++;
        modCount++;
        return previous;
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        values.subList(fromIndex, toIndex).clear();
        revision++;
        modCount++;
    }
}
