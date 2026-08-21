package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.IIncrementalEnum;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismFluids;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.ILegacyGasItem;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.TextComponentGroup;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemFlamethrower extends ItemMekanism implements ILegacyGasItem, IModeItem, IItemHUDProvider {

    public int TRANSFER_RATE = 16;

    public ItemFlamethrower() {
        super();
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
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
        list.add(EnumColor.GREY + LangUtils.localize("tooltip.mode") + ": " + EnumColor.GREY + getMode(itemstack).getName());
    }

    public void useGas(ItemStack stack) {
        useGas(stack, 1);
    }

    public GasStack useGas(ItemStack stack, int amount) {
        return GasInventorySlot.useGas(stack, MekanismFluids.Hydrogen, amount);
    }

    public boolean hasGas(ItemStack stack) {
        GasStack stored = getContainedGas(stack);
        return stored != null && stored.amount > 0;
    }

    public GasStack getContainedGas(ItemStack stack) {
        GasStack stored = getStoredGas(stack);
        return stored != null && stored.getGas() == MekanismFluids.Hydrogen ? stored : null;
    }

    private int getGasCapacity(ItemStack itemstack) {
        return MekanismConfig.current().general.maxFlamethrowerGas.val();
    }

    private int getGasTransferRate(ItemStack itemstack) {
        return TRANSFER_RATE;
    }

    public int getStored(ItemStack itemstack) {
        GasStack gas = getStoredGas(itemstack);
        return gas == null ? 0 : gas.amount;
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
            return gas.getGas().getTint();
        } else {
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
        }
    }

    private GasStack getStoredGas(ItemStack itemstack) {
        return GasInventorySlot.getStoredGas(itemstack, "stored");
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

    public ItemStack getEmptyItem() {
        ItemStack empty = new ItemStack(this);
        setStoredGas(empty, null);
        return empty;
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tabs, @Nonnull NonNullList<ItemStack> list) {
        if (!isInCreativeTab(tabs)) {
            return;
        }
        ItemStack empty = new ItemStack(this);
        setStoredGas(empty, null);
        list.add(empty);

        ItemStack filled = new ItemStack(this);
        setStoredGas(filled, new GasStack(MekanismFluids.Hydrogen, getGasCapacity(filled)));
        list.add(filled);
    }


    public FlamethrowerMode getMode(ItemStack stack) {
        return FlamethrowerMode.byIndexStatic(ItemDataUtils.getInt(stack, NBTConstants.MODE));
    }

    public void setMode(ItemStack stack, FlamethrowerMode mode) {
        ItemDataUtils.setInt(stack, NBTConstants.MODE, mode.ordinal());
    }


    @Override
    public void addHUDStrings(List<String> list, EntityPlayer player, ItemStack stack, EntityEquipmentSlot slotType) {
        int stored = getStored(stack);
        list.add(LangUtils.localize("tooltip.flamethrower.mode") + " " + getMode(stack).getName());
        if (stored > 0) {
            list.add(LangUtils.localize("tooltip.flamethrower.stored") + " " + EnumColor.ORANGE + stored);
        } else {
            list.add(LangUtils.localize("tooltip.flamethrower.stored") + " " + EnumColor.ORANGE + LangUtils.localize("tooltip.noGas"));
        }
    }

    @Override
    public void changeMode(@NotNull EntityPlayer player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        FlamethrowerMode mode = getMode(stack);
        FlamethrowerMode newMode = mode.adjust(shift);
        if (mode != newMode) {
            setMode(stack, newMode);
            displayChange.sendMessage(player, () -> new TextComponentGroup().translation("mekanism.tooltip.flamethrower.modeBump", getMode(stack).getTextComponent()));
        }
    }

    @Nonnull
    @Override
    public ITextComponent getScrollTextComponent(@Nonnull ItemStack stack) {
        return new TextComponentGroup(TextFormatting.GRAY).translation("mekanism.tooltip.flamethrower.modeBump", getMode(stack).getTextComponent());
    }

    public enum FlamethrowerMode implements IIncrementalEnum<FlamethrowerMode> {
        COMBAT("tooltip.flamethrower.combat", EnumColor.YELLOW),
        HEAT("tooltip.flamethrower.heat", EnumColor.ORANGE),
        INFERNO("tooltip.flamethrower.inferno", EnumColor.DARK_RED);

        private static final FlamethrowerMode[] MODES = values();
        private String unlocalized;
        private EnumColor color;

        FlamethrowerMode(String s, EnumColor c) {
            unlocalized = s;
            color = c;
        }


        public String getName() {
            return color + LangUtils.localize(unlocalized);
        }

        public TextComponentTranslation getTextComponent() {
            TextComponentTranslation component = new TextComponentTranslation(unlocalized);
            component.getStyle().setColor(color.textFormatting);
            return component;
        }

        @Override
        public FlamethrowerMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static FlamethrowerMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }
    }
}
