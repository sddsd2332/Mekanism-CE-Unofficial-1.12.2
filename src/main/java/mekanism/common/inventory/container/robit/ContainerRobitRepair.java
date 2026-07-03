package mekanism.common.inventory.container.robit;

import mekanism.common.entity.EntityRobit;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

public class ContainerRobitRepair extends ContainerRepair {

    public EntityRobit robit;

    public ContainerRobitRepair(InventoryPlayer inventory, EntityRobit entity) {
        super(inventory, entity.world, BlockPos.ORIGIN, inventory.player);
        robit = entity;
        robit.openInventory(inventory.player);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer entityplayer) {
        return !robit.isDead && SecurityUtils.canAccess(entityplayer, robit);
    }

    @Override
    public void onContainerClosed(@Nonnull EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        robit.closeInventory(playerIn);
    }
}
