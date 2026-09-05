package mekanism.common.recipe;

import java.util.Objects;

/** Immutable pair of global and category recipe generations. */
public final class RecipeGeneration {

    private final long global;
    private final long category;

    public RecipeGeneration(long global, long category) {
        if (global < 0 || category < 0) {
            throw new IllegalArgumentException("Recipe generations cannot be negative");
        }
        this.global = global;
        this.category = category;
    }

    public long getGlobal() {
        return global;
    }

    public long getGlobalGeneration() {
        return global;
    }

    public long getCategory() {
        return category;
    }

    public long getCategoryGeneration() {
        return category;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecipeGeneration)) {
            return false;
        }
        RecipeGeneration that = (RecipeGeneration) other;
        return global == that.global && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(global, category);
    }

    @Override
    public String toString() {
        return "RecipeGeneration{" + "global=" + global + ", category=" + category + '}';
    }
}
