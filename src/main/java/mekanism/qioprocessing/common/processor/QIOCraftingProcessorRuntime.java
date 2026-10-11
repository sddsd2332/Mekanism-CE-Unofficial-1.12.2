package mekanism.qioprocessing.common.processor;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;

import java.util.Map;
import java.util.Objects;

/**
 * Pure, server-thread processor scheduling rules.
 *
 * <p>This class deliberately does not locate worlds, QIO frequencies, recipes, or neighboring
 * tiles. Those operations belong to the Provider/Manifest executor. It only advances a validated
 * processor state when the host has enough local energy.</p>
 */
public final class QIOCraftingProcessorRuntime {
    public static final int MAX_UPGRADES = 64;
    private static final double MIN_ENERGY_PER_TICK = 0.0001D;

    public enum TickStatus {
        INACTIVE,
        WAITING_ENERGY,
        ADVANCED,
        COLLECTING
    }

    public static final class TickResult {
        private final QIOCraftingProcessorState state;
        private final double energy;
        private final TickStatus status;
        private final int advancedLanes;

        private TickResult(QIOCraftingProcessorState state, double energy, TickStatus status,
                           int advancedLanes) {
            this.state = state;
            this.energy = energy;
            this.status = status;
            this.advancedLanes = advancedLanes;
        }

        public QIOCraftingProcessorState state() {
            return state;
        }

        public double energy() {
            return energy;
        }

        public TickStatus status() {
            return status;
        }

        public int advancedLanes() {
            return advancedLanes;
        }
    }

    private QIOCraftingProcessorRuntime() {
    }

    public static double maxEnergy(QIOCraftingProcessorDefinition definition, int energyUpgrades) {
        Objects.requireNonNull(definition, "definition");
        int upgrades = boundedUpgradeCount(energyUpgrades, definition.getEnergyUpgradeLimit());
        return finite(definition.getEnergyCapacity() * (1D + upgrades));
    }

    public static double energyPerTick(QIOCraftingProcessorDefinition definition, int speedUpgrades,
                                       int energyUpgrades) {
        Objects.requireNonNull(definition, "definition");
        int speed = boundedUpgradeCount(speedUpgrades, definition.getSpeedUpgradeLimit());
        int energy = boundedUpgradeCount(energyUpgrades, definition.getEnergyUpgradeLimit());
        double speedMultiplier = 1D + speed * 0.5D;
        double energyMultiplier = Math.max(0.25D, 1D - energy * 0.05D);
        return Math.max(MIN_ENERGY_PER_TICK,
                finite(definition.getBaseEnergyUsage() * speedMultiplier * energyMultiplier));
    }

    public static long processingTicks(long baseTicks, int speedUpgrades,
                                       QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (baseTicks <= 0) throw new IllegalArgumentException("Base processing ticks must be positive");
        int speed = boundedUpgradeCount(speedUpgrades, definition.getSpeedUpgradeLimit());
        return Math.max(1L, (long) Math.ceil(baseTicks / (1D + speed * 0.5D)));
    }

    public static long operationsPerBatch(int stackingUpgrades,
                                          QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        int stacking = boundedUpgradeCount(stackingUpgrades, definition.getStackingUpgradeLimit());
        if (stacking >= 62) return Long.MAX_VALUE;
        return 1L << stacking;
    }

    public static TickResult tick(QIOCraftingProcessorState state,
                                  QIOCraftingProcessorDefinition definition, double storedEnergy, int speedUpgrades,
                                  int energyUpgrades, long tick) {
        return tick(state, definition, storedEnergy, speedUpgrades, energyUpgrades, tick, 1);
    }

    public static TickResult tick(QIOCraftingProcessorState state,
                                  QIOCraftingProcessorDefinition definition, double storedEnergy, int speedUpgrades,
                                  int energyUpgrades, long tick, long operationMultiplier) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(definition, "definition");
        if (operationMultiplier <= 0) {
            throw new IllegalArgumentException("Processor operation multiplier must be positive");
        }
        if (!Double.isFinite(storedEnergy) || storedEnergy < 0) {
            throw new IllegalArgumentException("Processor energy is invalid");
        }
        if (tick < 0) throw new IllegalArgumentException("Processor tick cannot be negative");
        if (state.state() != QIOCraftingProcessorState.State.ACTIVE) {
            return new TickResult(state, storedEnergy, TickStatus.INACTIVE, 0);
        }
        double energy = Math.min(storedEnergy, maxEnergy(definition, energyUpgrades));
        double cost = finite(energyPerTick(definition, speedUpgrades, energyUpgrades) *
                operationMultiplier);
        QIOCraftingProcessorState next = state;
        int advanced = 0;
        boolean waitingEnergy = false;
        boolean collecting = false;
        for (Map.Entry<Long, QIOCraftingProcessorState.Lane> entry : state.lanes().entrySet()) {
            QIOCraftingProcessorState.Lane lane = entry.getValue();
            switch (lane.state()) {
                case ALLOCATED:
                    next = next.updateLane(lane.laneId(), lane.currentTick(),
                            QIOCraftingProcessorState.LaneState.LOADING, "lane allocated");
                    break;
                case LOADING:
                    next = next.updateLane(lane.laneId(), lane.currentTick(),
                            QIOCraftingProcessorState.LaneState.PROCESSING, "inputs loaded");
                    break;
                case PROCESSING:
                    if (energy + MIN_ENERGY_PER_TICK < cost) {
                        waitingEnergy = true;
                        break;
                    }
                    energy -= cost;
                    long progress = lane.currentTick() + 1;
                    QIOCraftingProcessorState.LaneState target = progress >= lane.totalTicks()
                            ? QIOCraftingProcessorState.LaneState.COLLECTING
                            : QIOCraftingProcessorState.LaneState.PROCESSING;
                    next = next.updateLane(lane.laneId(), progress, target,
                            target == QIOCraftingProcessorState.LaneState.COLLECTING
                                    ? "processing finished" : "processing");
                    advanced++;
                    if (target == QIOCraftingProcessorState.LaneState.COLLECTING) collecting = true;
                    break;
                case COLLECTING:
                    collecting = true;
                    break;
                default:
                    break;
            }
        }
        TickStatus status = collecting && advanced == 0 ? TickStatus.COLLECTING
                : waitingEnergy && advanced == 0 ? TickStatus.WAITING_ENERGY : TickStatus.ADVANCED;
        return new TickResult(next, energy, status, advanced);
    }

    public static QIOCraftingProcessorState completeLane(QIOCraftingProcessorState state,
                                                         long laneId) {
        Objects.requireNonNull(state, "state");
        return state.updateLane(laneId, state.lanes().get(laneId).currentTick(),
                QIOCraftingProcessorState.LaneState.COMPLETED, "outputs delivered");
    }

    public static QIOCraftingProcessorState cancelLane(QIOCraftingProcessorState state,
                                                       long laneId, String reason) {
        Objects.requireNonNull(state, "state");
        return state.updateLane(laneId, state.lanes().get(laneId).currentTick(),
                QIOCraftingProcessorState.LaneState.CANCELLED, reason);
    }

    private static int boundedUpgradeCount(int value, int limit) {
        if (value < 0 || value > MAX_UPGRADES) throw new IllegalArgumentException("Invalid upgrade count");
        return Math.min(value, limit);
    }

    private static double finite(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Processor value is invalid");
        return value;
    }
}
