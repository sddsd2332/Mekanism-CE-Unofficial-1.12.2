package mekanism.common.item;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.render.IRenderBauble;
import mekanism.api.EnumColor;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.mixninapi.ElytraMixinHelp;
import mekanism.client.model.ModelArmoredJetpack;
import mekanism.client.model.ModelJetpack;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.ModelCustomArmor;
import mekanism.client.render.ModelCustomArmor.ArmorModel;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismItems;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.ILegacyGasItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TextComponentGroup;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

@Optional.InterfaceList({
        @Optional.Interface(iface = "baubles.api.IBauble", modid = MekanismHooks.Baubles_MOD_ID),
        @Optional.Interface(iface = "baubles.api.render.IRenderBauble", modid = MekanismHooks.Baubles_MOD_ID)
})
public class ItemJetpack extends ItemArmor implements ILegacyGasItem, ISpecialArmor, IJetpackItem, IItemHUDProvider, IBauble, IRenderBauble, IModeItem, ElytraMixinHelp {

    public int TRANSFER_RATE = 16;

    public ItemJetpack() {
        super(EnumHelper.addArmorMaterial("JETPACK", "jetpack", 0, new int[]{0, 0, 0, 0}, 0, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                0), 0, EntityEquipmentSlot.CHEST);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getStored(stack) > 0;
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
            MekanismRenderer.color(gas);
            return gas.getGas().getTint();
        } else {
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        GasStack gasStack = getStoredGas(itemstack);
        if (gasStack == null) {
            list.add(LangUtils.localize("tooltip.noGas") + ".");
        } else {
            list.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
        }
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.mode") + ": " + EnumColor.GREY + getJetpackMode(itemstack).getName());
    }

    @Override
    public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot armorType, Entity entity) {
        return armorType == EntityEquipmentSlot.CHEST;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "mekanism:render/NullArmor.png";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, EntityEquipmentSlot armorSlot, ModelBiped _default) {
        ModelCustomArmor model = ModelCustomArmor.INSTANCE;
        if (this == MekanismItems.Jetpack) {
            model.modelType = ArmorModel.JETPACK;
        } else if (this == MekanismItems.ArmoredJetpack) {
            model.modelType = ArmorModel.ARMOREDJETPACK;
        }
        return model;
    }


    private int getGasCapacity(ItemStack itemstack) {
        return MekanismConfig.current().general.maxJetpackGas.val();
    }

    private int getGasTransferRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    public int getStored(ItemStack itemstack) {
        GasStack gas = getStoredGas(itemstack);
        return gas == null ? 0 : gas.amount;
    }

    @Override
    public boolean canUseJetpack(ItemStack stack) {
        return hasGas(stack);
    }

    @Override
    public JetpackMode getJetpackMode(ItemStack stack) {
        return JetpackMode.byIndexStatic(ItemDataUtils.getInt(stack, NBTConstants.MODE));
    }

    @Override
    public void useJetpackFuel(ItemStack stack) {
        useGas(stack, 1);
    }

    @Override
    public double getJetpackThrust(ItemStack stack) {
        return 0.15;
    }


    private GasStack getStoredGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
    }

    private boolean hasGas(ItemStack stack) {
        GasStack stored = getContainedGas(stack);
        return stored != null && stored.amount > 0;
    }

    private GasStack getContainedGas(ItemStack stack) {
        GasStack stored = getStoredGas(stack);
        return stored != null && stored.getGas() == MekanismFluids.Hydrogen ? stored : null;
    }

    private GasStack useGas(ItemStack stack, int amount) {
        return GasInventorySlot.useGas(stack, MekanismFluids.Hydrogen, amount);
    }

    private void setStoredGas(ItemStack itemstack, GasStack stack) {
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.Hydrogen) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getGasCapacity(itemstack));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, RateLimitGasHandler.create(() -> getGasTransferRate(stack), () -> getGasCapacity(stack),
              mekanism.api.functions.ConstantPredicates.notExternal(), mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
              gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.Hydrogen, "stored"));
    }


    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack empty = new ItemStack(this);
        list.add(empty);

        ItemStack filled = new ItemStack(this);
        setStoredGas(filled, new GasStack(MekanismFluids.Hydrogen, getGasCapacity(filled)));
        list.add(filled);
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, @Nonnull ItemStack armor, DamageSource source,
                                         double damage, int slot) {
        if (this == MekanismItems.Jetpack) {
            return new ArmorProperties(0, 0, 0);
        } else if (this == MekanismItems.ArmoredJetpack) {
            return new ArmorProperties(1, MekanismConfig.current().general.armoredJetpackDamageRatio.val(),
                    MekanismConfig.current().general.armoredJetpackDamageMax.val());
        }
        return new ArmorProperties(0, 0, 0);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, @Nonnull ItemStack armor, int slot) {
        if (armor.getItem() == MekanismItems.Jetpack) {
            return 0;
        } else if (armor.getItem() == MekanismItems.ArmoredJetpack) {
            return 12;
        }
        return 0;
    }

    @Override
    public void damageArmor(EntityLivingBase entity, @Nonnull ItemStack stack, DamageSource source, int damage, int slot) {
    }

    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        if (slotType == getEquipmentSlot()) {
            int stored = getStored(stack);
            list.add(LangUtils.localize("tooltip.jetpack.mode") + " " + getJetpackMode(stack).getName());
            if (stored > 0) {
                list.add(LangUtils.localize("tooltip.jetpack.stored") + " " + EnumColor.ORANGE + stored);
            } else {
                list.add(LangUtils.localize("tooltip.jetpack.stored") + " " + EnumColor.ORANGE + LangUtils.localize("tooltip.noGas"));
            }

        }
    }


    public void setMode(ItemStack stack, JetpackMode mode) {
        ItemDataUtils.setInt(stack, NBTConstants.MODE, mode.ordinal());
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        JetpackMode mode = getJetpackMode(stack);
        JetpackMode newMode = mode.adjust(shift);
        if (mode != newMode) {
            setMode(stack, newMode);
            displayChange.sendMessage(player,()->new TextComponentGroup().translation("jetpack.mekanism.mode_change").string(newMode.getName()));
        }
    }

    @Override
    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.BODY;
    }


    @SideOnly(Side.CLIENT)
    @Override
    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    public void onPlayerBaubleRender(ItemStack itemStack, EntityPlayer entityPlayer, RenderType renderType, float v) {
        ModelJetpack jetpack = new ModelJetpack();
        ModelArmoredJetpack armoredJetpack = new ModelArmoredJetpack();
        if (renderType == RenderType.BODY) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 0.06F);
            MekanismRenderer.bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.RENDER, "Jetpack.png"));
            if (this == MekanismItems.Jetpack) {
                jetpack.render(0.0625F);
            } else if (this == MekanismItems.ArmoredJetpack) {
                armoredJetpack.render(0.0625F);
            }
            GlStateManager.popMatrix();
        }
    }


    @Override
    public boolean supportsSlotType(ItemStack stack, @Nonnull EntityEquipmentSlot slotType) {
        return slotType == armorType;
    }

    @Override
    public boolean willAutoSync(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, EntityLivingBase entity) {
        GasStack stored = getContainedGas(stack);
        if (armorType == EntityEquipmentSlot.CHEST && !entity.isSneaking() && stored != null && stored.amount > 0) {
            return getJetpackMode(stack) == JetpackMode.VECTOR;
        }
        return false;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, EntityLivingBase entity, int flightTicks) {
        return true;
    }

}
