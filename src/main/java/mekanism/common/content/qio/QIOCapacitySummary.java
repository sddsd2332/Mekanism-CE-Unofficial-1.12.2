package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Exact finite capacity plus independently tracked unlimited-drive counts. */
public final class QIOCapacitySummary {

    public static final QIOCapacitySummary EMPTY = new QIOCapacitySummary(QIOAmount.ZERO, QIOAmount.ZERO, 0, 0);

    private final QIOAmount finiteCountCapacity;
    private final QIOAmount finiteTypeCapacity;
    private final int unlimitedCountDrives;
    private final int unlimitedTypeDrives;

    public QIOCapacitySummary(@Nonnull QIOAmount finiteCountCapacity, @Nonnull QIOAmount finiteTypeCapacity,
          int unlimitedCountDrives, int unlimitedTypeDrives) {
        this.finiteCountCapacity = Objects.requireNonNull(finiteCountCapacity, "finiteCountCapacity");
        this.finiteTypeCapacity = Objects.requireNonNull(finiteTypeCapacity, "finiteTypeCapacity");
        this.unlimitedCountDrives = Math.max(0, unlimitedCountDrives);
        this.unlimitedTypeDrives = Math.max(0, unlimitedTypeDrives);
    }

    @Nonnull
    public QIOAmount getFiniteCountCapacity() {
        return finiteCountCapacity;
    }

    @Nonnull
    public QIOAmount getFiniteTypeCapacity() {
        return finiteTypeCapacity;
    }

    public int getUnlimitedCountDrives() {
        return unlimitedCountDrives;
    }

    public int getUnlimitedTypeDrives() {
        return unlimitedTypeDrives;
    }

    public boolean hasUnlimitedCount() {
        return unlimitedCountDrives > 0;
    }

    public boolean hasUnlimitedTypes() {
        return unlimitedTypeDrives > 0;
    }

    public long getCountCapacityClamped() {
        return hasUnlimitedCount() ? Long.MAX_VALUE : finiteCountCapacity.longValueClamped();
    }

    public int getTypeCapacityClamped() {
        return hasUnlimitedTypes() ? Integer.MAX_VALUE : finiteTypeCapacity.intValueClamped();
    }

    public double getCountLevel(@Nonnull QIOAmount stored) {
        return hasUnlimitedCount() ? 0 : stored.ratioTo(finiteCountCapacity);
    }

    public double getTypeLevel(int storedTypes) {
        return hasUnlimitedTypes() ? 0 : QIOAmount.of(storedTypes).ratioTo(finiteTypeCapacity);
    }

    public void write(ByteBuf buffer) {
        finiteCountCapacity.write(buffer);
        finiteTypeCapacity.write(buffer);
        buffer.writeInt(unlimitedCountDrives);
        buffer.writeInt(unlimitedTypeDrives);
    }

    @Nonnull
    public static QIOCapacitySummary read(ByteBuf buffer) {
        QIOAmount count = QIOAmount.read(buffer);
        QIOAmount types = QIOAmount.read(buffer);
        int unlimitedCount = buffer.readInt();
        int unlimitedTypes = buffer.readInt();
        if (unlimitedCount < 0 || unlimitedTypes < 0) {
            throw new IllegalArgumentException("Negative unlimited QIO drive count");
        }
        return new QIOCapacitySummary(count, types, unlimitedCount, unlimitedTypes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof QIOCapacitySummary)) {
            return false;
        }
        QIOCapacitySummary other = (QIOCapacitySummary) obj;
        return unlimitedCountDrives == other.unlimitedCountDrives && unlimitedTypeDrives == other.unlimitedTypeDrives &&
              finiteCountCapacity.equals(other.finiteCountCapacity) && finiteTypeCapacity.equals(other.finiteTypeCapacity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(finiteCountCapacity, finiteTypeCapacity, unlimitedCountDrives, unlimitedTypeDrives);
    }
}
