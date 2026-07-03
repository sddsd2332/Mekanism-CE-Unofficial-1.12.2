package mekanism.common.content.tank;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.base.MultiblockGasTank;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;

import javax.annotation.Nullable;

public class DynamicGasTank extends MultiblockGasTank<TileEntityDynamicTank> {

    public DynamicGasTank(TileEntityDynamicTank tileEntity) {
        super(tileEntity);
    }

    boolean canMutate(SynchronizedTankData data) {
        return multiblock.structure == data && multiblock.getWorld() != null && !multiblock.getWorld().isRemote;
    }

    @Override
    @Nullable
    public GasStack getGas() {
        return multiblock.structure != null ? multiblock.structure.gasstored : null;
    }

    @Override
    @Nullable
    public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        if (multiblock.structure != null && multiblock.structure.hasFluid()) {
            return stack;
        }
        return super.insert(stack, action, automationType);
    }

    @Override
    public boolean canReceive(@Nullable Gas gas) {
        return (multiblock.structure == null || !multiblock.structure.hasFluid()) && super.canReceive(gas);
    }

    @Override
    public boolean canReceiveType(@Nullable Gas gas) {
        return (multiblock.structure == null || !multiblock.structure.hasFluid()) && super.canReceiveType(gas);
    }

    @Override
    public void setGas(@Nullable GasStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.gasstored = stack;
        }
    }

    @Override
    public int getMaxGas() {
        return multiblock.structure != null ? multiblock.structure.volume * TankUpdateProtocol.FLUID_PER_TANK : 0;
    }

    @Override
    protected void updateValveData() {
        if (multiblock.structure != null) {
            Coord4D coord4D = Coord4D.get(multiblock);
            for (ValveData data : multiblock.structure.valves) {
                if (coord4D.equals(data.location)) {
                    data.onTransfer();
                }
            }
        }
    }
}
