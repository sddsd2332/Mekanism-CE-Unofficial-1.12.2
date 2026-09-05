package mekanism.common.recipe.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable worker result which can be atomically validated and committed on server. */
public class RecipeExecutionPlan {

    private final String recipeId;
    private final String recipeSignature;
    private final long globalRecipeGeneration;
    private final long categoryRecipeGeneration;
    private final int laneIndex;
    private final long machineStateVersion;
    private final long configurationVersion;
    private final long qioLeaseVersion;
    private final long portOwnershipVersion;
    private final String mode;
    private final int operations;
    private final long energy;
    private final double energyDouble;
    private final int newOperatingTicks;
    private final boolean active;
    private final Map<String, Long> inputConsumption;
    private final Map<String, Long> perTickConsumption;
    private final Map<String, Long> completionConsumption;
    private final Map<String, ImmutableResourceSnapshot> outputs;
    private final Set<String> errors;
    private final long randomSeed;
    private final Map<Integer, RecipeLanePlan> lanes;

    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, operations, energy, newOperatingTicks, active, inputConsumption,
              Collections.emptyMap(), Collections.emptyMap(), outputs, errors, randomSeed, 0, 0, 0, "");
    }

    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, Long> perTickConsumption, Map<String, Long> completionConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed,
          long configurationVersion, long qioLeaseVersion, long portOwnershipVersion, String mode) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, operations, energy, newOperatingTicks, active, inputConsumption,
              perTickConsumption, completionConsumption, outputs, errors, randomSeed,
              configurationVersion, qioLeaseVersion, portOwnershipVersion, mode, Collections.emptyMap());
    }

    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, Long> perTickConsumption, Map<String, Long> completionConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed,
          long configurationVersion, long qioLeaseVersion, long portOwnershipVersion, String mode,
          Map<Integer, RecipeLanePlan> lanes) {
        this.recipeId = checkedId(recipeId);
        this.recipeSignature = recipeSignature == null ? "" : recipeSignature;
        if (globalRecipeGeneration < 0 || categoryRecipeGeneration < 0 || machineStateVersion < 0 ||
            laneIndex < 0 || operations < 0 || newOperatingTicks < 0 || !Double.isFinite(energy) || energy < 0) {
            throw new IllegalArgumentException("Invalid recipe execution plan value");
        }
        this.globalRecipeGeneration = globalRecipeGeneration;
        this.categoryRecipeGeneration = categoryRecipeGeneration;
        this.laneIndex = laneIndex;
        this.machineStateVersion = machineStateVersion;
        if (configurationVersion < 0 || qioLeaseVersion < 0 || portOwnershipVersion < 0) {
            throw new IllegalArgumentException("Plan configuration versions cannot be negative");
        }
        this.configurationVersion = configurationVersion;
        this.qioLeaseVersion = qioLeaseVersion;
        this.portOwnershipVersion = portOwnershipVersion;
        this.mode = mode == null ? "" : mode;
        this.operations = operations;
        this.energyDouble = energy;
        this.energy = energy >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) energy;
        this.newOperatingTicks = newOperatingTicks;
        this.active = active;
        this.inputConsumption = copyConsumption(inputConsumption);
        this.perTickConsumption = copyConsumption(perTickConsumption);
        this.completionConsumption = copyConsumption(completionConsumption);
        this.outputs = outputs == null || outputs.isEmpty() ? Collections.emptyMap() :
              Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.errors = errors == null || errors.isEmpty() ? Collections.emptySet() :
              Collections.unmodifiableSet(new LinkedHashSet<>(errors));
        this.randomSeed = randomSeed;
        this.lanes = copyLanes(lanes, laneIndex, operations, newOperatingTicks, active, outputs, errors);
    }

    /** Extended constructor without explicit per-tick/completion maps. */
    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed,
          long configurationVersion, long qioLeaseVersion, long portOwnershipVersion, String mode) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, operations, energy, newOperatingTicks, active, inputConsumption,
              Collections.emptyMap(), Collections.emptyMap(), outputs, errors, randomSeed,
              configurationVersion, qioLeaseVersion, portOwnershipVersion, mode);
    }

    public RecipeExecutionPlan(String recipeId, long globalRecipeGeneration, long machineStateVersion,
          int operations) {
        this(recipeId, "", globalRecipeGeneration, globalRecipeGeneration, 0, machineStateVersion,
              operations, 0, 0, operations > 0, Collections.emptyMap(), Collections.emptyMap(),
              Collections.emptySet(), 0);
    }

    protected RecipeExecutionPlan(Builder builder) {
        this(builder.recipeId, builder.recipeSignature, builder.globalRecipeGeneration,
              builder.categoryRecipeGeneration, builder.laneIndex, builder.machineStateVersion,
              builder.operations, builder.energy, builder.newOperatingTicks, builder.active,
              builder.inputConsumption, builder.perTickConsumption, builder.completionConsumption,
              builder.outputs, builder.errors, builder.randomSeed,
              builder.configurationVersion, builder.qioLeaseVersion, builder.portOwnershipVersion,
              builder.mode, builder.lanes);

    }

    private static String checkedId(String id) {
        String checked = Objects.requireNonNull(id, "recipeId");
        if (checked.isEmpty() || checked.length() > 512) throw new IllegalArgumentException("recipeId has an invalid length");
        return checked;
    }

    private static Map<String, Long> copyConsumption(Map<String, Long> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            long amount = Objects.requireNonNull(entry.getValue(), "consumption");
            if (amount < 0) throw new IllegalArgumentException("Resource consumption cannot be negative");
            copy.put(Objects.requireNonNull(entry.getKey(), "resource key"), amount);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Integer, RecipeLanePlan> copyLanes(Map<Integer, RecipeLanePlan> values,
          int fallbackLane, int operations, int newOperatingTicks, boolean active,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors) {
        Map<Integer, RecipeLanePlan> copy = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<Integer, RecipeLanePlan> entry : values.entrySet()) {
                Integer key = Objects.requireNonNull(entry.getKey(), "lane key");
                RecipeLanePlan value = Objects.requireNonNull(entry.getValue(), "lane plan");
                if (key < 0 || key != value.getLaneIndex() || copy.put(key, value) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate lane plan");
                }
            }
        }
        if (copy.isEmpty()) {
            copy.put(fallbackLane, new RecipeLanePlan(fallbackLane, operations, newOperatingTicks,
                  active, outputs, errors));
        }
        return Collections.unmodifiableMap(copy);
    }

    public String getRecipeId() { return recipeId; }
    public String getRecipeKey() { return recipeId; }
    public String getRecipeSignature() { return recipeSignature; }
    public String getSemanticSignature() { return recipeSignature; }
    public long getGlobalRecipeGeneration() { return globalRecipeGeneration; }
    public long getRecipeGeneration() { return globalRecipeGeneration; }
    public long getCategoryRecipeGeneration() { return categoryRecipeGeneration; }
    public long getCategoryVersion() { return categoryRecipeGeneration; }
    public int getLaneIndex() { return laneIndex; }
    public int getCacheIndex() { return laneIndex; }
    public long getMachineStateVersion() { return machineStateVersion; }
    public long getStateVersion() { return machineStateVersion; }
    public long getConfigurationVersion() { return configurationVersion; }
    public long getConfigVersion() { return configurationVersion; }
    public long getQioLeaseVersion() { return qioLeaseVersion; }
    public long getQIOLeaseVersion() { return qioLeaseVersion; }
    public long getPortOwnershipVersion() { return portOwnershipVersion; }
    public String getMode() { return mode; }
    public int getOperations() { return operations; }
    public int getOperationCount() { return operations; }
    public long getEnergy() { return energy; }
    public double getEnergyAsDouble() { return energyDouble; }
    public int getNewOperatingTicks() { return newOperatingTicks; }
    public int getOperatingTicks() { return newOperatingTicks; }
    public boolean isActive() { return active; }
    public Map<String, Long> getInputConsumption() { return inputConsumption; }
    public Map<String, Long> getInputCosts() { return inputConsumption; }
    public Map<String, Long> getPerTickConsumption() { return perTickConsumption; }
    public Map<String, Long> getCompletionConsumption() { return completionConsumption; }
    public Map<String, ImmutableResourceSnapshot> getOutputs() { return outputs; }
    public Set<String> getErrors() { return errors; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public long getRandomSeed() { return randomSeed; }
    public Map<Integer, RecipeLanePlan> getLanes() { return lanes; }
    public RecipeLanePlan getLane(int lane) { return lanes.get(lane); }

    /** Checks all optimistic-concurrency fields before a commit. */
    public boolean isValidFor(RecipeRunSnapshot snapshot) {
        return snapshot != null && recipeId.equals(snapshot.getRecipeId()) &&
              recipeSignature.equals(snapshot.getRecipeSignature()) &&
              globalRecipeGeneration == snapshot.getGlobalRecipeGeneration() &&
              categoryRecipeGeneration == snapshot.getCategoryRecipeGeneration() &&
              laneIndex == snapshot.getLaneIndex() &&
              machineStateVersion == snapshot.getMachineStateVersion() &&
              randomSeed == snapshot.getRandomSeed() &&
              configurationVersion == snapshot.getConfigurationVersion() &&
              qioLeaseVersion == snapshot.getQioLeaseVersion() &&
              portOwnershipVersion == snapshot.getPortOwnershipVersion() &&
              mode.equals(snapshot.getMode());
    }

    public boolean matches(RecipeRunSnapshot snapshot) { return isValidFor(snapshot); }

    public static Builder builder(String recipeId) { return new Builder(recipeId); }

    public static final class Builder {
        private final String recipeId;
        private String recipeSignature = "";
        private long globalRecipeGeneration;
        private long categoryRecipeGeneration;
        private int laneIndex;
        private long machineStateVersion;
        private long configurationVersion;
        private long qioLeaseVersion;
        private long portOwnershipVersion;
        private String mode = "";
        private int operations;
        private double energy;
        private int newOperatingTicks;
        private boolean active;
        private Map<String, Long> inputConsumption = Collections.emptyMap();
        private Map<String, Long> perTickConsumption = Collections.emptyMap();
        private Map<String, Long> completionConsumption = Collections.emptyMap();
        private Map<String, ImmutableResourceSnapshot> outputs = Collections.emptyMap();
        private Set<String> errors = Collections.emptySet();
        private long randomSeed;
        private Map<Integer, RecipeLanePlan> lanes = Collections.emptyMap();

        private Builder(String recipeId) { this.recipeId = recipeId; }
        public Builder recipeSignature(String value) { recipeSignature = value; return this; }
        public Builder globalRecipeGeneration(long value) { globalRecipeGeneration = value; return this; }
        public Builder categoryRecipeGeneration(long value) { categoryRecipeGeneration = value; return this; }
        public Builder recipeGeneration(long value) { return globalRecipeGeneration(value).categoryRecipeGeneration(value); }
        public Builder laneIndex(int value) { laneIndex = value; return this; }
        public Builder cacheIndex(int value) { return laneIndex(value); }
        public Builder machineStateVersion(long value) { machineStateVersion = value; return this; }
        public Builder stateVersion(long value) { return machineStateVersion(value); }
        public Builder configurationVersion(long value) { configurationVersion = value; return this; }
        public Builder configVersion(long value) { return configurationVersion(value); }
        public Builder qioLeaseVersion(long value) { qioLeaseVersion = value; return this; }
        public Builder QIOLeaseVersion(long value) { return qioLeaseVersion(value); }
        public Builder portOwnershipVersion(long value) { portOwnershipVersion = value; return this; }
        public Builder mode(String value) { mode = value; return this; }
        public Builder operations(int value) { operations = value; return this; }
        public Builder operationCount(int value) { return operations(value); }
        public Builder energy(double value) { energy = value; return this; }
        public Builder newOperatingTicks(int value) { newOperatingTicks = value; return this; }
        public Builder operatingTicks(int value) { return newOperatingTicks(value); }
        public Builder active(boolean value) { active = value; return this; }
        public Builder inputConsumption(Map<String, Long> value) { inputConsumption = value; return this; }
        public Builder perTickConsumption(Map<String, Long> value) { perTickConsumption = value; return this; }
        public Builder completionConsumption(Map<String, Long> value) { completionConsumption = value; return this; }
        public Builder perTickCost(String key, long value) {
            Map<String, Long> copy = new LinkedHashMap<>(perTickConsumption);
            copy.put(key, value);
            perTickConsumption = copy;
            return this;
        }
        public Builder completionCost(String key, long value) {
            Map<String, Long> copy = new LinkedHashMap<>(completionConsumption);
            copy.put(key, value);
            completionConsumption = copy;
            return this;
        }
        public Builder inputCost(String key, long value) {
            Map<String, Long> copy = new LinkedHashMap<>(inputConsumption); copy.put(key, value); inputConsumption = copy; return this;
        }
        public Builder outputs(Map<String, ImmutableResourceSnapshot> value) { outputs = value; return this; }
        public Builder output(String key, ImmutableResourceSnapshot value) {
            Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>(outputs); copy.put(key, value); outputs = copy; return this;
        }
        public Builder errors(Set<String> value) { errors = value; return this; }
        public Builder addError(String value) {
            Set<String> copy = new LinkedHashSet<>(errors); copy.add(Objects.requireNonNull(value)); errors = copy; return this;
        }
        public Builder randomSeed(long value) { randomSeed = value; return this; }
        public Builder lanes(Map<Integer, RecipeLanePlan> value) { lanes = value; return this; }
        public Builder lane(RecipeLanePlan value) {
            Map<Integer, RecipeLanePlan> copy = new LinkedHashMap<>(lanes);
            copy.put(value.getLaneIndex(), value);
            lanes = copy;
            return this;
        }
        public RecipeExecutionPlan build() { return new RecipeExecutionPlan(this); }
    }
}
