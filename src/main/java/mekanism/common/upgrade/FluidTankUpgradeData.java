package mekanism.common.upgrade;

import mekanism.common.base.IFluidContainerManager.ContainerEditMode;
import mekanism.common.tier.BaseTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FluidTankUpgradeData extends TierUpgradeData {

    public final EnumFacing facing;
    public final EnumFacing clientFacing;
    public final int ticker;
    public final boolean redstone;
    public final boolean redstoneLastTick;
    public final boolean doAutoSync;
    public final boolean active;
    public final boolean clientActive;
    public final ContainerEditMode editMode;
    public final int valve;
    @Nullable
    public final FluidStack valveFluid;
    @Nullable
    public final FluidStack stored;
    public final int currentRedstoneLevel;
    public final NBTTagCompound componentData;
    public final NBTTagCompound inputSlot;
    public final NBTTagCompound outputSlot;

    public FluidTankUpgradeData(@Nonnull BaseTier upgradeTier, @Nonnull EnumFacing facing, @Nonnull EnumFacing clientFacing, int ticker,
          boolean redstone, boolean redstoneLastTick, boolean doAutoSync, boolean active, boolean clientActive,
          @Nonnull ContainerEditMode editMode, int valve, @Nullable FluidStack valveFluid, @Nullable FluidStack stored,
          int currentRedstoneLevel, @Nonnull NBTTagCompound componentData, @Nonnull NBTTagCompound inputSlot,
          @Nonnull NBTTagCompound outputSlot) {
        super(upgradeTier);
        this.facing = facing;
        this.clientFacing = clientFacing;
        this.ticker = ticker;
        this.redstone = redstone;
        this.redstoneLastTick = redstoneLastTick;
        this.doAutoSync = doAutoSync;
        this.active = active;
        this.clientActive = clientActive;
        this.editMode = editMode;
        this.valve = valve;
        this.valveFluid = valveFluid == null ? null : valveFluid.copy();
        this.stored = stored == null ? null : stored.copy();
        this.currentRedstoneLevel = currentRedstoneLevel;
        this.componentData = componentData.copy();
        this.inputSlot = inputSlot.copy();
        this.outputSlot = outputSlot.copy();
    }
}
