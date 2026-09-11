package mekanism.common.recipe.lookup;

import javax.annotation.Nullable;
import java.util.Objects;

/** Exact and 1.12 wildcard queries are frozen independently: wildCopy can remove item NBT. */
public final class RecipeLookupQuery {
    private final RecipeInputKey exact;
    private final RecipeInputKey wildcard;

    public RecipeLookupQuery(RecipeInputKey exact, @Nullable RecipeInputKey wildcard) {
        this.exact = Objects.requireNonNull(exact, "Exact recipe query");
        if (wildcard != null && wildcard.getShape() != exact.getShape()) throw new IllegalArgumentException("Wildcard query changes input shape");
        this.wildcard = wildcard;
    }

    public RecipeInputKey getExact() { return exact; }
    @Nullable public RecipeInputKey getWildcard() { return wildcard; }
    @Override public int hashCode() { return 31 * exact.hashCode() + Objects.hashCode(wildcard); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeLookupQuery)) return false;
        RecipeLookupQuery query = (RecipeLookupQuery) other;
        return exact.equals(query.exact) && Objects.equals(wildcard, query.wildcard);
    }
}
