package mekanism.common.lib.inventory;

import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.item.CursedTransporterItemHandler;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.transmitters.TransporterImpl;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public abstract class TransitRequest implements Iterable<TransitRequest.ItemData> {

    private final TransitResponse empty = new TransitResponse(ItemStack.EMPTY, null);

    public static SimpleTransitRequest simple(ItemStack stack) {
        return new SimpleTransitRequest(stack);
    }

    public static TransitRequest getFromTransport(TransporterStack stack) {
        return getFromStack(stack.itemStack);
    }

    public static TransitRequest getFromStack(ItemStack stack) {
        return simple(stack);
    }

    public static TransitRequest anyItem(@Nullable IItemHandler inventory, int amount) {
        return definedItem(inventory, amount, Finder.ANY);
    }

    public static TransitRequest definedItem(@Nullable IItemHandler inventory, int amount, Finder finder) {
        return definedItem(inventory, 1, amount, finder);
    }

    public static TransitRequest definedItem(@Nullable IItemHandler inventory, int min, int max, Finder finder) {
        HandlerTransitRequest ret = new HandlerTransitRequest(inventory);
        if (inventory == null) {
            return ret;
        }
        for (int i = inventory.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = inventory.extractItem(i, max, true);
            if (!stack.isEmpty() && finder.modifies(stack)) {
                HashedItem hashed = HashedItem.raw(stack);
                int toUse = Math.min(stack.getCount(), max - ret.getCount(hashed));
                if (toUse == 0) {
                    continue;
                }
                ret.addItem(StackUtils.size(stack, toUse), i);
            }
        }
        for (Iterator<ItemData> iterator = ret.iterator(); iterator.hasNext(); ) {
            if (iterator.next().getTotalCount() < min) {
                iterator.remove();
            }
        }
        return ret;
    }

    public static TransitRequest anyItem(TileEntity tile, EnumFacing side, int amount) {
        return definedItem(tile, side, amount, Finder.ANY);
    }

    public static TransitRequest buildInventoryMap(TileEntity tile, EnumFacing side, int amount) {
        return buildInventoryMap(tile, side, amount, Finder.ANY);
    }

    public static TransitRequest buildInventoryMap(TileEntity tile, EnumFacing side, int amount, Finder finder) {
        return definedItem(tile, side.getOpposite(), amount, finder);
    }

    public static TransitRequest definedItem(TileEntity tile, EnumFacing side, int amount, Finder finder) {
        return definedItem(tile, side, 1, amount, finder);
    }

    public static TransitRequest definedItem(TileEntity tile, EnumFacing side, int min, int max, Finder finder) {
        if (!InventoryUtils.assertItemHandler("TransitRequest", tile, side)) {
            return new TileTransitRequest(tile, side);
        }
        TileTransitRequest ret = new TileTransitRequest(tile, side);
        IItemHandler inventory = InventoryUtils.getItemHandler(tile, side);
        for (int i = inventory.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = inventory.extractItem(i, max, true);
            if (!stack.isEmpty() && finder.modifies(stack)) {
                HashedItem hashed = HashedItem.raw(stack);
                int toUse = Math.min(stack.getCount(), max - ret.getCount(hashed));
                if (toUse == 0) {
                    continue;
                }
                ret.addItem(StackUtils.size(stack, toUse), i);
            }
        }
        for (Iterator<ItemData> iterator = ret.iterator(); iterator.hasNext(); ) {
            if (iterator.next().getTotalCount() < min) {
                iterator.remove();
            }
        }
        return ret;
    }

    @Nonnull
    public TransitResponse addToInventory(TileEntity tile, EnumFacing side, int min, boolean force) {
        if (isEmpty()) {
            return getEmptyResponse();
        } else if (force && tile instanceof IAdvancedTransportEjector ejector) {
            return ejector.sendHome(this);
        }
        IItemHandler inventory = InventoryUtils.getItemHandler(tile, side.getOpposite());
        return addToInventoryUnchecked(inventory, min);
    }

    @Nonnull
    public TransitResponse eject(TileEntity outputter, @Nullable IItemHandler target, int min,
          Function<Object, mekanism.api.EnumColor> outputColor) {
        if (isEmpty()) {
            return getEmptyResponse();
        } else if (target instanceof CursedTransporterItemHandler cursed) {
            TransporterImpl transporter = cursed.getTransporter();
            return transporter.insert(outputter == null ? cursed.getFromPos() : Coord4D.get(outputter), this, outputColor.apply(transporter), true, min);
        }
        return addToInventoryUnchecked(target, min);
    }

    @Nonnull
    public TransitResponse addToInventoryUnchecked(@Nullable IItemHandler inventory, int min) {
        if (inventory == null) {
            return getEmptyResponse();
        }
        int slots = inventory.getSlots();
        if (slots == 0) {
            return getEmptyResponse();
        }
        if (min > 1) {
            TransitResponse response = getPredictedInsert(inventory);
            if (response.isEmpty() || response.getSendingAmount() < min) {
                return getEmptyResponse();
            }
        }
        for (ItemData data : this) {
            ItemStack origInsert = StackUtils.size(data.getStack(), data.getTotalCount());
            ItemStack toInsert = origInsert.copy();
            for (int i = 0; i < slots; i++) {
                toInsert = inventory.insertItem(i, toInsert, false);
                if (toInsert.isEmpty()) {
                    return createResponse(origInsert, data);
                }
            }
            if (TransporterManager.didEmit(origInsert, toInsert)) {
                return createResponse(TransporterManager.getToUse(origInsert, toInsert), data);
            }
        }
        return getEmptyResponse();
    }

    @Nonnull
    public TransitResponse getPredictedInsert(IItemHandler inventory) {
        for (ItemData data : this) {
            ItemStack origInsert = StackUtils.size(data.getStack(), data.getTotalCount());
            ItemStack toInsert = origInsert.copy();
            for (int i = 0; i < inventory.getSlots(); i++) {
                toInsert = inventory.insertItem(i, toInsert, true);
                if (toInsert.isEmpty()) {
                    return createResponse(origInsert, data);
                }
            }
            if (TransporterManager.didEmit(origInsert, toInsert)) {
                return createResponse(TransporterManager.getToUse(origInsert, toInsert), data);
            }
        }
        return getEmptyResponse();
    }

    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Nonnull
    public TransitResponse createResponse(ItemStack inserted, ItemData data) {
        return new TransitResponse(inserted, data);
    }

    @Nonnull
    public TransitResponse createSimpleResponse() {
        for (ItemData data : this) {
            return createResponse(data.getStack(), data);
        }
        return getEmptyResponse();
    }

    @Nonnull
    public TransitResponse getEmptyResponse() {
        return empty;
    }

    public static class TransitResponse {

        public static final TransitResponse EMPTY = new TransitResponse(ItemStack.EMPTY, null);

        private final ItemStack inserted;
        private final ItemData slotData;

        public TransitResponse(@Nonnull ItemStack inserted, @Nullable ItemData slotData) {
            this.inserted = inserted;
            this.slotData = slotData;
        }

        public int getSendingAmount() {
            return inserted.getCount();
        }

        @Nullable
        public ItemData getSlotData() {
            return slotData;
        }

        @Nonnull
        public ItemStack getStack() {
            return inserted;
        }

        public boolean isEmpty() {
            return inserted.isEmpty() || slotData == null || slotData.getTotalCount() == 0;
        }

        @Nonnull
        public ItemStack getRejected() {
            if (isEmpty()) {
                return ItemStack.EMPTY;
            }
            return slotData.getItemType().createStack(slotData.getTotalCount() - getSendingAmount());
        }

        @Nonnull
        public ItemStack getRejected(ItemStack orig) {
            return isEmpty() ? orig : StackUtils.size(orig, orig.getCount() - getSendingAmount());
        }

        @Nonnull
        public ItemStack use(int amount) {
            return slotData == null ? ItemStack.EMPTY : slotData.use(amount);
        }

        @Nonnull
        public ItemStack useAll() {
            return use(getSendingAmount());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            TransitResponse other = (TransitResponse) obj;
            return (inserted == other.inserted || ItemStack.areItemStacksEqual(inserted, other.inserted)) && Objects.equals(slotData, other.slotData);
        }

        @Override
        public int hashCode() {
            int code = 1;
            code = 31 * code + inserted.getItem().hashCode();
            code = 31 * code + inserted.getCount();
            if (inserted.hasTagCompound()) {
                code = 31 * code + inserted.getTagCompound().hashCode();
            }
            code = 31 * code + Objects.hashCode(slotData);
            return code;
        }
    }

    public static class ItemData {

        private final HashedItem itemType;
        protected int totalCount;

        public ItemData(HashedItem itemType) {
            this.itemType = itemType;
        }

        public HashedItem getItemType() {
            return itemType;
        }

        public int getTotalCount() {
            return totalCount;
        }

        @Nonnull
        public ItemStack getStack() {
            return getItemType().createStack(getTotalCount());
        }

        @Nonnull
        public ItemStack use(int amount) {
            Mekanism.logger.error("Can't 'use' with this type of TransitResponse: {}", getClass().getName());
            return ItemStack.EMPTY;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ItemData itemData = (ItemData) obj;
            return getTotalCount() == itemData.getTotalCount() && getItemType().equals(itemData.getItemType());
        }

        @Override
        public int hashCode() {
            return 31 * getItemType().hashCode() + getTotalCount();
        }
    }

    public static class SimpleTransitRequest extends CollectionTransitRequest {

        private final List<ItemData> slotData;

        protected SimpleTransitRequest(ItemStack stack) {
            slotData = Collections.singletonList(new SimpleItemData(stack));
        }

        @Override
        public List<ItemData> getItemData() {
            return slotData;
        }

        public static class SimpleItemData extends ItemData {

            public SimpleItemData(ItemStack stack) {
                super(HashedItem.create(stack));
                totalCount = stack.getCount();
            }
        }
    }
}
