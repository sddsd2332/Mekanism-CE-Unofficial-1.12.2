package mekanism.common.recipe.lookup;

import java.util.Objects;

/** Table identity is distinct from the global invalidation generation on a machine plan. */
public final class RecipeTableStamp {
    private final long session;
    private final long mapIdentity;
    private final long generation;
    private final long publication;

    public RecipeTableStamp(long session, long mapIdentity, long generation, long publication) {
        if (session <= 0 || mapIdentity <= 0 || generation < 0 || publication <= 0) {
            throw new IllegalArgumentException("Invalid recipe table stamp");
        }
        this.session = session;
        this.mapIdentity = mapIdentity;
        this.generation = generation;
        this.publication = publication;
    }

    public long getSession() { return session; }
    public long getMapIdentity() { return mapIdentity; }
    public long getGeneration() { return generation; }
    public long getPublication() { return publication; }
    @Override public int hashCode() { return Objects.hash(session, mapIdentity, generation, publication); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeTableStamp)) return false;
        RecipeTableStamp stamp = (RecipeTableStamp) other;
        return session == stamp.session && mapIdentity == stamp.mapIdentity && generation == stamp.generation && publication == stamp.publication;
    }
}
