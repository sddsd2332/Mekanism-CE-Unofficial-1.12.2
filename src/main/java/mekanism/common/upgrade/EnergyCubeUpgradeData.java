package mekanism.common.upgrade;

import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.tier.BaseTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;

public class EnergyCubeUpgradeData extends TierUpgradeData {

    public final EnumFacing facing;
    public final EnumFacing clientFacing;
    public final int ticker;
    public final boolean redstone;
    public final boolean redstoneLastTick;
    public final boolean doAutoSync;
    public final double energy;
    public final int currentRedstoneLevel;
    public final RedstoneControl controlType;
    public final NBTTagCompound componentData;
    public final NBTTagCompound chargeSlot;
    public final NBTTagCompound dischargeSlot;

    public EnergyCubeUpgradeData(@Nonnull BaseTier upgradeTier, @Nonnull EnumFacing facing, @Nonnull EnumFacing clientFacing, int ticker,
          boolean redstone, boolean redstoneLastTick, boolean doAutoSync, double energy, int currentRedstoneLevel,
          @Nonnull RedstoneControl controlType, @Nonnull NBTTagCompound componentData, @Nonnull NBTTagCompound chargeSlot,
          @Nonnull NBTTagCompound dischargeSlot) {
        super(upgradeTier);
        this.facing = facing;
        this.clientFacing = clientFacing;
        this.ticker = ticker;
        this.redstone = redstone;
        this.redstoneLastTick = redstoneLastTick;
        this.doAutoSync = doAutoSync;
        this.energy = energy;
        this.currentRedstoneLevel = currentRedstoneLevel;
        this.controlType = controlType;
        this.componentData = componentData.copy();
        this.chargeSlot = chargeSlot.copy();
        this.dischargeSlot = dischargeSlot.copy();
    }
}
