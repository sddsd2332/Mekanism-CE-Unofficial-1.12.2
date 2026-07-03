package mekanism.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.NBTConstants;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.client.render.ModelCustomArmor;
import mekanism.client.render.ModelCustomArmor.ArmorModel;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

@InterfaceList({
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID),
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
})
public class ItemFreeRunners extends ItemArmor implements ILegacyEnergizedItem, ISpecialElectricItem, IEnergyContainerItem, ISpecialArmor, IItemHUDProvider, IModeItem {

    /**
     * The maximum amount of energy this item can hold.
     */
    public double MAX_ELECTRICITY = 64000;

    public ItemFreeRunners() {
        super(EnumHelper.addArmorMaterial("FRICTIONBOOTS", "frictionboots", 0, new int[]{0, 0, 0, 0}, 0,
                SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0), 0, EntityEquipmentSlot.FEET);
        setMaxStackSize(1);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot armorType, Entity entity) {
        return armorType == EntityEquipmentSlot.FEET;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "mekanism:render/NullArmor.png";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, EntityEquipmentSlot armorSlot, ModelBiped _default) {
        ModelCustomArmor model = ModelCustomArmor.INSTANCE;
        if (this == MekanismItems.FreeRunners) {
            model.modelType = ArmorModel.FREERUNNERS;
        } else if (this == MekanismItems.ArmoredFreeRunners) {
            model.modelType = ArmorModel.ARMOREDFREERUNNERS;
        }
        return model;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        list.add(EnumColor.AQUA + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack), getEnergyCapacity(itemstack)));
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.mode") + ": " + EnumColor.GREY + getMode(itemstack).getName());
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
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        return StorageUtils.getNeededEnergy(itemStack) > 0;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack theItem, int energy, boolean simulate) {
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
        if (canSendEnergy(theItem)) {
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return StorageUtils.getStoredEnergy(stack) > 0;
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
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }


    @Override
    public ArmorProperties getProperties(EntityLivingBase player, @Nonnull ItemStack armor, DamageSource source, double damage, int slot) {
        ArmorProperties properties = new ArmorProperties(0, 0, 0);
        if (this == MekanismItems.FreeRunners) {
            properties = new ArmorProperties(0, 0, 0);
        } else if (this == MekanismItems.ArmoredFreeRunners) {
            properties = new ArmorProperties(1, MekanismConfig.current().general.armoredFreeRunnersRatio.val(), MekanismConfig.current().general.armoredFreeRunnersDamageMax.val());
            properties.Toughness = 2;
        }
        return properties;
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, @Nonnull ItemStack armor, int slot) {
        if (armor.getItem() == MekanismItems.FreeRunners) {
            return 0;
        } else if (armor.getItem() == MekanismItems.ArmoredFreeRunners) {
            return 3;
        }
        return 0;
    }

    @Override
    public void damageArmor(EntityLivingBase entity, @Nonnull ItemStack stack, DamageSource source, int damage, int slot) {

    }

    @Override
    public boolean supportsSlotType(ItemStack stack, @Nonnull EntityEquipmentSlot slotType) {
        return slotType == armorType;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack),
                    ConstantPredicates.alwaysFalse(), ConstantPredicates.alwaysTrue()));
    }

    public FreeRunnerMode getMode(ItemStack itemStack) {
        return FreeRunnerMode.byIndexStatic(ItemDataUtils.getInt(itemStack, NBTConstants.MODE));
    }

    public void setMode(ItemStack itemStack, FreeRunnerMode mode) {
        ItemDataUtils.setInt(itemStack, NBTConstants.MODE, mode.ordinal());
    }

    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        if (slotType == getEquipmentSlot()) {
            list.add(LangUtils.localize("tooltip.free_runners.mode") + " " + getMode(stack).getName());
            list.add(LangUtils.localize("tooltip.free_runners.stored") + " " + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(stack)));
        }
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        FreeRunnerMode mode = getMode(stack);
        FreeRunnerMode newMode = mode.adjust(shift);
        if (mode != newMode) {
            setMode(stack, newMode);
            displayChange.sendMessage(player, () -> new TextComponentString(EnumColor.GREY + LangUtils.localize("tooltip.free_runners.mode") + " " + EnumColor.INDIGO + newMode.getName()));
        }
    }

    public enum FreeRunnerMode implements IIncrementalEnum<FreeRunnerMode> {
        NORMAL("tooltip.freerunner.regular", EnumColor.DARK_GREEN, true, true),
        SAFETY("tooltip.freerunner.safety", EnumColor.ORANGE, true, false),
        DISABLED("tooltip.freerunner.disabled", EnumColor.DARK_RED, false, false);

        private static final FreeRunnerMode[] MODES = values();

        private final boolean preventsFallDamage;
        private final boolean providesStepBoost;
        private final String unlocalized;
        private final EnumColor color;

        FreeRunnerMode(String unlocalized, EnumColor color, boolean preventsFallDamage, boolean providesStepBoost) {
            this.preventsFallDamage = preventsFallDamage;
            this.providesStepBoost = providesStepBoost;
            this.unlocalized = unlocalized;
            this.color = color;
        }


        public boolean preventsFallDamage() {
            return preventsFallDamage;
        }

        public boolean providesStepBoost() {
            return providesStepBoost;
        }

        public String getName() {
            return color + LangUtils.localize(unlocalized);
        }

        @Override
        public FreeRunnerMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static FreeRunnerMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }
}
