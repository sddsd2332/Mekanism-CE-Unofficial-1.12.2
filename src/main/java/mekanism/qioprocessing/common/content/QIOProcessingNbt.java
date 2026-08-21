package mekanism.qioprocessing.common.content;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * QIO 处理模块中的 QIOProcessingNbt 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingNbt {

    private QIOProcessingNbt() {
    }

    public static void writeUUID(NBTTagCompound data, String key, UUID value) {
        data.setString(key, Objects.requireNonNull(value, key).toString());
    }

    @Nonnull
    public static UUID readUUID(NBTTagCompound data, String key) throws QIOProcessingDataException {
        String value = data.getString(key);
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
            return parsed;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid UUID in " + key + ": " + value, e);
        }
    }

    @Nullable
    public static UUID readOptionalUUID(NBTTagCompound data, String key) throws QIOProcessingDataException {
        return data.hasKey(key, NBT.TAG_STRING) ? readUUID(data, key) : null;
    }

    @Nonnull
    public static <E extends Enum<E>> E readEnum(NBTTagCompound data, String key, Class<E> type)
          throws QIOProcessingDataException {
        String value = data.getString(key);
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Unknown " + key + " value: " + value, e);
        }
    }

    @Nonnull
    public static NBTTagList writeAmounts(Map<PortableResourceDescriptor, Long> amounts) {
        NBTTagList list = new NBTTagList();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("resource", entry.getKey().write());
            data.setLong("amount", entry.getValue());
            list.appendTag(data);
        });
        return list;
    }

    @Nonnull
    public static Map<PortableResourceDescriptor, Long> readAmounts(NBTTagCompound parent, String key,
          int maximumEntries) throws QIOProcessingDataException {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries cannot be negative");
        }
        if (!parent.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(key + " is missing from current-schema data");
        }
        NBTTagList list = parent.getTagList(key, NBT.TAG_COMPOUND);
        if (list.tagCount() > maximumEntries) {
            throw new QIOProcessingDataException(key + " contains " + list.tagCount() +
                  " entries, above the limit " + maximumEntries);
        }
        Map<PortableResourceDescriptor, Long> amounts = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey("resource", NBT.TAG_COMPOUND) ||
                  !entry.hasKey("amount", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException(
                      "Incomplete amount entry in " + key + '[' + i + ']');
            }
            PortableResourceDescriptor resource;
            try {
                resource = PortableResourceDescriptor.read(entry.getCompoundTag("resource"));
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid resource in " + key + '[' + i + ']', e);
            }
            long amount = entry.getLong("amount");
            if (amount <= 0) {
                throw new QIOProcessingDataException("Non-positive amount in " + key + '[' + i + ']');
            }
            if (amounts.put(resource, amount) != null) {
                throw new QIOProcessingDataException("Duplicate resource in " + key + ": " + resource);
            }
        }
        return Collections.unmodifiableMap(amounts);
    }

    @Nonnull
    public static Map<PortableResourceDescriptor, Long> copyAmounts(
          Map<PortableResourceDescriptor, Long> amounts, boolean allowEmpty, String name) {
        Objects.requireNonNull(amounts, name);
        Map<PortableResourceDescriptor, Long> copy = new LinkedHashMap<>();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            PortableResourceDescriptor resource = Objects.requireNonNull(entry.getKey(), name + " resource");
            Long amount = Objects.requireNonNull(entry.getValue(), name + " amount");
            if (amount <= 0) {
                throw new IllegalArgumentException(name + " amounts must be positive");
            }
            copy.put(resource, amount);
        });
        if (!allowEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return Collections.unmodifiableMap(copy);
    }

    @Nonnull
    public static NBTTagList writeExactAmounts(
          Map<PortableResourceDescriptor, BigInteger> amounts) {
        NBTTagList list = new NBTTagList();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("resource", entry.getKey().write());
            data.setString("amount", entry.getValue().toString());
            list.appendTag(data);
        });
        return list;
    }

    @Nonnull
    public static Map<PortableResourceDescriptor, BigInteger> readExactAmounts(
          NBTTagCompound parent, String key, int maximumEntries) throws QIOProcessingDataException {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("maximumEntries cannot be negative");
        }
        if (!parent.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(key + " is missing from current-schema data");
        }
        NBTTagList list = parent.getTagList(key, NBT.TAG_COMPOUND);
        if (list.tagCount() > maximumEntries) {
            throw new QIOProcessingDataException(key + " contains " + list.tagCount() +
                  " entries, above the limit " + maximumEntries);
        }
        Map<PortableResourceDescriptor, BigInteger> amounts = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey("resource", NBT.TAG_COMPOUND) ||
                  !entry.hasKey("amount", NBT.TAG_STRING)) {
                throw new QIOProcessingDataException(
                      "Incomplete exact amount entry in " + key + '[' + i + ']');
            }
            PortableResourceDescriptor resource;
            BigInteger amount;
            try {
                resource = PortableResourceDescriptor.read(entry.getCompoundTag("resource"));
                amount = new BigInteger(entry.getString("amount"));
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid exact amount in " + key + '[' + i + ']', e);
            }
            if (amount.signum() < 0) {
                throw new QIOProcessingDataException("Negative exact amount in " + key + '[' + i + ']');
            }
            if (amounts.put(resource, amount) != null) {
                throw new QIOProcessingDataException("Duplicate resource in " + key + ": " + resource);
            }
        }
        return Collections.unmodifiableMap(amounts);
    }

    @Nonnull
    public static Map<PortableResourceDescriptor, BigInteger> copyExactAmounts(
          Map<PortableResourceDescriptor, BigInteger> amounts, boolean allowEmpty, String name) {
        Objects.requireNonNull(amounts, name);
        Map<PortableResourceDescriptor, BigInteger> copy = new LinkedHashMap<>();
        amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            PortableResourceDescriptor resource = Objects.requireNonNull(entry.getKey(), name + " resource");
            BigInteger amount = Objects.requireNonNull(entry.getValue(), name + " amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException(name + " amounts cannot be negative");
            }
            copy.put(resource, amount);
        });
        if (!allowEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return Collections.unmodifiableMap(copy);
    }

    public static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    public static long requireRevision(long value, String name) {
        if (value < -1) {
            throw new IllegalArgumentException(name + " must be -1 or non-negative");
        }
        return value;
    }

    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
