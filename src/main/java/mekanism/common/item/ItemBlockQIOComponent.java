package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.item.interfaces.IColoredItem;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Shared item implementation for QIO component blocks. */
public class ItemBlockQIOComponent extends ItemBlock implements IItemSustainedInventory, ISecurityItem, IFrequencyItem, IColoredItem,
      IItemBlockPlacementData {

    public ItemBlockQIOComponent(Block block) {
        super(block);
        setMaxStackSize(64);
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        if (!world.isRemote && world.getTotalWorldTime() % 100 == 0) {
            syncColorWithFrequency(stack);
        }
    }

    @Override
    public void setFrequencyAware(@Nonnull ItemStack stack, @Nullable FrequencyAware<?> frequencyAware) {
        IFrequencyItem.super.setFrequencyAware(stack, frequencyAware);
        syncColorWithFrequency(stack, frequencyAware == null ? null : frequencyAware.frequency());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        FrequencyIdentity identity = getFrequency(stack);
        if (identity != null) {
            tooltip.add(EnumColor.INDIGO + LangUtils.localize("gui.frequency") + ": " + EnumColor.GREY + identity.key());
            tooltip.add(EnumColor.INDIGO + LangUtils.localize("gui.mode") + ": " + EnumColor.GREY +
                  LangUtils.localize("gui." + identity.securityMode().name().toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        if (!(tileEntity instanceof TileEntityQIOComponent tile)) {
            return;
        }
        UUID owner = getOwnerUUID(stack);
        tile.getSecurity().setOwnerUUID(owner == null ? placer.getUniqueID() : owner);
        tile.getSecurity().setMode(getSecurity(stack));
        tile.setInventory(getSustainedInventory(stack));
        if (ItemDataUtils.hasData(stack, "qioSustained", NBT.TAG_COMPOUND)) {
            tile.readSustainedQIOData(ItemDataUtils.getCompound(stack, "qioSustained"));
        }
        FrequencyIdentity identity = getFrequency(stack);
        if (!world.isRemote && identity != null) {
            tile.setFrequency(FrequencyType.QIO, identity, placer.getUniqueID());
        }
    }

    @Override
    public FrequencyType<QIOFrequency> getFrequencyType() {
        return FrequencyType.QIO;
    }

    @Nullable
    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        String value = ItemDataUtils.getString(stack, "ownerUUID");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public void setOwnerUUID(ItemStack stack, @Nullable UUID owner) {
        if (owner == null) {
            ItemDataUtils.removeData(stack, "ownerUUID");
        } else {
            ItemDataUtils.setString(stack, "ownerUUID", owner.toString());
        }
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return true;
    }

    @Override
    public SecurityMode getSecurity(ItemStack stack) {
        int ordinal = ItemDataUtils.getInt(stack, "security");
        return ordinal >= 0 && ordinal < SecurityMode.values().length ? SecurityMode.values()[ordinal] : SecurityMode.PUBLIC;
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
        if (getOwnerUUID(stack) == null || mode == null || mode == SecurityMode.PUBLIC) {
            ItemDataUtils.removeData(stack, "security");
        } else {
            ItemDataUtils.setInt(stack, "security", mode.ordinal());
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        return true;
    }

    /** Stores non-inventory QIO configuration on a dropped block item. */
    public void setSustainedQIOData(TileEntityQIOComponent tile, ItemStack stack) {
        if (tile == null || stack == null || stack.isEmpty()) {
            return;
        }
        NBTTagCompound data = new NBTTagCompound();
        tile.writeSustainedQIOData(data);
        if (data.isEmpty()) {
            ItemDataUtils.removeData(stack, "qioSustained");
        } else {
            ItemDataUtils.setCompound(stack, "qioSustained", data);
        }
    }
}
