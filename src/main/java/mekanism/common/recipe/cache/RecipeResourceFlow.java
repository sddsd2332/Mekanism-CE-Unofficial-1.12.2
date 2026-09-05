package mekanism.common.recipe.cache;

import java.util.Objects;

/** One immutable resource edge in a compiled recipe definition. */
public final class RecipeResourceFlow {

    public enum Phase {
        PER_TICK,
        COMPLETION,
        OUTPUT
    }

    private final String key;
    private final ImmutableResourceSnapshot resource;
    private final Phase phase;
    private final double probability;
    private final boolean stochastic;

    public RecipeResourceFlow(String key, ImmutableResourceSnapshot resource, Phase phase) {
        this(key, resource, phase, 1, false);
    }

    public RecipeResourceFlow(String key, ImmutableResourceSnapshot resource, Phase phase,
          double probability) {
        this(key, resource, phase, probability, probability < 1);
    }

    public RecipeResourceFlow(String key, ImmutableResourceSnapshot resource, Phase phase,
          double probability, boolean stochastic) {
        this.key = Objects.requireNonNull(key, "Resource flow key cannot be null");
        this.resource = Objects.requireNonNull(resource, "Resource flow value cannot be null");
        this.phase = Objects.requireNonNull(phase, "Resource flow phase cannot be null");
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Resource flow probability must be between zero and one");
        }
        this.probability = probability;
        this.stochastic = stochastic;
    }

    public String getKey() { return key; }
    public ImmutableResourceSnapshot getResource() { return resource; }
    public Phase getPhase() { return phase; }
    public double getProbability() { return probability; }
    public boolean isStochastic() { return stochastic; }
}
