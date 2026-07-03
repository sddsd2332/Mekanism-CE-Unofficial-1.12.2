package mekanism.common.tile.component.config.slot;

import mekanism.api.gas.IExtendedGasTank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GasSlotInfo extends BaseSlotInfo {

    private final List<IExtendedGasTank> tanks;
    private final List<IExtendedGasTank> inputTanks;
    private final List<IExtendedGasTank> outputTanks;

    public GasSlotInfo(boolean canInput, boolean canOutput, IExtendedGasTank... tanks) {
        this(canInput, canOutput, Arrays.asList(tanks));
    }

    public GasSlotInfo(boolean canInput, boolean canOutput, List<IExtendedGasTank> tanks) {
        this(canInput, canOutput, tanks, canInput ? tanks : Collections.emptyList(), canOutput ? tanks : Collections.emptyList());
    }

    public GasSlotInfo(boolean canInput, boolean canOutput, List<IExtendedGasTank> tanks, List<IExtendedGasTank> outputTanks) {
        this(canInput, canOutput, tanks, getInputTanks(canInput, canOutput, tanks, outputTanks), canOutput ? outputTanks : Collections.emptyList());
    }

    public GasSlotInfo(boolean canInput, boolean canOutput, List<IExtendedGasTank> tanks, List<IExtendedGasTank> inputTanks,
          List<IExtendedGasTank> outputTanks) {
        super(canInput, canOutput);
        this.tanks = Collections.unmodifiableList(new ArrayList<>(tanks));
        this.inputTanks = Collections.unmodifiableList(new ArrayList<>(inputTanks));
        this.outputTanks = Collections.unmodifiableList(new ArrayList<>(outputTanks));
    }

    public List<IExtendedGasTank> getTanks() {
        return tanks;
    }

    public List<IExtendedGasTank> getInputTanks() {
        return canInput() ? inputTanks : Collections.emptyList();
    }

    public List<IExtendedGasTank> getOutputTanks() {
        return canOutput() ? outputTanks : Collections.emptyList();
    }

    public boolean canInput(IExtendedGasTank tank) {
        return getInputTanks().contains(tank);
    }

    public boolean canOutput(IExtendedGasTank tank) {
        return getOutputTanks().contains(tank);
    }

    @Override
    public boolean isEmpty() {
        for (IExtendedGasTank tank : getOutputTanks()) {
            if (tank.getGasAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean hasTank(IExtendedGasTank tank) {
        return tanks.contains(tank);
    }

    private static List<IExtendedGasTank> getInputTanks(boolean canInput, boolean canOutput, List<IExtendedGasTank> tanks,
          List<IExtendedGasTank> outputTanks) {
        if (!canInput) {
            return Collections.emptyList();
        }
        if (canOutput && !outputTanks.isEmpty() && outputTanks.size() < tanks.size()) {
            List<IExtendedGasTank> inputTanks = new ArrayList<>(tanks);
            inputTanks.removeAll(outputTanks);
            if (!inputTanks.isEmpty()) {
                return inputTanks;
            }
        }
        return tanks;
    }
}
