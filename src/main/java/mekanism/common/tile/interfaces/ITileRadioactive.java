package mekanism.common.tile.interfaces;

import mekanism.api.MekanismAPI;
import mekanism.api.gas.*;
import mekanism.api.math.MathUtils;
import mekanism.common.Mekanism;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldNameable;

import javax.annotation.Nullable;
import java.util.List;

public interface ITileRadioactive {

    static float calculateRadiationScale(IGasHandler handler, TileEntity tile, BlockPos pos) {
        if (MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            try {
                if (handler instanceof IMekanismGasHandler mekanismGasHandler) {
                    // IMekanismGasHandler does not override getCountGasTanks(), so its count is always
                    // getGasTanks(getGasSideFor()).size() with getGasSideFor() returning null. Reading the live list
                    // once yields the same tanks in the same order while skipping two delegating interface frames
                    // per machine per tick, and lets the scan use the tank objects directly.
                    return sumRadiationScale(mekanismGasHandler.getGasTanks(null));
                }
                int tanks = GasInventorySlot.getTankCount(handler);
                if (tanks > 0) {
                    float summedScale = 0;
                    for (int tank = 0; tank < tanks; tank++) {
                        GasStack gasStack = GasInventorySlot.getGasInTank(handler, tank);
                        int capacity = GasInventorySlot.getTankCapacity(handler, tank);
                        if (isRadioactiveContents(gasStack, capacity)) {
                            summedScale += gasStack.amount / (float) capacity;
                        }
                    }
                    return summedScale / tanks;
                }
            } catch (Exception e) {
                logRadiationScaleError(tile, pos);
            }
        }
        return 0;
    }

    static boolean hasRadioactiveContents(IGasHandler handler) {
        if (!MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            return false;
        }
        try {
            if (handler instanceof IMekanismGasHandler mekanismGasHandler) {
                for (IExtendedGasTank tank : mekanismGasHandler.getGasTanks(null)) {
                    if (isRadioactiveContents(tank.getGas(), tank.getCapacity())) {
                        return true;
                    }
                }
                return false;
            }
            int tanks = GasInventorySlot.getTankCount(handler);
            for (int tank = 0; tank < tanks; tank++) {
                if (isRadioactiveContents(GasInventorySlot.getGasInTank(handler, tank),
                        GasInventorySlot.getTankCapacity(handler, tank))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // A live capability may disappear during block removal; fail closed.
        }
        return false;
    }

    /**
     * Averages the radioactive fill fraction across the supplied tanks.
     */
    static float sumRadiationScale(List<IExtendedGasTank> gasTanks) {
        if (gasTanks.isEmpty()) {
            return 0;
        }
        float summedScale = 0;
        for (IExtendedGasTank gasTank : gasTanks) {
            GasStack gasStack = gasTank.getGas();
            // getCapacity() is the accessor the indexed path used (getGasTankCapacity -> getCapacity).
            // The capacity is the amount the tank accepts, which is not always its max: subclasses such as
            // ValidatingGasTank may allow less, and BasicGasTank.getMaxGas() itself defers to getCapacity().
            int capacity = gasTank.getCapacity();
            if (isRadioactiveContents(gasStack, capacity)) {
                summedScale += gasStack.amount / (float) capacity;
            }
        }
        return summedScale / gasTanks.size();
    }

    static boolean isRadioactiveContents(@Nullable GasStack gasStack, int capacity) {
        return capacity > 0 && gasStack != null && gasStack.getGas() != null && gasStack.getGas().isRadiation();
    }

    @Deprecated
    static float calculateRadiationScale(GasTankInfo[] tanks, TileEntity tile, BlockPos pos) {
        if (tile instanceof IGasHandler handler) {
            return calculateRadiationScale(handler, tile, pos);
        }
        return 0;
    }

    static void logRadiationScaleError(TileEntity tile, BlockPos pos) {
        if (tile instanceof IWorldNameable worldNameable && worldNameable.getName() != null && !worldNameable.getName().isEmpty()) {
            Mekanism.logger.error("Cannot add radiation from the machine,Machine Name :{}, Machine position : x={}, y={}, z={}", worldNameable.getName(), pos.getX(), pos.getY(), pos.getZ());
        } else {
            Mekanism.logger.error("Cannot add radiation from the machine,Machine position :x={}, y={}, z={}", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    float getRadiationScale();

    default boolean hasRadioactiveContents() {
        return false;
    }

    default int getRadiationParticleCount() {
        return MathUtils.clampToInt(10 * getRadiationScale());
    }
}
