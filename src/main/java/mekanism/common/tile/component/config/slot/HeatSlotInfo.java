package mekanism.common.tile.component.config.slot;

import mekanism.api.heat.IHeatCapacitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HeatSlotInfo extends BaseSlotInfo {

    private final List<IHeatCapacitor> heatCapacitors;

    public HeatSlotInfo(boolean canInput, boolean canOutput, IHeatCapacitor... heatCapacitors) {
        this(canInput, canOutput, Arrays.asList(heatCapacitors));
    }

    public HeatSlotInfo(boolean canInput, boolean canOutput, List<IHeatCapacitor> heatCapacitors) {
        super(canInput, canOutput);
        this.heatCapacitors = Collections.unmodifiableList(heatCapacitors);
    }

    public List<IHeatCapacitor> getHeatCapacitors() {
        return heatCapacitors;
    }
}
