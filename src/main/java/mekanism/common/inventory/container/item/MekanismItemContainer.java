package mekanism.common.inventory.container.item;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;

public abstract class MekanismItemContainer extends MekanismContainer
      implements IItemStackBackedContainer {

    protected final EnumHand hand;
    protected final ItemStackSlotAccess itemAccess;

    protected MekanismItemContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        this(inv, hand, ItemStackSlotAccess.getSlotForHand(inv, hand), stack);
    }

    protected MekanismItemContainer(InventoryPlayer inv, EnumHand hand, int itemSlot, ItemStack stack) {
        this(inv, new ItemStackSlotAccess(inv, hand, itemSlot, stack));
    }

    protected MekanismItemContainer(InventoryPlayer inv, ItemStackSlotAccess itemAccess) {
        super(inv);
        this.hand = itemAccess.getHand();
        this.itemAccess = itemAccess;
        if (isValidStack(getStack())) {
            addContainerTrackers();
        }
        addSlotsAndOpen();
    }

    protected void addContainerTrackers() {
        ItemStack stack = getStack();
        if (stack.getItem() instanceof IItemContainerTracker containerTracker) {
            containerTracker.addContainerTrackers(this, stack);
        }
    }

    protected boolean isValidStack(@Nonnull ItemStack current) {
        ItemStack openingStack = itemAccess.getOpeningStack();
        return !current.isEmpty() && !openingStack.isEmpty() && current.getItem() == openingStack.getItem();
    }

    @Override
    protected void addInventorySlots(@Nonnull InventoryPlayer inv) {
        super.addInventorySlots(inv);
        if (offhandSlots.isEmpty()) {
            track(SyncableItemStack.create(() -> inv.player.getHeldItemOffhand(), item -> inv.offHandInventory.set(0, item)));
        }
        if (hotBarSlots.isEmpty()) {
            if (hand == EnumHand.MAIN_HAND) {
                track(SyncableItemStack.create(itemAccess::getStack, itemAccess::setStack));
            }
            for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
                if (i != itemAccess.getSlot() || hand != EnumHand.MAIN_HAND) {
                    int index = i;
                    track(SyncableItemStack.create(() -> inv.mainInventory.get(index), item -> inv.mainInventory.set(index, item)));
                }
            }
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        ItemStack current = getStack();
        return itemAccess.isOriginalStackPresent() && isValidStack(current) &&
              (player.world.isRemote || SecurityUtils.canAccess(player, current));
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!player.world.isRemote && !canInteractWith(player)) {
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Nonnull
    public ItemStack getStack() {
        return itemAccess.getStack();
    }

    public int getItemSlot() {
        return itemAccess.getSlot();
    }

    @Nonnull
    @Override
    public ItemStackSlotAccess getItemAccess() {
        return itemAccess;
    }

    public EnumHand getHand() {
        return hand;
    }

    public interface IItemContainerTracker {

        void addContainerTrackers(MekanismContainer container, ItemStack stack);
    }
}
