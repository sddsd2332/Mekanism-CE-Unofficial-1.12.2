package mekanism.common.recipe.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Whitelisted immutable recipe semantics consumed by worker-side calculation. */
public final class RecipeSemanticsSnapshot {

    private static final RecipeSemanticsSnapshot EMPTY = new RecipeSemanticsSnapshot(
          Collections.emptyList(), Collections.emptyList(), 0, 0, false);

    private final List<RecipeResourceFlow> inputs;
    private final List<RecipeResourceFlow> outputs;
    private final double extraEnergy;
    private final int requiredTicksOverride;
    private final boolean supported;

    public RecipeSemanticsSnapshot(List<RecipeResourceFlow> inputs,
          List<RecipeResourceFlow> outputs, double extraEnergy, int requiredTicksOverride,
          boolean supported) {
        this.inputs = immutable(inputs);
        this.outputs = immutable(outputs);
        this.extraEnergy = Double.isFinite(extraEnergy) && extraEnergy > 0 ? extraEnergy : 0;
        this.requiredTicksOverride = Math.max(0, requiredTicksOverride);
        this.supported = supported;
    }

    public static RecipeSemanticsSnapshot empty() { return EMPTY; }

    private static List<RecipeResourceFlow> immutable(List<RecipeResourceFlow> values) {
        return values == null || values.isEmpty() ? Collections.emptyList() :
              Collections.unmodifiableList(new ArrayList<>(values));
    }

    public List<RecipeResourceFlow> getInputs() { return inputs; }
    public List<RecipeResourceFlow> getOutputs() { return outputs; }
    public double getExtraEnergy() { return extraEnergy; }
    public int getRequiredTicksOverride() { return requiredTicksOverride; }
    public boolean isSupported() { return supported; }
    public boolean isEmpty() { return inputs.isEmpty() && outputs.isEmpty(); }
}
