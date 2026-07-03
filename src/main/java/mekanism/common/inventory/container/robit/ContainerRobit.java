package mekanism.common.inventory.container.robit;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.entity.EntityRobit;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import javax.annotation.Nonnull;

public abstract class ContainerRobit extends MekanismContainer {

    public EntityRobit robit;

    protected ContainerRobit(InventoryPlayer inventory, EntityRobit robit) {
        super(inventory);
        this.robit = robit;
        robit.addContainerTrackers(this, getType());
        addSlotsAndOpen();
    }

    protected abstract EntityRobit.ContainerType getType();

    protected void addRobitInventorySlots(EntityRobit.ContainerType containerType) {
        for (IInventorySlot inventorySlot : robit.getContainerInventorySlots(containerType)) {
            Slot slot = inventorySlot.createContainerSlot();
            if (slot != null) {
                addSlot(slot);
            }
        }
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addRobitInventorySlots(getType());
    }

    @Override
    protected void openInventory(@Nonnull InventoryPlayer inv) {
        super.openInventory(inv);
        robit.openInventory(inv.player);
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        super.closeInventory(player);
        robit.closeInventory(player);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer entityplayer) {
        return !robit.isDead && SecurityUtils.canAccess(entityplayer, robit);
    }
}
