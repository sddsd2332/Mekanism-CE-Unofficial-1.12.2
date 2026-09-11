package mekanism.common.recipe.lookup;

import java.util.Objects;

/** Session-local registry identity, detached from the registry object itself. */
public final class ResourceType {
    public enum Kind { EMPTY, ITEM, FLUID, GAS, INFUSION }

    static final ResourceType EMPTY = new ResourceType(0, Kind.EMPTY, "");
    private final long id;
    private final Kind kind;
    private final String name;

    ResourceType(long id, Kind kind, String name) {
        if (id < 0 || id == 0 && kind != Kind.EMPTY) throw new IllegalArgumentException("Invalid resource type id");
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "Resource kind");
        this.name = Objects.requireNonNull(name, "Resource name");
    }

    public long getId() { return id; }
    public Kind getKind() { return kind; }
    public String getName() { return name; }
    @Override public int hashCode() { return Long.hashCode(id); }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ResourceType && id == ((ResourceType) other).id;
    }
}
