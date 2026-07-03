package mekanism.common.tile.multiblock;

import mekanism.api.Coord4D;
import mekanism.api.IContentsListener;
import mekanism.api.IHeatTransfer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.heat.ProxiedHeatCapacitorHolder;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.EnumFacing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TileEntityThermalEvaporationValve extends TileEntityThermalEvaporationBlock implements IHeatTransfer, IComparatorSupport {

    public boolean prevMaster = false;
    private int currentRedstoneLevel;

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> getController() != null,
              side -> getController() != null,
              this::getValveFluidTanks,
              this::getValveFluidTanksForInsert,
              this::getValveFluidTanksForExtract
        );
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        return ProxiedHeatCapacitorHolder.create(
              side -> true,
              side -> true,
              side -> getController() == null ? Collections.emptyList() : Collections.singletonList(this)
        );
    }

    private List<IExtendedFluidTank> getValveFluidTanks(EnumFacing side) {
        TileEntityThermalEvaporationController controller = getController();
        return controller == null ? Collections.emptyList() : Arrays.asList(controller.inputTank, controller.outputTank);
    }

    private List<IExtendedFluidTank> getValveFluidTanksForInsert(EnumFacing side) {
        TileEntityThermalEvaporationController controller = getController();
        return controller == null ? Collections.emptyList() : Collections.singletonList(controller.inputTank);
    }

    private List<IExtendedFluidTank> getValveFluidTanksForExtract(EnumFacing side) {
        TileEntityThermalEvaporationController controller = getController();
        return controller == null ? Collections.emptyList() : Collections.singletonList(controller.outputTank);
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if ((master == null) == prevMaster) {
            for (EnumFacing side : EnumFacing.VALUES) {
                Coord4D obj = Coord4D.get(this).offset(side);
                if (obj.exists(world) && !obj.isAirBlock(world) && !(obj.getTileEntity(world) instanceof TileEntityThermalEvaporationBlock)) {
                    MekanismUtils.notifyNeighborofChange(world, obj, this.pos);
                }
            }
        }
        prevMaster = master != null;
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public double getTemp() {
        return 0;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 1;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return 0;
    }

    @Override
    public void transferHeatTo(double heat) {
        TileEntityThermalEvaporationController controller = getController();
        if (controller != null) {
            controller.heatToAbsorb += heat;
        }
    }

    @Override
    public double[] simulateHeat() {
        return new double[]{0, 0};
    }

    @Override
    public double applyTemperatureChange() {
        return 0;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return getController() != null;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        return null;
    }

    @Override
    public int getRedstoneLevel() {
        TileEntityThermalEvaporationController controller = getController();
        if (controller != null) {
            return MekanismUtils.redstoneLevelFromContents(controller.inputTank.getFluidAmount(), controller.inputTank.getCapacity());
        }
        return 0;
    }
}
