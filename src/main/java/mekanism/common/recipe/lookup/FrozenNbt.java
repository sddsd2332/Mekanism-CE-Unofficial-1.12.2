package mekanism.common.recipe.lookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable NBT values. This worker type has no Minecraft, registry or callback references. */
public final class FrozenNbt {
    public enum Type {
        END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY;

        public static Type fromId(int id) {
            if (id < 0 || id >= values().length) throw new IllegalArgumentException("Unknown NBT type " + id);
            return values()[id];
        }
    }

    private final Type type;
    private final long number;
    private final String text;
    private final byte[] bytes;
    private final int[] ints;
    private final long[] longs;
    private final Type elementType;
    private final List<FrozenNbt> elements;
    private final Map<String, FrozenNbt> members;
    private final int hash;

    private FrozenNbt(Type type, long number, String text, byte[] bytes, int[] ints, long[] longs,
          Type elementType, List<FrozenNbt> elements, Map<String, FrozenNbt> members) {
        this.type = type;
        this.number = number;
        this.text = text;
        this.bytes = bytes;
        this.ints = ints;
        this.longs = longs;
        this.elementType = elementType;
        this.elements = elements;
        this.members = members;
        long equalityNumber = number;
        if (type == Type.FLOAT && Float.intBitsToFloat((int) number) == 0) equalityNumber = 0;
        if (type == Type.DOUBLE && Double.longBitsToDouble(number) == 0) equalityNumber = 0;
        int result = 31 * type.ordinal() + Long.hashCode(equalityNumber);
        result = 31 * result + Objects.hashCode(text);
        result = 31 * result + Arrays.hashCode(bytes);
        result = 31 * result + Arrays.hashCode(ints);
        result = 31 * result + Arrays.hashCode(longs);
        result = 31 * result + (elementType == null ? 0 : elementType.ordinal());
        result = 31 * result + Objects.hashCode(elements);
        hash = 31 * result + Objects.hashCode(members);
    }

    public static FrozenNbt integer(Type type, long value) {
        Objects.requireNonNull(type, "NBT numeric type");
        if (type == Type.END && value != 0 || type == Type.BYTE && value != (byte) value ||
              type == Type.SHORT && value != (short) value || type == Type.INT && value != (int) value ||
              type.ordinal() > Type.LONG.ordinal()) {
            throw new IllegalArgumentException("Invalid integral NBT value for " + type);
        }
        return scalar(type, value, null);
    }

    public static FrozenNbt floatValue(float value) { return scalar(Type.FLOAT, Float.floatToIntBits(value), null); }
    public static FrozenNbt doubleValue(double value) { return scalar(Type.DOUBLE, Double.doubleToLongBits(value), null); }
    public static FrozenNbt string(String value) { return scalar(Type.STRING, 0, Objects.requireNonNull(value, "NBT string")); }

    private static FrozenNbt scalar(Type type, long number, String text) {
        return new FrozenNbt(type, number, text, null, null, null, null, null, null);
    }

    public static FrozenNbt byteArray(byte[] value) {
        return new FrozenNbt(Type.BYTE_ARRAY, 0, null, Objects.requireNonNull(value).clone(), null, null, null, null, null);
    }

    public static FrozenNbt intArray(int[] value) {
        return new FrozenNbt(Type.INT_ARRAY, 0, null, null, Objects.requireNonNull(value).clone(), null, null, null, null);
    }

    public static FrozenNbt longArray(long[] value) {
        return new FrozenNbt(Type.LONG_ARRAY, 0, null, null, null, Objects.requireNonNull(value).clone(), null, null, null);
    }

    public static FrozenNbt list(Type elementType, List<FrozenNbt> values) {
        Objects.requireNonNull(elementType, "NBT list element type");
        Objects.requireNonNull(values, "NBT list");
        List<FrozenNbt> copy = new ArrayList<>(values.size());
        for (FrozenNbt value : values) {
            if (Objects.requireNonNull(value, "NBT list element").type != elementType || elementType == Type.END) {
                throw new IllegalArgumentException("NBT list contains an invalid element type");
            }
            copy.add(value);
        }
        return new FrozenNbt(Type.LIST, 0, null, null, null, null, elementType,
              Collections.unmodifiableList(copy), null);
    }

    public static FrozenNbt compound(Map<String, FrozenNbt> values) {
        Objects.requireNonNull(values, "NBT compound");
        Map<String, FrozenNbt> copy = new LinkedHashMap<>();
        for (Map.Entry<String, FrozenNbt> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "NBT key");
            FrozenNbt value = Objects.requireNonNull(entry.getValue(), "NBT value");
            if (value.type == Type.END) throw new IllegalArgumentException("Named END is not a valid NBT compound entry");
            copy.put(key, value);
        }
        return new FrozenNbt(Type.COMPOUND, 0, null, null, null, null, null, null,
              Collections.unmodifiableMap(copy));
    }

    public Type getType() { return type; }
    public long getNumberBits() { return number; }
    public String getText() { require(Type.STRING); return text; }
    public byte[] getBytes() { require(Type.BYTE_ARRAY); return bytes.clone(); }
    public int[] getInts() { require(Type.INT_ARRAY); return ints.clone(); }
    public long[] getLongs() { require(Type.LONG_ARRAY); return longs.clone(); }
    public Type getElementType() { require(Type.LIST); return elementType; }
    public List<FrozenNbt> getElements() { require(Type.LIST); return elements; }
    public Map<String, FrozenNbt> getMembers() { require(Type.COMPOUND); return members; }

    private void require(Type expected) {
        if (type != expected) throw new IllegalStateException("Expected " + expected + ", found " + type);
    }

    /** NBT matching uses numeric ==, including signed zero and nonmatching NaNs. */
    public boolean matches(FrozenNbt other) {
        if (other == null || type != other.type) return false;
        if (type == Type.FLOAT) return Float.intBitsToFloat((int) number) == Float.intBitsToFloat((int) other.number);
        if (type == Type.DOUBLE) return Double.longBitsToDouble(number) == Double.longBitsToDouble(other.number);
        if (type == Type.COMPOUND) {
            if (!members.keySet().equals(other.members.keySet())) return false;
            for (Map.Entry<String, FrozenNbt> entry : members.entrySet()) {
                if (!entry.getValue().matches(other.members.get(entry.getKey()))) return false;
            }
            return true;
        }
        if (type == Type.LIST) {
            if (elementType != other.elementType || elements.size() != other.elements.size()) return false;
            for (int i = 0; i < elements.size(); i++) if (!elements.get(i).matches(other.elements.get(i))) return false;
            return true;
        }
        return equals(other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FrozenNbt)) return false;
        FrozenNbt value = (FrozenNbt) other;
        if (type != value.type || hash != value.hash) return false;
        boolean sameNumber = number == value.number;
        if (type == Type.FLOAT) sameNumber |= Float.intBitsToFloat((int) number) == Float.intBitsToFloat((int) value.number);
        if (type == Type.DOUBLE) sameNumber |= Double.longBitsToDouble(number) == Double.longBitsToDouble(value.number);
        return sameNumber && Objects.equals(text, value.text) && Arrays.equals(bytes, value.bytes) &&
              Arrays.equals(ints, value.ints) && Arrays.equals(longs, value.longs) && elementType == value.elementType &&
              Objects.equals(elements, value.elements) && Objects.equals(members, value.members);
    }

    @Override public int hashCode() { return hash; }
}
