package mekanism.qioprocessing.common.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry declaration only. Displayed outputs/candidates do not prove world-independent craftability.
 */
public final class RecipeDeclaration {
    public enum Layout {SHAPED, SHAPELESS, UNSPECIFIED}

    private final String id;
    private final String sourceType;
    private final RecipeItemKey output;
    private final int outputCount;
    private final Layout layout;
    private final int width, height;
    private final List<RecipeIngredient> inputs;
    private final String signature;

    RecipeDeclaration(String id, String sourceType, RecipeItemKey output, int outputCount,
                      Layout layout, int width, int height, List<RecipeIngredient> inputs) {
        this.id = id;
        this.sourceType = sourceType;
        this.output = output;
        this.outputCount = outputCount;
        this.layout = layout;
        this.width = width;
        this.height = height;
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        RecipeFingerprint digest = new RecipeFingerprint().add(id).add(sourceType).add(output.fingerprint())
                .add(Integer.toString(outputCount)).add(layout.name()).add(width + ":" + height);
        for (RecipeIngredient ingredient : inputs) digest.add(ingredient.fingerprint());
        signature = digest.finish();
    }

    public String id() {
        return id;
    }

    public String sourceType() {
        return sourceType;
    }

    public RecipeItemKey output() {
        return output;
    }

    public int outputCount() {
        return outputCount;
    }

    public Layout layout() {
        return layout;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<RecipeIngredient> inputs() {
        return inputs;
    }

    public String signature() {
        return signature;
    }

    public boolean hasDeclaredCandidates() {
        for (RecipeIngredient input : inputs) if (!input.hasDeclaredCandidates()) return false;
        return true;
    }
}

