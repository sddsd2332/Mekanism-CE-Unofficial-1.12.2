package mekanism.api.gas;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implement this if your tile entity accepts gas from an external source.
 * 如果机器支持外部气体，实现该功能
 * @author AidanBrady
 */
public interface IGasHandler {

    GasTankInfo[] NONE = new GasTankInfo[0];

    /**
     * Transfer a certain amount of gas to this block.
     *
     * @param stack - gas to add
     * @return gas added
     */
    int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer);

    /**
     * Draws a certain amount of gas from this block.
     *
     * @param amount - amount to draw
     * @return gas drawn
     */
    GasStack drawGas(EnumFacing side, int amount, boolean doTransfer);

    /**
     * Whether or not this block can accept gas from a certain side.
     *
     * @param side - side to check
     * @param type - type of gas to check
     * @return if block accepts gas
     */
    boolean canReceiveGas(EnumFacing side, Gas type);

    /**
     * Whether or not this block can be drawn of gas from a certain side.
     *
     * @param side - side to check
     * @param type - type of gas to check
     * @return if block can be drawn of gas
     */
    boolean canDrawGas(EnumFacing side, Gas type);

    /**
     * Read-only legacy tank count bridge for plain {@link IGasHandler} callers.
     * Prefer the richer extended/mekanism handler interfaces when available.
     */
    default int getLegacyTankCount() {
        return getTankInfo().length;
    }

    /**
     * Read-only legacy tank contents bridge for plain {@link IGasHandler} callers.
     * Prefer the richer extended/mekanism handler interfaces when available.
     */
    @Nullable
    default GasStack getLegacyGasInTank(int tank) {
        GasTankInfo[] tankInfo = getTankInfo();
        return tank >= 0 && tank < tankInfo.length ? tankInfo[tank].getGas() : null;
    }

    /**
     * Read-only legacy tank capacity bridge for plain {@link IGasHandler} callers.
     * Prefer the richer extended/mekanism handler interfaces when available.
     */
    default int getLegacyTankCapacity(int tank) {
        GasTankInfo[] tankInfo = getTankInfo();
        return tank >= 0 && tank < tankInfo.length ? tankInfo[tank].getMaxGas() : 0;
    }

    /**
     * Legacy compat view of the tanks present on this handler. READ ONLY. DO NOT MODIFY.
     * Prefer direct handler/tank accessors when possible instead of materializing this snapshot bridge.
     *
     * @return an array of GasTankInfo elements corresponding to all tanks.
     */
    @Deprecated
    @Nonnull
    default GasTankInfo[] getTankInfo() {
        return NONE;
    }
}
