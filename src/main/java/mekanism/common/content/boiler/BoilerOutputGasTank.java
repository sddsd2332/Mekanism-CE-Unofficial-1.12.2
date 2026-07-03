package mekanism.common.content.boiler;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;

import javax.annotation.Nullable;

public class BoilerOutputGasTank extends BoilerGasTank {

    public BoilerOutputGasTank(TileEntityBoilerCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    public boolean isValid(@Nullable Gas gas) {
        return gas == MekanismFluids.Sodium;
    }

    @Override
    @Nullable
    public GasStack getGas() {
        return multiblock.structure != null ? multiblock.structure.OutputGas : null;
    }

    @Override
    public void setGas(GasStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.OutputGas = stack;
        }
    }

    @Override
    public int getMaxGas() {
        if (multiblock.structure == null) {
            return 0;
        }
        return multiblock.isRemote() ? multiblock.clientSteamCapacity : multiblock.structure.getOutputGasCapacity();
    }
}
