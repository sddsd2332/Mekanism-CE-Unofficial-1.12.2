package mekanism.common.recipe.lookup;

import java.util.Objects;

/** A frozen resource requirement or output, with explicit phase and probability. */
public final class RecipeFlow {
    public enum Phase { COMPLETION_INPUT, PER_TICK_INPUT, TEMPLATE_INPUT, OUTPUT }
    private final RecipePort port;
    private final ResourceSnapshot resource;
    private final Phase phase;
    private final double probability;
    private final boolean samplesRandom;

    public RecipeFlow(RecipePort port, ResourceSnapshot resource, Phase phase, double probability, boolean samplesRandom) {
        this.port = Objects.requireNonNull(port, "Recipe flow port");
        this.resource = Objects.requireNonNull(resource, "Recipe flow resource");
        this.phase = Objects.requireNonNull(phase, "Recipe flow phase");
        if (resource.isEmpty() || resource.getIdentity().getType().getKind() != port.getKind()) {
            throw new IllegalArgumentException("Recipe flow has an empty resource or wrong port kind");
        }
        if (!Double.isFinite(probability) || probability < 0 || probability > 1 ||
              phase != Phase.OUTPUT && (samplesRandom || probability != 1)) {
            throw new IllegalArgumentException("Invalid recipe flow probability");
        }
        this.probability = probability;
        this.samplesRandom = samplesRandom;
    }

    public RecipePort getPort() { return port; }
    public ResourceSnapshot getResource() { return resource; }
    public Phase getPhase() { return phase; }
    public double getProbability() { return probability; }
    public boolean samplesRandom() { return samplesRandom; }
}
