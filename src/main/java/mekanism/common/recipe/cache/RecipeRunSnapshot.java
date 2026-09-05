package mekanism.common.recipe.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Short-lived immutable input to a recipe planner. It deliberately contains only
 * copied resource values and primitive world/machine observations.
 */
public class RecipeRunSnapshot {

    private final String recipeId;
    private final String recipeSignature;
    private final long globalRecipeGeneration;
    private final long categoryRecipeGeneration;
    private final int laneIndex;
    private final long machineStateVersion;
    private final long randomSeed;
    private final long configurationVersion;
    private final long qioLeaseVersion;
    private final long portOwnershipVersion;
    private final String mode;
    private final int operatingTicks;
    private final int requiredTicks;
    private final double storedEnergy;
    private final double energyPerTick;
    private final boolean redstonePowered;
    private final boolean active;
    private final int dimension;
    private final long worldTime;
    private final Map<String, ImmutableResourceSnapshot> inputs;
    private final Map<String, ImmutableResourceSnapshot> outputs;
    private final Map<String, Integer> upgrades;
    private final RecipeSemanticsSnapshot recipeSemantics;
    private final Map<Integer, RecipeLaneSnapshot> lanes;
    private final boolean explicitLanes;

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, randomSeed, operatingTicks, requiredTicks, storedEnergy, energyPerTick,
              redstonePowered, active, dimension, worldTime, inputs, outputs, upgrades, 0, 0, 0, "",
              RecipeSemanticsSnapshot.empty());
    }

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, randomSeed, operatingTicks, requiredTicks, storedEnergy, energyPerTick,
              redstonePowered, active, dimension, worldTime, inputs, outputs, upgrades,
              configurationVersion, qioLeaseVersion, portOwnershipVersion, mode,
              RecipeSemanticsSnapshot.empty());
    }

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode, RecipeSemanticsSnapshot recipeSemantics) {
        this(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, randomSeed, operatingTicks, requiredTicks, storedEnergy, energyPerTick,
              redstonePowered, active, dimension, worldTime, inputs, outputs, upgrades,
              configurationVersion, qioLeaseVersion, portOwnershipVersion, mode, recipeSemantics,
              Collections.emptyMap());
    }

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode, RecipeSemanticsSnapshot recipeSemantics,
          Map<Integer, RecipeLaneSnapshot> lanes) {
        this.recipeId = checkedId(recipeId);
        this.recipeSignature = recipeSignature == null ? "" : recipeSignature;
        if (globalRecipeGeneration < 0 || categoryRecipeGeneration < 0 || machineStateVersion < 0) {
            throw new IllegalArgumentException("Snapshot generations and state version cannot be negative");
        }
        if (laneIndex < 0 || operatingTicks < 0 || requiredTicks < 0) {
            throw new IllegalArgumentException("Snapshot indexes and ticks cannot be negative");
        }
        this.globalRecipeGeneration = globalRecipeGeneration;
        this.categoryRecipeGeneration = categoryRecipeGeneration;
        this.laneIndex = laneIndex;
        this.machineStateVersion = machineStateVersion;
        this.randomSeed = randomSeed;
        if (configurationVersion < 0 || qioLeaseVersion < 0 || portOwnershipVersion < 0) {
            throw new IllegalArgumentException("Snapshot configuration versions cannot be negative");
        }
        this.configurationVersion = configurationVersion;
        this.qioLeaseVersion = qioLeaseVersion;
        this.portOwnershipVersion = portOwnershipVersion;
        this.mode = mode == null ? "" : mode;
        this.operatingTicks = operatingTicks;
        this.requiredTicks = requiredTicks;
        this.storedEnergy = finiteNonNegative(storedEnergy);
        this.energyPerTick = finiteNonNegative(energyPerTick);
        this.redstonePowered = redstonePowered;
        this.active = active;
        this.dimension = dimension;
        this.worldTime = worldTime;
        this.inputs = copyResources(inputs);
        this.outputs = copyResources(outputs);
        this.upgrades = copyUpgrades(upgrades);
        this.recipeSemantics = Objects.requireNonNull(recipeSemantics, "Recipe semantics cannot be null");
        this.explicitLanes = lanes != null && !lanes.isEmpty();
        this.lanes = copyLanes(lanes, laneIndex, operatingTicks, requiredTicks, active);
    }

    public RecipeRunSnapshot(String recipeId, long globalRecipeGeneration, long machineStateVersion,
          long randomSeed) {
        this(recipeId, "", globalRecipeGeneration, globalRecipeGeneration, 0, machineStateVersion, randomSeed,
              0, 0, 0, 0, false, false, 0, 0, Collections.emptyMap(), Collections.emptyMap(),
              Collections.emptyMap());
    }

    protected RecipeRunSnapshot(Builder builder) {
        this(builder.recipeId, builder.recipeSignature, builder.globalRecipeGeneration,
              builder.categoryRecipeGeneration, builder.laneIndex, builder.machineStateVersion,
              builder.randomSeed, builder.operatingTicks, builder.requiredTicks, builder.storedEnergy,
              builder.energyPerTick, builder.redstonePowered, builder.active, builder.dimension,
              builder.worldTime, builder.inputs, builder.outputs, builder.upgrades,
              builder.configurationVersion, builder.qioLeaseVersion, builder.portOwnershipVersion,
              builder.mode, builder.recipeSemantics, builder.lanes);
    }

    private static String checkedId(String id) {
        String checked = Objects.requireNonNull(id, "recipeId");
        if (checked.isEmpty() || checked.length() > 512) {
            throw new IllegalArgumentException("recipeId has an invalid length");
        }
        return checked;
    }

    private static Map<String, ImmutableResourceSnapshot> copyResources(Map<String, ImmutableResourceSnapshot> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ImmutableResourceSnapshot> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "resource key");
            ImmutableResourceSnapshot value = Objects.requireNonNull(entry.getValue(), "resource value");
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> copyUpgrades(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "upgrade key"), Math.max(0,
                  Objects.requireNonNull(entry.getValue(), "upgrade value")));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private static Map<Integer, RecipeLaneSnapshot> copyLanes(Map<Integer, RecipeLaneSnapshot> values,
          int fallbackLane, int fallbackTicks, int fallbackRequiredTicks, boolean fallbackActive) {
        Map<Integer, RecipeLaneSnapshot> copy = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<Integer, RecipeLaneSnapshot> entry : values.entrySet()) {
                Integer key = Objects.requireNonNull(entry.getKey(), "lane key");
                RecipeLaneSnapshot value = Objects.requireNonNull(entry.getValue(), "lane value");
                if (key < 0 || key != value.getLaneIndex() || copy.put(key, value) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate lane snapshot");
                }
            }
        }
        if (copy.isEmpty()) {
            copy.put(fallbackLane, new RecipeLaneSnapshot(fallbackLane, fallbackTicks,
                  Math.max(1, fallbackRequiredTicks), fallbackActive));
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
    public long getRandomSeed() { return randomSeed; }
    public long getConfigurationVersion() { return configurationVersion; }
    public long getConfigVersion() { return configurationVersion; }
    public long getQioLeaseVersion() { return qioLeaseVersion; }
    public long getQIOLeaseVersion() { return qioLeaseVersion; }
    public long getPortOwnershipVersion() { return portOwnershipVersion; }
    public String getMode() { return mode; }
    public int getOperatingTicks() { return operatingTicks; }
    public int getRequiredTicks() { return requiredTicks; }
    public double getStoredEnergy() { return storedEnergy; }
    public double getEnergyPerTick() { return energyPerTick; }
    public boolean isRedstonePowered() { return redstonePowered; }
    public boolean isActive() { return active; }
    public int getDimension() { return dimension; }
    public long getWorldTime() { return worldTime; }
    public Map<String, ImmutableResourceSnapshot> getInputs() { return inputs; }
    public Map<String, ImmutableResourceSnapshot> getOutputs() { return outputs; }
    public Map<String, Integer> getUpgrades() { return upgrades; }
    public RecipeSemanticsSnapshot getRecipeSemantics() { return recipeSemantics; }
    public Map<Integer, RecipeLaneSnapshot> getLanes() { return lanes; }
    public boolean hasExplicitLanes() { return explicitLanes; }
    public RecipeLaneSnapshot getLane(int lane) { return lanes.get(lane); }
    public ImmutableResourceSnapshot getInput(String key) { return inputs.get(key); }
    public ImmutableResourceSnapshot getOutput(String key) { return outputs.get(key); }

    public boolean sameVersionAndRecipe(RecipeRunSnapshot other) {
        return other != null && globalRecipeGeneration == other.globalRecipeGeneration &&
              categoryRecipeGeneration == other.categoryRecipeGeneration &&
              machineStateVersion == other.machineStateVersion && laneIndex == other.laneIndex &&
              recipeId.equals(other.recipeId) && recipeSignature.equals(other.recipeSignature) &&
              configurationVersion == other.configurationVersion && qioLeaseVersion == other.qioLeaseVersion &&
              portOwnershipVersion == other.portOwnershipVersion && mode.equals(other.mode);
    }

    public static Builder builder(String recipeId) {
        return new Builder(recipeId);
    }

    public static final class Builder {
        private final String recipeId;
        private String recipeSignature = "";
        private long globalRecipeGeneration;
        private long categoryRecipeGeneration;
        private int laneIndex;
        private long machineStateVersion;
        private long randomSeed;
        private long configurationVersion;
        private long qioLeaseVersion;
        private long portOwnershipVersion;
        private String mode = "";
        private int operatingTicks;
        private int requiredTicks;
        private double storedEnergy;
        private double energyPerTick;
        private boolean redstonePowered;
        private boolean active;
        private int dimension;
        private long worldTime;
        private Map<String, ImmutableResourceSnapshot> inputs = Collections.emptyMap();
        private Map<String, ImmutableResourceSnapshot> outputs = Collections.emptyMap();
        private Map<String, Integer> upgrades = Collections.emptyMap();
        private RecipeSemanticsSnapshot recipeSemantics = RecipeSemanticsSnapshot.empty();
        private Map<Integer, RecipeLaneSnapshot> lanes = Collections.emptyMap();

        private Builder(String recipeId) { this.recipeId = recipeId; }
        public Builder recipeSignature(String value) { recipeSignature = value; return this; }
        public Builder globalRecipeGeneration(long value) { globalRecipeGeneration = value; return this; }
        public Builder categoryRecipeGeneration(long value) { categoryRecipeGeneration = value; return this; }
        public Builder recipeGeneration(long value) { return globalRecipeGeneration(value).categoryRecipeGeneration(value); }
        public Builder laneIndex(int value) { laneIndex = value; return this; }
        public Builder cacheIndex(int value) { return laneIndex(value); }
        public Builder machineStateVersion(long value) { machineStateVersion = value; return this; }
        public Builder stateVersion(long value) { return machineStateVersion(value); }
        public Builder randomSeed(long value) { randomSeed = value; return this; }
        public Builder configurationVersion(long value) { configurationVersion = value; return this; }
        public Builder configVersion(long value) { return configurationVersion(value); }
        public Builder qioLeaseVersion(long value) { qioLeaseVersion = value; return this; }
        public Builder QIOLeaseVersion(long value) { return qioLeaseVersion(value); }
        public Builder portOwnershipVersion(long value) { portOwnershipVersion = value; return this; }
        public Builder mode(String value) { mode = value; return this; }
        public Builder operatingTicks(int value) { operatingTicks = value; return this; }
        public Builder requiredTicks(int value) { requiredTicks = value; return this; }
        public Builder energy(double stored, double perTick) { storedEnergy = stored; energyPerTick = perTick; return this; }
        public Builder storedEnergy(double value) { storedEnergy = value; return this; }
        public Builder energyPerTick(double value) { energyPerTick = value; return this; }
        public Builder redstonePowered(boolean value) { redstonePowered = value; return this; }
        public Builder active(boolean value) { active = value; return this; }
        public Builder dimension(int value) { dimension = value; return this; }
        public Builder worldTime(long value) { worldTime = value; return this; }
        public Builder inputs(Map<String, ImmutableResourceSnapshot> value) { inputs = value; return this; }
        public Builder outputs(Map<String, ImmutableResourceSnapshot> value) { outputs = value; return this; }
        public Builder upgrades(Map<String, Integer> value) { upgrades = value; return this; }
        public Builder recipeSemantics(RecipeSemanticsSnapshot value) { recipeSemantics = value; return this; }
        public Builder lanes(Map<Integer, RecipeLaneSnapshot> value) { lanes = value; return this; }
        public Builder lane(RecipeLaneSnapshot value) {
            Map<Integer, RecipeLaneSnapshot> copy = new LinkedHashMap<>(lanes);
            copy.put(value.getLaneIndex(), value);
            lanes = copy;
            return this;
        }
        public Builder input(String key, ImmutableResourceSnapshot value) {
            Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>(inputs);
            copy.put(key, value); inputs = copy; return this;
        }
        public Builder output(String key, ImmutableResourceSnapshot value) {
            Map<String, ImmutableResourceSnapshot> copy = new LinkedHashMap<>(outputs);
            copy.put(key, value); outputs = copy; return this;
        }
        public RecipeRunSnapshot build() { return new RecipeRunSnapshot(this); }
    }
}
