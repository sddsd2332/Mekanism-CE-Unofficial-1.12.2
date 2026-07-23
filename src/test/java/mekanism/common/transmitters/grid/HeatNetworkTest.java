package mekanism.common.transmitters.grid;

import mekanism.api.heat.HeatAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatNetworkTest {

    @Test
    void efficiencyDoesNotOverflowAtSaturatedFlow() {
        assertEquals(0.5, HeatNetwork.getEfficiency(HeatAPI.MAX_HEAT, HeatAPI.MAX_HEAT));
        assertEquals(1, HeatNetwork.getEfficiency(HeatAPI.MAX_HEAT, 0));
        assertEquals(0, HeatNetwork.getEfficiency(0, HeatAPI.MAX_HEAT));
    }
}
