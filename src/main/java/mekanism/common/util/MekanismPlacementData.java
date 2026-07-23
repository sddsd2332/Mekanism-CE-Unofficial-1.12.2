package mekanism.common.util;

import mekanism.common.Mekanism;
import mekanism.common.Upgrade;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ISustainedTank;
import mekanism.common.base.ITileNetwork;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/** Shared block-item data restoration used by vanilla placement and external placement tools. */
public final class MekanismPlacementData {

    private MekanismPlacementData() {
    }

    /**
     * Applies item-owned placement data and synchronizes the completed tile state once.
     * Call this at the end of a block's {@code onBlockPlacedBy} initialization.
     */
    public static boolean apply(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EntityLivingBase placer, @Nonnull ItemStack stack) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity == null || stack.isEmpty() || !(stack.getItem() instanceof IItemBlockPlacementData placementData)) {
            return false;
        }
        placementData.restorePlacementData(stack, placer, world, pos, tileEntity);
        tileEntity.markDirty();
        world.markBlockRangeForRenderUpdate(pos, pos);
        if (!world.isRemote && tileEntity instanceof ITileNetwork && Mekanism.packetHandler != null) {
            sendFinalUpdate(tileEntity);
        }
        return true;
    }

    /**
     * Restores data shared by Mekanism machine items. Item-specific tier and capacity data
     * must be restored before invoking this method.
     */
    public static void restoreCommon(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull TileEntity tileEntity) {
        if (tileEntity instanceof IUpgradeTile upgradeTile && Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(stack))) {
            upgradeTile.readUpgrades(ItemDataUtils.getDataMap(stack));
        }
        if (tileEntity instanceof ISideConfiguration config && ItemDataUtils.hasData(stack, "sideDataStored")) {
            config.getConfig().read(ItemDataUtils.getDataMap(stack));
            config.getEjector().read(ItemDataUtils.getDataMap(stack));
        }
        if (tileEntity instanceof ISustainedData data && stack.hasTagCompound()) {
            data.readSustainedData(stack);
        }
        if (tileEntity instanceof IRedstoneControl redstoneControl && ItemDataUtils.hasData(stack, "controlType")) {
            int controlType = ItemDataUtils.getInt(stack, "controlType");
            IRedstoneControl.RedstoneControl[] values = IRedstoneControl.RedstoneControl.values();
            if (controlType >= 0 && controlType < values.length) {
                redstoneControl.setControlType(values[controlType]);
            }
        }
        if (tileEntity instanceof ISustainedTank tileTank && stack.getItem() instanceof ISustainedTank itemTank && itemTank.hasTank(stack)) {
            if (itemTank.getFluidStack(stack) != null) {
                tileTank.setFluidStack(itemTank.getFluidStack(stack));
            }
        }
        if (tileEntity instanceof ISustainedInventory tileInventory && stack.getItem() instanceof ISustainedInventory itemInventory) {
            tileInventory.setInventory(itemInventory.getInventory(stack));
        }
        if (tileEntity instanceof TileEntityElectricBlock electricBlock) {
            electricBlock.setEnergy(StorageUtils.getStoredEnergyForDisplay(stack));
        }
        if (tileEntity instanceof ISecurityTile securityTile && stack.getItem() instanceof ISecurityItem securityItem) {
            securityTile.getSecurity().setOwnerUUID(securityItem.getOwnerUUID(stack));
            if (securityItem.hasSecurity(stack)) {
                securityTile.getSecurity().setMode(securityItem.getSecurity(stack));
            }
            if (securityItem.getOwnerUUID(stack) == null) {
                securityTile.getSecurity().setOwnerUUID(placer.getUniqueID());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendFinalUpdate(TileEntity tileEntity) {
        Mekanism.packetHandler.sendUpdatePacket((TileEntity & ITileNetwork) tileEntity);
    }
}
