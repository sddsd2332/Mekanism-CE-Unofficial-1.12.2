package mekanism.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.EnumColor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismClient;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ITierItem;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.item.interfaces.IItemBlockPlacementData;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

@InterfaceList({
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID)
})
public class ItemBlockEnergyCube extends ItemBlock implements ILegacyEnergizedItem, ISpecialElectricItem, IItemSustainedInventory, IEnergyContainerItem, ISecurityItem,
      ITierItem, IItemBlockPlacementData {

    public Block metaBlock;

    public ItemBlockEnergyCube(Block block) {
        super(block);
        metaBlock = block;
        setHasSubtypes(true);
        setNoRepair();
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        if (itemstack.getCount() <= 1) {
            list.add(EnumColor.BRIGHT_GREEN + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack)));
        }
        list.add(EnumColor.INDIGO + LangUtils.localize("tooltip.capacity") + ": " + EnumColor.GREY +
                MekanismUtils.getEnergyDisplay(getEnergyCapacity(itemstack)));

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

    @Nonnull
    @Override
    public String getTranslationKey(ItemStack itemstack) {
        return getTranslationKey() + getBaseTier(itemstack).getSimpleName();
    }


    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        return getBaseTier(itemstack).getColor() + LangUtils.localize("tile.EnergyCube" + getBaseTier(itemstack).getSimpleName() + ".name");
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world, @Nonnull BlockPos pos,
          @Nonnull TileEntity tileEntity) {
        if (!(tileEntity instanceof TileEntityEnergyCube energyCube)) {
            return;
        }
        energyCube.tier = EnergyCubeTier.values()[getBaseTier(stack).ordinal()];
        boolean hasStoredSideConfig = ItemDataUtils.hasData(stack, "sideDataStored");
        MekanismPlacementData.restoreCommon(stack, placer, energyCube);
        if (energyCube.tier == EnergyCubeTier.CREATIVE && !hasStoredSideConfig) {
            boolean filled = energyCube.getEnergy() > 0;
            energyCube.configComponent.fillConfig(TransmissionType.ENERGY, filled ? DataType.OUTPUT : DataType.INPUT);
            energyCube.configComponent.setEjecting(TransmissionType.ENERGY, filled);
        }
    }

    public void setCreativeDefaultSideConfig(ItemStack stack, boolean filled) {
        TileEntityEnergyCube template = new TileEntityEnergyCube();
        template.tier = EnergyCubeTier.CREATIVE;
        template.configComponent.fillConfig(TransmissionType.ENERGY, filled ? DataType.OUTPUT : DataType.INPUT);
        template.configComponent.setEjecting(TransmissionType.ENERGY, filled);
        template.configComponent.write(ItemDataUtils.getDataMap(stack));
        template.ejectorComponent.write(ItemDataUtils.getDataMap(stack));
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

    public double getEnergyCapacity(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return EnergyCubeTier.values()[getBaseTier(itemStack).ordinal()].getMaxEnergy();
    }

    public void setStoredEnergy(ItemStack itemStack, double amount) {
        if (itemStack.getCount() > 1) {
            return;
        }
        if (getBaseTier(itemStack) == BaseTier.CREATIVE) {
            double max = getEnergyCapacity(itemStack);
            amount = StorageUtils.getStoredEnergy(itemStack) > 0 || amount >= max ? max : 0;
        }
        StorageUtils.setStoredEnergy(itemStack, amount, getEnergyCapacity(itemStack));
    }

    public double getEnergyTransfer(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        return itemStack.getCount() <= 1;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return itemStack.getCount() <= 1;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canReceiveEnergy(theItem)) {
            double amount = RFIntegration.fromRF(energy);
            double remainder = StorageUtils.insertEnergy(theItem, amount, mekanism.api.Action.get(!simulate));
            return RFIntegration.toRF(amount - remainder);
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canSendEnergy(theItem)) {
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), mekanism.api.Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return stack.getCount() == 1 && StorageUtils.getStoredEnergy(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        double capacity = getEnergyCapacity(stack);
        return capacity <= 0 ? 1D : 1D - (StorageUtils.getStoredEnergy(stack) / capacity);
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
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

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> EnergyCubeTier.values()[getBaseTier(stack).ordinal()]));
    }
}
