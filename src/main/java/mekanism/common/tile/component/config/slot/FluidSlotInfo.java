package mekanism.common.tile.component.config.slot;

import mekanism.api.fluid.IExtendedFluidTank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FluidSlotInfo extends BaseSlotInfo {

    private final List<IExtendedFluidTank> tanks;
    private final List<IExtendedFluidTank> inputTanks;
    private final List<IExtendedFluidTank> outputTanks;

    public FluidSlotInfo(boolean canInput, boolean canOutput, IExtendedFluidTank... tanks) {
        this(canInput, canOutput, Arrays.asList(tanks));
    }

    public FluidSlotInfo(boolean canInput, boolean canOutput, List<IExtendedFluidTank> tanks) {
        this(canInput, canOutput, tanks, canInput ? tanks : Collections.emptyList(), canOutput ? tanks : Collections.emptyList());
    }

    public FluidSlotInfo(boolean canInput, boolean canOutput, List<IExtendedFluidTank> tanks, List<IExtendedFluidTank> outputTanks) {
        this(canInput, canOutput, tanks, getInputTanks(canInput, canOutput, tanks, outputTanks), canOutput ? outputTanks : Collections.emptyList());
    }

    public FluidSlotInfo(boolean canInput, boolean canOutput, List<IExtendedFluidTank> tanks, List<IExtendedFluidTank> inputTanks,
          List<IExtendedFluidTank> outputTanks) {
        super(canInput, canOutput);
        this.tanks = Collections.unmodifiableList(new ArrayList<>(tanks));
        this.inputTanks = Collections.unmodifiableList(new ArrayList<>(inputTanks));
        this.outputTanks = Collections.unmodifiableList(new ArrayList<>(outputTanks));
    }

    public List<IExtendedFluidTank> getTanks() {
        return tanks;
    }

    public List<IExtendedFluidTank> getInputTanks() {
        return canInput() ? inputTanks : Collections.emptyList();
    }

    public List<IExtendedFluidTank> getOutputTanks() {
        return canOutput() ? outputTanks : Collections.emptyList();
    }

    public boolean canInput(IExtendedFluidTank tank) {
        return getInputTanks().contains(tank);
    }

    public boolean canOutput(IExtendedFluidTank tank) {
        return getOutputTanks().contains(tank);
    }

    @Override
    public boolean isEmpty() {
        for (IExtendedFluidTank tank : getOutputTanks()) {
            if (tank.getFluidAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean hasTank(IExtendedFluidTank tank) {
        return tanks.contains(tank);
    }

    private static List<IExtendedFluidTank> getInputTanks(boolean canInput, boolean canOutput, List<IExtendedFluidTank> tanks,
          List<IExtendedFluidTank> outputTanks) {
        if (!canInput) {
            return Collections.emptyList();
        }
        if (canOutput && !outputTanks.isEmpty() && outputTanks.size() < tanks.size()) {
            List<IExtendedFluidTank> inputTanks = new ArrayList<>(tanks);
            inputTanks.removeAll(outputTanks);
            if (!inputTanks.isEmpty()) {
                return inputTanks;
            }
        }
        return tanks;
    }
}
