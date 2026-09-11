package mekanism.common.integration.ic2;

import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import net.minecraft.util.EnumFacing;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies IC2 transfer probing does not mutate the sink before execution. */
class IC2TransferSimulationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void simulateAndExecuteHaveTheSameAcceptedAmount() {
        RecordingSink sink = new RecordingSink(20);

        double simulated = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 20, true);
        assertEquals(20, simulated, 0.000001);
        assertEquals(0, sink.accepted, 0.000001);

        double executed = IC2Integration.transferToSink(sink, EnumFacing.NORTH, 20, false);
        assertEquals(simulated, executed, 0.000001);
        assertEquals(20, sink.accepted, 0.000001);
    }

    private static final class RecordingSink implements IEnergySink {
        private final double demand;
        private double accepted;

        private RecordingSink(double demand) {
            this.demand = demand;
        }

        @Override
        public double getDemandedEnergy() {
            return Math.max(0, demand - accepted);
        }

        @Override
        public int getSinkTier() {
            return 0;
        }

        @Override
        public double injectEnergy(EnumFacing direction, double amount, double voltage) {
            double acceptedNow = Math.min(amount, getDemandedEnergy());
            accepted += acceptedNow;
            return amount - acceptedNow;
        }

        @Override
        public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing direction) {
            return true;
        }
    }
}
