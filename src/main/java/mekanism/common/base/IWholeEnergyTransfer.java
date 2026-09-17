package mekanism.common.base;

import net.minecraft.util.EnumFacing;

/** Optional atomic integer-unit transfer for wrappers whose storage can retain fractions. */
public interface IWholeEnergyTransfer {
    long transferEnergyUnits(EnumFacing side, long maximum, double joulesPerUnit, boolean input, boolean simulate);
}
