package mekanism.qioprocessing.common.util;

import net.minecraft.nbt.NBTTagLongArray;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/** Small compatibility helpers for NBT data that has no public 1.12 accessor. */
public final class QIONbtUtils {

    private static final Field LONG_ARRAY_DATA = findLongArrayDataField();

    private QIONbtUtils() {
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
