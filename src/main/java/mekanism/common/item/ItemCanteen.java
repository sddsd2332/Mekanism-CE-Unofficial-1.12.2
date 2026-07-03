package mekanism.common.item;


import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.util.LangUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemCanteen extends ItemMekanism implements IGasItem {
    public static final int TRANSFER_RATE = 100;
    public static final int ItemStack = 50;

    public ItemCanteen() {
        super();
        setMaxStackSize(1);
        setNoRepair();
        setRarity(EnumRarity.UNCOMMON);
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
        return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
    }

    public GasStack useGas(ItemStack itemstack, int amount) {
        return GasInventorySlot.useGas(itemstack, MekanismFluids.NutritionalPaste, amount);
    }

    public boolean hasGas(ItemStack itemstack) {
        GasStack stored = getContainedGas(itemstack);
        return stored != null && stored.amount > 0;
    }

    public GasStack getContainedGas(ItemStack itemstack) {
        GasStack stored = getGas(itemstack);
        return stored != null && stored.getGas() == MekanismFluids.NutritionalPaste ? stored : null;
    }

    @Override
    public int getMaxGas(ItemStack itemstack) {
        return MekanismConfig.current().general.maxCanteen.val();
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
        if (stack.getGas() != MekanismFluids.NutritionalPaste) {
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
        if (gas == null || gas.getGas() != MekanismFluids.NutritionalPaste || amount <= 0) {
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


    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        return type == MekanismFluids.NutritionalPaste;
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
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.NutritionalPaste) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getMaxGas(itemstack));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, RateLimitGasHandler.create(() -> getRate(stack), () -> getMaxGas(stack),
              mekanism.api.functions.ConstantPredicates.notExternal(), mekanism.api.functions.ConstantPredicates.alwaysTrueBi(),
              gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.NutritionalPaste, "stored"));
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
        setGas(filled, new GasStack(MekanismFluids.NutritionalPaste, getMaxGas(filled)));
        list.add(filled);
    }


    @Nonnull
    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World worldIn, EntityLivingBase entityLiving) {
        if (!worldIn.isRemote && entityLiving instanceof EntityPlayer player && player.canEat(false)) {
            GasStack stored = getContainedGas(stack);
            int storedAmount = stored == null ? 0 : stored.amount;
            int needed = Math.min(20 - player.getFoodStats().getFoodLevel(), storedAmount / MekanismConfig.current().general.nutritionalPasteMBPerFood.val());
            if (needed > 0) {
                GasStack used = useGas(stack, needed * MekanismConfig.current().general.nutritionalPasteMBPerFood.val());
                int fed = used == null ? 0 : used.amount / MekanismConfig.current().general.nutritionalPasteMBPerFood.val();
                if (fed > 0) {
                    player.getFoodStats().addStats(fed, MekanismConfig.current().general.nutritionalPasteSaturation.val());
                }
            }
        }
        return stack;
    }

    @Nonnull
    @Override
    public EnumAction getItemUseAction(ItemStack itemstack) {
        return EnumAction.DRINK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack itemstack) {
        return 32;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (player.canEat(false)) {
            player.setActiveHand(hand);
            return ActionResult.newResult(EnumActionResult.SUCCESS, itemstack);
        }
        return ActionResult.newResult(EnumActionResult.FAIL, itemstack);
    }
}
