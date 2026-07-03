package mekanism.common.upgrade;

import mekanism.common.tier.BaseTier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;

public class BinUpgradeData extends TierUpgradeData {

    public final EnumFacing facing;
    public final EnumFacing clientFacing;
    public final int ticker;
    public final boolean redstone;
    public final boolean redstoneLastTick;
    public final boolean doAutoSync;
    public final boolean active;
    public final NBTTagCompound binSlot;

    public BinUpgradeData(@Nonnull BaseTier upgradeTier, @Nonnull EnumFacing facing, @Nonnull EnumFacing clientFacing, int ticker,
          boolean redstone, boolean redstoneLastTick, boolean doAutoSync, boolean active, @Nonnull NBTTagCompound binSlot) {
        super(upgradeTier);
        this.facing = facing;
        this.clientFacing = clientFacing;
        this.ticker = ticker;
        this.redstone = redstone;
        this.redstoneLastTick = redstoneLastTick;
        this.doAutoSync = doAutoSync;
        this.active = active;
        this.binSlot = binSlot.copy();
    }
}
