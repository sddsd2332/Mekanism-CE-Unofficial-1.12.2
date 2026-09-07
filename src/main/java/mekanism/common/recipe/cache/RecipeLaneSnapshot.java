package mekanism.common.recipe.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable processing and resource state for one machine lane at capture time. */
public final class RecipeLaneSnapshot {

    private final int laneIndex;
    private final int operatingTicks;
    private final int requiredTicks;
    private final int baselineMaxOperations;
    private final int maxProcessingPasses;
    private final boolean active;
    private final boolean recipePresent;
    private final boolean keepProgressWithoutRecipe;
    private final Map<String, ImmutableResourceSnapshot> inputs;
    private final Map<String, ImmutableResourceSnapshot> outputContents;
    private final Map<String, Long> outputCapacities;
    private final Set<String> sharedInputKeys;
    private final Set<String> templateInputKeys;
    private final Set<String> sharedOutputKeys;
    private final Set<String> errors;
    private final RecipeSemanticsSnapshot recipeSemantics;
    private final double energyPerTick;
    private final boolean pooledOutputs;
    private final boolean interchangeableOutputs;
    private final boolean pausedForErrors;
    private final Map<String, Long> perTickInputMultipliers;
    private final Map<String, Map<String, Long>> outputInsertionLimits;

    public RecipeLaneSnapshot(int laneIndex, int operatingTicks, int requiredTicks, boolean active) {
        this(laneIndex, operatingTicks, requiredTicks, 1, active, true,
              Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
              Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
              RecipeSemanticsSnapshot.empty());
    }

    public RecipeLaneSnapshot(int laneIndex, int operatingTicks, int requiredTicks,
          int baselineMaxOperations, boolean active, boolean recipePresent,
          Map<String, ImmutableResourceSnapshot> inputs,
          Map<String, ImmutableResourceSnapshot> outputContents,
          Map<String, Long> outputCapacities, Set<String> sharedInputKeys,
          Set<String> sharedOutputKeys, Set<String> errors,
          RecipeSemanticsSnapshot recipeSemantics) {
        this(laneIndex, operatingTicks, requiredTicks, baselineMaxOperations, active, recipePresent,
              inputs, outputContents, outputCapacities, sharedInputKeys, sharedOutputKeys, errors,
              recipeSemantics, -1, false, false, false, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), false, 1);
    }

    private RecipeLaneSnapshot(int laneIndex, int operatingTicks, int requiredTicks,
          int baselineMaxOperations, boolean active, boolean recipePresent,
          Map<String, ImmutableResourceSnapshot> inputs,
          Map<String, ImmutableResourceSnapshot> outputContents,
          Map<String, Long> outputCapacities, Set<String> sharedInputKeys,
          Set<String> sharedOutputKeys, Set<String> errors,
          RecipeSemanticsSnapshot recipeSemantics, double energyPerTick, boolean pooledOutputs, boolean interchangeableOutputs, boolean pausedForErrors,
          Map<String, Long> perTickInputMultipliers, Map<String, Map<String, Long>> outputInsertionLimits, Set<String> templateInputKeys,
          boolean keepProgressWithoutRecipe, int maxProcessingPasses) {
        if (laneIndex < 0 || operatingTicks < 0 || requiredTicks < 0 || baselineMaxOperations < 0) {
            throw new IllegalArgumentException("Lane index, ticks, and operation limit cannot be negative");
        }
        this.laneIndex = laneIndex;
        this.operatingTicks = operatingTicks;
        this.requiredTicks = Math.max(1, requiredTicks);
        this.baselineMaxOperations = baselineMaxOperations;
        if (maxProcessingPasses < 1) throw new IllegalArgumentException("Processing pass limit must be positive");
        this.maxProcessingPasses = maxProcessingPasses;
        this.active = active;
        this.recipePresent = recipePresent;
        this.keepProgressWithoutRecipe = keepProgressWithoutRecipe;
        this.inputs = copyResources(inputs, "lane input");
        this.outputContents = copyResources(outputContents, "lane output");
        this.outputCapacities = copyCapacities(outputCapacities);
        this.sharedInputKeys = copySharedKeys(sharedInputKeys, this.inputs, "input");
        this.templateInputKeys = copySharedKeys(templateInputKeys, this.inputs, "template input");
        this.sharedOutputKeys = copySharedKeys(sharedOutputKeys, this.outputContents, "output");
        this.errors = copyErrors(errors);
        this.recipeSemantics = Objects.requireNonNull(recipeSemantics, "Lane recipe semantics cannot be null");
        if (!Double.isFinite(energyPerTick) || energyPerTick < -1) {
            throw new IllegalArgumentException("Invalid lane energy per tick");
        }
        this.energyPerTick = energyPerTick;
        this.pooledOutputs = pooledOutputs;
        this.interchangeableOutputs = interchangeableOutputs;
        this.pausedForErrors = pausedForErrors;
        this.perTickInputMultipliers = copyCapacities(perTickInputMultipliers);
        Map<String, Map<String, Long>> limits = new LinkedHashMap<>();
        outputInsertionLimits.forEach((key, value) -> limits.put(key, copyCapacities(value)));
        this.outputInsertionLimits = Collections.unmodifiableMap(limits);
    }

    private RecipeLaneSnapshot(Builder builder) {
        this(builder.laneIndex, builder.operatingTicks, builder.requiredTicks,
              builder.baselineMaxOperations, builder.active, builder.recipePresent,
              builder.inputs, builder.outputContents, builder.outputCapacities,
              builder.sharedInputKeys, builder.sharedOutputKeys, builder.errors,
              builder.recipeSemantics, builder.energyPerTick, builder.pooledOutputs, builder.interchangeableOutputs, builder.pausedForErrors,
              builder.perTickInputMultipliers, builder.outputInsertionLimits, builder.templateInputKeys, builder.keepProgressWithoutRecipe,
              builder.maxProcessingPasses);
    }

    private static Map<String, ImmutableResourceSnapshot> copyResources(
          Map<String, ImmutableResourceSnapshot> values, String description) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ImmutableResourceSnapshot> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), description + " key"),
                  Objects.requireNonNull(entry.getValue(), description + " value"));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Long> copyCapacities(Map<String, Long> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            long capacity = Objects.requireNonNull(entry.getValue(), "lane output capacity");
            if (capacity < 0) throw new IllegalArgumentException("Lane output capacity cannot be negative");
            copy.put(Objects.requireNonNull(entry.getKey(), "lane output capacity key"), capacity);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> copySharedKeys(Set<String> values,
          Map<String, ImmutableResourceSnapshot> resources, String description) {
        if (values == null || values.isEmpty()) return Collections.emptySet();
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            String key = Objects.requireNonNull(value, "shared lane " + description + " key");
            if (!resources.containsKey(key)) {
                throw new IllegalArgumentException("Shared lane " + description + " key has no resource: " + key);
            }
            copy.add(key);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Set<String> copyErrors(Set<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptySet();
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) copy.add(Objects.requireNonNull(value, "lane error"));
        return Collections.unmodifiableSet(copy);
    }

    RecipeLaneSnapshot withRecipeSemantics(boolean present, RecipeSemanticsSnapshot semantics) {
        if (present == recipePresent && semantics == recipeSemantics) return this;
        return new RecipeLaneSnapshot(this, present, semantics);
    }

    /** Only accepts an already frozen lane; public constructors still copy caller-owned data. */
    private RecipeLaneSnapshot(RecipeLaneSnapshot source, boolean present, RecipeSemanticsSnapshot semantics) {
        laneIndex = source.laneIndex;
        operatingTicks = source.operatingTicks;
        requiredTicks = source.requiredTicks;
        baselineMaxOperations = source.baselineMaxOperations;
        maxProcessingPasses = source.maxProcessingPasses;
        active = source.active;
        recipePresent = present;
        keepProgressWithoutRecipe = source.keepProgressWithoutRecipe;
        inputs = source.inputs;
        outputContents = source.outputContents;
        outputCapacities = source.outputCapacities;
        sharedInputKeys = source.sharedInputKeys;
        templateInputKeys = source.templateInputKeys;
        sharedOutputKeys = source.sharedOutputKeys;
        errors = source.errors;
        recipeSemantics = Objects.requireNonNull(semantics, "Lane recipe semantics cannot be null");
        energyPerTick = source.energyPerTick;
        pooledOutputs = source.pooledOutputs;
        interchangeableOutputs = source.interchangeableOutputs;
        pausedForErrors = source.pausedForErrors;
        perTickInputMultipliers = source.perTickInputMultipliers;
        outputInsertionLimits = source.outputInsertionLimits;
    }

    public int getLaneIndex() { return laneIndex; }
    public int getOperatingTicks() { return operatingTicks; }
    public int getRequiredTicks() { return requiredTicks; }
    public int getBaselineMaxOperations() { return baselineMaxOperations; }
    public int getMaxProcessingPasses() { return maxProcessingPasses; }
    public boolean isActive() { return active; }
    public boolean isRecipePresent() { return recipePresent; }
    public boolean shouldKeepProgressWithoutRecipe() { return keepProgressWithoutRecipe; }
    public Map<String, ImmutableResourceSnapshot> getInputs() { return inputs; }
    public Map<String, ImmutableResourceSnapshot> getOutputContents() { return outputContents; }
    public Map<String, Long> getOutputCapacities() { return outputCapacities; }
    public Set<String> getSharedInputKeys() { return sharedInputKeys; }
    public Set<String> getTemplateInputKeys() { return templateInputKeys; }
    public Set<String> getSharedOutputKeys() { return sharedOutputKeys; }
    public Set<String> getErrors() { return errors; }
    public RecipeSemanticsSnapshot getRecipeSemantics() { return recipeSemantics; }
    public double getEnergyPerTick() { return energyPerTick; }
    public boolean hasPooledOutputs() { return pooledOutputs; }
    public boolean hasInterchangeableOutputs() { return interchangeableOutputs; }
    public boolean isPausedForErrors() { return pausedForErrors; }
    public long getPerTickInputMultiplier(String key) { return perTickInputMultipliers.getOrDefault(key, 1L); }
    public long getOutputCapacity(String key, ImmutableResourceSnapshot output) {
        Map<String, Long> limits = outputInsertionLimits.get(key);
        return limits == null ? outputCapacities.getOrDefault(key, 0L) : limits.getOrDefault(output.identityKey(), 0L);
    }

    public static Builder builder(int laneIndex) { return new Builder(laneIndex); }

    public static final class Builder {
        private final int laneIndex;
        private int operatingTicks;
        private int requiredTicks = 1;
        private int baselineMaxOperations = 1;
        private int maxProcessingPasses = 1;
        private boolean active;
        private boolean recipePresent = true;
        private boolean keepProgressWithoutRecipe;
        private Map<String, ImmutableResourceSnapshot> inputs = Collections.emptyMap();
        private Map<String, ImmutableResourceSnapshot> outputContents = Collections.emptyMap();
        private Map<String, Long> outputCapacities = Collections.emptyMap();
        private Set<String> sharedInputKeys = Collections.emptySet();
        private Set<String> templateInputKeys = Collections.emptySet();
        private Set<String> sharedOutputKeys = Collections.emptySet();
        private Set<String> errors = Collections.emptySet();
        private RecipeSemanticsSnapshot recipeSemantics = RecipeSemanticsSnapshot.empty();
        private double energyPerTick = -1;
        private boolean pooledOutputs;
        private boolean interchangeableOutputs;
        private boolean pausedForErrors;
        private Map<String, Long> perTickInputMultipliers = Collections.emptyMap();
        private final Map<String, Map<String, Long>> outputInsertionLimits = new LinkedHashMap<>();

        private Builder(int laneIndex) { this.laneIndex = laneIndex; }
        public Builder energyPerTick(double value) { energyPerTick = value; return this; }
        public Builder pooledOutputs(boolean value) { pooledOutputs = value; return this; }
        public Builder interchangeableOutputs(boolean value) { interchangeableOutputs = value; return this; }
        public Builder pausedForErrors(boolean value) { pausedForErrors = value; return this; }
        public Builder templateInputKeys(Set<String> value) { templateInputKeys = value; return this; }
        public Builder keepProgressWithoutRecipe(boolean value) { keepProgressWithoutRecipe = value; return this; }
        public Builder maxProcessingPasses(int value) { maxProcessingPasses = value; return this; }
        public Builder perTickInputMultipliers(Map<String, Long> value) { perTickInputMultipliers = value; return this; }
        public Builder outputLimit(String key, ImmutableResourceSnapshot resource, long limit) {
            outputInsertionLimits.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(resource.identityKey(), limit);
            return this;
        }
        public Builder operatingTicks(int value) { operatingTicks = value; return this; }
        public Builder requiredTicks(int value) { requiredTicks = value; return this; }
        public Builder baselineMaxOperations(int value) { baselineMaxOperations = value; return this; }
        public Builder active(boolean value) { active = value; return this; }
        public Builder recipePresent(boolean value) { recipePresent = value; return this; }
        public Builder recipeSemantics(RecipeSemanticsSnapshot value) { recipeSemantics = value; return this; }
        public Builder inputs(Map<String, ImmutableResourceSnapshot> value) { inputs = value; return this; }
        public Builder outputContents(Map<String, ImmutableResourceSnapshot> value) { outputContents = value; return this; }
        public Builder outputCapacities(Map<String, Long> value) { outputCapacities = value; return this; }
        public Builder errors(Set<String> value) { errors = value; return this; }
        public Builder input(String key, ImmutableResourceSnapshot value) {
            return input(key, value, false);
        }
        public Builder input(String key, ImmutableResourceSnapshot value, boolean shared) {
            Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>(inputs);
            copy.put(key, value);
            inputs = copy;
            if (shared) {
                Set<String> sharedCopy = new LinkedHashSet<>(sharedInputKeys);
                sharedCopy.add(key);
                sharedInputKeys = sharedCopy;
            }
            return this;
        }
        public Builder output(String key, ImmutableResourceSnapshot value, long capacity) {
            return output(key, value, capacity, false);
        }
        public Builder output(String key, ImmutableResourceSnapshot value, long capacity, boolean shared) {
            Map<String, ImmutableResourceSnapshot> resourceCopy = new LinkedHashMap<>(outputContents);
            resourceCopy.put(key, value);
            outputContents = resourceCopy;
            Map<String, Long> capacityCopy = new LinkedHashMap<>(outputCapacities);
            capacityCopy.put(key, capacity);
            outputCapacities = capacityCopy;
            if (shared) {
                Set<String> sharedCopy = new LinkedHashSet<>(sharedOutputKeys);
                sharedCopy.add(key);
                sharedOutputKeys = sharedCopy;
            }
            return this;
        }
        public Builder error(String value) {
            Set<String> copy = new LinkedHashSet<>(errors);
            copy.add(value);
            errors = copy;
            return this;
        }
        public RecipeLaneSnapshot build() { return new RecipeLaneSnapshot(this); }
    }
}
