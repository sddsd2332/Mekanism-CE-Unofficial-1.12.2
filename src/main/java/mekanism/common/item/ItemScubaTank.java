package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.ModelCustomArmor;
import mekanism.client.render.ModelCustomArmor.ArmorModel;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.TextComponentGroup;
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
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemScubaTank extends ItemArmor implements IGasItem, IItemHUDProvider, IModeItem {

    public int TRANSFER_RATE = 16;

    public ItemScubaTank() {
        super(EnumHelper.addArmorMaterial("SCUBATANK", "scubatank", 0, new int[]{0, 0, 0, 0}, 0,
                SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0), 0, EntityEquipmentSlot.CHEST);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        GasStack gasStack = getGas(itemstack);
        if (gasStack == null) {
            list.add(LangUtils.localize("tooltip.noGas") + ".");
        } else {
            list.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
        }
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.flowing") + ": " + (getFlowing(itemstack) ? EnumColor.DARK_GREEN : EnumColor.DARK_RED) + getFlowingStr(itemstack));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return getStored(stack) > 0;
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
        model.modelType = ArmorModel.SCUBATANK;
        return model;
    }

    public void useGas(ItemStack itemstack) {
        useGas(itemstack, 1);
    }

    public GasStack useGas(ItemStack itemstack, int amount) {
        return GasInventorySlot.useGas(itemstack, MekanismFluids.Oxygen, amount);
    }

    public boolean hasGas(ItemStack itemstack) {
        GasStack stored = getContainedGas(itemstack);
        return stored != null && stored.amount > 0;
    }

    public GasStack getContainedGas(ItemStack itemstack) {
        GasStack stored = getGas(itemstack);
        return stored != null && stored.getGas() == MekanismFluids.Oxygen ? stored : null;
    }

    @Override
    public int getMaxGas(ItemStack itemstack) {
        return MekanismConfig.current().general.maxScubaGas.val();
    }

    @Override
    public int getRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    @Override
    public int addGas(ItemStack itemstack, GasStack stack) {
        GasStack storedGas = getGas(itemstack);
        if (storedGas != null && storedGas.getGas() != stack.getGas()) {
            return 0;
        }
        if (stack.getGas() != MekanismFluids.Oxygen) {
            return 0;
        }
        int stored = storedGas == null ? 0 : storedGas.amount;
        int toUse = Math.min(getMaxGas(itemstack) - stored, Math.min(getRate(itemstack), stack.amount));
        setGas(itemstack, new GasStack(stack.getGas(), stored + toUse));
        return toUse;
    }

    @Override
    public GasStack removeGas(ItemStack itemstack, int amount) {
        GasStack gas = getGas(itemstack);
        if (gas == null || gas.getGas() != MekanismFluids.Oxygen || amount <= 0) {
            return null;
        }
        int gasToUse = Math.min(gas.amount, Math.min(getRate(itemstack), amount));
        if (gasToUse <= 0) {
            return null;
        }
        int remaining = gas.amount - gasToUse;
        setGas(itemstack, remaining <= 0 ? null : new GasStack(gas.getGas(), remaining));
        return new GasStack(gas.getGas(), gasToUse);
    }

    public int getStored(ItemStack itemstack) {
        GasStack gas = getGas(itemstack);
        return gas == null ? 0 : gas.amount;
    }

    public void toggleFlowing(ItemStack stack) {
        setFlowing(stack, !getFlowing(stack));
    }

    public boolean getFlowing(ItemStack stack) {
        return ItemDataUtils.getBoolean(stack, "flowing");
    }

    public String getFlowingStr(ItemStack stack) {
        boolean flowing = getFlowing(stack);
        return LangUtils.localize("tooltip." + (flowing ? "yes" : "no"));
    }

    public void setFlowing(ItemStack stack, boolean flowing) {
        ItemDataUtils.setBoolean(stack, "flowing", flowing);
    }

    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        return type == MekanismFluids.Oxygen;
    }

    @Override
    public boolean canProvideGas(ItemStack itemstack, Gas type) {
        GasStack gas = getGas(itemstack);
        return gas != null && gas.amount > 0 && (type == null || gas.getGas() == type);
    }

    @Override
    public GasStack getGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
    }

    @Override
    public void setGas(ItemStack itemstack, GasStack stack) {
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.Oxygen) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getMaxGas(itemstack));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, RateLimitGasHandler.create(() -> getRate(stack), () -> getMaxGas(stack),
              mekanism.api.functions.ConstantPredicates.notExternal(), mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
              gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.Oxygen, "stored"));
    }


    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack empty = new ItemStack(this);
        setGas(empty, null);
        list.add(empty);
        ItemStack filled = new ItemStack(this);
        setGas(filled, new GasStack(MekanismFluids.Oxygen, getMaxGas(filled)));
        list.add(filled);
    }

    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        if (slotType == getEquipmentSlot()) {
            GasStack gas = getGas(stack);
            int stored = gas == null ? 0 : gas.amount;
            String state = getFlowing(stack) ? EnumColor.DARK_GREEN + LangUtils.localize("gui.on") : EnumColor.DARK_RED + LangUtils.localize("gui.off");
            list.add(LangUtils.localize("tooltip.scuba_tank.mode") + " " + state);
            list.add(stored == 0 ? (LangUtils.localize("tooltip.noGas") + ".") : (gas.getGas().getLocalizedName() + ": " + stored));
        }

    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        if (Math.abs(shift) % 2 == 1) {
            //We are changing by an odd amount, so toggle the mode
            boolean newState = !getFlowing(stack);
            setFlowing(stack, newState);
                displayChange.sendMessage(player,()->new TextComponentGroup().translation("tooltip.flowing", LangUtils.onOffColoured(newState)));
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

}
