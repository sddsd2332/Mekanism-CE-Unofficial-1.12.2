package mekanism.common.recipe.lookup;

import java.util.Objects;

/** Identifies an entry in exactly one table publication; it never retains the live recipe. */
public final class RecipeEntryToken {
    private final RecipeTableStamp table;
    private final int ordinal;

    public RecipeEntryToken(RecipeTableStamp table, int ordinal) {
        this.table = Objects.requireNonNull(table, "Recipe table stamp");
        if (ordinal < 0) throw new IllegalArgumentException("Negative recipe entry ordinal");
        this.ordinal = ordinal;
    }

    public RecipeTableStamp getTable() { return table; }
    public int getOrdinal() { return ordinal; }
    @Override public int hashCode() { return 31 * table.hashCode() + ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeEntryToken)) return false;
        RecipeEntryToken token = (RecipeEntryToken) other;
        return ordinal == token.ordinal && table.equals(token.table);
    }
}
