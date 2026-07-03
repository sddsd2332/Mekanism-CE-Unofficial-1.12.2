package mekanism.common.inventory.container.item;

import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;

public abstract class MekanismItemContainer extends MekanismContainer {

    protected final EnumHand hand;
    protected ItemStack stack;

    protected MekanismItemContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv);
        this.hand = hand;
        this.stack = stack;
        if (!stack.isEmpty()) {
            addContainerTrackers();
        }
        addSlotsAndOpen();
    }

    protected void addContainerTrackers() {
        if (stack.getItem() instanceof IItemContainerTracker containerTracker) {
            containerTracker.addContainerTrackers(this, stack);
        }
    }

    @Override
    protected void addInventorySlots(@Nonnull InventoryPlayer inv) {
        super.addInventorySlots(inv);
        if (offhandSlots.isEmpty()) {
            track(SyncableItemStack.create(() -> inv.player.getHeldItemOffhand(), item -> {
                inv.offHandInventory.set(0, item);
                if (hand == EnumHand.OFF_HAND && !item.isEmpty() && item.getItem() == stack.getItem()) {
                    stack = item;
                }
            }));
        }
        if (hotBarSlots.isEmpty()) {
            if (hand == EnumHand.MAIN_HAND) {
                track(SyncableItemStack.create(() -> inv.player.getHeldItemMainhand(), item -> {
                    inv.mainInventory.set(inv.currentItem, item);
                    if (!item.isEmpty() && item.getItem() == stack.getItem()) {
                        stack = item;
                    }
                }));
            }
            for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
                if (i != inv.currentItem || hand != EnumHand.MAIN_HAND) {
                    int index = i;
                    track(SyncableItemStack.create(() -> inv.mainInventory.get(index), item -> inv.mainInventory.set(index, item)));
                }
            }
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        ItemStack held = player.getHeldItem(hand);
        return !stack.isEmpty() && !held.isEmpty() && held.getItem() == stack.getItem();
    }

    public EnumHand getHand() {
        return hand;
    }

    public interface IItemContainerTracker {

        void addContainerTrackers(MekanismContainer container, ItemStack stack);
    }
}
