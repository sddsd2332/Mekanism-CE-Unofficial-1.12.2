package mekanism.common.recipe.cache;

import java.util.Objects;

/** Immutable fuel and generator observations, captured only on the server. */
public final class GasFuelState {
    public final ImmutableResourceSnapshot fuel;
    public final int capacity;
    public final int burnTicks;
    public final int maxBurnTicks;
    public final double generationRate;
    public final double output;
    public final double clientUsed;
    public final double storedEnergy;
    public final double maxEnergy;
    public final int recipeBurnTicks;
    public final double recipeEnergy;
    public final long processes;
    public final boolean canOperate;
    public final boolean canInsertCurrentRate;
    public final boolean dynamicOutput;
    public final double resetOutput;
    public final long outputProcesses;

    public GasFuelState(ImmutableResourceSnapshot fuel, int capacity, int burnTicks, int maxBurnTicks,
          double generationRate, double output, double clientUsed, double storedEnergy, double maxEnergy,
          int recipeBurnTicks, double recipeEnergy, long processes, boolean canOperate,
          boolean canInsertCurrentRate, boolean dynamicOutput, double resetOutput, long outputProcesses) {
        this.fuel = Objects.requireNonNull(fuel);
        this.capacity = capacity;
        this.burnTicks = burnTicks;
        this.maxBurnTicks = maxBurnTicks;
        this.generationRate = generationRate;
        this.output = output;
        this.clientUsed = clientUsed;
        this.storedEnergy = storedEnergy;
        this.maxEnergy = maxEnergy;
        this.recipeBurnTicks = recipeBurnTicks;
        this.recipeEnergy = recipeEnergy;
        this.processes = processes;
        this.canOperate = canOperate;
        this.canInsertCurrentRate = canInsertCurrentRate;
        this.dynamicOutput = dynamicOutput;
        this.resetOutput = resetOutput;
        this.outputProcesses = outputProcesses;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GasFuelState)) return false;
        GasFuelState state = (GasFuelState) other;
        return fuel.equals(state.fuel) && capacity == state.capacity && burnTicks == state.burnTicks &&
              maxBurnTicks == state.maxBurnTicks && Double.compare(generationRate, state.generationRate) == 0 &&
              Double.compare(output, state.output) == 0 && Double.compare(clientUsed, state.clientUsed) == 0 &&
              Double.compare(storedEnergy, state.storedEnergy) == 0 && Double.compare(maxEnergy, state.maxEnergy) == 0 &&
              recipeBurnTicks == state.recipeBurnTicks && Double.compare(recipeEnergy, state.recipeEnergy) == 0 &&
              processes == state.processes && canOperate == state.canOperate && canInsertCurrentRate == state.canInsertCurrentRate &&
              dynamicOutput == state.dynamicOutput && Double.compare(resetOutput, state.resetOutput) == 0 &&
              outputProcesses == state.outputProcesses;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fuel, capacity, burnTicks, maxBurnTicks, generationRate, output, clientUsed,
              storedEnergy, maxEnergy, recipeBurnTicks, recipeEnergy, processes, canOperate,
              canInsertCurrentRate, dynamicOutput, resetOutput, outputProcesses);
    }
}
