package mekanism.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IitemfishRod;
import mekanism.api.gear.Magnetic;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.MekanismModules;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.mekafishrod.IMekaFishHook;
import mekanism.common.content.gear.shared.ModuleEnergyUnit;
import mekanism.common.entity.EntityMeka;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Loader;
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
public class ItemMekaFishingRod extends ItemFishingRod implements ILegacyEnergizedItem, ISpecialElectricItem, IEnergyContainerItem, IModuleContainerItem, IModeItem, Magnetic, IitemfishRod {

    public final int ENERGY_PER_CONFIGURE = 400;

    public ItemMekaFishingRod() {
        setCreativeTab(Mekanism.tabMekanism);
        setMaxStackSize(1);
        setNoRepair();
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
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack discharged = new ItemStack(this);
        list.add(discharged);
        ItemStack charged = new ItemStack(this);
        StorageUtils.setStoredEnergy(charged, getEnergyCapacity(charged));
        list.add(charged);
        ItemStack FullStack = new ItemStack(this);
        setAllModule(FullStack);
        StorageUtils.setStoredEnergy(FullStack, getEnergyCapacity(FullStack));
        list.add(FullStack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        if (MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            addModuleDetails(stack, tooltip);
        } else {
            tooltip.add(EnumColor.AQUA + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(stack), getEnergyCapacity(stack)));
            tooltip.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return !stack.isEmpty() && super.hasEffect(stack) && IModuleContainerItem.hasOtherEnchants(stack);
    }


    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }


    @SideOnly(Side.CLIENT)
    public boolean isFull3D() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldRotateAroundWhenRendering() {
        return false;
    }

    //meka钓鱼竿不需要附魔
    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }


    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return getModules(stack).stream().filter(Module::handlesModeChange).findFirst().map(module -> module.getModeScrollComponent(stack)).orElse(null);
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, IModeItem.DisplayChange displayChange) {
        for (Module<?> module : getModules(stack)) {
            if (module.handlesModeChange()) {
                module.changeMode(player, stack, shift, displayChange);
                return;
            }
        }
    }

    public double getEnergyCapacity(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        double energy = MekanismConfig.current().meka.mekaFishBaseEnergyCapacity.val();
        return module == null ? energy : module.getCustomInstance().getEnergyCapacity(module, energy);
    }

    public double getEnergyTransfer(ItemStack stack) {
        IModule<ModuleEnergyUnit> module = getModule(stack, MekanismModules.ENERGY_UNIT);
        double energy = MekanismConfig.current().meka.mekaFishBaseChargeRate.val();
        return module == null ? energy : module.getCustomInstance().getChargeRate(module, energy);
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return false;
        }
        return StorageUtils.getNeededEnergy(itemStack) > 0;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
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
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack), ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysTrue()));
    }


    @Override
    public boolean isMagnetic(ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.MAGNETIC_UNIT);
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
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.capabilities.isCreativeMode && StorageUtils.getStoredEnergy(stack) < ENERGY_PER_CONFIGURE) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (player.fishEntity != null) {
            player.fishEntity.handleHookRetraction();
            if (!player.capabilities.isCreativeMode) {
                StorageUtils.extractEnergy(stack, ENERGY_PER_CONFIGURE, Action.EXECUTE);
            }
            player.swingArm(hand);
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_BOBBER_RETRIEVE, SoundCategory.NEUTRAL, 1.0F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
        } else {
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_BOBBER_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
            if (!world.isRemote) {
                EntityFishHook entityfishhook = new EntityFishHook(world, player);
                //添加一个cap，用来表明这是mek的浮漂
                Capabilities.getMekaFishCap(entityfishhook).ifPresent(IMekaFishHook::setFish);
                //设置钓鱼速度
                IModule<?> speed = getModule(stack, MekanismModules.FISHING_SPEED_UNIT);
                if (speed != null && speed.isEnabled()) {
                    //如果安装了mekmixinhelp，则钓鱼速度可以到最大
                    entityfishhook.setLureSpeed(Loader.isModLoaded(MekanismHooks.MekanismMixinHelp_MOD_ID) ? speed.getInstalledCount() : Math.min(speed.getInstalledCount(), 5));
                }
                //设置钓鱼的幸运度
                IModule<?> luck = getModule(stack, MekanismModules.FISHING_COLLECTING_UNIT);
                if (luck != null && luck.isEnabled()) {
                    entityfishhook.setLuck(luck.getInstalledCount());
                }
                world.spawnEntity(entityfishhook);
            }
            //挥动手臂
            player.swingArm(hand);
            player.addStat(StatList.getObjectUseStats(this));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        getModules(stack).forEach(module -> module.onUpdateModule(stack, world, entity, itemSlot, isSelected));
    }


}
