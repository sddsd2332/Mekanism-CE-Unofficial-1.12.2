package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismClient;
import mekanism.client.MekanismKeyHandler;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ITierItem;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.item.interfaces.ILegacyGasItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityGasTank;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismPlacementData;
import mekanism.common.util.SecurityUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class ItemBlockGasTank extends ItemBlock implements ILegacyGasItem, IItemSustainedInventory, ITierItem, ISecurityItem, IItemBlockPlacementData {

    /**
     * How fast this tank can transfer gas.
     */
    public static final int TRANSFER_RATE = 256;
    public Block metaBlock;
    /**
     * The maximum amount of gas this tank can hold.
     */
    public int MAX_GAS = 96000;

    public ItemBlockGasTank(Block block) {
        super(block);
        metaBlock = block;
        setHasSubtypes(true);
        //setMaxStackSize(1);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    public int getMetadata(int i) {
        return i;
    }

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack itemstack) {
        return getTranslationKey() + getBaseTier(itemstack).getSimpleName();
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        return getBaseTier(itemstack).getColor() + LangUtils.localize("tile.GasTank" + getBaseTier(itemstack).getSimpleName() + ".name");
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        if (!(tileEntity instanceof TileEntityGasTank gasTank)) {
            return;
        }
        gasTank.tier = GasTankTier.values()[getBaseTier(stack).ordinal()];
        gasTank.gasTank.setMaxGas(gasTank.tier.getStorage());
        gasTank.gasTank.setGas(getStoredGas(stack));
        MekanismPlacementData.restoreCommon(stack, placer, gasTank);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        GasStack gasStack = getStoredGas(itemstack);
        if (itemstack.getCount() <= 1) {
            if (gasStack == null) {
                list.add(EnumColor.DARK_RED + LangUtils.localize("gui.empty") + ".");
            } else {
                String amount = gasStack.amount == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : Integer.toString(gasStack.amount);
                list.add(EnumColor.ORANGE + gasStack.getGas().getLocalizedName() + ": " + EnumColor.GREY + amount);
            }
        }
        int cap = GasTankTier.values()[getBaseTier(itemstack).ordinal()].getStorage();
        list.add(EnumColor.INDIGO + LangUtils.localize("tooltip.capacity") + ": " + EnumColor.GREY + (cap == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : cap));

        if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.AQUA + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                    EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
        } else {
            if (hasSecurity(itemstack)) {
                list.add(SecurityUtils.getOwnerDisplay(Minecraft.getMinecraft().player, MekanismClient.clientUUIDMap.get(getOwnerUUID(itemstack))));
                list.add(EnumColor.GREY + LangUtils.localize("gui.security") + ": " + SecurityUtils.getSecurityDisplay(itemstack, Side.CLIENT));
                if (SecurityUtils.isOverridden(itemstack, Side.CLIENT)) {
                    list.add(EnumColor.RED + "(" + LangUtils.localize("gui.overridden") + ")");
                }
            }

            list.add(EnumColor.AQUA + LangUtils.localize("tooltip.inventory") + ": " + EnumColor.GREY +
                    LangUtils.transYesNo(getInventory(itemstack) != null && getInventory(itemstack).tagCount() != 0));
        }
    }

    private GasStack getStoredGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
    }

    private void setStoredGas(ItemStack itemstack, GasStack stack) {
        if (itemstack.getCount() > 1) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getGasCapacity(itemstack));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, RateLimitGasHandler.create(() -> GasTankTier.values()[getBaseTier(stack).ordinal()], "stored")) {
            @Override
            public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
                if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
                    return itemStack.getCount() == 1;
                }
                return super.hasCapability(capability, facing);
            }

            @Override
            public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
                if (capability == Capabilities.GAS_HANDLER_CAPABILITY && itemStack.getCount() != 1) {
                    return null;
                }
                return super.getCapability(capability, facing);
            }
        };
    }

    public ItemStack getEmptyItem(GasTankTier tier) {
        ItemStack empty = new ItemStack(this);
        setBaseTier(empty, tier.getBaseTier());
        setStoredGas(empty, null);
        return empty;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        for (GasTankTier tier : GasTankTier.values()) {
            ItemStack empty = new ItemStack(this);
            setBaseTier(empty, tier.getBaseTier());
            list.add(empty);
        }
        if (MekanismConfig.current().general.prefilledGasTanks.val()) {
            GasRegistry.getRegisteredGasses().forEach(type -> {
                if (type.isVisible() || MekanismConfig.current().mekce.ShowHiddenGas.val()) {
                    ItemStack filled = new ItemStack(this);
                    setBaseTier(filled, BaseTier.CREATIVE);
                    setStoredGas(filled, new GasStack(type, getGasCapacity(filled)));
                    list.add(filled);
                }
            });
        }
    }

    @Override
    public BaseTier getBaseTier(ItemStack itemstack) {
        if (!itemstack.hasTagCompound()) {
            return BaseTier.BASIC;
        }
        int tier = itemstack.getTagCompound().getInteger("tier");
        if (tier >= 0 && tier < BaseTier.values().length) {
            return BaseTier.values()[tier];
        }
        return BaseTier.BASIC;
    }

    @Override
    public void setBaseTier(ItemStack itemstack, BaseTier tier) {
        if (!itemstack.hasTagCompound()) {
            itemstack.setTagCompound(new NBTTagCompound());
        }
        itemstack.getTagCompound().setInteger("tier", tier.ordinal());
    }

    private int getGasCapacity(ItemStack itemstack) {
        if (itemstack.getCount() > 1) {
            return 0;
        }
        return GasTankTier.values()[getBaseTier(itemstack).ordinal()].getStorage();
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
    public boolean showDurabilityBar(ItemStack stack) {
        if (stack.getCount() > 1) {
            return false;
        }
        GasStack gas = getStoredGas(stack);
        return gas != null && gas.amount > 0; // No bar for empty containers as bars are drawn on top of stack count number
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        GasStack gas = getStoredGas(stack);
        return 1D - ((gas != null ? (double) gas.amount : 0D) / (double) getGasCapacity(stack));
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        GasStack gas = getStoredGas(stack);
        if (gas != null) {
            return gas.getGas().getTint();
        } else {
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, "ownerUUID")) {
            try {
                return UUID.fromString(ItemDataUtils.getString(stack, "ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
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
    public SecurityMode getSecurity(ItemStack stack) {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return SecurityMode.PUBLIC;
        }
        int security = ItemDataUtils.getInt(stack, "security");
        if (security >= 0 && security < SecurityMode.values().length) {
            return SecurityMode.values()[security];
        }
        return SecurityMode.PUBLIC;
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
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
