package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.IConfigCardAccess.ISpecialConfigData;
import mekanism.common.Mekanism;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ITileNetwork;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemConfigurationCard extends ItemMekanism {

    public ItemConfigurationCard() {
        super();
        setMaxStackSize(1);
        setRarity(EnumRarity.UNCOMMON);
        this.addPropertyOverride(new ResourceLocation("mode"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                return (stack.getItem() instanceof ItemConfigurationCard && getData(stack) != null) ? 1.0F : 0.0F;
            }
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        super.addInformation(itemstack, world, list, flag);
        list.add(EnumColor.GREY + LangUtils.localize("gui.data") + ": " + EnumColor.INDIGO + LangUtils.localize(getDataType(itemstack)));
    }


    @Nonnull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (CapabilityUtils.hasCapability(tileEntity, Capabilities.CONFIG_CARD_CAPABILITY, side)) {
            if (!SecurityUtils.canAccess(player, tileEntity)) {
                SecurityUtils.displayNoAccess(player);
                return EnumActionResult.FAIL;
            }
            ItemStack stack = player.getHeldItem(hand);
            if (player.isSneaking()) {
                if (!world.isRemote) {
                    NBTTagCompound data = getBaseData(tileEntity);
                    if (CapabilityUtils.hasCapability(tileEntity, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side)) {
                        ISpecialConfigData special = CapabilityUtils.getCapability(tileEntity, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side);
                        data = special.getConfigurationData(data);
                    }
                    if (data != null) {
                        data.setString("dataType", getNameFromTile(tileEntity, side));
                        setData(stack, data);
                        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("tooltip.configurationCard.got").replaceAll("%s", EnumColor.INDIGO + LangUtils.localize(data.getString("dataType")) + EnumColor.GREY)));
                        if (player instanceof EntityPlayerMP playerMP) {
                            MekanismCriteriaTriggers.CONFIGURATION_CARD.trigger(playerMP, true);
                        }
                    }
                }
            } else {
                NBTTagCompound data = getData(stack);
                if (data == null) {
                    return EnumActionResult.PASS;
                }
                if (!world.isRemote) {
                    if (getNameFromTile(tileEntity, side).equals(getDataType(stack))) {
                        setBaseData(data, tileEntity, player);
                        if (CapabilityUtils.hasCapability(tileEntity, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side)) {
                            ISpecialConfigData special = CapabilityUtils.getCapability(tileEntity, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side);
                            special.setConfigurationData(data);
                        }
                        updateTile(tileEntity);
                        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.DARK_GREEN + LangUtils.localize("tooltip.configurationCard.set").replaceAll("%s", EnumColor.INDIGO + LangUtils.localize(getDataType(stack)) + EnumColor.DARK_GREEN)));
                        if (player instanceof EntityPlayerMP playerMP) {
                            MekanismCriteriaTriggers.CONFIGURATION_CARD.trigger(playerMP, false);
                        }
                    } else {
                        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.RED + LangUtils.localize("tooltip.configurationCard.unequal") + "."));
                    }
                }
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }


    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                stack.setTagCompound(null);
                //发送成功清除数据的消息
                player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("tooltip.configuration_card.mekanism.cleared") + "."));
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(world, player, hand);
    }


    private <TILE extends TileEntity & ITileNetwork> void updateTile(TileEntity tileEntity) {
        //Check the capability in case for some reason the tile doesn't want to expose the fact it has it
        ITileNetwork network = CapabilityUtils.getCapability(tileEntity, Capabilities.TILE_NETWORK_CAPABILITY, null);
        if (network instanceof TileEntity) {
            //Ensure the implementation is still a tile entity
            Mekanism.packetHandler.sendUpdatePacket((TILE) network);
        }
    }

    private NBTTagCompound getBaseData(TileEntity tile) {
        NBTTagCompound nbtTags = new NBTTagCompound();
        if (tile instanceof IRedstoneControl control) {
            nbtTags.setInteger("controlType", control.getControlType().ordinal());
        }
        if (tile instanceof ISideConfiguration configuration) {
            configuration.getConfig().write(nbtTags);
            configuration.getEjector().write(nbtTags);
        }
        if (tile instanceof TileEntityContainerBlock block) {
            block.getFrequencyComponent().writeConfiguredFrequencies(nbtTags);
        }
        return nbtTags;
    }

    private void setBaseData(NBTTagCompound nbtTags, TileEntity tile, EntityPlayer player) {
        if (tile instanceof IRedstoneControl control) {
            int controlType = nbtTags.getInteger("controlType");
            if (controlType >= 0 && controlType < RedstoneControl.values().length) {
                control.setControlType(RedstoneControl.values()[controlType]);
            }
        }
        if (tile instanceof ISideConfiguration configuration) {
            configuration.getConfig().read(nbtTags);
            configuration.getEjector().read(nbtTags);
        }
        if (tile instanceof TileEntityContainerBlock block) {
            block.getFrequencyComponent().readConfiguredFrequencies(player.getUniqueID(), nbtTags);
        }
    }

    private String getNameFromTile(TileEntity tile, EnumFacing side) {
        String ret = Integer.toString(tile.hashCode());
        if (tile instanceof TileEntityContainerBlock block) {
            ret = block.getName();
        }
        if (CapabilityUtils.hasCapability(tile, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side)) {
            ISpecialConfigData special = CapabilityUtils.getCapability(tile, Capabilities.SPECIAL_CONFIG_DATA_CAPABILITY, side);
            ret = special.getDataType();
        }
        return ret;
    }

    public void setData(ItemStack itemstack, NBTTagCompound data) {
        if (data != null) {
            ItemDataUtils.setCompound(itemstack, "data", data);
        } else {
            ItemDataUtils.removeData(itemstack, "data");
        }
    }

    public NBTTagCompound getData(ItemStack itemstack) {
        NBTTagCompound data = ItemDataUtils.getCompound(itemstack, "data");
        if (data.isEmpty()) {
            return null;
        }
        return ItemDataUtils.getCompound(itemstack, "data");
    }

    public String getDataType(ItemStack itemstack) {
        NBTTagCompound data = getData(itemstack);
        if (data != null) {
            return data.getString("dataType");
        }
        return "gui.none";
    }
}
