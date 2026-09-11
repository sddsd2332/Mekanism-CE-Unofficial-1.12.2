package mekanism.common.recipe.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Closed execution data shared by built-in and explicitly projected third-party entries. */
public final class RecipeDefinition {
    public enum Model { STANDARD, CONSTANT_SECONDARY, CHEMICAL_PAIR, ROTARY, AMBIENT, TEMPLATE, GAS_FUEL, EVAPORATION }
    private final Model model;
    private final List<RecipeFlow> inputs;
    private final List<RecipeFlow> outputs;
    private final int duration;
    private final double extraEnergy;
    private final double fuelEnergy;
    private final String unsupportedReason;

    public RecipeDefinition(Model model, List<RecipeFlow> inputs, List<RecipeFlow> outputs,
          int duration, double extraEnergy, double fuelEnergy) {
        this(model, inputs, outputs, duration, extraEnergy, fuelEnergy, "");
    }

    private RecipeDefinition(Model model, List<RecipeFlow> inputs, List<RecipeFlow> outputs,
          int duration, double extraEnergy, double fuelEnergy, String unsupportedReason) {
        this.model = Objects.requireNonNull(model, "Recipe execution model");
        this.inputs = freeze(inputs, false);
        this.outputs = freeze(outputs, true);
        if (duration < 0 || !Double.isFinite(extraEnergy) || extraEnergy < 0 || !Double.isFinite(fuelEnergy) || fuelEnergy < 0) {
            throw new IllegalArgumentException("Invalid recipe time or energy");
        }
        this.duration = duration;
        this.extraEnergy = extraEnergy;
        this.fuelEnergy = fuelEnergy;
        this.unsupportedReason = unsupportedReason;
    }

    private static List<RecipeFlow> freeze(List<RecipeFlow> flows, boolean output) {
        List<RecipeFlow> copy = new ArrayList<>();
        Set<RecipePort> ports = new HashSet<>();
        for (RecipeFlow flow : Objects.requireNonNull(flows, "Recipe flows")) {
            Objects.requireNonNull(flow, "Recipe flow");
            if ((flow.getPhase() == RecipeFlow.Phase.OUTPUT) != output || !ports.add(flow.getPort())) {
                throw new IllegalArgumentException("Recipe flow has the wrong phase or duplicates a port");
            }
            copy.add(flow);
        }
        return Collections.unmodifiableList(copy);
    }

    public static RecipeDefinition unsupported(String reason) {
        if (Objects.requireNonNull(reason, "Unsupported reason").isEmpty()) throw new IllegalArgumentException("Missing unsupported reason");
        return new RecipeDefinition(Model.STANDARD, Collections.emptyList(), Collections.emptyList(), 0, 0, 0, reason);
    }

    public Model getModel() { return model; }
    public List<RecipeFlow> getInputs() { return inputs; }
    public List<RecipeFlow> getOutputs() { return outputs; }
    public int getDuration() { return duration; }
    public double getExtraEnergy() { return extraEnergy; }
    public double getFuelEnergy() { return fuelEnergy; }
    public boolean isSupported() { return unsupportedReason.isEmpty(); }
    public String getUnsupportedReason() { return unsupportedReason; }
}
