package mekanism.common.item;

import mekanism.api.Coord4D;
import mekanism.api.MekanismAPI;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.fluid.item.ItemStackMekanismFluidHandler;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.LangUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class ItemGaugeDropper extends ItemMekanism implements IGasItem {

    public static final int TRANSFER_RATE = 16;
    public static int CAPACITY = Fluid.BUCKET_VOLUME;

    public ItemGaugeDropper() {
        super();
        setMaxStackSize(1);
        setRarity(EnumRarity.UNCOMMON);
    }

    public ItemStack getEmptyItem() {
        ItemStack empty = new ItemStack(this);
        setGas(empty, null);
        return empty;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (isInCreativeTab(tabs)) {
            list.add(getEmptyItem());
        }
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        GasStack gas = getGas(stack);
        return gas != null || FluidContainerUtils.getFluidContained(stack) != null;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        GasStack gas = getGas(stack);
        double gasRatio = (gas != null ? (double) gas.amount : 0D) / (double) CAPACITY;
        FluidStack fluidContained = FluidContainerUtils.getFluidContained(stack);
        double fluidRatio = (fluidContained != null ? (double) fluidContained.amount : 0D) / (double) CAPACITY;
        return 1D - Math.max(gasRatio, fluidRatio);
    }

    @Override
    public int getRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        GasStack gas = getGas(stack);
        FluidStack fluidStack = FluidContainerUtils.getFluidContained(stack);
        if (gas != null) {
            MekanismRenderer.color(gas);
            return gas.getGas().getTint();
        } else if (fluidStack != null && fluidStack.getFluid().getColor() != 0xFFFFFFFF) { //Because it is possible that the liquid is not colored
            return fluidStack.getFluid().getColor();
        } else
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking() && !world.isRemote) {
            GasStack gas = getGas(stack);
            FluidStack fluidStack = FluidContainerUtils.getFluidContained(stack);
            if (gas != null){
                MekanismAPI.getRadiationManager().dumpRadiation(new Coord4D(player), gas);
            }
            setGas(stack, null);
            IFluidHandlerItem fluidHandler = FluidContainerUtils.getFluidHandlerCapability(stack);
            if (fluidHandler != null) {
                fluidHandler.drain(CAPACITY, true);
            }
            if ((gas != null || fluidStack != null) && player instanceof EntityPlayerMP playerMP) {
                MekanismCriteriaTriggers.USE_GAUGE_DROPPER.trigger(playerMP);
            }
            ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        GasStack gasStack = getGas(itemstack);
        FluidStack fluidStack = FluidContainerUtils.getFluidContained(itemstack);
        if (gasStack == null && fluidStack == null) {
            list.add(LangUtils.localize("gui.empty") + ".");
        } else if (gasStack != null) {
            list.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
        } else {
            list.add(LangUtils.localize("tooltip.stored") + " " + fluidStack.getFluid().getLocalizedName(fluidStack) + ": " + fluidStack.amount);
        }
    }

    @Override
    public int getRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    @Override
    public int addGas(ItemStack itemstack, GasStack stack) {
        GasStack stored = getGas(itemstack);
        if (stack == null || stack.amount <= 0 || stack.getGas() == null || hasStoredFluid(itemstack) || stored != null && stored.getGas() != stack.getGas()) {
            return 0;
        }
        int storedAmount = stored == null ? 0 : stored.amount;
        int toUse = Math.min(getMaxGas(itemstack) - storedAmount, Math.min(getRate(itemstack), stack.amount));
        if (toUse <= 0) {
            return 0;
        }
        setGas(itemstack, new GasStack(stack.getGas(), storedAmount + toUse));
        return toUse;
    }

    @Override
    public GasStack removeGas(ItemStack itemstack, int amount) {
        GasStack gas = getGas(itemstack);
        if (gas == null) {
            return null;
        }
        Gas type = gas.getGas();
        int stored = gas.amount;
        int gasToUse = Math.min(stored, Math.min(getRate(itemstack), amount));
        int remaining = stored - gasToUse;
        setGas(itemstack, remaining <= 0 ? null : new GasStack(type, remaining));
        return new GasStack(type, gasToUse);
    }

    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        GasStack gas = getGas(itemstack);
        return !hasStoredFluid(itemstack) && (gas == null || gas.getGas() == type);
    }

    @Override
    public boolean canProvideGas(ItemStack itemstack, Gas type) {
        GasStack gas = getGas(itemstack);
        return gas != null && (type == null || gas.getGas() == type);
    }

    @Override
    public GasStack getGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "gasStack");
    }

    @Override
    public void setGas(ItemStack itemstack, GasStack stack) {
        if (stack != null && (stack.amount <= 0 || stack.getGas() == null)) {
            stack = null;
        }
        if (stack != null && hasStoredFluid(itemstack)) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "gasStack", getMaxGas(itemstack));
    }

    @Override
    public int getMaxGas(ItemStack itemstack) {
        return CAPACITY;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack,
              RateLimitGasHandler.create(() -> getRate(stack), () -> getMaxGas(stack), ConstantPredicates.alwaysTrueBi(),
                    (gasStack, automationType) -> getLegacyFluid(stack) == null, ConstantPredicates.alwaysTrue(), "gasStack"),
              new GaugeDropperFluidHandler());
    }

    @Nullable
    private static FluidStack getLegacyFluid(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(FluidHandlerItemStack.FLUID_NBT_KEY, 10)) {
            return null;
        }
        FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(FluidHandlerItemStack.FLUID_NBT_KEY));
        return fluidStack != null && fluidStack.amount > 0 ? fluidStack : null;
    }

    private static boolean hasStoredFluid(ItemStack stack) {
        return FluidContainerUtils.getFluidContained(stack) != null || getLegacyFluid(stack) != null;
    }

    private static void setLegacyFluid(ItemStack stack, @Nullable FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null) {
                tag.removeTag(FluidHandlerItemStack.FLUID_NBT_KEY);
                if (tag.isEmpty()) {
                    stack.setTagCompound(null);
                }
            }
            return;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        FluidStack stored = new FluidStack(fluidStack, Math.min(fluidStack.amount, CAPACITY));
        NBTTagCompound fluidTag = new NBTTagCompound();
        stored.writeToNBT(fluidTag);
        stack.getTagCompound().setTag(FluidHandlerItemStack.FLUID_NBT_KEY, fluidTag);
    }

    private static class GaugeDropperFluidHandler extends ItemStackMekanismFluidHandler {

        private final IExtendedFluidTank tank = VariableCapacityFluidTank.create(() -> CAPACITY, BasicFluidTank.alwaysTrueBi,
              (fluidStack, automationType) -> GasInventorySlot.getStoredGas(getStack(), "gasStack") == null, BasicFluidTank.alwaysTrue, this);

        @Override
        protected List<IExtendedFluidTank> getInitialTanks() {
            return Collections.singletonList(tank);
        }

        @Override
        protected void load() {
            tank.setStackUnchecked(getLegacyFluid(getStack()));
        }

        @Override
        public void onContentsChanged() {
            setLegacyFluid(getStack(), tank.getFluid());
        }
    }
}
