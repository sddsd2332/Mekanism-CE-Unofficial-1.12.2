package mekanism.common.recipe.lookup;

import javax.annotation.Nullable;
import java.util.Objects;

/** Resource type, item damage/metadata, tag and capability data, with quantity kept separately. */
public final class ResourceIdentity {
    static final ResourceIdentity EMPTY = new ResourceIdentity(ResourceType.EMPTY, 0, 0, null, null);
    private final ResourceType type;
    private final int damage;
    private final int metadata;
    private final FrozenNbt tag;
    private final FrozenNbt capabilities;
    private final int hash;

    public ResourceIdentity(ResourceType type, int damage, int metadata, @Nullable FrozenNbt tag,
          @Nullable FrozenNbt capabilities) {
        this.type = Objects.requireNonNull(type, "Resource type");
        if (type.getKind() != ResourceType.Kind.ITEM && (damage != 0 || metadata != 0 || capabilities != null)) {
            throw new IllegalArgumentException("Only item identities carry metadata or capabilities");
        }
        if (tag != null && tag.getType() != FrozenNbt.Type.COMPOUND ||
              capabilities != null && capabilities.getType() != FrozenNbt.Type.COMPOUND) {
            throw new IllegalArgumentException("Resource tags must be NBT compounds");
        }
        this.damage = damage;
        this.metadata = metadata;
        this.tag = tag;
        this.capabilities = capabilities;
        hash = Objects.hash(type, damage, metadata, tag, capabilities);
    }

    public ResourceType getType() { return type; }
    public int getDamage() { return damage; }
    public int getMetadata() { return metadata; }
    @Nullable public FrozenNbt getTag() { return tag; }
    @Nullable public FrozenNbt getCapabilities() { return capabilities; }

    /** The 1.12 ingredient matcher ignores capability data and accepts wildcard on either side. */
    public boolean matchesIngredient(ResourceIdentity other, boolean ignoreItemNbt) {
        if (other == null || !type.equals(other.type)) return false;
        if (type.getKind() == ResourceType.Kind.ITEM) {
            if (damage != 32767 && other.damage != 32767 && damage != other.damage) return false;
            if (ignoreItemNbt) return true;
        }
        return tag == null ? other.tag == null : tag.matches(other.tag);
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceIdentity)) return false;
        ResourceIdentity identity = (ResourceIdentity) other;
        return hash == identity.hash && type.equals(identity.type) && damage == identity.damage &&
              metadata == identity.metadata && Objects.equals(tag, identity.tag) && Objects.equals(capabilities, identity.capabilities);
    }
}
