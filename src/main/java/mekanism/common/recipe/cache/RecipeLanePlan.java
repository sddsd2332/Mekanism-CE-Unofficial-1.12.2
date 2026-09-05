package mekanism.common.recipe.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Immutable worker result for one lane in an atomic whole-machine plan. */
public final class RecipeLanePlan {

    private final int laneIndex;
    private final int operations;
    private final int firstPassOperations;
    private final int completedPasses;
    private final double energy;
    private final int newOperatingTicks;
    private final boolean active;
    private final Map<String, Long> inputConsumption;
    private final Map<String, Long> perTickConsumption;
    private final Map<String, Long> completionConsumption;
    private final Map<String, ImmutableResourceSnapshot> outputs;
    private final Set<String> errors;

    public RecipeLanePlan(int laneIndex, int operations, int newOperatingTicks, boolean active,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors) {
        this(laneIndex, operations, 0, newOperatingTicks, active, Collections.emptyMap(),
              Collections.emptyMap(), Collections.emptyMap(), outputs, errors);
    }

    public RecipeLanePlan(int laneIndex, int operations, double energy, int newOperatingTicks,
          boolean active, Map<String, Long> inputConsumption,
          Map<String, Long> perTickConsumption, Map<String, Long> completionConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors) {
        this(laneIndex, operations, energy, newOperatingTicks, active, inputConsumption,
              perTickConsumption, completionConsumption, outputs, errors, operations,
              operations > 0 && newOperatingTicks == 0 ? 1 : 0);
    }

    public RecipeLanePlan(int laneIndex, int operations, double energy, int newOperatingTicks,
          boolean active, Map<String, Long> inputConsumption,
          Map<String, Long> perTickConsumption, Map<String, Long> completionConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors,
          int firstPassOperations, int completedPasses) {
        if (laneIndex < 0 || operations < 0 || newOperatingTicks < 0) {
            throw new IllegalArgumentException("Invalid lane plan value");
        }
        if (!Double.isFinite(energy) || energy < 0) {
            throw new IllegalArgumentException("Invalid lane energy value");
        }
        this.laneIndex = laneIndex;
        this.operations = operations;
        if (firstPassOperations < 0 || firstPassOperations > operations || completedPasses < 0 || completedPasses > operations) {
            throw new IllegalArgumentException("Invalid processing pass result");
        }
        this.firstPassOperations = firstPassOperations;
        this.completedPasses = completedPasses;
        this.energy = energy;
        this.newOperatingTicks = newOperatingTicks;
        this.active = active;
        this.inputConsumption = copyConsumption(inputConsumption);
        this.perTickConsumption = copyConsumption(perTickConsumption);
        this.completionConsumption = copyConsumption(completionConsumption);
        this.outputs = outputs == null || outputs.isEmpty() ? Collections.emptyMap() :
              Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.errors = errors == null || errors.isEmpty() ? Collections.emptySet() :
              Collections.unmodifiableSet(new LinkedHashSet<>(errors));
    }

    private static Map<String, Long> copyConsumption(Map<String, Long> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            long amount = entry.getValue() == null ? -1 : entry.getValue();
            if (amount < 0) throw new IllegalArgumentException("Lane resource consumption cannot be negative");
            copy.put(entry.getKey(), amount);
        }
        return Collections.unmodifiableMap(copy);
    }

    public int getLaneIndex() { return laneIndex; }
    public int getOperations() { return operations; }
    public int getFirstPassOperations() { return firstPassOperations; }
    public int getCompletedPasses() { return completedPasses; }
    public double getEnergyAsDouble() { return energy; }
    public int getNewOperatingTicks() { return newOperatingTicks; }
    public boolean isActive() { return active; }
    public Map<String, Long> getInputConsumption() { return inputConsumption; }
    public Map<String, Long> getPerTickConsumption() { return perTickConsumption; }
    public Map<String, Long> getCompletionConsumption() { return completionConsumption; }
    public Map<String, ImmutableResourceSnapshot> getOutputs() { return outputs; }
    public Set<String> getErrors() { return errors; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeLanePlan)) return false;
        RecipeLanePlan plan = (RecipeLanePlan) other;
        return laneIndex == plan.laneIndex && operations == plan.operations &&
              firstPassOperations == plan.firstPassOperations && completedPasses == plan.completedPasses &&
              Double.compare(energy, plan.energy) == 0 && newOperatingTicks == plan.newOperatingTicks &&
              active == plan.active && inputConsumption.equals(plan.inputConsumption) &&
              perTickConsumption.equals(plan.perTickConsumption) && completionConsumption.equals(plan.completionConsumption) &&
              outputs.equals(plan.outputs) && errors.equals(plan.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(laneIndex, operations, firstPassOperations, completedPasses, energy, newOperatingTicks, active, inputConsumption,
              perTickConsumption, completionConsumption, outputs, errors);
    }
}
