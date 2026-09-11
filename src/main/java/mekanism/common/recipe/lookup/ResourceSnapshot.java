package mekanism.common.recipe.lookup;

import java.util.Objects;

/** Immutable amount and detached identity. Quantity arithmetic never copies NBT or initializes capabilities. */
public final class ResourceSnapshot {
    private static final ResourceSnapshot EMPTY = new ResourceSnapshot(ResourceIdentity.EMPTY, 0, 0);
    private final ResourceIdentity identity;
    private final long amount;
    private final int itemLimit;

    private ResourceSnapshot(ResourceIdentity identity, long amount, int itemLimit) {
        this.identity = Objects.requireNonNull(identity, "Resource identity");
        if (amount < 0 || amount > Integer.MAX_VALUE || itemLimit < 0) {
            throw new IllegalArgumentException("Invalid resource quantity or item limit");
        }
        if (identity.getType().getKind() == ResourceType.Kind.EMPTY && amount != 0) {
            throw new IllegalArgumentException("An empty resource cannot have a quantity");
        }
        this.amount = amount;
        this.itemLimit = itemLimit;
    }

    public static ResourceSnapshot of(ResourceIdentity identity, long amount, int itemLimit) {
        ResourceSnapshot value = new ResourceSnapshot(identity, amount, itemLimit);
        return amount == 0 ? EMPTY : value;
    }

    public static ResourceSnapshot empty() { return EMPTY; }
    public ResourceIdentity getIdentity() { return identity; }
    public long getAmount() { return amount; }
    public int getItemLimit() { return itemLimit; }
    public boolean isEmpty() { return amount == 0; }

    public ResourceSnapshot withAmount(long amount) {
        if (amount == this.amount) return this;
        return of(identity, amount, itemLimit);
    }

    @Override public int hashCode() { return 31 * identity.hashCode() + Long.hashCode(amount); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceSnapshot)) return false;
        ResourceSnapshot value = (ResourceSnapshot) other;
        return amount == value.amount && itemLimit == value.itemLimit && identity.equals(value.identity);
    }
}
