package mekanism.common.upgrade;

import mekanism.api.gas.GasStack;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.tier.BaseTier;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GasTankUpgradeData extends TierUpgradeData {

    public final EnumFacing facing;
    public final EnumFacing clientFacing;
    public final int ticker;
    public final boolean redstone;
    public final boolean redstoneLastTick;
    public final boolean doAutoSync;
    public final RedstoneControl controlType;
    public final GasMode dumping;
    @Nullable
    public final GasStack stored;
    public final int currentGasAmount;
    public final int currentRedstoneLevel;
    public final NBTTagCompound componentData;
    public final NBTTagCompound drainSlot;
    public final NBTTagCompound fillSlot;

    public GasTankUpgradeData(@Nonnull BaseTier upgradeTier, @Nonnull EnumFacing facing, @Nonnull EnumFacing clientFacing, int ticker,
          boolean redstone, boolean redstoneLastTick, boolean doAutoSync, @Nonnull RedstoneControl controlType,
          @Nonnull GasMode dumping, @Nullable GasStack stored, int currentGasAmount, int currentRedstoneLevel,
          @Nonnull NBTTagCompound componentData, @Nonnull NBTTagCompound drainSlot, @Nonnull NBTTagCompound fillSlot) {
        super(upgradeTier);
        this.facing = facing;
        this.clientFacing = clientFacing;
        this.ticker = ticker;
        this.redstone = redstone;
        this.redstoneLastTick = redstoneLastTick;
        this.doAutoSync = doAutoSync;
        this.controlType = controlType;
        this.dumping = dumping;
        this.stored = stored == null ? null : stored.copy();
        this.currentGasAmount = currentGasAmount;
        this.currentRedstoneLevel = currentRedstoneLevel;
        this.componentData = componentData.copy();
        this.drainSlot = drainSlot.copy();
        this.fillSlot = fillSlot.copy();
    }
}
