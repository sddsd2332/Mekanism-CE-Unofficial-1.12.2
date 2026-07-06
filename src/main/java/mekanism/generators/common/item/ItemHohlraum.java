package mekanism.generators.common.item;

import mekanism.api.EnumColor;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gas.GasStack;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.ILegacyGasItem;
import mekanism.common.util.LangUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemHohlraum extends ItemMekanismGenerators implements ILegacyGasItem {

    public static final int TRANSFER_RATE = 1;

    public static int getMaxGasCapacity() {
        return MekanismConfig.current().generators.ItemHohlraumMaxGas.val();
    }

    public ItemHohlraum() {
        super();
        setMaxStackSize(1);
    }

    @Nullable
    public GasStack getContainedGas(ItemStack stack) {
        GasStack stored = getStoredGas(stack);
        return stored != null && stored.getGas() == MekanismFluids.FusionFuel ? stored.copy() : null;
    }

    public boolean isReadyForReaction(ItemStack stack) {
        GasStack stored = getContainedGas(stack);
        int capacity = GasInventorySlot.getTankCapacity(stack, 0);
        return stored != null && stored.amount == (capacity > 0 ? capacity : getMaxGasCapacity());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        GasStack gasStack = getContainedGas(itemstack);
        if (gasStack == null) {
            list.add(LangUtils.localize("tooltip.noGas") + ".");
            list.add(EnumColor.DARK_RED + LangUtils.localize("tooltip.insufficientFuel"));
        } else {
            list.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
            if (isReadyForReaction(itemstack)) {
                list.add(EnumColor.DARK_GREEN + LangUtils.localize("tooltip.readyForReaction") + "!");
            } else {
                list.add(EnumColor.DARK_RED + LangUtils.localize("tooltip.insufficientFuel"));
            }
        }
    }

    private int getGasCapacity(ItemStack itemstack) {
        return getMaxGasCapacity();
    }

    private int getGasTransferRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    public int getStored(ItemStack itemstack) {
        GasStack stored = getStoredGas(itemstack);
        return stored == null ? 0 : stored.amount;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        int capacity = GasInventorySlot.getTankCapacity(stack, 0);
        if (capacity <= 0) {
            capacity = getMaxGasCapacity();
        }
        return capacity <= 0 ? 1 : 1D - (getStored(stack) / (double) capacity);
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

    private GasStack getStoredGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
    }

    private void setStoredGas(ItemStack itemstack, GasStack stack) {
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.FusionFuel) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "stored", getGasCapacity(itemstack));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, RateLimitGasHandler.create(() -> getGasTransferRate(stack), () -> getGasCapacity(stack),
              ConstantPredicates.alwaysFalseBi(), ConstantPredicates.alwaysTrueBi(),
              gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.FusionFuel, "stored"));
    }

    public ItemStack getEmptyItem() {
        ItemStack stack = new ItemStack(this);
        setStoredGas(stack, null);
        return stack;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        list.add(getEmptyItem());
        ItemStack filled = new ItemStack(this);
        setStoredGas(filled, new GasStack(MekanismFluids.FusionFuel, getGasCapacity(filled)));
        list.add(filled);
    }
}
