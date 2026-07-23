package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * Non-negative QIO amount that stays compact until a sum exceeds {@code long}.
 */
public final class QIOAmount implements Comparable<QIOAmount> {

    private static final int MAX_NETWORK_BYTES = 256;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    public static final QIOAmount ZERO = new QIOAmount(0, null);
    public static final QIOAmount LONG_MAX_VALUE = new QIOAmount(Long.MAX_VALUE, null);
    public static final QIOAmount INT_MAX_VALUE = new QIOAmount(Integer.MAX_VALUE, null);

    private final long compactValue;
    @Nullable
    private final BigInteger expandedValue;

    private QIOAmount(long compactValue, @Nullable BigInteger expandedValue) {
        this.compactValue = compactValue;
        this.expandedValue = expandedValue;
    }

    @Nonnull
    public static QIOAmount of(long value) {
        if (value <= 0) {
            return ZERO;
        }
        if (value == Long.MAX_VALUE) {
            return LONG_MAX_VALUE;
        }
        if (value == Integer.MAX_VALUE) {
            return INT_MAX_VALUE;
        }
        return new QIOAmount(value, null);
    }

    @Nonnull
    public static QIOAmount of(@Nullable BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return ZERO;
        }
        if (value.compareTo(LONG_MAX) <= 0) {
            return of(value.longValue());
        }
        return new QIOAmount(Long.MAX_VALUE, value);
    }

    @Nonnull
    public static QIOAmount parse(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return ZERO;
        }
        try {
            return of(new BigInteger(value));
        } catch (NumberFormatException ignored) {
            return ZERO;
        }
    }

    @Nonnull
    public QIOAmount add(long amount) {
        if (amount <= 0) {
            return this;
        }
        if (expandedValue == null && compactValue <= Long.MAX_VALUE - amount) {
            return of(compactValue + amount);
        }
        return of(toBigInteger().add(BigInteger.valueOf(amount)));
    }

    @Nonnull
    public QIOAmount add(@Nullable QIOAmount amount) {
        if (amount == null || amount.isZero()) {
            return this;
        }
        if (isZero()) {
            return amount;
        }
        if (expandedValue == null && amount.expandedValue == null && compactValue <= Long.MAX_VALUE - amount.compactValue) {
            return of(compactValue + amount.compactValue);
        }
        return of(toBigInteger().add(amount.toBigInteger()));
    }

    @Nonnull
    public QIOAmount multiply(long factor) {
        if (factor <= 0 || isZero()) {
            return ZERO;
        }
        if (expandedValue == null && compactValue <= Long.MAX_VALUE / factor) {
            return of(compactValue * factor);
        }
        return of(toBigInteger().multiply(BigInteger.valueOf(factor)));
    }

    @Nonnull
    public QIOAmount divideRoundUp(long divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("QIO amount divisor must be positive");
        }
        if (isZero()) {
            return ZERO;
        }
        BigInteger[] result = toBigInteger().divideAndRemainder(BigInteger.valueOf(divisor));
        return of(result[1].signum() == 0 ? result[0] : result[0].add(BigInteger.ONE));
    }

    public boolean isZero() {
        return expandedValue == null && compactValue == 0;
    }

    public boolean isExpanded() {
        return expandedValue != null;
    }

    public long longValueClamped() {
        return expandedValue == null ? compactValue : Long.MAX_VALUE;
    }

    public int intValueClamped() {
        return expandedValue != null || compactValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) compactValue;
    }

    @Nonnull
    public BigInteger toBigInteger() {
        return expandedValue == null ? BigInteger.valueOf(compactValue) : expandedValue;
    }

    public double ratioTo(@Nullable QIOAmount capacity) {
        if (capacity == null || capacity.isZero() || isZero()) {
            return 0;
        }
        if (compareTo(capacity) >= 0) {
            return 1;
        }
        if (expandedValue == null && capacity.expandedValue == null) {
            return compactValue / (double) capacity.compactValue;
        }
        return new BigDecimal(toBigInteger()).divide(new BigDecimal(capacity.toBigInteger()), MathContext.DECIMAL64).doubleValue();
    }

    public void write(ByteBuf buffer) {
        buffer.writeBoolean(isExpanded());
        if (!isExpanded()) {
            buffer.writeLong(compactValue);
            return;
        }
        byte[] encoded = expandedValue.toByteArray();
        if (encoded.length > MAX_NETWORK_BYTES) {
            throw new IllegalArgumentException("QIO amount is too large to synchronize");
        }
        buffer.writeShort(encoded.length);
        buffer.writeBytes(encoded);
    }

    @Nonnull
    public static QIOAmount read(ByteBuf buffer) {
        if (!buffer.readBoolean()) {
            long value = buffer.readLong();
            if (value < 0) {
                throw new IllegalArgumentException("Negative compact QIO amount");
            }
            return of(value);
        }
        int length = buffer.readUnsignedShort();
        if (length <= 0 || length > MAX_NETWORK_BYTES || buffer.readableBytes() < length) {
            throw new IllegalArgumentException("Invalid expanded QIO amount length");
        }
        byte[] encoded = new byte[length];
        buffer.readBytes(encoded);
        BigInteger value = new BigInteger(encoded);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Negative expanded QIO amount");
        }
        return of(value);
    }

    @Override
    public int compareTo(@Nonnull QIOAmount other) {
        if (expandedValue == null && other.expandedValue == null) {
            return Long.compare(compactValue, other.compactValue);
        }
        return toBigInteger().compareTo(other.toBigInteger());
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof QIOAmount && compareTo((QIOAmount) obj) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toBigInteger());
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
    }
}
