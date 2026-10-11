package mekanism.qioprocessing.common.util;

import net.minecraft.nbt.NBTTagLongArray;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * Small compatibility helpers for NBT data that has no public 1.12 accessor.
 */
public final class QIONbtUtils {

    private static final Field LONG_ARRAY_DATA = findLongArrayDataField();

    private QIONbtUtils() {
    }

    /**
     * Conservative bounded allocation estimate for admission checks; never constructs stacks or codecs.
     */
    public static long boundedSize(net.minecraft.nbt.NBTBase value, long limit) {
        return boundedSize(value, limit, 0);
    }

    private static long boundedSize(net.minecraft.nbt.NBTBase value, long limit, int depth) {
        if (limit < 0 || depth > 128) return Long.MAX_VALUE;
        if (value == null) return 0;
        long size = 64;
        switch (value.getId()) {
            case 7:
                size += ((net.minecraft.nbt.NBTTagByteArray) value).getByteArray().length;
                break;
            case 8:
                size += 3L * ((net.minecraft.nbt.NBTTagString) value).getString().length();
                break;
            case 11:
                size += 4L * ((net.minecraft.nbt.NBTTagIntArray) value).getIntArray().length;
                break;
            case 12:
                size += 8L * longArrayLength((NBTTagLongArray) value);
                break;
            case 9:
                for (net.minecraft.nbt.NBTBase child : (net.minecraft.nbt.NBTTagList) value) {
                    long childSize = boundedSize(child, limit - size, depth + 1);
                    if (childSize > limit - size) return Long.MAX_VALUE;
                    size += childSize;
                }
                break;
            case 10:
                net.minecraft.nbt.NBTTagCompound compound = (net.minecraft.nbt.NBTTagCompound) value;
                for (String key : compound.getKeySet()) {
                    size += 64L + 3L * key.length();
                    if (size > limit) return Long.MAX_VALUE;
                    long childSize = boundedSize(compound.getTag(key), limit - size, depth + 1);
                    if (childSize > limit - size) return Long.MAX_VALUE;
                    size += childSize;
                }
                break;
            default:
                break;
        }
        return size > limit ? Long.MAX_VALUE : size;
    }

    public static int longArrayLength(@Nonnull NBTTagLongArray tag) {
        try {
            long[] values = (long[]) LONG_ARRAY_DATA.get(tag);
            if (values == null) {
                throw new IllegalStateException("NBTTagLongArray has no backing data");
            }
            return values.length;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Unable to inspect NBTTagLongArray", error);
        }
    }

    private static Field findLongArrayDataField() {
        try {
            // Resolve the SRG name through Forge so this also finds the single-letter field in
            // a production-obfuscated 1.12.2 runtime.
            return ObfuscationReflectionHelper.findField(NBTTagLongArray.class,
                    "field_193587_b");
        } catch (RuntimeException ignored) {
            // Development launches may not have the remapper initialized yet; use literal names.
        }
        for (String name : new String[]{"data", "field_193587_b"}) {
            try {
                Field field = NBTTagLongArray.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
                // Try the development and SRG names before failing module initialization.
            }
        }
        throw new IllegalStateException("Unable to locate NBTTagLongArray backing data");
    }
}
