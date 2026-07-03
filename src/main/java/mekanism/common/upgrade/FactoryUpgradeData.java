package mekanism.common.upgrade;

import mekanism.api.gas.GasStack;
import mekanism.common.InfuseStorage;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.tier.BaseTier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

public class FactoryUpgradeData extends TierUpgradeData {

    public final EnumFacing facing;
    public final EnumFacing clientFacing;
    public final int ticker;
    public final boolean redstone;
    public final boolean redstoneLastTick;
    public final boolean doAutoSync;
    public final double energy;
    public final boolean active;
    public final double previousEnergy;
    public final RedstoneControl controlType;
    public final NBTTagCompound componentData;
    public final RecipeType recipeType;
    public final boolean sorting;
    public final int[] progress;
    public final long[] usedSoFar;
    public final InfuseStorage infuseStored;
    public final ItemStack energySlot;
    public final ItemStack extraSlot;
    public final ItemStack[] inputSlots;
    public final ItemStack[] outputSlots;
    public final ItemStack[] secondaryOutputSlots;
    @Nullable
    public final GasStack inputGas;
    @Nullable
    public final GasStack outputGas;
    @Nullable
    public final FluidStack inputFluid;

    public FactoryUpgradeData(@Nonnull BaseTier upgradeTier, @Nonnull EnumFacing facing, @Nonnull EnumFacing clientFacing, int ticker,
          boolean redstone, boolean redstoneLastTick, boolean doAutoSync, double energy, boolean active, double previousEnergy,
          @Nonnull RedstoneControl controlType, @Nonnull NBTTagCompound componentData, @Nonnull RecipeType recipeType,
          boolean sorting, @Nonnull int[] progress, @Nonnull long[] usedSoFar, @Nonnull InfuseStorage infuseStored,
          @Nonnull ItemStack energySlot, @Nonnull ItemStack extraSlot, @Nonnull ItemStack[] inputSlots, @Nonnull ItemStack[] outputSlots,
          @Nonnull ItemStack[] secondaryOutputSlots, @Nullable GasStack inputGas, @Nullable GasStack outputGas, @Nullable FluidStack inputFluid) {
        super(upgradeTier);
        this.facing = facing;
        this.clientFacing = clientFacing;
        this.ticker = ticker;
        this.redstone = redstone;
        this.redstoneLastTick = redstoneLastTick;
        this.doAutoSync = doAutoSync;
        this.energy = energy;
        this.active = active;
        this.previousEnergy = previousEnergy;
        this.controlType = controlType;
        this.componentData = componentData.copy();
        this.recipeType = recipeType;
        this.sorting = sorting;
        this.progress = Arrays.copyOf(progress, progress.length);
        this.usedSoFar = Arrays.copyOf(usedSoFar, usedSoFar.length);
        this.infuseStored = copyInfusion(infuseStored);
        this.energySlot = energySlot.copy();
        this.extraSlot = extraSlot.copy();
        this.inputSlots = copySlots(inputSlots);
        this.outputSlots = copySlots(outputSlots);
        this.secondaryOutputSlots = copySlots(secondaryOutputSlots);
        this.inputGas = inputGas == null ? null : inputGas.copy();
        this.outputGas = outputGas == null ? null : outputGas.copy();
        this.inputFluid = inputFluid == null ? null : inputFluid.copy();
    }

    private static InfuseStorage copyInfusion(InfuseStorage source) {
        InfuseStorage copy = new InfuseStorage();
        copy.copyFrom(source);
        return copy;
    }

    private static ItemStack[] copySlots(ItemStack[] slots) {
        ItemStack[] copy = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            copy[i] = slots[i].copy();
        }
        return copy;
    }
}
