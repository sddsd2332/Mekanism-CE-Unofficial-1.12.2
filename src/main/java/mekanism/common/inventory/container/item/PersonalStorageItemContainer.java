package mekanism.common.inventory.container.item;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.InventoryPersonalChest;
import mekanism.common.inventory.container.slot.HotBarSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import javax.annotation.Nonnull;

public class PersonalStorageItemContainer extends MekanismItemContainer {

    private final InventoryPersonalChest itemInventory;

    public PersonalStorageItemContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        this(inv, hand, stack, new InventoryPersonalChest(stack, hand));
    }

    public PersonalStorageItemContainer(InventoryPlayer inv, InventoryPersonalChest itemInventory) {
        this(inv, itemInventory.currentHand, itemInventory.getStack(), itemInventory);
    }

    private PersonalStorageItemContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack, InventoryPersonalChest itemInventory) {
        super(inv, hand, stack);
        this.itemInventory = itemInventory;
        super.addSlotsAndOpen();
    }

    @Override
    protected void addSlotsAndOpen() {
        // The item inventory must be initialized before its slots are added.
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        for (IInventorySlot inventorySlot : itemInventory.getInventorySlots(null)) {
            Slot containerSlot = inventorySlot.createContainerSlot();
            if (containerSlot != null) {
                addSlot(containerSlot);
            }
        }
    }

    @Override
    protected int getInventoryYOffset() {
        return 140;
    }

    @Override
    protected void openInventory(@Nonnull InventoryPlayer inv) {
        super.openInventory(inv);
        itemInventory.openInventory(inv.player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        super.closeInventory(player);
        itemInventory.closeInventory(player);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return super.canInteractWith(player) && MachineType.get(stack) == MachineType.PERSONAL_CHEST;
    }

    @Override
    protected HotBarSlot createHotBarSlot(@Nonnull InventoryPlayer inv, int index, int x, int y) {
        if (hand == EnumHand.MAIN_HAND && index == inv.currentItem) {
            return new HotBarSlot(inv, index, x, y) {

                @Override
                public boolean canTakeStack(@Nonnull EntityPlayer player) {
                    return false;
                }
            };
        }
        return super.createHotBarSlot(inv, index, x, y);
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (clickType == ClickType.SWAP) {
            if (hand == EnumHand.OFF_HAND && dragType == 40) {
                return ItemStack.EMPTY;
            } else if (hand == EnumHand.MAIN_HAND && dragType >= 0 && dragType < hotBarSlots.size() && !hotBarSlots.get(dragType).canTakeStack(player)) {
                return ItemStack.EMPTY;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }
}
