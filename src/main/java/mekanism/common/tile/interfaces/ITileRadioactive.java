package mekanism.common.tile.interfaces;

import mekanism.api.MekanismAPI;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.api.math.MathUtils;
import mekanism.common.Mekanism;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldNameable;

public interface ITileRadioactive {

    static float calculateRadiationScale(IGasHandler handler, TileEntity tile, BlockPos pos) {
        if (MekanismAPI.getRadiationManager().isRadiationEnabled()) {
            int tanks = GasInventorySlot.getTankCount(handler);
            if (tanks > 0) {
                float summedScale = 0;
                try {
                    for (int tank = 0; tank < tanks; tank++) {
                        GasStack gasStack = GasInventorySlot.getGasInTank(handler, tank);
                        int capacity = GasInventorySlot.getTankCapacity(handler, tank);
                        if (capacity > 0 && gasStack != null && gasStack.getGas() != null && gasStack.getGas().isRadiation()) {
                            //TODO: Eventually we may want to debate doing this based on the radioactivity
                            // but for now this will work well
                            summedScale += gasStack.amount / (float) capacity;
                        }
                    }
                    return summedScale / tanks;
                } catch (Exception e) {
                    logRadiationScaleError(tile, pos);
                }
            }
        }
        return 0;
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

    default int getRadiationParticleCount() {
        return MathUtils.clampToInt(10 * getRadiationScale());
    }
}
