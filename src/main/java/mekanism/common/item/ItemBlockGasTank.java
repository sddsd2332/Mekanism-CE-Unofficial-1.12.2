package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
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
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.TileEntityGasTank;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

public class ItemBlockGasTank extends ItemBlock implements IGasItem, IItemSustainedInventory, ITierItem, ISecurityItem {

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
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY,
                                float hitZ, @Nonnull IBlockState state) {
        boolean place = super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state);
        if (place) {
            TileEntityGasTank tileEntity = (TileEntityGasTank) world.getTileEntity(pos);
            tileEntity.tier = GasTankTier.values()[getBaseTier(stack).ordinal()];
            tileEntity.gasTank.setMaxGas(tileEntity.tier.getStorage());
            tileEntity.gasTank.setGas(getGas(stack));
            ((ISecurityTile) tileEntity).getSecurity().setOwnerUUID(getOwnerUUID(stack));

            if (hasSecurity(stack)) {
                ((ISecurityTile) tileEntity).getSecurity().setMode(getSecurity(stack));
            }
            if (getOwnerUUID(stack) == null) {
                ((ISecurityTile) tileEntity).getSecurity().setOwnerUUID(player.getUniqueID());
            }

            if (ItemDataUtils.hasData(stack, "sideDataStored")) {
                ((ISideConfiguration) tileEntity).getConfig().read(ItemDataUtils.getDataMap(stack));
                ((ISideConfiguration) tileEntity).getEjector().read(ItemDataUtils.getDataMap(stack));
            }

            ((ISustainedInventory) tileEntity).setInventory(getInventory(stack));
            if (!world.isRemote) {
                Mekanism.packetHandler.sendUpdatePacket(tileEntity);
            }
        }
        return place;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        GasStack gasStack = getGas(itemstack);
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

    @Override
    public GasStack getGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
    }

    @Override
    public void setGas(ItemStack itemstack, GasStack stack) {
        if (itemstack.getCount() > 1) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getMaxGas(itemstack));
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
        setGas(empty, null);
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
                    setGas(filled, new GasStack(type, getMaxGas(filled)));
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

    @Override
    public int getMaxGas(ItemStack itemstack) {
        if (itemstack.getCount() > 1) {
            return 0;
        }
        return GasTankTier.values()[getBaseTier(itemstack).ordinal()].getStorage();
    }

    @Override
    public int getRate(ItemStack itemstack) {
        if (itemstack.getCount() > 1) {
            return 0;
        }
        return GasTankTier.values()[getBaseTier(itemstack).ordinal()].getOutput();
    }

    @Override
    public int addGas(ItemStack itemstack, GasStack stack) {
        if (itemstack.getCount() > 1) {
            return 0;
        }
        BaseTier baseTier = getBaseTier(itemstack);
        GasStack storedGas = getGas(itemstack);
        if (storedGas != null && storedGas.getGas() != stack.getGas() && baseTier != BaseTier.CREATIVE && storedGas.getGas().isRadiation()) {
            return 0;
        }
        if (baseTier == BaseTier.CREATIVE) {
            setGas(itemstack, new GasStack(stack.getGas(), Integer.MAX_VALUE));
            return stack.amount;
        }
        int stored = storedGas == null ? 0 : storedGas.amount;
        int toUse = Math.min(getMaxGas(itemstack) - stored, Math.min(getRate(itemstack), stack.amount));
        setGas(itemstack, new GasStack(stack.getGas(), stored + toUse));
        return toUse;
    }

    @Override
    public GasStack removeGas(ItemStack itemstack, int amount) {
        if (itemstack.getCount() > 1) {
            return null;
        }
        GasStack gas = getGas(itemstack);
        if (gas == null) {
            return null;
        }
        Gas type = gas.getGas();
        int stored = gas.amount;
        int gasToUse = Math.min(stored, Math.min(getRate(itemstack), amount));
        if (getBaseTier(itemstack) != BaseTier.CREATIVE) {
            setGas(itemstack, new GasStack(type, stored - gasToUse));
        }
        return new GasStack(type, gasToUse);
    }

    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        if (itemstack.getCount() > 1) {
            return false;
        }
        if (getBaseTier(itemstack) != BaseTier.CREATIVE && type != null && type.isRadiation()) {
            return false;
        }
        GasStack gas = getGas(itemstack);
        return gas == null || gas.getGas() == type;
    }

    @Override
    public boolean canProvideGas(ItemStack itemstack, Gas type) {
        if (itemstack.getCount() > 1) {
            return false;
        }
        GasStack gas = getGas(itemstack);
        if (getBaseTier(itemstack) != BaseTier.CREATIVE && (gas != null && (type == null || gas.getGas().isRadiation()))) {
            return false;
        }
        return gas != null && (type == null || gas.getGas() == type);
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
        GasStack gas = getGas(stack);
        return gas != null && gas.amount > 0; // No bar for empty containers as bars are drawn on top of stack count number
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        GasStack gas = getGas(stack);
        return 1D - ((gas != null ? (double) gas.amount : 0D) / (double) getMaxGas(stack));
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        GasStack gas = getGas(stack);
        if (gas != null) {
            MekanismRenderer.color(gas);
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
