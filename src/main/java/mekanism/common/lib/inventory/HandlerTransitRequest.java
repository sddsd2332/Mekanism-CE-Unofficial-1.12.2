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
            if (handler != null && !slotMap.isEmpty() && amount > 0) {
                HashedItem itemType = getItemType();
                ItemStack itemStack = itemType.getInternalStack();
                int extractedTotal = 0;
                for (ObjectIterator<Int2IntMap.Entry> iterator = slotMap.int2IntEntrySet().iterator(); iterator.hasNext(); ) {
                    Int2IntMap.Entry entry = iterator.next();
                    int slot = entry.getIntKey();
                    int currentCount = entry.getIntValue();
                    int toUse = Math.min(amount, currentCount);
                    if (toUse <= 0) {
                        continue;
                    }
                    ItemStack ret = handler.extractItem(slot, toUse, false);
                    boolean stackable = InventoryUtils.areItemsStackable(itemStack, ret);
                    int extracted = ret.isEmpty() ? 0 : ret.getCount();
                    if (!stackable) {
                        Mekanism.logger.warn("An inventory's returned content {} does not line up with HandlerTransitRequest's prediction.", stackable ? "count" : "type");
                        Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, slot: {}", itemStack, toUse, ret, slot);
                        Mekanism.logger.warn("ItemHandler: {}", handler.getClass().getName());
                        // Forge has no transaction API in 1.12. If the handler
                        // changed type between planning and execution, restore
                        // the unexpected stack before aborting this response.
                        if (!ret.isEmpty()) {
                            ItemStack remainder = handler.insertItem(slot, ret, false);
                            if (!remainder.isEmpty()) {
                                Mekanism.logger.error("Unable to restore mismatched ItemHandler extraction: {}", remainder);
                            }
                        }
                        break;
                    }
                    if (extracted != toUse) {
                        Mekanism.logger.warn("An inventory's returned content count does not line up with HandlerTransitRequest's prediction.");
                        Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, slot: {}", itemStack, toUse, ret, slot);
                        Mekanism.logger.warn("ItemHandler: {}", handler.getClass().getName());
                    }
                    // Never debit the prediction. The handler's returned stack
                    // is the only authoritative amount in this non-transactional
                    // 1.12 API. A short return remains in slotMap and can be
                    // retried by the next request without creating phantom items.
                    extracted = Math.min(toUse, Math.max(0, extracted));
                    if (extracted == 0) {
                        break;
                    }
                    amount -= extracted;
                    totalCount -= extracted;
                    if (totalCount == 0) {
                        itemMap.remove(itemType);
                    }
                    currentCount -= extracted;
                    extractedTotal += extracted;
                    if (currentCount <= 0) {
                        iterator.remove();
                    } else {
                        entry.setValue(currentCount);
                    }
                    if (amount == 0) {
                        break;
                    }
                }
                return extractedTotal == 0 ? ItemStack.EMPTY : itemType.createStack(extractedTotal);
            }
            return ItemStack.EMPTY;
        }
    }
}
