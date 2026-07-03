package mekanism.common.lib.inventory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public abstract class CollectionTransitRequest extends TransitRequest {

    protected abstract Collection<? extends ItemData> getItemData();

    @Override
    public boolean isEmpty() {
        return getItemData().isEmpty();
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<ItemData> iterator() {
        return (Iterator<ItemData>) getItemData().iterator();
    }

    @Override
    public void forEach(Consumer<? super ItemData> action) {
        getItemData().forEach(action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Spliterator<ItemData> spliterator() {
        return (Spliterator<ItemData>) getItemData().spliterator();
    }
}
