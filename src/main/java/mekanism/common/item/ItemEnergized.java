package mekanism.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@InterfaceList({
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID),
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
})
public class ItemEnergized extends ItemMekanism implements ILegacyEnergizedItem, ISpecialElectricItem, IEnergyContainerItem {

    /**
     * The maximum amount of energy this item can hold.
     */
    public double MAX_ELECTRICITY;

    private final boolean allowsExternalEnergyExtraction;

    public ItemEnergized(double maxElectricity) {
        this(maxElectricity, false);
    }

    protected ItemEnergized(double maxElectricity, boolean allowsExternalEnergyExtraction) {
        super();
        MAX_ELECTRICITY = maxElectricity;
        this.allowsExternalEnergyExtraction = allowsExternalEnergyExtraction;
    }

    public ItemEnergized() {
        this(1000000, true);
        setRarity(EnumRarity.UNCOMMON);
        this.addPropertyOverride(new ResourceLocation("energy"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                if (stack.getItem() instanceof ItemEnergized) {
                    double ratio = StorageUtils.getEnergyRatio(stack);
                    if (ratio > 0 && ratio <= 0.3F) {
                        return 0.3F;
                    } else if (ratio > 0.3F && ratio <= 0.6F) {
                        return 0.6F;
                    } else if (ratio > 0.6F && ratio <= 1.0F) {
                        return 1.0F;
                    }
                }
                return 0.0F;
            }
        });
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return stack.getCount() == 1 && StorageUtils.getStoredEnergy(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1D - StorageUtils.getEnergyRatio(stack);
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        if (itemstack.getCount() <= 1) {
            list.add(EnumColor.AQUA + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack), getEnergyCapacity(itemstack)));
        }
    }

    public ItemStack getUnchargedItem() {
        return new ItemStack(this);
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack discharged = new ItemStack(this);
        list.add(discharged);
        ItemStack charged = new ItemStack(this);
        StorageUtils.setStoredEnergy(charged, getEnergyCapacity(charged));
        list.add(charged);
    }

    public double getEnergyCapacity(ItemStack itemStack) {
        return MAX_ELECTRICITY;
    }

    public double getEnergyTransfer(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return false;
        }
        return StorageUtils.getNeededEnergy(itemStack) > 0;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        if (!allowsExternalEnergyExtraction || itemStack.getCount() > 1) {
            return false;
        }
        return StorageUtils.getStoredEnergy(itemStack) > 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canReceiveEnergy(theItem)) {
            double amount = RFIntegration.fromRF(energy);
            double remainder = StorageUtils.insertEnergy(theItem, amount, Action.get(!simulate));
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
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    public int getEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    public int getMaxEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack),
                    allowsExternalEnergyExtraction ? ConstantPredicates.alwaysTrue() : BasicEnergyContainer.manualOnly,
                    ConstantPredicates.alwaysTrue()));
    }

}
