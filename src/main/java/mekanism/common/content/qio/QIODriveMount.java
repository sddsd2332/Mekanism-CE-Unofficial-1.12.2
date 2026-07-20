package mekanism.common.content.qio;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public final class QIODriveMount implements Comparable<QIODriveMount> {

    private final IQIODriveHolder holder;
    private final int dimension;
    private final BlockPos position;
    private final int slot;

    public QIODriveMount(IQIODriveHolder holder, int slot) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.dimension = holder.getQIODimension();
        this.position = Objects.requireNonNull(holder.getQIOPosition(), "holder position").toImmutable();
        if (slot < 0) {
            throw new IllegalArgumentException("QIO drive slot cannot be negative");
        }
        this.slot = slot;
    }

    public QIODriveMount(IQIODriveHolder holder, int dimension, BlockPos position, int slot) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.dimension = dimension;
        this.position = Objects.requireNonNull(position, "position").toImmutable();
        if (slot < 0) {
            throw new IllegalArgumentException("QIO drive slot cannot be negative");
        }
        this.slot = slot;
    }

    public IQIODriveHolder getHolder() {
        return holder;
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getSlot() {
        return slot;
    }

    @Override
    public int compareTo(QIODriveMount other) {
        int comparison = Integer.compare(dimension, other.dimension);
        if (comparison == 0) {
            comparison = Integer.compare(position.getX(), other.position.getX());
        }
        if (comparison == 0) {
            comparison = Integer.compare(position.getY(), other.position.getY());
        }
        if (comparison == 0) {
            comparison = Integer.compare(position.getZ(), other.position.getZ());
        }
        return comparison == 0 ? Integer.compare(slot, other.slot) : comparison;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof QIODriveMount)) {
            return false;
        }
        QIODriveMount other = (QIODriveMount) obj;
        return dimension == other.dimension && slot == other.slot && position.equals(other.position);
    }

    @Override
    public int hashCode() {
        int result = dimension;
        result = 31 * result + position.hashCode();
        return 31 * result + slot;
    }

    @Override
    public String toString() {
        return "QIODriveMount{" + dimension + ":" + position + ", slot=" + slot + '}';
    }
}
