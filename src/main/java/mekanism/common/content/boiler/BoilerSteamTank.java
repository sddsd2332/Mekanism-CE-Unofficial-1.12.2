package mekanism.common.content.boiler;

import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class BoilerSteamTank extends BoilerTank {

    public BoilerSteamTank(TileEntityBoilerCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() == FluidRegistry.getFluid("steam");
    }

    @Override
    @Nullable
    public FluidStack getFluid() {
        return multiblock.structure != null ? multiblock.structure.steamStored : null;
    }

    @Override
    public void setFluid(FluidStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.steamStored = stack;
        }
    }

    @Override
    public int getCapacity() {
        if (multiblock.structure == null) {
            return 0;
        }
        return multiblock.isRemote() ? multiblock.clientSteamCapacity : multiblock.structure.getSteamCapacity();
    }
}
