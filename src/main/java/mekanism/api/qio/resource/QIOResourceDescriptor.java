package mekanism.api.qio.resource;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable, amount-free and world-independent QIO resource identity. */
public final class QIOResourceDescriptor implements Comparable<QIOResourceDescriptor> {

    private static final String CODEC = "codec";
    private static final String FAMILY = "family";
    private static final String CODEC_VERSION = "codecVersion";
    private static final String STORAGE_UNITS = "storageUnitsPerUnit";
    private static final String PAYLOAD = "payload";

    private final ResourceLocation codecId;
    private final String family;
    private final int codecVersion;
    private final long storageUnitsPerUnit;
    private final NBTTagCompound payload;
    private final boolean semanticIdentity;
    private final int semanticTypeHash;
    private final String stableKey;
    private final int hashCode;

    private QIOResourceDescriptor(ResourceLocation codecId, String family, int codecVersion,
          long storageUnitsPerUnit, NBTTagCompound payload) {
        this.codecId = Objects.requireNonNull(codecId, "QIO codec id cannot be null");
        this.family = QIOResourceFamily.requireValid(family);
        if (codecVersion <= 0) {
            throw new IllegalArgumentException("QIO codec version must be positive");
        }
        if (storageUnitsPerUnit <= 0) {
            throw new IllegalArgumentException("QIO storage units per resource must be positive");
        }
        this.codecVersion = codecVersion;
        this.storageUnitsPerUnit = storageUnitsPerUnit;
        NBTTagCompound canonicalPayload = canonicalCopy(Objects.requireNonNull(payload,
              "QIO resource payload cannot be null"));
        QIOResourceCodec<?> registered = QIOResourceCodecRegistry.INSTANCE.get(codecId);
        SemanticIdentity identity = createSemanticIdentity(registered, canonicalPayload);
        if (identity == null) {
            this.payload = canonicalPayload;
            semanticIdentity = false;
            semanticTypeHash = 0;
            hashCode = Objects.hash(codecId, family, codecVersion, storageUnitsPerUnit, this.payload);
        } else {
            this.payload = identity.canonicalPayload;
            semanticIdentity = true;
            semanticTypeHash = identity.typeHash;
            hashCode = Objects.hash(codecId, family, storageUnitsPerUnit, semanticTypeHash);
        }
        stableKey = codecId + "|" + family + "|" + codecVersion + "|" + storageUnitsPerUnit + "|" + this.payload;
    }

    @Nonnull
    public static <T> QIOResourceDescriptor of(@Nonnull QIOResourceCodec<T> codec, @Nonnull T value) {
        Objects.requireNonNull(codec, "QIO resource codec cannot be null");
        if (QIOResourceCodecRegistry.INSTANCE.get(codec.getCodecId()) != codec) {
            throw new IllegalArgumentException("QIO resource codec is not registered: " + codec.getCodecId());
        }
        String family = QIOResourceFamily.requireValid(codec.getFamily());
        int version = codec.getCodecVersion();
        long storageUnits = codec.getStorageUnitsPerUnit();
        T normalized = codec.normalize(Objects.requireNonNull(value, "QIO resource cannot be null"));
        NBTTagCompound payload = codec.writeTemplate(normalized);
        QIOResourceDescriptor descriptor = new QIOResourceDescriptor(codec.getCodecId(), family, version,
              storageUnits, payload);
        if (!descriptor.semanticIdentity) {
            throw new IllegalArgumentException("QIO codec failed its normalize/identity round trip: " +
                  codec.getCodecId());
        }
        return descriptor;
    }

    /** Creates a descriptor from trusted persistent metadata without requiring the codec to be installed. */
    @Nonnull
    public static QIOResourceDescriptor persisted(@Nonnull ResourceLocation codecId,
          @Nonnull String family, int codecVersion, long storageUnitsPerUnit,
          @Nonnull NBTTagCompound payload) {
        return new QIOResourceDescriptor(codecId, family, codecVersion, storageUnitsPerUnit, payload);
    }

    @Nonnull
    public ResourceLocation getCodecId() {
        return codecId;
    }

    @Nonnull
    public String getFamily() {
        return family;
    }

    public int getCodecVersion() {
        return codecVersion;
    }

    public long getStorageUnitsPerUnit() {
        return storageUnitsPerUnit;
    }

    @Nonnull
    public NBTTagCompound getPayload() {
        return payload.copy();
    }

    /** Whether the currently registered codec can safely decode this exact persisted descriptor. */
    public boolean isResolved() {
        return semanticIdentity && matchesMetadata(QIOResourceCodecRegistry.INSTANCE.get(codecId));
    }

    @Nullable
    public Object resolve() {
        if (!semanticIdentity) {
            return null;
        }
        QIOResourceCodec<?> codec = QIOResourceCodecRegistry.INSTANCE.get(codecId);
        if (!matchesMetadata(codec)) {
            return null;
        }
        try {
            return decodeUnchecked(codec);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public <T> T resolve(@Nonnull QIOResourceCodec<T> codec) {
        Objects.requireNonNull(codec, "QIO resource codec cannot be null");
        if (!semanticIdentity || !codecId.equals(codec.getCodecId()) || !matchesMetadata(codec)) {
            return null;
        }
        try {
            return codec.normalize(codec.readTemplate(payload.copy(), codecVersion));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(CODEC, codecId.toString());
        data.setString(FAMILY, family);
        data.setInteger(CODEC_VERSION, codecVersion);
        data.setLong(STORAGE_UNITS, storageUnitsPerUnit);
        data.setTag(PAYLOAD, payload.copy());
        return data;
    }

    @Nonnull
    public static QIOResourceDescriptor read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "QIO resource descriptor cannot be null");
        if (!data.hasKey(CODEC, NBT.TAG_STRING) || !data.hasKey(FAMILY, NBT.TAG_STRING) ||
              !data.hasKey(CODEC_VERSION, NBT.TAG_INT) || !data.hasKey(STORAGE_UNITS, NBT.TAG_LONG) ||
              !data.hasKey(PAYLOAD, NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Incomplete QIO resource descriptor");
        }
        ResourceLocation codecId;
        try {
            codecId = new ResourceLocation(data.getString(CODEC));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid QIO resource codec id: " + data.getString(CODEC), e);
        }
        return persisted(codecId, data.getString(FAMILY), data.getInteger(CODEC_VERSION),
              data.getLong(STORAGE_UNITS), data.getCompoundTag(PAYLOAD));
    }

    @Override
    public int compareTo(@Nonnull QIOResourceDescriptor other) {
        Objects.requireNonNull(other, "other");
        // This is a deterministic serialization order, not the codec's semantic identity order.
        // sameType may equate different payloads and cannot define a total order by itself.
        return stableKey.compareTo(other.stableKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof QIOResourceDescriptor other)) {
            return false;
        }
        if (storageUnitsPerUnit != other.storageUnitsPerUnit || !codecId.equals(other.codecId) ||
              !family.equals(other.family)) {
            return false;
        }
        if (semanticIdentity != other.semanticIdentity) {
            return false;
        }
        if (!semanticIdentity) {
            return codecVersion == other.codecVersion && payload.equals(other.payload);
        }
        if (semanticTypeHash != other.semanticTypeHash) {
            return false;
        }
        QIOResourceCodec<?> codec = QIOResourceCodecRegistry.INSTANCE.get(codecId);
        if (!matchesMetadata(codec) || !other.matchesMetadata(codec)) {
            return codecVersion == other.codecVersion && payload.equals(other.payload);
        }
        try {
            return sameTypeUnchecked(codec, decodeUnchecked(codec), other.decodeUnchecked(codec));
        } catch (RuntimeException ignored) {
            return codecVersion == other.codecVersion && payload.equals(other.payload);
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return stableKey;
    }

    private boolean matchesMetadata(@Nullable QIOResourceCodec<?> codec) {
        return codec != null && codecId.equals(codec.getCodecId()) && family.equals(codec.getFamily()) &&
              storageUnitsPerUnit == codec.getStorageUnitsPerUnit();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object decodeUnchecked(QIOResourceCodec<?> codec) {
        QIOResourceCodec rawCodec = codec;
        return rawCodec.normalize(rawCodec.readTemplate(payload.copy(), codecVersion));
    }

    @Nullable
    private SemanticIdentity createSemanticIdentity(@Nullable QIOResourceCodec<?> codec,
          NBTTagCompound sourcePayload) {
        if (!matchesMetadata(codec)) {
            return null;
        }
        try {
            Object normalized = decodeUnchecked(codec, sourcePayload);
            int typeHash = typeHashUnchecked(codec, normalized);
            NBTTagCompound rewritten = canonicalCopy(writeTemplateUnchecked(codec, normalized));
            Object roundTripped = decodeUnchecked(codec, rewritten);
            if (!sameTypeUnchecked(codec, normalized, roundTripped) ||
                  typeHash != typeHashUnchecked(codec, roundTripped)) {
                throw new IllegalArgumentException("QIO codec identity contract failed for " + codecId);
            }
            return new SemanticIdentity(rewritten, typeHash);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object decodeUnchecked(QIOResourceCodec<?> codec, NBTTagCompound sourcePayload) {
        QIOResourceCodec rawCodec = codec;
        return rawCodec.normalize(rawCodec.readTemplate(sourcePayload.copy(), codecVersion));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean sameTypeUnchecked(QIOResourceCodec<?> codec, Object first, Object second) {
        return ((QIOResourceCodec) codec).sameType(first, second);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int typeHashUnchecked(QIOResourceCodec<?> codec, Object value) {
        return ((QIOResourceCodec) codec).typeHash(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NBTTagCompound writeTemplateUnchecked(QIOResourceCodec<?> codec, Object value) {
        return ((QIOResourceCodec) codec).writeTemplate(value);
    }

    private static final class SemanticIdentity {

        private final NBTTagCompound canonicalPayload;
        private final int typeHash;

        private SemanticIdentity(NBTTagCompound canonicalPayload, int typeHash) {
            this.canonicalPayload = canonicalPayload;
            this.typeHash = typeHash;
        }
    }

    private static NBTTagCompound canonicalCopy(NBTTagCompound compound) {
        NBTTagCompound copy = new NBTTagCompound();
        List<String> keys = new ArrayList<>(compound.getKeySet());
        Collections.sort(keys);
        for (String key : keys) {
            copy.setTag(key, canonicalCopy(compound.getTag(key)));
        }
        return copy;
    }

    private static NBTBase canonicalCopy(NBTBase value) {
        if (value instanceof NBTTagCompound compound) {
            return canonicalCopy(compound);
        }
        if (value instanceof NBTTagList list) {
            NBTTagList copy = new NBTTagList();
            for (NBTBase element : list) {
                copy.appendTag(canonicalCopy(element));
            }
            return copy;
        }
        return value.copy();
    }
}
