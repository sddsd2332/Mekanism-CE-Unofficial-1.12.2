package mekanism.common.item.armor;

import cofh.redstoneflux.api.IEnergyContainerItem;
import com.google.common.collect.Multimap;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.IHazmatLike;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.EnumColor;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.FloatSupplier;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.ICustomModule.ModuleDamageAbsorbInfo;
import mekanism.api.gear.IModule;
import mekanism.api.gear.Magnetic;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismItems;
import mekanism.common.MekanismModules;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.capabilities.laser.item.LaserDissipationHandler;
import mekanism.common.capabilities.radiation.item.NCRadiationShieldingHandler;
import mekanism.common.capabilities.radiation.item.RadiationShieldingHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.shared.ModuleColorModulationUnit;
import mekanism.common.content.gear.shared.ModuleEnergyUnit;
import mekanism.common.entity.EntityMeka;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.lib.Color;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

@Optional.InterfaceList({
        @Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID),
        @Optional.Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Optional.Interface(iface = "ic2.api.item.IHazmatLike", modid = MekanismHooks.IC2_MOD_ID),
})
public abstract class ItemMekaSuitArmor extends ItemArmor implements IModuleContainerItem, IModeItem,
        ILegacyEnergizedItem, ISpecialElectricItem, IEnergyContainerItem, IHazmatLike, Magnetic {

    private static final Set<DamageSource> ALWAYS_SUPPORTED_SOURCES = new LinkedHashSet<>(Arrays.asList(
            DamageSource.ANVIL, DamageSource.CACTUS, DamageSource.CRAMMING, DamageSource.DRAGON_BREATH,
            DamageSource.FALL, DamageSource.FALLING_BLOCK, DamageSource.FLY_INTO_WALL, DamageSource.GENERIC,
            DamageSource.HOT_FLOOR, DamageSource.IN_FIRE, DamageSource.IN_WALL, DamageSource.LAVA, DamageSource.LIGHTNING_BOLT,
            DamageSource.ON_FIRE, DamageSource.WITHER));

    public static Set<DamageSource> getSupportedSources() {
        return Collections.unmodifiableSet(ALWAYS_SUPPORTED_SOURCES);
    }

    public static ArmorMaterial MEKASUIT_MATERIAL = EnumHelper.addArmorMaterial("mekasuit", "mekanism:mekasuit", -1, new int[]{3, 6, 8, 3}, 0, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 3);


    private final float absorption;
    //Full laser dissipation causes 3/4 of the energy to be dissipated and the remaining energy to be refracted
    private final double laserDissipation;
    private final double laserRefraction;

    public ItemMekaSuitArmor(int renderIndex, EntityEquipmentSlot slot) {
        super(MEKASUIT_MATERIAL, renderIndex, slot);
        setNoRepair();
        setMaxStackSize(1);
        setCreativeTab(Mekanism.tabMekanism);
        if (slot == EntityEquipmentSlot.HEAD) {
            absorption = 0.15F;
            laserDissipation = 0.15;
            laserRefraction = 0.2;
        } else if (slot == EntityEquipmentSlot.CHEST) {
            absorption = 0.4F;
            laserDissipation = 0.3;
            laserRefraction = 0.4;
        } else if (slot == EntityEquipmentSlot.LEGS) {
            absorption = 0.3F;
            laserDissipation = 0.1875;
            laserRefraction = 0.25;
        } else if (slot == EntityEquipmentSlot.FEET) {
            absorption = 0.15F;
            laserDissipation = 0.1125;
            laserRefraction = 0.15;
        } else {
            throw new IllegalArgumentException("Unknown Equipment Slot Type");
        }
    }

    @Override
    public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot Type, Entity entity) {
        return Type == armorType;
    }


    /*@Override
    public void damageArmor(EntityLivingBase entity, @Nonnull ItemStack stack, DamageSource source, int damage, int slot) {
        if (Mekanism.hooks.IC2Loaded) {
            if (isModuleEnabled(stack, MekanismModules.RADIATION_SHIELDING_UNIT)) {
                Potion radiation = Potion.getPotionFromResourceLocation("ic2:radiation");
                if (radiation != null && entity.isPotionActive(radiation)) {
                    entity.removePotionEffect(radiation);
                }
            }
        }
    }

     */

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        if (MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            addModuleDetails(stack, tooltip);
        } else {
            tooltip.add(EnumColor.AQUA + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(stack), getEnergyCapacity(stack)));
            addInformation(stack, world, tooltip);
            tooltip.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
        }
    }


    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip) {

    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return StorageUtils.getStoredEnergy(stack) > 0;
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
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return !stack.isEmpty() && super.hasEffect(stack) && IModuleContainerItem.hasOtherEnchants(stack);
    }


    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack discharged = new ItemStack(this);
        items.add(discharged);

        ItemStack stack = new ItemStack(this);
        StorageUtils.setStoredEnergy(stack, getEnergyCapacity(stack), getEnergyCapacity(stack));
        items.add(stack);

        ItemStack FullStack = new ItemStack(this);
        setAllModule(FullStack);
        StorageUtils.setStoredEnergy(FullStack, getEnergyCapacity(FullStack), getEnergyCapacity(FullStack));
        if (FullStack.getItem() == MekanismItems.MEKASUIT_HELMET) {
            GasInventorySlot.setGasContained(FullStack, new GasStack(MekanismFluids.NutritionalPaste, getGasCapabilityCapacity(FullStack)));
        } else if (FullStack.getItem() == MekanismItems.MEKASUIT_BODYARMOR) {
            GasInventorySlot.setGasContained(FullStack, new GasStack(MekanismFluids.Hydrogen, getGasCapabilityCapacity(FullStack)));
        }
        addALLItemStack(FullStack);
        items.add(FullStack);
    }

    public void addALLItemStack(ItemStack stack) {

    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        super.onArmorTick(world, player, stack);
        NCRadiationShieldingHandler.clearLegacyArmorShieldingTag(stack);
        getModules(stack).forEach(module -> module.tick(player));
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        getModules(stack).forEach(module -> module.onUpdateModule(stack, world, entity, itemSlot, isSelected));
        NCRadiationShieldingHandler.clearLegacyArmorShieldingTag(stack);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        super.onCreated(stack, world, player);
        NCRadiationShieldingHandler.clearLegacyArmorShieldingTag(stack);
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesModeChange()) {
                module.changeMode(player, stack, shift, displayChange);
                return;
            }
        }
    }


    @Override
    public boolean supportsSlotType(ItemStack stack, @Nonnull EntityEquipmentSlot slotType) {
        return slotType == armorType && getModules(stack).stream().anyMatch(Module::handlesModeChange);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        boolean overlay = "overlay".equals(type);
        if (slot == EntityEquipmentSlot.LEGS) {
            return overlay ? "mekanism:render/mekasuitpants_overlay.png" : "mekanism:render/mekasuitpants.png";
        }
        return overlay ? "mekanism:render/mekasuit_overlay.png" : "mekanism:render/mekasuit.png";
    }

    @Nullable
    public static Color getColorModulation(@NotNull ItemStack stack) {
        if (stack.getItem() instanceof ItemMekaSuitArmor armor) {
            IModule<ModuleColorModulationUnit> module = armor.getModule(stack, MekanismModules.COLOR_MODULATION_UNIT);
            if (module != null) {
                return module.getCustomInstance().getColor();
            }
        }
        return null;
    }

    @Override
    public int getColor(ItemStack stack) {
        Color color = getColorModulation(stack);
        return color == null ? super.getColor(stack) : color.argb();
    }

    public double getEnergyCapacity(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.current().meka.mekaSuitBaseEnergyCapacity.val() : module.getCustomInstance().getEnergyCapacity(module);
    }

    public double getEnergyTransfer(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        return module == null ? MekanismConfig.current().meka.mekaSuitBaseChargeRate.val() : module.getCustomInstance().getChargeRate(module);
    }


    public static float getDamageAbsorbed(EntityPlayer player, DamageSource source, float amount) {
        return getDamageAbsorbed(player, source, amount, null);
    }

    public static boolean tryAbsorbAll(EntityPlayer player, DamageSource source, float amount) {
        List<Runnable> energyUsageCallbacks = new ArrayList<>(4);
        if (getDamageAbsorbed(player, source, amount, energyUsageCallbacks) >= 1) {
            //If we can fully absorb it, actually use the energy from the various pieces and then return that we absorbed it all
            energyUsageCallbacks.forEach(Runnable::run);
            return true;
        }
        return false;
    }


    private static float getDamageAbsorbed(EntityPlayer player, DamageSource source, float amount, @Nullable List<Runnable> energyUseCallbacks) {
        if (amount <= 0) {
            return 0;
        }
        float ratioAbsorbed = 0;
        List<FoundArmorDetails> armorDetails = new ArrayList<>();
        //Start by looping the armor, allowing modules to absorb damage if they can
        for (ItemStack stack : player.getArmorInventoryList()) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemMekaSuitArmor armor) {
                IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
                if (energyContainer != null) {
                    FoundArmorDetails details = new FoundArmorDetails(energyContainer, armor);
                    armorDetails.add(details);
                    for (Module<?> module : details.armor.getModules(stack)) {
                        if (module.isEnabled()) {
                            ModuleDamageAbsorbInfo damageAbsorbInfo = getModuleDamageAbsorbInfo(module, source);
                            if (damageAbsorbInfo != null) {
                                float absorption = damageAbsorbInfo.getAbsorptionRatio().getAsFloat();
                                ratioAbsorbed += absorbDamage(details.usageInfo, amount, absorption, ratioAbsorbed, damageAbsorbInfo.getEnergyCost());
                                if (ratioAbsorbed >= 1) {
                                    //If we have fully absorbed the damage, stop checking/trying to absorb more
                                    break;
                                }
                            }
                        }
                    }
                    if (ratioAbsorbed >= 1) {
                        //If we have fully absorbed the damage, stop checking/trying to absorb more
                        break;
                    }
                }
            }
        }
        if (ratioAbsorbed < 1) {
            //If we haven't fully absorbed it check the individual pieces of armor for if they can absorb any
            FloatSupplier absorbRatio = null;
            for (FoundArmorDetails details : armorDetails) {
                if (absorbRatio == null) {
                    //If we haven't looked up yet if we can absorb the damage type and if we can't
                    // stop checking if the armor is able to
                    if (!ALWAYS_SUPPORTED_SOURCES.contains(source) && source.isUnblockable()) {
                        break;
                    }
                    // Next lookup the ratio at which we can absorb the given damage type from the config
                    absorbRatio = MekanismConfig.current().meka.mekaSuitDamageRatios.getOrDefault(source, MekanismConfig.current().meka.mekaSuitUnspecifiedDamageRatio);
                    if (absorbRatio.getAsFloat() == 0) {
                        //If the config specifies that the damage type shouldn't be blocked at all
                        // stop checking if the armor is able to
                        break;
                    }
                }
                float absorption = details.armor.absorption * absorbRatio.getAsFloat();
                ratioAbsorbed += absorbDamage(details.usageInfo, amount, absorption, ratioAbsorbed, MekanismConfig.current().meka.mekaSuitEnergyUsageDamage.val());
                if (ratioAbsorbed >= 1) {
                    //If we have fully absorbed the damage, stop checking/trying to absorb more
                    break;
                }
            }
        }
        armorDetails.forEach(details -> {
            if (details.usageInfo.energyUsed != 0) {
                if (energyUseCallbacks == null) {
                    details.energyContainer.extract(details.usageInfo.energyUsed, Action.EXECUTE, AutomationType.MANUAL);
                } else {
                    energyUseCallbacks.add(() -> details.energyContainer.extract(details.usageInfo.energyUsed, Action.EXECUTE, AutomationType.MANUAL));
                }
            }
        });
        return Math.min(ratioAbsorbed, 1);
    }

    @Nullable
    private static <MODULE extends ICustomModule<MODULE>> ModuleDamageAbsorbInfo getModuleDamageAbsorbInfo(IModule<MODULE> module, DamageSource damageSource) {
        return module.getCustomInstance().getDamageAbsorbInfo(module, damageSource);
    }

    private static float absorbDamage(EnergyUsageInfo usageInfo, float amount, float absorption, float currentAbsorbed, double energyCost) {
        //Cap the amount that we can absorb to how much we have left to absorb
        absorption = Math.min(1 - currentAbsorbed, absorption);
        float toAbsorb = amount * absorption;
        if (toAbsorb > 0) {
            double usage = energyCost * toAbsorb;
            if (usage == 0) {
                //No energy is actually needed to absorb the damage, either because of the config
                // or how small the amount to absorb is
                return absorption;
            } else if (usageInfo.energyAvailable >= usage) {
                //If we have more energy available than we need, increase how much energy we "used"
                // and decrease how much we have available.
                usageInfo.energyUsed = usageInfo.energyUsed + (usage);
                usageInfo.energyAvailable = usageInfo.energyAvailable - (usage);
                return absorption;
            } else if (usageInfo.energyAvailable != 0) {
                //Otherwise, if we have energy available but not as much as needed to fully absorb it
                // then we calculate what ratio we are able to block
                float absorbedPercent = (float) (usageInfo.energyAvailable / (usage));
                usageInfo.energyUsed = usageInfo.energyUsed + (usageInfo.energyAvailable);
                usageInfo.energyAvailable = 0;
                return absorption * absorbedPercent;
            }
        }
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack stack, int energy, boolean simulate) {
        if (canReceiveEnergy(stack)) {
            double amount = RFIntegration.fromRF(energy);
            double remainder = StorageUtils.insertEnergy(stack, amount, Action.get(!simulate));
            return RFIntegration.toRF(amount - remainder);
        }
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (canSendEnergy(theItem)) {
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean addsProtection(EntityLivingBase entity, EntityEquipmentSlot slotType, ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.RADIATION_SHIELDING_UNIT);
    }

    /*
    @Override
    public boolean handleUnblockableDamage(EntityLivingBase entity, @Nonnull ItemStack stack, DamageSource source, double damage, int slot) {
        if (isModuleEnabled(stack, MekanismModules.RADIATION_SHIELDING_UNIT)) {
            return source.damageType.equals("radiation") || source.damageType.equals("sulphuric_acid") || source.damageType.equals("acid_burn") || source.damageType.equals("corium_burn") || source.damageType.equals("hot_coolant_burn");
        }
        return false;
    }

     */

    @Override
    public @NotNull Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot equipmentSlot) {
        Multimap<String, AttributeModifier> multimap = super.getItemAttributeModifiers(equipmentSlot);
        double amount = 0;
        if (equipmentSlot == EntityEquipmentSlot.HEAD) {
            amount = 1.5D;
        } else if (equipmentSlot == EntityEquipmentSlot.CHEST) {
            amount = 4D;
        } else if (equipmentSlot == EntityEquipmentSlot.LEGS) {
            amount = 3D;
        } else if (equipmentSlot == EntityEquipmentSlot.FEET) {
            amount = 1.5D;
        }
        if (equipmentSlot == this.armorType) {
            multimap.put(SharedMonsterAttributes.KNOCKBACK_RESISTANCE.getName(), new AttributeModifier(ARMOR_MODIFIERS[equipmentSlot.getIndex()], "Armor knockback resistance", amount, 0));
        }
        return multimap;
    }

    @Nonnull
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);
        getModules(stack).forEach(module -> module.multimapModule(multimap, slot, stack));
        return multimap;
    }


    //TODO - 1.18: Switch this to a record
    private static class FoundArmorDetails {
        private final IEnergyContainer energyContainer;
        private final EnergyUsageInfo usageInfo;
        private final ItemMekaSuitArmor armor;

        public FoundArmorDetails(IEnergyContainer energyContainer, ItemMekaSuitArmor armor) {
            this.energyContainer = energyContainer;
            this.usageInfo = new EnergyUsageInfo(energyContainer.getEnergy());
            this.armor = armor;
        }

    }

    private static class EnergyUsageInfo {

        private double energyAvailable;
        private double energyUsed = 0;

        public EnergyUsageInfo(double energyAvailable) {
            //Copy it so we can just use minusEquals without worry
            this.energyAvailable = energyAvailable;
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        return StorageUtils.getNeededEnergy(itemStack) > 0;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    protected boolean hasGasCapabilitySupport() {
        return false;
    }

    protected boolean isGasCapabilityEnabled(ItemStack stack) {
        return false;
    }

    protected int getGasCapabilityRate(ItemStack stack) {
        return 0;
    }

    protected int getGasCapabilityCapacity(ItemStack stack) {
        return 0;
    }

    @Nullable
    protected String getGasCapabilityLegacyKey() {
        return null;
    }

    protected Predicate<GasStack> getGasCapabilityValidator(ItemStack stack) {
        return gasStack -> false;
    }

    public int insertGas(ItemStack stack, GasStack gasStack) {
        return GasInventorySlot.insertGas(stack, gasStack, true);
    }

    @Nullable
    public GasStack useGas(ItemStack stack, Gas type, int amount) {
        return GasInventorySlot.useGas(stack, type, amount);
    }

    @Nullable
    public GasStack getContainedGas(ItemStack stack, Gas type) {
        if (stack.isEmpty() || !hasGasCapabilitySupport() || !isGasCapabilityEnabled(stack)) {
            return null;
        }
        return GasInventorySlot.getContainedGas(stack, type, getGasCapabilityLegacyKey());
    }

    //TODO
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        List<ItemCapabilityWrapper.ItemCapability> capabilities = new ArrayList<>();

        capabilities.add(new TeslaItemWrapper());
        capabilities.add(new ForgeEnergyItemWrapper());
        capabilities.add(RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack), ConstantPredicates.alwaysFalse(), ConstantPredicates.alwaysTrue()));
        capabilities.add(LaserDissipationHandler.create(item -> isModuleEnabled(item, MekanismModules.LASER_DISSIPATION_UNIT) ? laserDissipation : 0, item -> isModuleEnabled(item, MekanismModules.LASER_DISSIPATION_UNIT) ? laserRefraction : 0));
        capabilities.add(RadiationShieldingHandler.create(item -> isModuleEnabled(item, MekanismModules.RADIATION_SHIELDING_UNIT) ? ItemHazmatSuitArmor.getShieldingByArmor(armorType) : 0));
        if (mekanism.common.capabilities.Capabilities.NC_CAPABILITY_RADIATION_RESISTANCE != null) {
            capabilities.add(NCRadiationShieldingHandler.create(item -> isModuleEnabled(item, MekanismModules.RADIATION_SHIELDING_UNIT) ? ItemHazmatSuitArmor.getShieldingByArmor(armorType) * 100 : 0, item -> isModuleEnabled(item, MekanismModules.RADIATION_SHIELDING_UNIT) ? ItemHazmatSuitArmor.getShieldingByArmor(armorType) * 100 : 0, false));
        }
        if (hasGasCapabilitySupport()) {
            capabilities.add(RateLimitGasHandler.create(() -> getGasCapabilityRate(stack), () -> getGasCapabilityCapacity(stack),
                  mekanism.api.functions.ConstantPredicates.notExternal(),
                  (gasStack, automationType) -> isGasCapabilityEnabled(stack),
                  getGasCapabilityValidator(stack), getGasCapabilityLegacyKey()));
        }
        return new ItemCapabilityWrapper(stack, capabilities.toArray(new ItemCapabilityWrapper.ItemCapability[0])) {
            @Override
            public boolean hasCapability(@Nonnull net.minecraftforge.common.capabilities.Capability<?> capability, EnumFacing facing) {
                if (capability == Capabilities.GAS_HANDLER_CAPABILITY && ItemMekaSuitArmor.this.hasGasCapabilitySupport()) {
                    return ItemMekaSuitArmor.this.isGasCapabilityEnabled(itemStack);
                }
                return super.hasCapability(capability, facing);
            }

            @Override
            public <T> T getCapability(@Nonnull net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing facing) {
                if (capability == Capabilities.GAS_HANDLER_CAPABILITY &&
                    (!ItemMekaSuitArmor.this.hasGasCapabilitySupport() || !ItemMekaSuitArmor.this.isGasCapabilityEnabled(itemStack))) {
                    return null;
                }
                return super.getCapability(capability, facing);
            }
        };
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        return new EntityMeka(world, location, itemstack);

    }


    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack itemstack) {
        return EnumRarity.EPIC.getColor() + super.getItemStackDisplayName(itemstack);
    }

    @Override
    public boolean isMagnetic(ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.MAGNETIC_UNIT);
    }


}
