package mekanism.qioprocessing.common.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shared candidate domain, not an executable replacement for Ingredient.apply().
 */
public final class RecipeIngredient {
    public static final RecipeIngredient EMPTY = new RecipeIngredient("empty", Collections.emptyList(), true);
    private final String sourceType;
    private final List<RecipeItemKey> candidates;
    private final boolean emptySlot;
    private final int hash;
    private final String fingerprint;

    RecipeIngredient(String sourceType, List<RecipeItemKey> candidates, boolean emptySlot) {
        this.sourceType = sourceType;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.emptySlot = emptySlot;
        hash = Objects.hash(sourceType, this.candidates, emptySlot);
        RecipeFingerprint digest = new RecipeFingerprint().add(sourceType).add(Boolean.toString(emptySlot));
        for (RecipeItemKey key : candidates) digest.add(key.fingerprint());
        fingerprint = digest.finish();
    }

    public String sourceType() {
        return sourceType;
    }

    public List<RecipeItemKey> candidates() {
        return candidates;
    }

    public boolean isEmptySlot() {
        return emptySlot;
    }

    public boolean hasDeclaredCandidates() {
        return emptySlot || !candidates.isEmpty();
    }

    String fingerprint() {
        return fingerprint;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeIngredient)) return false;
        RecipeIngredient that = (RecipeIngredient) other;
        return emptySlot == that.emptySlot && sourceType.equals(that.sourceType) && candidates.equals(that.candidates);
    }
}

