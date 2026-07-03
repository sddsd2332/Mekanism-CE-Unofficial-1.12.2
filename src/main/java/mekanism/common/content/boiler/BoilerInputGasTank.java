package mekanism.common.content.boiler;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;

import javax.annotation.Nullable;

public class BoilerInputGasTank extends BoilerGasTank {

    public BoilerInputGasTank(TileEntityBoilerCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    public boolean isValid(@Nullable Gas gas) {
        return gas == MekanismFluids.SuperheatedSodium;
    }

    @Override
    @Nullable
    public GasStack getGas() {
        return multiblock.structure != null ? multiblock.structure.InputGas : null;
    }

    @Override
    public void setGas(@Nullable GasStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.InputGas = stack;
        }
    }

    @Override
    public int getMaxGas() {
        if (multiblock.structure == null) {
            return 0;
        }
        return multiblock.isRemote() ? multiblock.clientWaterCapacity : multiblock.structure.getInputGasCapacity();
    }
}
