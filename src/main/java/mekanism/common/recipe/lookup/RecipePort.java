package mekanism.common.recipe.lookup;

import java.util.Objects;

/** Typed recipe port. A machine binds these local roles to its captured real-container identities. */
public final class RecipePort {
    private final ResourceType.Kind kind;
    private final int index;

    public RecipePort(ResourceType.Kind kind, int index) {
        this.kind = Objects.requireNonNull(kind, "Recipe port kind");
        if (kind == ResourceType.Kind.EMPTY || index < 0) throw new IllegalArgumentException("Invalid recipe resource port");
        this.index = index;
    }

    public ResourceType.Kind getKind() { return kind; }
    public int getIndex() { return index; }
    @Override public int hashCode() { return 31 * kind.ordinal() + index; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipePort)) return false;
        RecipePort port = (RecipePort) other;
        return kind == port.kind && index == port.index;
    }
}
