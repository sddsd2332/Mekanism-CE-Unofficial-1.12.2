package mekanism.generators.common.tile.turbine;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.PipeUtils;
import mekanism.generators.common.content.turbine.TurbineVentFluidTank;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Collections;
import java.util.EnumSet;

public class TileEntityTurbineVent extends TileEntityTurbineCasing {

    public TurbineVentFluidTank ventTank;

    public TileEntityTurbineVent() {
        super("TurbineVent");
        ventTank = new TurbineVentFluidTank(this);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> false,
              side -> isFormed(),
              side -> isFormed() ? Collections.singletonList(ventTank) : Collections.emptyList()
        );
    }

    private boolean isFormed() {
        return (!isRemote() && structure != null) || (isRemote() && clientHasStructure);
    }


    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null && structure.getVentWaterAmount() > 0) {
            EmitUtils.forEachSide(getWorld(), getPos(), EnumSet.allOf(EnumFacing.class), (tile, side) -> {
                FluidStack fluidStack = ventTank.getFluid();
                if (fluidStack == null) {
                    return;
                }
                IFluidHandler handler = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
                if (handler != null && PipeUtils.canFill(handler, fluidStack)) {
                    int filled = handler.fill(fluidStack, true);
                    if (filled > 0) {
                        ventTank.extract(filled, Action.EXECUTE, AutomationType.INTERNAL);
                    }
                }
            });
        }
    }
}
