package mekanism.common.lib.inventory;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import mekanism.common.Mekanism;
import mekanism.common.util.InventoryUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HandlerTransitRequest extends CollectionTransitRequest {

    private final IItemHandler handler;
    private Map<HashedItem, HandlerItemData> itemMap = Collections.emptyMap();

    public HandlerTransitRequest(IItemHandler handler) {
        this.handler = handler;
    }

    public void addItem(ItemStack stack, int slot) {
        HashedItem hashed = HashedItem.create(stack);
        if (itemMap.isEmpty()) {
            itemMap = new LinkedHashMap<>();
        }
        itemMap.computeIfAbsent(hashed, HandlerItemData::new).addSlot(slot, stack);
    }

    public int getCount(HashedItem itemType) {
        ItemData data = itemMap.get(itemType);
        return data == null ? 0 : data.getTotalCount();
    }

    protected IItemHandler getHandler() {
        return handler;
    }

    @Override
    public Collection<HandlerItemData> getItemData() {
        return itemMap.values();
    }

    @Override
    public boolean isEmpty() {
        return itemMap.isEmpty();
    }

    public class HandlerItemData extends ItemData {

        private final Int2IntMap slotMap = new Int2IntLinkedOpenHashMap();

        public HandlerItemData(HashedItem itemType) {
            super(itemType);
        }

        public void addSlot(int id, ItemStack stack) {
            slotMap.put(id, stack.getCount());
            totalCount += stack.getCount();
        }

        @Override
        public ItemStack use(int amount) {
            IItemHandler handler = getHandler();
            if (handler != null && !slotMap.isEmpty()) {
                HashedItem itemType = getItemType();
                ItemStack itemStack = itemType.getInternalStack();
                for (ObjectIterator<Int2IntMap.Entry> iterator = slotMap.int2IntEntrySet().iterator(); iterator.hasNext(); ) {
                    Int2IntMap.Entry entry = iterator.next();
                    int slot = entry.getIntKey();
                    int currentCount = entry.getIntValue();
                    int toUse = Math.min(amount, currentCount);
                    ItemStack ret = handler.extractItem(slot, toUse, false);
                    boolean stackable = InventoryUtils.areItemsStackable(itemStack, ret);
                    if (!stackable || ret.getCount() != toUse) {
                        Mekanism.logger.warn("An inventory's returned content {} does not line up with HandlerTransitRequest's prediction.", stackable ? "count" : "type");
                        Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, slot: {}", itemStack, toUse, ret, slot);
                        Mekanism.logger.warn("ItemHandler: {}", handler.getClass().getName());
                    }
                    amount -= toUse;
                    totalCount -= toUse;
                    if (totalCount == 0) {
                        itemMap.remove(itemType);
                    }
                    currentCount -= toUse;
                    if (currentCount == 0) {
                        iterator.remove();
                    } else {
                        entry.setValue(currentCount);
                    }
                    if (amount == 0) {
                        break;
                    }
                }
            }
            return getStack();
        }
    }
}
