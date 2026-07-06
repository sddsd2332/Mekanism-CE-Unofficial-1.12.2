package mekanism.multiblockmachine.common.item;

import mekanism.api.EnumColor;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismClient;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public abstract class ItemBlockLargeBase extends ItemBlock implements IItemSustainedInventory, ISecurityItem {

    public String name;

    public ItemBlockLargeBase(Block block, String machine) {
        super(block);
        name = machine;
        setNoRepair();
        setCreativeTab(MekanismMultiblockMachine.tabMekanismMultiblockMachine);
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        return LangUtils.localize("tile." + name + ".name");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                    EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.AQUA + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                    EnumColor.GREY + " " + LangUtils.localize("tooltip.and") + " " + EnumColor.AQUA +
                    GameSettings.getKeyDisplayString(MekanismKeyHandler.handModeSwitchKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDesc") + ".");
        } else if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.handModeSwitchKey)) {
            if (itemstack.getItem() instanceof ISecurityItem iSecurityItem) {
                if (iSecurityItem.hasSecurity(itemstack)) {
                    list.add(SecurityUtils.getOwnerDisplay(Minecraft.getMinecraft().player, MekanismClient.clientUUIDMap.get(iSecurityItem.getOwnerUUID(itemstack))));
                    list.add(EnumColor.GREY + LangUtils.localize("gui.security") + ": " + SecurityUtils.getSecurityDisplay(itemstack, Side.CLIENT));
                    if (SecurityUtils.isOverridden(itemstack, Side.CLIENT)) {
                        list.add(EnumColor.RED + "(" + LangUtils.localize("gui.overridden") + ")");
                    }
                }
            }
            addInformation(itemstack,list,world,flag);
            if (itemstack.getItem() instanceof ISustainedTank tank && itemstack.getCount() <= 1) {
                FluidStack fluidStack = tank.getFluidStack(itemstack);
                if (fluidStack != null && itemstack.getCount() <= 1) {
                    list.add(EnumColor.PINK + LangUtils.localizeFluidStack(fluidStack) + ": " + EnumColor.GREY + tank.getFluidStack(itemstack).amount + "mB");
                }
            }
            if (itemstack.getItem() instanceof ISustainedInventory inventory) {
                list.add(EnumColor.AQUA + LangUtils.localize("tooltip.inventory") + ": " + EnumColor.GREY + LangUtils.transYesNo(inventory.getInventory(itemstack) != null && inventory.getInventory(itemstack).tagCount() != 0));
            }
            if (Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(itemstack))) {
                Upgrade.buildComponentMap(ItemDataUtils.getDataMapIfPresent(itemstack)).forEach((key, value) -> list.add(key.getColor() + "- " + key.getName() + (key.canMultiply() ? ": " + EnumColor.GREY + "x" + value : "")));
            }
        } else {
            String getDescription = LangUtils.localize("tooltip." + name);
            list.addAll(MekanismUtils.splitTooltip(getDescription, itemstack));
        }
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack,List<String> list,World world,ITooltipFlag flag){
    }

    @Override
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState state) {
        boolean place = true;
        Block block = world.getBlockState(pos).getBlock();
        if (!block.isReplaceable(world, pos)) {
            return false;
        }
        if (canPlace(stack, player, world, pos, side, hitX, hitY, hitZ, state)) {
            place = false;
        }
        if (place && super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state)) {
            if (world.getTileEntity(pos) instanceof TileEntityBasicBlock tileEntity) {
                if (tileEntity instanceof ISecurityTile security) {
                    security.getSecurity().setOwnerUUID(getOwnerUUID(stack));
                    if (hasSecurity(stack)) {
                        security.getSecurity().setMode(getSecurity(stack));
                    }
                    if (getOwnerUUID(stack) == null) {
                        security.getSecurity().setOwnerUUID(player.getUniqueID());
                    }
                }
                if (tileEntity instanceof IUpgradeTile upgradeTile) {
                    if (Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(stack))) {
                        upgradeTile.readUpgrades(ItemDataUtils.getDataMap(stack));
                    }
                }
                if (tileEntity instanceof ISideConfiguration config) {
                    if (ItemDataUtils.hasData(stack, "sideDataStored")) {
                        config.getConfig().read(ItemDataUtils.getDataMap(stack));
                        config.getEjector().read(ItemDataUtils.getDataMap(stack));
                    }
                }
                if (tileEntity instanceof ISustainedData data) {
                    if (stack.getTagCompound() != null) {
                        data.readSustainedData(stack);
                    }
                }
                if (tileEntity instanceof IRedstoneControl redstoneControl) {
                    if (ItemDataUtils.hasData(stack, "controlType")) {
                        redstoneControl.setControlType(MekanismUtils.getByIndex(IRedstoneControl.RedstoneControl.values(), ItemDataUtils.getInt(stack, "controlType"), IRedstoneControl.RedstoneControl.DISABLED));
                    }
                }
                if (tileEntity instanceof ISustainedInventory inventory) {
                    inventory.setInventory(getInventory(stack));
                }
                addOtherMachine(tileEntity, stack, world);
            }
            return true;
        }
        return false;
    }

    public void addOtherMachine(TileEntity tileEntity, ItemStack stack, World world) {
    }

    public boolean canPlace(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState state) {
        return false;
    }

    @Override
    public void setInventory(NBTTagList nbtTags, Object... data) {
        if (data[0] instanceof ItemStack stack) {
            ItemDataUtils.setList(stack, "Items", nbtTags);
        }
    }

    @Override
    public NBTTagList getInventory(Object... data) {
        if (data[0] instanceof ItemStack stack) {
            return ItemDataUtils.getList(stack, "Items");
        }
        return null;
    }


    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, "ownerUUID")) {
            return UUID.fromString(ItemDataUtils.getString(stack, "ownerUUID"));
        }
        return null;
    }

    @Override
    public void setOwnerUUID(ItemStack stack, UUID owner) {
        if (owner == null) {
            ItemDataUtils.removeData(stack, "ownerUUID");
        } else {
            ItemDataUtils.setString(stack, "ownerUUID", owner.toString());
        }
    }

    @Override
    public ISecurityTile.SecurityMode getSecurity(ItemStack stack) {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return ISecurityTile.SecurityMode.PUBLIC;
        }
        return MekanismUtils.getByIndex(ISecurityTile.SecurityMode.values(), ItemDataUtils.getInt(stack, "security"), ISecurityTile.SecurityMode.PUBLIC);
    }

    @Override
    public void setSecurity(ItemStack stack, ISecurityTile.SecurityMode mode) {
        if (getOwnerUUID(stack) == null) {
            ItemDataUtils.removeData(stack, "security");
        } else {
            ItemDataUtils.setInt(stack, "security", mode.ordinal());
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        return true;
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return hasSecurity(stack);
    }


}
