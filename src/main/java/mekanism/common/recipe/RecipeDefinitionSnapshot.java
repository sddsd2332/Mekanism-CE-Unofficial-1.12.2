package mekanism.common.recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, registry-independent semantic projection of one machine recipe. */
public final class RecipeDefinitionSnapshot {

    private final String recipeId;
    private final RecipeGeneration generation;
    private final String semanticSignature;
    private final Map<String, String> values;

    public RecipeDefinitionSnapshot(String recipeId, RecipeGeneration generation, String semanticSignature,
          Map<String, String> values) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.semanticSignature = semanticSignature == null ? "" : semanticSignature;
        if (values == null || values.isEmpty()) {
            this.values = Collections.emptyMap();
        } else {
            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                copy.put(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
            }
            this.values = Collections.unmodifiableMap(copy);
        }
    }

    public String getRecipeId() { return recipeId; }
    public String getRecipeKey() { return recipeId; }
    public RecipeGeneration getGeneration() { return generation; }
    public long getGlobalRecipeGeneration() { return generation.getGlobalGeneration(); }
    public long getCategoryRecipeGeneration() { return generation.getCategoryGeneration(); }
    public String getSemanticSignature() { return semanticSignature; }
    public String getRecipeSignature() { return semanticSignature; }
    public Map<String, String> getValues() { return values; }

    public boolean sameSemantics(RecipeDefinitionSnapshot other) {
        return other != null && recipeId.equals(other.recipeId) && semanticSignature.equals(other.semanticSignature);
    }
}
